#include <seccomp.h>
#include <stdio.h>

extern int nosocket_main(int argc, char *argv[]);

static int exec_called;

int __wrap_seccomp_load(scmp_filter_ctx context) {
    (void)context;
    return -1;
}

int __wrap_execvp(const char *file, char *const argv[]) {
    (void)file;
    (void)argv;
    exec_called = 1;
    return -1;
}

int main(void) {
    char *arguments[] = {"nosocket", "/bin/true", NULL};
    int result = nosocket_main(2, arguments);
    if (result != 1 || exec_called) {
        fprintf(stderr, "nosocket ran the job after seccomp_load failed\n");
        return 1;
    }
    return 0;
}
