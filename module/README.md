# Warden Zygisk module (layer C)

Root-only. Makes rooted-list apps believe the phone is rooted and gives them a
working `su`, by hooking their process at Zygote-specialize time.

## Build
1. Fetch the official `zygisk.hpp` into `jni/` (topjohnwu/zygisk-module-sample).
2. `ndk-build` (or add a CMake target) → `libwarden.so`.
3. Package as a Magisk module: `module.prop` + `zygisk/arm64-v8a.so` = libwarden.so.
4. Flash in Magisk, enable Zygisk, reboot.

## Data contract
Reads `/data/adb/warden/rooted.list` — one `pkg [profile]` per line, written by
the manager's RootedListStore (mirror it there from the app's private dir, or
point the store at this path when running as root).

## Android 17
The Zygisk API version must match the running Magisk. If Zygisk is absent the
module is inert. Hooked symbols (`access`, `faccessat`, `__system_property_get`)
are stable across API 26..37.
