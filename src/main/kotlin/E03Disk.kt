package ose.processing

import java.io.File
import java.io.IOException
import kotlin.math.ceil

// 连续磁盘存储空间的分配和回收
// 实验要求：
// 1.	要在磁盘上建立顺序文件时，必须把按序排列的逻辑记录依次存放在磁盘的连续存储空间中。可假定磁盘初始化时，已把磁盘存储空间划分成若干等长
//      的块（扇区），按柱面号和盘面号的顺序给每一块确定一个编号。随着文件的建立、删除、磁盘存储空间被分成许多区（每一区包含若干块），有的区存放着文件，而有的区是空闲的。当要建立顺序文件时必须找到一个合适的空闲区来存放文件记录，当一个文件被删除时，则该文件占用的区应成为空闲区。为此可用一张空闲区表来记录磁盘存储空间中尚未占用的部分，格式如下：
//
// 表3.1□初始空闲内存示意图
//
// 2.	建立文件时，先查找空闲区表，从空闲区表中找出一个块数能满足要求的区，由起始空闲块号能依次推得可使用的其它块号。若不需要占用该区的所有
//      块时，则剩余的块仍应为未分配的空闲块，这时要修改起始空闲块号和空闲块数。若占用了该区的所有块，则删去该空闲区。删除一个文件时，需要考
//      虑空闲块的合并情况。
// 3.	当找到空闲块后，必须启动磁盘把信息存放到指定的块中，启动磁盘必须给出由三个参数组成的物理地址：盘面号、柱面号和物理记录号（即扇区号）。
//      故必须把找到的空闲块号换算成磁盘的物理地址。
//      为了减少移臂次数，磁盘上的信息按柱面上各磁道顺序存放。现假定一个盘组共有200个柱面，（编号0-199）每个柱面有20个磁道（编号0-19，同
//      一柱面上的各磁道分布在各盘面上，故磁道号即盘面号。），每个磁道被分成等长的6个物理记录（编号0-5，每个盘面被分成若干个扇区，故每个磁道
//      上的物理记录号即为对应的扇区号）。那么，空闲块号与磁盘物理地址的对应关系如下：
//          物理记录号 = 空闲块号 % 6
//          磁道号=（空闲块号 / 6 ）% 20
//          柱面号=（空闲块号 / 6）/20
// 4.	删除一个文件时，从文件目录表中可得到该文件在磁盘上的起始地址和逻辑记录个数，假定每个逻辑记录占磁盘上的一块，则可推算出归还后的起始空
//      闲块号和块数，登记到空闲区表中。换算关系如下：
//          起始空闲块号=（柱面号*20+磁道号）*6+物理记录号
//          空闲块数=逻辑记录数
//
// 要求把分配到的空闲块转换成磁盘物理地址，把归还的磁盘空间转换成空闲块号。
// 要求能接受来自键盘或文件的空间申请及释放请求，能显示或打印分配及回收后的空闲区表以及分配到的磁盘空间的起始物理地址：包括柱面号、磁道号、
// 物理记录号（扇区号）。



/**
 * 空闲磁盘块描述
 *
 * @property block 起始空闲块
 * @property count 空闲块数目
 */
data class FreeBlock(
    val block: Int,
    val count: Int
)

/**
 * 文件分配表项
 *
 * @property fileName 文件名
 * @property size 文件大小
 * @property blockTable 分配磁盘块表
 */
data class FAT(
    val fileName: String,
    val size: Int,
    val blockTable: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FAT

        if (fileName != other.fileName) return false
        if (size != other.size) return false
        if (!blockTable.contentEquals(other.blockTable)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + size
        result = 31 * result + blockTable.contentHashCode()
        return result
    }
}

/**
 * 磁盘
 *
 * @property cylinderCount 柱面数
 * @property trackCount 磁道数（每个盘面上的磁道数）
 * @property sectorCount 扇区数（每个盘面上的扇区数）
 */
class Disk(
    val cylinderCount: Int,
    val trackCount: Int,
    val sectorCount: Int
) {

    val totalSectorCount = cylinderCount * trackCount * sectorCount

    /**
     * 寻道至对应的逻辑块
     *
     * @param block 逻辑块
     */
    fun seek(block: Int) {
        PhysicalSector.fromBlock(block, this).let {
            println("柱面：${it.cylinder}, 磁道：${it.track}, 扇区：${it.sector}")
        }
    }

}

/**
 * 物理扇区，描述一个具体扇区的物理位置
 *
 * @property cylinder 柱面号
 * @property track 磁道号
 * @property sector 扇区号
 */
data class PhysicalSector(
    val cylinder: Int,
    val track: Int,
    val sector: Int
) {

    companion object {

        /**
         * 将逻辑块转换到对应磁盘的物理扇区描述
         *
         * @param block 逻辑块
         * @param disk 磁盘
         */
        @JvmStatic
        fun fromBlock(block: Int, disk: Disk) = PhysicalSector(
            cylinder = block / disk.trackCount / disk.sectorCount,
            track = (block / disk.sectorCount) % 60,
            sector = block % disk.sectorCount
        )
    }

}

/**
 * 磁盘管理器
 *
 * @property disk 磁盘
 * @property blockSize 块大小
 */
class DiskManager(private val disk: Disk, private val blockSize: Int = 1) {

    private val totalBlock: Int = disk.totalSectorCount

    private val freeBlocks = mutableListOf(FreeBlock(0, totalBlock)) // 默认全部空闲
    private val files = mutableListOf<FAT>()
    private var restBlock = totalBlock

    fun createFile(fileName: String, size: Int): Boolean {
        val sizeInBlock = ceil((size / blockSize).toDouble()).toInt()
        println("正在创建文件：$fileName")
        if (sizeInBlock > restBlock) {
            throw IOException("磁盘空间不足")
        }
        if (files.any { it.fileName == fileName }) {
            throw FileAlreadyExistsException(File(fileName))
        }
        val blockTable = IntArray(sizeInBlock)
        var p = 0
        while (p < sizeInBlock) {
            blockTable[p] = freeBlocks[0].block
            p++
            if (freeBlocks[0].count - 1 == 0) {
                freeBlocks.removeAt(0)
            } else {
                val block = freeBlocks[0].block + 1
                seekDisk(block)
                // 频繁分配新对象，效率不高（不过只考虑实现，所以不考虑此问题）
                freeBlocks[0] =
                    freeBlocks[0].copy(
                        block = block,
                        count = freeBlocks[0].count - 1
                    )
            }
        }
        restBlock -= sizeInBlock
        files.add(FAT(fileName, size, blockTable))
        return true
    }

    private fun seekDisk(block: Int) {
        disk.seek(block)
    }

    fun deleteFile(fileName: String): Boolean {
        val id = files.indexOfFirst { it.fileName == fileName }
        if (id < 0) return false
        val fat = files.removeAt(id)

        val blocks = collectFreeBlocks(fat)
        mergeFreeBlocks(blocks)

        restBlock += fat.blockTable.size

        return true
    }

    /**
     * 合并空闲块
     *
     * @param blocks 空闲块
     */
    private fun mergeFreeBlocks(blocks: MutableList<FreeBlock>) {
        freeBlocks.addAll(blocks)
        freeBlocks.sortBy { it.block }
        var i = freeBlocks.size - 1
        while (i > 0) {
            if (freeBlocks[i - 1].block + freeBlocks[i - 1].count == freeBlocks[i].block) {
                freeBlocks[i - 1] = freeBlocks[i - 1].copy(count = freeBlocks[i - 1].count + freeBlocks[i].count)
                freeBlocks.removeAt(i)
            }
            i--
        }
    }

    /**
     * 收集文件的块
     *
     * @param fat
     * @return
     */
    private fun collectFreeBlocks(fat: FAT): MutableList<FreeBlock> {
        val blocks = mutableListOf<FreeBlock>()
        var blockId = -1
        var count = 0
        fat.blockTable.forEach {
            if (blockId + count == it) {
                count++
            } else {
                if (blockId >= 0) {
                    blocks.add(FreeBlock(blockId, count))
                } else {
                    blockId = it
                    count = 1
                }
            }
        }
        if (blocks.lastOrNull()?.block != blockId) {
            // 说明最后的空闲块没有加入到列表中，所以需要特殊处理一下
            blocks.add(FreeBlock(blockId, count))
        }
        return blocks
    }

    /**
     * 展示磁盘占用
     *
     */
    fun showUsageInfo() {
        println("剩余空闲块数：$restBlock")
        freeBlocks.forEach {
            println("起始空闲块：${it.block}，块数：${it.count}")
        }
        files.forEach {
            println("文件名：${it.fileName}，文件占用大小：${it.size}，占用块表：${it.blockTable.joinToString()}")
        }
    }

}

fun main() {
    val diskManager = DiskManager(Disk(200, 20, 6))
    // diskManager.createFile("README.md", 20)
    // diskManager.createFile("hello.cpp", 1)
    // diskManager.createFile("numcpp.cpp", 5)
    // diskManager.displayFreeBlock()
    // diskManager.deleteFile("hello.cpp")
    // diskManager.displayFreeBlock()

    while (true) {
        diskManager.showUsageInfo()
        println("N.创建文件 D.删除文件 Q.退出")
        val command = readLine() ?: continue
        when (command.uppercase()) {
            "N" -> {
                println("请输入文件名：")
                val fileName = readLine() ?: continue
                println("请输入文件大小：")
                val size = readLine()?.toInt() ?: continue
                try {
                    diskManager.createFile(fileName, size)
                } catch (e: FileAlreadyExistsException) {
                    println("文件已存在")
                } catch (e: IOException) {
                    println(e.message)
                }
            }
            "D" -> {
                println("请输入欲删除文件的文件名：")
                val fileName = readLine() ?: continue
                if (diskManager.deleteFile(fileName)) {
                    println("删除成功")
                } else {
                    println("删除失败，文件不存在")
                }
            }
            "Q" -> {
                break
            }
        }
    }
}