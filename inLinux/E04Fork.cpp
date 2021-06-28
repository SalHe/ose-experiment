#include <unistd.h>
#include <stdio.h>

// 利用fork()系统调用创建进程。
// 了解进程的创建过程，进一步理解进程的概念，明确进程和程序的区别。
// 编制一段程序，使用系统调用fork( )创建两个子进程，这样在此程序运行时，
// 在系统中就有一个父进程和两个子进程在活动。每一个进程在屏幕上显示一个字符，
// 其中父进程显示字符A，子进程分别显示字符 B和字符C。试观察、记录并分析屏幕
// 上进程调度的情况。

int main(int argc, char const *argv[])
{
    pid_t pid = fork();
    if (pid < 0)
        printf("创建失败\n");
    else if (pid > 0)
        printf("B,C\n");
    else // pid == 0
        printf("A\n");
    return 0;
}
