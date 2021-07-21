package ose.processing

import kotlin.IllegalArgumentException

/**
 * 设备
 *
 * @property name 设备名
 * @property total 设备总量
 * @property used 已占用设备
 * @property available 可用设备
 */
data class Device(
    val name: String,
    val total: Int,
    val used: Int
) {
    val available: Int
        get() = total - used
}

/**
 * 设备分配表
 *
 * @property process 进程名
 * @property maxDevicesCount 最大设备数目
 * @property devicesCount 已占有设备数目
 */
data class DATItem(
    val process: String,
    val maxDevicesCount: IntArray,
    val devicesCount: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DATItem

        if (process != other.process) return false
        if (!devicesCount.contentEquals(other.devicesCount)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = process.hashCode()
        result = 31 * result + devicesCount.contentHashCode()
        return result
    }
}

/**
 * 设备资源矩阵
 *
 * 作为[ose.processing.DeviceManager.getDevicesMatrix]的返回中间结果。
 * 建议使用结构语法使用返回值。
 *
 * @property available 可用设备矩阵
 * @property maxDevices 最大设备占用数矩阵
 * @property holdDevices 已占有设备数矩阵
 * @property needDevices 仍需设备数据帧
 * @property finish 进程完成矩阵，总为false，方便用于计算生成的临时数组
 */
data class DevicesMatrix(
    val available: Array<Int>,
    val maxDevices: Array<IntArray>,
    val holdDevices: Array<IntArray>,
    val needDevices: Array<IntArray>,
    val finish: BooleanArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DevicesMatrix

        if (!available.contentEquals(other.available)) return false
        if (!maxDevices.contentDeepEquals(other.maxDevices)) return false
        if (!holdDevices.contentDeepEquals(other.holdDevices)) return false
        if (!needDevices.contentDeepEquals(other.needDevices)) return false
        if (!finish.contentEquals(other.finish)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = available.contentHashCode()
        result = 31 * result + maxDevices.contentDeepHashCode()
        result = 31 * result + holdDevices.contentDeepHashCode()
        result = 31 * result + needDevices.contentDeepHashCode()
        result = 31 * result + finish.contentHashCode()
        return result
    }
}

/**
 * 设备管理器
 *
 * @constructor
 * 根据给定的设备列表创建设备管理器
 *
 * @param devices 给定设备列表
 */
class DeviceManager(devices: List<Device>) {

    private val allDevices = devices.toMutableList()
    private val dat = mutableListOf<DATItem>()
    val deviceKindCount: Int
        get() = allDevices.size
    val processCount: Int
        get() = dat.size

    private fun getDevicesMatrix(): DevicesMatrix {
        val available = allDevices.map { it.available }.toTypedArray()                     // 可用资源数
        val maxDevices = dat.map { it.maxDevicesCount.copyOf() }.toTypedArray()                     // 最大资源数
        val holdDevices = dat.map { it.devicesCount.copyOf() }.toTypedArray()                       // 已占有设备数
        val needDevices = Array<IntArray>(processCount) { processId ->                          // 需要设备数
            IntArray(deviceKindCount) { deviceId -> maxDevices[processId][deviceId] - holdDevices[processId][deviceId] }
        }
        val finish = BooleanArray(processCount)                                             // 已完成（默认false）
        return DevicesMatrix(available, maxDevices, holdDevices, needDevices, finish)
    }

    /**
     * 判断请求设备是否安全（使用银行家算法）
     *
     * @param process 进程名（应保证该进程存在）
     * @param devices 请求设备数
     * @return
     */
    private fun canRequireDevices(process: String, devices: IntArray): Boolean {
        // 实现银行家算法
        val (available, _, holdDevices, needDevices, finish) = getDevicesMatrix()
        val id = dat.indexOfFirst { it.process == process } // 当前进程对应ID

        // 首先得为给定进程分配设备
        for (i in available.indices) {
            if (available[i] < devices[i])
                return false
            available[i] -= devices[i]
            holdDevices[id][i] += devices[i]
            needDevices[id][i] -= devices[i]
        }
        // finish[id] = true

        // 现在考虑为其他进程分配
        while (true) {
            // 寻找第一个可分配的设备
            val nextProcessId = needDevices.withIndex().indexOfFirst { (processId, processNeedDevices) ->
                !finish[processId]  // 保证进程未完成
                        &&
                        // 进程是否能完成分配？
                        processNeedDevices.withIndex()
                            .all { (deviceKind, needDevice) -> available[deviceKind] >= needDevice }
            }

            // 找不到可分配设备进程说明：
            //      1. 进程全部完成分配
            //      2. 存在未完成进程，但死锁
            if (nextProcessId < 0) break

            // 如果可以分配假设给其分配，然后回收
            // 可以不用处理对应的max, need，因为不会在遍历它
            // 但是需要处理available
            for (i in available.indices) {
                available[i] += holdDevices[nextProcessId][i]
            }

            // 可以不更新进程对应的设备持有数、需要数
            // 因为下一次分配一定不会处理他
            finish[nextProcessId] = true
        }

        return finish.all { it }
    }

    /**
     * 获取设备顺序序列
     *
     */
    fun getDevicesSequence() = allDevices.map { it.name }

    /**
     * 创建进程
     *
     * @param process 进程名
     * @param maxDevices 最大设备数
     */
    fun createProcess(process: String, maxDevices: IntArray) {
        if (dat.any { it.process == process })
            throw IllegalArgumentException("已存在的进程")
        if (maxDevices.size != deviceKindCount)
            throw IllegalArgumentException("设备最大数不能正确对应设备种类")
        dat.add(DATItem(process, maxDevices, IntArray(deviceKindCount)))
    }

    /**
     * 请求设备
     *
     * 如果该次请求会使得系统进入不安全态，那么请求会被拒绝，返回false。
     *
     * @param process 欲请求设备的进程
     * @param devices 设备请求数
     * @return 是否请求成功
     */
    fun requireDevices(process: String, devices: IntArray): Boolean {
        // 如果保证其处于安全状态，即可分配设备
        val processId = dat.indexOfFirst { it.process == process }
        if (processId < 0) throw IllegalStateException("不存在该进程")
        val datItem = dat[processId]

        if (!canRequireDevices(process, devices)) return false

        // 更新DAT表项及设备资源表
        for (i in datItem.devicesCount.indices) {
            datItem.devicesCount[i] += devices[i]
            allDevices[i] = allDevices[i].let { it.copy(used = it.used + devices[i]) }
        }
        return true
    }

    /**
     * 销毁进程并释放其占用的设备
     *
     * @param process 进程名
     */
    fun destroyProcess(process: String) {
        val id = dat.indexOfFirst { it.process == process }
        if (id < 0) throw IllegalStateException("找不到对应进程")
        val datItem = dat.removeAt(id)
        datItem.devicesCount.forEachIndexed { index, count ->
            val device = allDevices[index]
            allDevices[index] = device.copy(used = device.used - count)
        }
    }

    /**
     * 展示设备使用情况
     *
     */
    fun displayDevices() {
        println("设备表：")
        allDevices.forEach { println("设备：${it.name}, 资源数：${it.available}, 已占用：${it.used}, 剩余可用：${it.available}") }
        println("资源占用(" + getDevicesSequence().joinToString(" ") + ")：")
        dat.forEach {
            println("${it.process}, 最大需求：${it.maxDevicesCount.joinToString()}, 已占用：${it.devicesCount.joinToString()}")
        }
    }

}

/**
 * 从标准输入读入一个整形数组，以空格分割。
 *
 */
private fun inputIntArray() = readLine()?.split(" ")?.map {
    try {
        return@map it.toInt()
    } catch (e: Exception) {
        return@map 0
    }
}?.toIntArray()

fun main() {

    var example = false
    val deviceManager = DeviceManager(
        createDevicesList().let {
            if (it.size == 0) {
                example = true
                listOf(
                    Device("A", 9, 0),
                    Device("B", 3, 0),
                    Device("C", 6, 0),
                )
            } else
                it
        }
    )

    if (example) {
        deviceManager.createProcess("P1", intArrayOf(3, 2, 2))
        deviceManager.createProcess("P2", intArrayOf(6, 1, 3))
        deviceManager.createProcess("P3", intArrayOf(3, 1, 4))
        deviceManager.createProcess("P4", intArrayOf(4, 2, 2))
    }

    while (true) {
        println("< 按回车键继续 >")
        readLine()
        deviceManager.displayDevices()

        println("请输入要执行的命令：N.创建进程 F.销毁进程并撤销设备 R.申请设备 Q.退出")
        val command = readLine() ?: continue

        when (command.uppercase()) {
            "N" -> {
                val devices = deviceManager.getDevicesSequence()

                println("请输入进程名：")
                val processName = readLine() ?: continue
                if (processName.isEmpty()) continue

                println("请依次输入${devices.joinToString()}的资源最大占用数，以空格分隔：")
                val maxDevices = inputIntArray() ?: continue
                if (maxDevices.size != deviceManager.deviceKindCount) {
                    println("设备种类不匹配！")
                    continue
                }

                try {
                    deviceManager.createProcess(processName, maxDevices)
                    println("创建进程成功！")
                } catch (e: IllegalArgumentException) {
                    println("创建进程失败：${e.message}")
                }

            }
            "F" -> {
                println("请输入进程名：")
                val processName = readLine() ?: continue
                if (processName.isEmpty()) continue
                try {
                    deviceManager.destroyProcess(processName)
                    println("成功销毁进程并释放设备")
                } catch (e: IllegalStateException) {
                    println(e.message)
                }
            }
            "R" -> {
                val devices = deviceManager.getDevicesSequence()

                println("请输入进程名：")
                val processName = readLine() ?: continue
                if (processName.isEmpty()) continue

                println("请依次输入${devices.joinToString()}的资源请求数，以空格分隔：")
                val needDevices = inputIntArray() ?: continue
                if (needDevices.size != deviceManager.deviceKindCount) {
                    println("设备种类不匹配！")
                    continue
                }

                try {
                    if (deviceManager.requireDevices(processName, needDevices)) {
                        println("请求设备成功！")
                    } else {
                        println("请求设备失败：若请求设备将产生死锁！")
                    }
                } catch (e: IllegalStateException) {
                    println(e.message)
                }
            }
            "Q" -> {
                break
            }
        }
    }

}

private fun createDevicesList(): MutableList<Device> {
    val devices = mutableListOf<Device>()
    println("请先创建一个设备列表，如果给定列表为空，则会创建默认设备")
    while (true) {
        println("请输入设备名(为空代表结束创建)：")
        val name = readLine() ?: break
        if (name.isEmpty()) break

        println("请输入总数：")
        val count = try {
            readLine()?.toInt()
        } catch (e: NumberFormatException) {
            break
        } ?: break

        println("请输入已被占用数(可以忽略为0，可以认为是系统占用)：")
        val used = try {
            readLine()?.toInt()
        } catch (e: NumberFormatException) {
            0
        } ?: 0

        devices.add(Device(name, count, used))
    }
    return devices
}