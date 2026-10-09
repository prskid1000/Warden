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
 * In a cooperating app, WardenSu (in :api) ships this binary as libwardensu.so,
 * links it as `su` in a dir the app prepends to PATH, and hosts the relay socket.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>

#define WARDEN_SOCK "warden_exec" /* abstract namespace socket the broker listens on */

static int connect_abstract(const char *name) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    struct sockaddr_un addr = {0};
    addr.sun_family = AF_UNIX;
    /* abstract socket: leading NUL */
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, name, sizeof(addr.sun_path) - 2);
    socklen_t len = (socklen_t)(sizeof(sa_family_t) + 1 + strlen(name));
    if (connect(fd, (struct sockaddr *)&addr, len) < 0) { close(fd); return -1; }
    return fd;
}

/* Apps can't connect to the broker's socket (SELinux forbids app -> shell
 * connectto), so inside an app we go through the relay the app hosts with
 * WardenSu (same uid, same domain), which forwards over the broker binder.
 * Callers that may reach the broker directly fall back to its socket. */
static int connect_broker(void) {
    char relay[64];
    snprintf(relay, sizeof relay, "%s.%u", WARDEN_SOCK, (unsigned)getuid());
    int fd = connect_abstract(relay);
    return fd >= 0 ? fd : connect_abstract(WARDEN_SOCK);
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
    /* Wire protocol: the command, ended by a NUL (so a multi-line script arrives whole), then the broker
     * streams the output and ends it with a trailer: NUL + exit code + newline. The trailer is taken only from
     * the very end of the stream, so NULs inside the output (a PNG from screencap, find -print0) pass through. */
    (void)!write(fd, cmd, strlen(cmd));
    (void)!write(fd, "", 1);
    char buf[4096];
    /* Only a NUL near the end (a trailer is NUL + at most a few digits + newline) is held back; all other output is
     * written at once, so a running command's latest line shows (holding a fixed tail delayed it indefinitely). */
    enum { TAIL = 16 };
    char pend[TAIL]; size_t plen = 0;
    char work[TAIL + sizeof buf];
    ssize_t n;
    while ((n = read(fd, buf, sizeof buf)) > 0) {
        memcpy(work, pend, plen); memcpy(work + plen, buf, (size_t)n);
        size_t len = plen + (size_t)n, keep = len;
        for (size_t j = len; j > 0 && len - (j - 1) <= TAIL; j--)
            if (work[j - 1] == '\0') { keep = j - 1; break; }
        (void)!write(STDOUT_FILENO, work, keep);
        plen = len - keep; memcpy(pend, work + keep, plen);
    }
    close(fd);
    /* Find the trailer: a NUL, digits (maybe a '-'), a newline, at the very end. */
    int exit_code = 1;   /* no trailer: the broker went away mid-command */
    size_t out = plen;
    if (plen >= 3 && pend[plen - 1] == '\n') {
        size_t j = plen - 1;
        while (j > 0 && ((pend[j - 1] >= '0' && pend[j - 1] <= '9') || pend[j - 1] == '-')) j--;
        if (j > 0 && j < plen - 1 && pend[j - 1] == '\0') {
            char code[24]; size_t len = plen - 1 - j;
            if (len < sizeof code) { memcpy(code, pend + j, len); code[len] = '\0'; exit_code = atoi(code); out = j - 1; }
        }
    }
    (void)!write(STDOUT_FILENO, pend, out);
    return exit_code;
}
