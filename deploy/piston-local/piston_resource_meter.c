#define _GNU_SOURCE
#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc < 2) return 127;

    pid_t child = fork();
    if (child < 0) {
        dprintf(3, "-1\n");
        return 125;
    }
    if (child == 0) {
        close(3);
        execvp(argv[1], &argv[1]);
        _exit(127);
    }

    int status;
    struct rusage usage = {0};
    while (wait4(child, &status, 0, &usage) < 0) {
        if (errno == EINTR) continue;
        dprintf(3, "-1\n");
        return 125;
    }

    dprintf(3, "%ld\n", usage.ru_maxrss);
    close(3);

    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) {
        int signal_number = WTERMSIG(status);
        signal(signal_number, SIG_DFL);
        raise(signal_number);
        return 128 + signal_number;
    }
    return 125;
}
