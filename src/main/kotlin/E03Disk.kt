package ose.processing

import java.io.File
import java.io.IOException
import kotlin.math.ceil

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

    /**
     * 创建文件
     *
     * @param fileName 文件名
     * @param size 文件大小
     * @return
     */
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

    /**
     * 寻道
     *
     * @param block
     */
    private fun seekDisk(block: Int) {
        disk.seek(block)
    }

    /**
     * 删除文件
     *
     * @param fileName 文件名
     * @return
     */
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
        files.forEach(this::showFile)
    }

    /**
     * 展示FAT
     *
     * @param file FAT项
     */
    fun showFile(file: FAT) {
        println("文件名：${file.fileName}，文件占用大小：${file.size}，占用块表：${file.blockTable.joinToString()}")
    }

    /**
     * 查找文件
     *
     * @param fileName 文件名
     * @return
     */
    fun findFile(fileName: String): FAT? {
        return files.firstOrNull { it.fileName == fileName }
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
        println("N.创建文件 D.删除文件 S.选择文件并显示其块表 Q.退出")
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
            "S" -> {
                println("请输入欲删除文件的文件名：")
                val fileName = readLine() ?: continue
                diskManager.findFile(fileName).let {
                    if (it == null) {
                        println("未找到文件")
                    } else {
                        diskManager.showFile(it)
                    }
                }
            }
            "Q" -> {
                break
            }
        }
    }
}