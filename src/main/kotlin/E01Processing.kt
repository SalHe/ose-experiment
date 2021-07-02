package ose.processing

enum class ProcessStatus(val status: String) {
    /**
     * 准备就绪
     *
     */
    R("Ready"),

    /**
     * 进程结束
     *
     */
    E("End")
}

/**
 * 进程控制块
 *
 * @property name 进程名
 * @property time 进程剩余运行时间
 * @property priority 进程优先级（越大越优先）
 * @property status 进程状态
 * @property next 下一个进程控制块
 */
data class PCB(
    val name: String,

    var time: Long,
    var priority: Int,
    var status: ProcessStatus,
    var next: PCB? = null
) : Comparable<PCB> {

    /**
     * 在此重载比较运算符，以使比较的时候比较优先数
     *
     * @param other
     * @return
     */
    override fun compareTo(other: PCB): Int {
        return this.priority - other.priority
    }
}

/**
 * PCB链表
 *
 */
class PCBLinkedList : Iterable<PCB> {

    /**
     * 带头节点的PCB链表
     */
    private val pcbHeader: PCB = PCB("", 0, 0, ProcessStatus.E)

    /**
     * 添加进程控制块
     *
     * @param pcb 进程控制块
     */
    fun addPCB(pcb: PCB) {
        var cur = pcbHeader.next
        var pre = pcbHeader
        while (cur != null) {
            if (pcb > cur) { // 相当于比较两者的优先级（已重载比较运算符）
                // 如果给定进程优先级大于cur的优先级
                // 便将原链表关系由“pre - cur”变为"pre - PCB - cur"
                pre.next = pcb
                pcb.next = cur
                return
            }
            pre = cur
            cur = cur.next
        }

        // 如果执行到此步，说明给定进程只能加入到链表末
        pre.next = pcb
    }

    /**
     * 按优先度获取队列首个PCB
     *
     * @return
     */
    fun takeFirst(): PCB? {
        // 返回链表的第一个（我们已经保证队列第一个即为最大优先级的进程）
        return pcbHeader.next.also {
            // 同时将其从队列移除
            pcbHeader.next = pcbHeader.next?.next
            it?.next = null
        }
    }

    override fun iterator(): Iterator<PCB> =
        object : Iterator<PCB> {

            private var current = pcbHeader.next

            override fun hasNext(): Boolean = current != null

            override fun next(): PCB = current!!.also { current = current!!.next }
        }

}

/**
 * 进程调度器
 *
 */
class ProcessScheduler {

    private val pcbLinkedList: PCBLinkedList = PCBLinkedList()

    /**
     * 创建进程
     *
     * @param name 进程名
     * @param priority 优先数
     * @param time 运行时长
     */
    fun createProcess(name: String, priority: Int, time: Long) {
        pcbLinkedList.addPCB(PCB(name, time, priority, ProcessStatus.E))
    }

    /**
     * 调度直行一次程序
     *
     * @return 有程序可调度返回true，否则返回false
     */
    fun schedule(): Boolean {
        val pcb = pcbLinkedList.takeFirst() ?: return false  // 当PCB为null的时候执行"return false"
        pcb.priority--
        pcb.time--
        println(">>>>>>> 已享有CPU的进程：${pcb.name}")
        if (pcb.time <= 0) {
            pcb.status = ProcessStatus.E
            println(">>>>>>> ${pcb.name}运行完毕，已退出")
        } else {
            pcbLinkedList.addPCB(pcb)
        }
        println()
        return true
    }

    /**
     * 展示进程列表
     *
     */
    fun showProcessList() {
        println("-".repeat(50))
        print("Process".padEnd(20))
        print("Time".padEnd(5))
        print("Priority".padEnd(5))
        println()
        println("-".repeat(50))
        for (p in pcbLinkedList) {
            println("${p.name.padEnd(20)}${p.time}\t${p.priority}")
        }
        println("-".repeat(50))
    }

}

fun main() {
    val scheduler = ProcessScheduler()

    confirmAction("是否创建示例进程？") {
        scheduler.createProcess("Notepad.exe", 5, 3)
        scheduler.createProcess("Calc.exe", 5, 3)
        scheduler.createProcess("SYSTEM", 10, 3)
        println("已加入示例进程")
        scheduler.showProcessList()
    }

    println("您可以在调度之前创建新进程：")
    while (true) {
        if (requireUserCreateProcess(scheduler)) break
    }

    while (true) {
        scheduler.showProcessList()

        confirmAction("是否创建新进程？", defaultDo = false) {
            requireUserCreateProcess(scheduler)
        }

        if (!scheduler.schedule()) break
    }

}

private fun requireUserCreateProcess(scheduler: ProcessScheduler): Boolean {
    print("请输入要创建的进程名(为空则结束创建过程)：")
    val name = readLine()
    if (name.isNullOrEmpty()) return true

    print("请输入运行时间(若小于等于0或为无效整数则结束创建过程)：")
    val time = readLine()?.toLong() ?: return true
    if (time <= 0) return true

    print("请输入优先级(若小于等于0或为无效整数则结束创建过程)：")
    val priority = readLine()?.toInt() ?: return true
    if (priority <= 0) return true

    scheduler.createProcess(name, priority, time)
    return false
}

inline fun confirmAction(tips: String, defaultDo: Boolean = true, block: () -> Unit) {
    print(tips + (if (defaultDo) "(Y/n)" else "(y/N)"))
    if (readLine()?.uppercase().let { (it.isNullOrEmpty() && defaultDo) || it == "Y" }) {
        block()
    }
}