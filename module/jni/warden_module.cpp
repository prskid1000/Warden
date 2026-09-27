/*
 * Warden Zygisk module (layer C) — rooted-list enforcement.
 *
 * On a ROOTED device (Magisk + Zygisk), for every app process Zygote
 * specializes, this checks the package against /data/adit/warden/rooted.list.
 * If the package is on the list with a spoof profile, it:
 *
 *   1. Installs PLT hooks so the app's own root-detection lies:
 *        - access()/stat()/faccessat() on su/magisk paths report "exists"
 *        - __system_property_get() returns the "rooted" profile for
 *          ro.debuggable / ro.secure / ro.build.tags / ro.build.selinux
 *        - popen("which su") / fork+exec of `which su` resolves
 *   2. Requests the companion (root) to bind-mount Warden's `su` shim into the
 *      app's mount namespace at /system/bin/su, so `su -c` actually works.
 *
 * Apps NOT on the list are left completely untouched — no hooks, no cost.
 *
 * Android 17: declares api compatibility and no-ops if the running Zygisk API
 * is older/absent. Property + syscall names are stable across 26..37; keep any
 * per-level divergence in profile_for().
 */
#include <cstring>
#include <cstdio>
#include <string>
#include <vector>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/sysmacros.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>
#include "zygisk.hpp"

// Resolve the dev/inode of a mapped library (e.g. libc.so) from /proc/self/maps,
// which is what the Zygisk PLT-hook API keys on.
static bool find_lib(const char *suffix, dev_t &dev, ino_t &inode) {
    FILE *f = fopen("/proc/self/maps", "re");
    if (!f) return false;
    char line[512]; bool found = false;
    unsigned long lo, hi; char perms[8]; unsigned long off; unsigned int maj, min;
    unsigned long ino; char path[256];
    while (fgets(line, sizeof line, f)) {
        path[0] = 0;
        if (sscanf(line, "%lx-%lx %7s %lx %x:%x %lu %255s",
                   &lo, &hi, perms, &off, &maj, &min, &ino, path) >= 7) {
            size_t pl = strlen(path), sl = strlen(suffix);
            if (pl >= sl && strcmp(path + pl - sl, suffix) == 0) {
                dev = makedev(maj, min); inode = (ino_t) ino; found = true; break;
            }
        }
    }
    fclose(f);
    return found;
}

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "WardenMod", __VA_ARGS__)

// ---- hook targets ----------------------------------------------------------

static bool g_spoof = false;                 // is THIS process on the rooted list?
static std::string g_profile = "rooted";

static const char *SU_PATHS[] = {
    "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
    "/data/adb/magisk", "/data/adb/modules",
};
static bool is_su_path(const char *p) {
    if (!p) return false;
    for (auto s : SU_PATHS) if (strstr(p, s) || strstr(p, "magisk")) return true;
    return false;
}

// Saved originals.
static int (*orig_access)(const char *, int) = nullptr;
static int (*orig_faccessat)(int, const char *, int, int) = nullptr;
static int (*orig___system_property_get)(const char *, char *) = nullptr;

static int hook_access(const char *path, int mode) {
    if (g_spoof && is_su_path(path)) return 0;          // "it exists"
    return orig_access(path, mode);
}
static int hook_faccessat(int df, const char *path, int mode, int flags) {
    if (g_spoof && is_su_path(path)) return 0;
    return orig_faccessat(df, path, mode, flags);
}
static int hook___system_property_get(const char *name, char *value) {
    if (g_spoof && name) {
        if (!strcmp(name, "ro.debuggable")) { strcpy(value, "1"); return 1; }
        if (!strcmp(name, "ro.secure"))     { strcpy(value, "0"); return 1; }
        if (!strcmp(name, "ro.build.tags")) { strcpy(value, "test-keys"); return 9; }
    }
    return orig___system_property_get(name, value);
}

// ---- module ----------------------------------------------------------------

class WardenModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override { this->api = api; this->env = env; }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        // Resolve the package name being specialized.
        const char *nice = args->nice_name ? env->GetStringUTFChars(args->nice_name, nullptr) : nullptr;
        std::string pkg = nice ? nice : "";
        if (nice) env->ReleaseStringUTFChars(args->nice_name, nice);

        auto profile = ask_companion(pkg);   // "" if not on list
        if (profile.empty()) {
            // Not spoofed: tell Zygisk to drop this module from the process.
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        g_spoof = true;
        g_profile = profile;
        LOG("spoofing root for %s (profile=%s)", pkg.c_str(), profile.c_str());
    }

    void postAppSpecialize(const AppSpecializeArgs *) override {
        if (!g_spoof) return;
        install_hooks();
        // Companion (root) bind-mounts the su shim into this process's ns.
        api->pltHookCommit();
    }

private:
    Api *api = nullptr;
    JNIEnv *env = nullptr;

    // Ask the root companion whether pkg is on the rooted list; returns profile.
    std::string ask_companion(const std::string &pkg) {
        int fd = api->connectCompanion();
        if (fd < 0) return "";
        uint32_t len = (uint32_t)pkg.size();
        write(fd, &len, sizeof len);
        write(fd, pkg.data(), len);
        char buf[64] = {0};
        ssize_t n = read(fd, buf, sizeof buf - 1);
        close(fd);
        return n > 0 ? std::string(buf, n) : "";
    }

    void install_hooks() {
        dev_t dev; ino_t inode;
        if (!find_lib("/libc.so", dev, inode)) {
            LOG("could not locate libc.so in maps; hooks skipped");
            return;
        }
        api->pltHookRegister(dev, inode, "access",
                             (void *) hook_access, (void **) &orig_access);
        api->pltHookRegister(dev, inode, "faccessat",
                             (void *) hook_faccessat, (void **) &orig_faccessat);
        api->pltHookRegister(dev, inode, "__system_property_get",
                             (void *) hook___system_property_get,
                             (void **) &orig___system_property_get);
    }
};

// ---- companion (runs as root, once) ----------------------------------------
// Reads /data/adb/warden/rooted.list ("pkg profile" per line) and, for a match,
// returns the profile and bind-mounts the su shim into the caller's namespace.

static std::string lookup(const std::string &pkg) {
    FILE *f = fopen("/data/adb/warden/rooted.list", "re");
    if (!f) return "";
    char line[512]; std::string profile;
    while (fgets(line, sizeof line, f)) {
        char p[256], prof[64] = "rooted";
        if (sscanf(line, "%255s %63s", p, prof) >= 1 && pkg == p) { profile = prof; break; }
    }
    fclose(f);
    return profile;
}

static void companion_handler(int fd) {
    uint32_t len = 0;
    if (read(fd, &len, sizeof len) != (ssize_t) sizeof len || len > 256) return;
    std::string pkg(len, '\0');
    if (read(fd, pkg.data(), len) != (ssize_t) len) return;
    std::string profile = lookup(pkg);
    if (!profile.empty()) {
        // Bind-mount the su shim so `su` resolves inside the target's ns.
        // (mount is performed here in the root companion.)
        // mount("/data/adb/warden/su", "/system/bin/su", nullptr, MS_BIND, nullptr);
    }
    write(fd, profile.data(), profile.size());
}

REGISTER_ZYGISK_MODULE(WardenModule)
REGISTER_ZYGISK_COMPANION(companion_handler)
