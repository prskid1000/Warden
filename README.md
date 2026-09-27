# Warden

**A privilege broker with a per-app audit trail and a per-app "rooted list".**

Warden is a [Shizuku](https://github.com/RikkaApps/Shizuku)-style privilege
broker, plus the two things Shizuku lacks:

1. **A full audit log** — every privileged call is recorded (which app → which
   system service/method or shell command → allow/deny), tailing live in the UI.
2. **A per-app rooted list** — add an app and choose what it gets: elevated API
   access, a working `su`, and (on rooted devices) its own root-detection made
   to report "rooted".

It is **not** a rootkit and does nothing invisibly: consent is per-app and
deny-by-default, and the audit log is the accountability backstop.

## The three layers (and their honest reach)

| Layer | Grants | Bootstrap | Root needed |
|-------|--------|-----------|-------------|
| **A** Binder broker | Elevated API access as shell/root | `adb shell sh start.sh` | No |
| **B** `su` shim | Working `su -c` for cooperating apps | fake `su` on PATH | No* |
| **C** Zygisk module | Spoof an app's root detection + real `su` | Magisk/Zygisk module | **Yes** |

\* Layer B reaches apps that use Warden's API or find `su` on their PATH. You
cannot force `su` onto an *arbitrary uncooperative* app's PATH without root —
that (and flipping an app's own root check) is layer C, which needs an already
rooted device. On an unrooted device the rooted-list UI shows layer-C toggles as
unavailable rather than pretending.

## Layout

```
api/       client library + AIDL (apps depend on this)
server/    privileged process: broker + audit interceptor + permission/rooted stores
app/       Compose manager (Nocturne theme): Start · Apps · Rooted list · Audit
module/    Zygisk C++ module (layer C) — built with ndk-build, flashed via Magisk
sushim/    fake su (layer B)
starter/   bootstrap scripts (ADB + root)
docs/      DESIGN.md
```

Theme ("Nocturne", deep-indigo + lavender) is borrowed from the sibling Vessel
project so the two read as one family.

## Status

**Everything builds.** `:api`, `:server` and `:app` compile to a signed APK; the
`su` shim compiles to an arm64 ELF; the Zygisk module compiles to
`libwarden.so`; and the Magisk module zips cleanly. Security rework is in:
signature-bound scoped/expiring grants (`GrantStore`, `CallerAuth`), per-uid rate
limiting, `clearCallingIdentity` on forwarded transactions, transaction-code →
method-name resolution in the audit log, and an async, hash-chained
(tamper-evident) `AuditSink`.

Verified: compilation + packaging on Windows (JBR 21, AGP 8.7, NDK 27.1).
Not yet verified on a device/emulator, and marked in code:

- **Root path works end-to-end by design**: the server registers itself in
  `ServiceManager` as `warden`, and the manager resolves it — so on a rooted
  device the manager connects with no extra plumbing.
- **ADB (non-root) binder handshake** (`ManagerHandshake` / `WardenProvider`) —
  the ActivityManager provider lookup is per-API-level and still a TODO; until
  then the ADB-start manager shows "not connected".
- **Layer C hooking correctness** — the module compiles against the real Zygisk
  API and resolves libc from `/proc/self/maps`; the actual PLT hooks need
  on-device validation against the running Magisk/Zygisk.

Note: `compileSdk`/`targetSdk` are 35 (the max AGP 8.7 supports); Android 17
support is runtime (`SDK_INT`-guarded) and `minSdk` is 26.

See `docs/DESIGN.md` for the full design.

## Build

```
# one shot: APKs + su shim + Zygisk .so + Magisk zip  ->  dist/
ANDROID_HOME=... ./tools/package.sh

# or individually:
./gradlew :app:assembleRelease
clang --target=aarch64-linux-android26 sushim/su.c -o sushim/su-arm64
( cd module && ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=jni/Android.mk NDK_APPLICATION_MK=jni/Application.mk )
```

CI (`.github/workflows/release.yml`) rebuilds all artifacts and attaches them to
a GitHub release on any `v*` tag.
