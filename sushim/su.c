/*
 * Warden su shim (layer B).
 *
 * A drop-in `su` that does NOT itself hold root. Instead it forwards the
 * requested command to the Warden broker (which runs as shell or root) over a
 * local socket, and streams stdio back. Placed on a cooperating app's PATH so
 * `su -c "<cmd>"` and `su` transparently execute with the broker's identity.
 *
 * Argument shapes handled: `su`, `su -c CMD`, `su UID -c CMD`, `su -- CMD...`.
 * The broker audits every command it runs, so shim traffic shows up on the
 * Audit tab exactly like binder calls do.
 *
 * On a rooted device the Zygisk module bind-mounts this binary as /system/bin/su
 * inside rooted-list apps' mount namespaces (layer C); on ADB-only setups it is
 * dropped into the cooperating app's own files dir and prepended to PATH.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>

#define WARDEN_SOCK "warden_exec" /* abstract namespace socket the broker listens on */

static int connect_broker(void) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    struct sockaddr_un addr = {0};
    addr.sun_family = AF_UNIX;
    /* abstract socket: leading NUL */
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, WARDEN_SOCK, sizeof(addr.sun_path) - 2);
    socklen_t len = (socklen_t)(sizeof(sa_family_t) + 1 + strlen(WARDEN_SOCK));
    if (connect(fd, (struct sockaddr *)&addr, len) < 0) { close(fd); return -1; }
    return fd;
}

int main(int argc, char **argv) {
    /* Collect the command after "-c" / "--", or default to an interactive shell. */
    const char *cmd = NULL;
    for (int i = 1; i < argc; i++) {
        if ((strcmp(argv[i], "-c") == 0 || strcmp(argv[i], "--") == 0) && i + 1 < argc) {
            cmd = argv[i + 1];
            break;
        }
    }
    if (!cmd) cmd = "sh";

    int fd = connect_broker();
    if (fd < 0) {
        fprintf(stderr, "warden-su: broker not reachable (is the server running "
                        "and this app granted?)\n");
        return 1;
    }
    /* Wire protocol: length-prefixed command, then the broker relays stdio and
     * returns the exit code as a final byte. Kept minimal here; the broker side
     * lives in the server's exec socket handler. */
    dprintf(fd, "%s\n", cmd);
    /* Stream output; the broker ends with a NUL + exit code + newline trailer. */
    char buf[4096];
    ssize_t n;
    int exit_code = 0, in_trailer = 0;
    char code_buf[16]; size_t code_len = 0;
    while ((n = read(fd, buf, sizeof buf)) > 0) {
        for (ssize_t i = 0; i < n; i++) {
            if (!in_trailer && buf[i] == '\0') { in_trailer = 1; continue; }
            if (in_trailer) {
                if (buf[i] != '\n' && code_len < sizeof code_buf - 1)
                    code_buf[code_len++] = buf[i];
            } else {
                (void)!write(STDOUT_FILENO, &buf[i], 1);
            }
        }
    }
    close(fd);
    if (code_len) { code_buf[code_len] = '\0'; exit_code = atoi(code_buf); }
    return exit_code;
}
