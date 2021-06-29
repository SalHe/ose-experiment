package ose.processing

import kotlin.math.ceil

@JvmInline
value class Block(val id: Int)

data class FreeBlock(
    val block: Block,
    val count: Int
)

data class FAT(
    val fileName: String,
    val size: Int,
    val blockTable: Array<Block>
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

class Disk(
    val cylinderCount: Int,
    val trackCount: Int,
    val sectorCount: Int
) {

    val totalSectorCount = cylinderCount * trackCount * sectorCount

    fun seek(block: Block) {
        PhysicalSector.fromBlock(block, this).let {
            println("柱面：${it.cylinder}, 磁道：${it.track}, 扇区：${it.sector}")
        }
    }

}

data class PhysicalSector(
    val cylinder: Int,
    val track: Int,
    val sector: Int
) {

    companion object {
        @JvmStatic
        fun fromBlock(block: Block, disk: Disk) = PhysicalSector(
            cylinder = block.id / disk.trackCount / disk.sectorCount,
            track = (block.id / disk.sectorCount) % 60,
            sector = block.id % disk.sectorCount
        )
    }

}

class DiskManager(private val disk: Disk, private val blockSize: Int = 1) {

    private val totalBlock: Int = disk.totalSectorCount

    private val freeBlocks = mutableListOf(FreeBlock(Block(0), totalBlock)) // 默认全部空闲
    private val files = mutableListOf<FAT>()
    private var restBlock = totalBlock

    fun createFile(fileName: String, size: Int): Boolean {
        val sizeInBlock = ceil((size / blockSize).toDouble()).toInt()
        println("正在创建文件：$fileName")
        if (sizeInBlock > restBlock) {
            println("剩余磁盘空间不足！")
            return false
        }
        val blockTable = IntArray(sizeInBlock)
        var p = 0
        while (p < sizeInBlock) {
            blockTable[p] = freeBlocks[0].block.id
            p++
            if (freeBlocks[0].count - 1 == 0) {
                freeBlocks.removeAt(0)
            } else {
                val block = Block(freeBlocks[0].block.id + 1)
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
        files.add(FAT(fileName, size, blockTable.map { Block(it) }.toTypedArray()))
        return true
    }

    private fun seekDisk(block: Block) {
        disk.seek(block)
    }

    fun deleteFile(fileName: String): Boolean {
        val id = files.indexOfFirst { it.fileName == fileName }
        if (id < 0) return false
        val fat = files.removeAt(id)
        val blocks = mutableListOf<FreeBlock>()
        var blockId = -1
        var count = 0
        fat.blockTable.forEach {
            if (blockId + count == it.id) {
                count++
            } else {
                if (blockId >= 0) {
                    blocks.add(FreeBlock(Block(blockId), count))
                } else {
                    blockId = it.id
                    count = 0
                }
            }
        }

        // TODO 将blocks与freeBlocks合并

        return true
    }

    fun displayFreeBlock() {
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
    diskManager.createFile("README.md", 20)
    diskManager.createFile("hello.cpp", 1)
    diskManager.createFile("numcpp.cpp", 5)
    diskManager.displayFreeBlock()
    diskManager.deleteFile("numcpp.cpp")
    diskManager.displayFreeBlock()
}