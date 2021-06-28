package ose.processing

import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.ceil

// 在分页管理方式下采用位示图来表示主存分配情况，实现主存分配和回收
// 要求:
// 1.	假定系统的主存被分成大小相等的64个块，用0/1对应空闲/占用。
// 2.	当要装入一个作业时，根据作业对主存的需求量，先查空闲块数是否能满足作业要求，若能满足，则查位示图，修改位示图和空闲块数。位置与块号的对应关系为：
// 块号=j*8+i，其中i表示位，j表示字节。
// 根据分配的块号建立页表。页表包括两项：页号和块号。
// 3.	回收时，修改位示图和空闲块数。
// 要求能接受来自键盘的空间申请及释放请求，能显示位示图和空闲块数的变化，能显示进程的页表。

class Bitmap private constructor(private val bitsCount: Int) {

    private val bitmap = ByteArray(ceil(bitsCount / 8.0f).toInt())

    operator fun get(pos: Int): Boolean {
        val pByte = pos / 8
        val offset = pos % 8
        return (bitmap[pByte] and (((1 shl (7 - offset)).toByte()))) != 0.toByte()
    }

    operator fun set(pos: Int, value: Boolean) {
        val pByte = pos / 8
        val offset = pos % 8

        if (value) {
            bitmap[pByte] = bitmap[pByte] or ((1 shl (7 - offset)).toByte())
        } else {
            bitmap[pByte] = bitmap[pByte] and (1 shl (7 - offset)).inv().toByte()
        }
    }

    fun size() = bitsCount

    companion object {
        @JvmStatic
        fun fromBitsCount(count: Int): Bitmap {
            return Bitmap(count)
        }
    }

}

data class Job(
    val name: String,
    val jobSize: Int,
    val pages: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Job

        if (name != other.name) return false
        if (!pages.contentEquals(other.pages)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + pages.contentHashCode()
        return result
    }
}

class MemoryManager(blockCount: Int, val blockSize: Int) {

    private val memoryBitmap = Bitmap.fromBitsCount(blockCount)
    private var restBlocks = blockCount
    private val jobs = mutableListOf<Job>()

    fun newJob(name: String, jobSize: Int): Boolean {
        val sizeInPage = ceil(jobSize.toDouble() / blockSize).toInt()

        // 请求分配页
        if (sizeInPage <= restBlocks) {
            // 开始分配空闲页
            val pages = IntArray(sizeInPage)
            var p = 0
            var block = 0
            while (p < sizeInPage) {
                if (!memoryBitmap[block]) {
                    pages[p++] = block
                    memoryBitmap[block] = true
                }
                block++
            }
            restBlocks -= sizeInPage

            jobs.add(Job(name, jobSize, pages))
            return true
        } else {
            return false
        }
    }

    fun freeJob(name: String): Boolean {
        val id = jobs.indexOfFirst { it.name == name }
        if (id < 0) return false
        val job = jobs.removeAt(id)
        restBlocks += job.pages.size
        job.pages.forEach { memoryBitmap[it] = false }
        return true
    }

    fun display() {
        // 这样效率蛮低的
        print("当前内存块位示图：")
        for (block in 0 until memoryBitmap.size()) {
            if (block % 8 == 0) println()
            print(if (memoryBitmap[block]) 1 else 0)
            print(" ")
        }
        println()

        println("作业：")
        jobs.forEach { job ->
            println("作业名：${job.name}, 页表(页 -> 块)：" + buildString {
                job.pages.forEachIndexed { index, i ->
                    append("$index->$i ")
                }
            })
        }
    }

}

fun main() {
    val memoryManager = MemoryManager(64, 1)

    while (true) {
        memoryManager.display()

        println("请输入您接下来的操作：N.创建作业 F.销毁作业 Q.退出")
        when (readLine()?.uppercase()) {
            "N" -> {
                print("请输入作业名：")
                val name = readLine() ?: break
                print("请输入作业大小（块大小${memoryManager.blockSize}）：")
                val size = readLine()?.toInt() ?: break
                if (memoryManager.newJob(name, size)) {
                    println("作业创建成功！")
                } else {
                    println("空间不足，作业创建失败！")
                }
            }
            "F" -> {
                print("请输入作业名：")
                val name = readLine() ?: break
                if (memoryManager.freeJob(name)) {
                    println("销毁作业成功")
                } else {
                    println("未找到该作业")
                }
            }
            "Q" -> {
                break
            }
        }
    }

}