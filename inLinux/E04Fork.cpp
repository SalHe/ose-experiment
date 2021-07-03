#include <unistd.h>
#include <stdio.h>

int main(int argc, char const *argv[])
{
    pid_t pid = fork();
    if (pid < 0)
        printf("创建失败\n");
    else if (pid > 0)
    {
        printf("A, 子进程ID：%d\n", pid);
    }
    else // pid == 0
    {
        printf("B, 我是A创建的子进程，我即将创建进程C\n");
        pid = fork();
        if (pid < 0)
        {
            printf("从B创建子进程失败！\n");
        }
        else if (pid == 0)
        {
            printf("C, 我是B创建的子进程\n");
        }
    }
    return 0;
}
