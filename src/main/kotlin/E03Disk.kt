package ose.processing

import kotlin.math.ceil

data class FreeBlock(
    val blockId: Int,
    val count: Int
)

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

class DiskManager(private val totalBlock: Int, private val blockSize: Int = 1) {

    private val freeBlocks = mutableListOf(FreeBlock(0, totalBlock)) // 默认全部空闲
    private val files = mutableListOf<FAT>()
    private var restBlock = totalBlock

    fun createFile(fileName: String, size: Int): Boolean {
        val sizeInBlock = ceil((size / blockSize).toDouble()).toInt()
        if (sizeInBlock > restBlock) return false
        val blockTable = IntArray(sizeInBlock)
        var p = 0
        while (p < sizeInBlock) {
            blockTable[p] = freeBlocks[0].blockId

            p++
            if (freeBlocks[0].count - 1 == 0) {
                freeBlocks.removeAt(0)
            } else {
                // 频繁分配新对象，效率不高（不过只考虑实现，所以不考虑此问题）
                freeBlocks[0] =
                    freeBlocks[0].copy(
                        blockId = freeBlocks[0].blockId + 1,
                        count = freeBlocks[0].count - 1
                    )
            }
        }
        restBlock -= sizeInBlock
        files.add(FAT(fileName, size, blockTable))
        return true
    }

    fun deleteFile(fileName: String): Boolean {
        val id = files.indexOfFirst { it.fileName == fileName }
        if (id < 0) return false
        val fat = files.removeAt(id)
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
            println("起始空闲块：${it.blockId}，块数：${it.count}")
        }
        files.forEach {
            println("文件名：${it.fileName}，文件占用大小：${it.size}，占用块表：${it.blockTable.joinToString()}")
        }
    }

}

fun main() {
    val diskManager = DiskManager(64)
    diskManager.createFile("README.md", 20)
    diskManager.createFile("hello.cpp", 1)
    diskManager.createFile("numcpp.cpp", 5)
    diskManager.displayFreeBlock()
    diskManager.deleteFile("numcpp.cpp")
    diskManager.displayFreeBlock()
}