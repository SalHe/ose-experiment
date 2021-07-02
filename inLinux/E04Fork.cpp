#include <unistd.h>
#include <stdio.h>

int main(int argc, char const *argv[])
{
    pid_t pid = fork();
    if (pid < 0)
        printf("创建失败\n");
    else if (pid > 0)
    {
        printf("B\n");
        pid = fork();
        if (pid < 0)
        {
            printf("从B创建子进程失败！\n");
        }
        else if (pid > 0)
        {
            printf("C\n");
        }
    }
    else // pid == 0
        printf("A\n");
    return 0;
}
