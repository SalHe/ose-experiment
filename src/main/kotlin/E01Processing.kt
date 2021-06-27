package ose.processing
// 通过实习模拟在单处理器环境下的处理器调度，加深了解处理器调度的工作。
//      本实验设计一个按优先数调度算法实现处理器调度的程序，该程序模拟CPU对于进程的调度，进程使用PCB控制，处理器可以添加进程、运行进程、阻塞进程、
// 唤醒进程。在该模拟程序中，进程拥有运行时间来确定执行时间、优先级来决定进程运行顺序。
//
// 具体要求：
// 1.	假定系统有n个进程，每个进程用一个PCB来代表。PCB的结构为：
// 	进程名——如P1~Pn。
// 	指针——按优先数的大小把n个进程连成队列，用指针指出下一个进程PCB的首地址。
// 	要求运行时间——假设进程需要运行的单位时间数。
// 	优先数——赋予进程的优先数，调度时总是选取优先数大的进程先执行。
// 	状态——假设两种状态：就绪和结束，用R表示就绪，用E表示结束。初始状态都为就绪状态。
// 2.	开始运行之前，为每个进程确定它的“优先数”和“要求运行时间”。通过键盘输入这些参数读入这些参数。
// 3.	处理器总是选择优先级最高的进程运行，优先级最高的进程或者在队首，或者通过算法从就绪队列中选出优先级最高的进程。采用动态改变优先数的办
//      法，进程每调度运行1次，优先数减1，要求运行时间减1。
// 4.	进程运行一次后，若要求运行时间不等于0，则将它加入就绪队列，否则，将状态改为“结束”，退出就绪队列。
// 5.	若就绪队列为空，结束，否则转到(3)重复。
// 要求能从键盘接收输入的进程优先数及要求运行时间，能显示每次进程调度的情况，如哪个进程在运行，哪些进程就绪，就绪进程的排列情况。

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

class ProcessScheduler {

    private val pcbLinkedList: PCBLinkedList = PCBLinkedList()

    fun createProcess(name: String, priority: Int, time: Long) {
        pcbLinkedList.addPCB(PCB(name, time, priority, ProcessStatus.E))
    }

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

    println("是否创建示例进程？(Y/n)")
    if (readLine()?.uppercase().let { it == "Y" || it.isNullOrEmpty() }) {
        scheduler.createProcess("Notepad.exe", 5, 3)
        scheduler.createProcess("Calc.exe", 5, 3)
        scheduler.createProcess("SYSTEM", 10, 3)
    }

    while (true) {
        print("请输入要创建的进程名(为空则结束创建过程)：")
        val name = readLine()
        if (name.isNullOrEmpty()) break

        print("请输入运行时间(若小于等于0或为无效整数则结束创建过程)：")
        val time = readLine()?.toLong() ?: break
        if (time <= 0) break

        print("请输入优先级(若小于等于0或为无效整数则结束创建过程)：")
        val priority = readLine()?.toInt() ?: break
        if (priority <= 0) break

        scheduler.createProcess(name, priority, time)
    }

    while (true) {
        scheduler.showProcessList()
        if (!scheduler.schedule()) break
    }

}