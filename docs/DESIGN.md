# Warden — design

**A privilege broker with a per-app audit trail and a per-app "rooted list".**
Shizuku's model, plus the two things it lacks: (1) a full audit log of *which app
called what*, and (2) a per-app switch that makes a chosen app believe the phone
is rooted and gives it root features.

Working name `Warden`; rename via `PRODUCT_NAME` in `gradle.properties` and
`applicationId` in `app/build.gradle.kts` (mirrors Vessel's convention).

---

## The three privilege layers

| Layer | Grants requesting apps | Bootstrap | Needs root? |
|-------|------------------------|-----------|-------------|
| **A. Binder broker** | Elevated **API** access — call system services (`IPackageManager`, `IActivityManager`, appops, settings…) as `shell`/root | `adb shell sh start.sh` launches the server; or `su` | No (ADB) |
| **B. `su` shim** | A working `su -c` for apps that shell out, routed to the broker's `newProcess` | fake `su` on the caller's PATH | No, for cooperating apps |
| **C. Zygisk module** | Injects into a **rooted-list** app: spoofs its root-detection checks *and* feeds it real `su` + broker | Magisk/Zygisk module, server as uid 0 | **Yes** |

### Honest reach boundary
- **A** and **B** work today from a pure-ADB start (server = uid `2000 shell`).
- **B** only reaches apps that look up `su` on PATH or use Warden's API — you
  cannot put `su` on an *arbitrary* app's PATH without root.
- **C** is the only layer that flips an *uncooperative* third-party app's own
  root check. It requires an already-rooted device (Magisk). On an unrooted
  device the rooted-list UI still exists but marks C-only capabilities as
  unavailable rather than pretending.

## The rooted list (headline feature)
`RootedListStore` holds per-package entries: `{pkg, spoofDetection, giveSu,
giveBroker, propsProfile}`. The manager UI toggles these per app.

- `giveBroker` / `giveSu` (layers A/B) apply on any device for cooperating apps.
- `spoofDetection` + `propsProfile` (layer C) tell the Zygisk module, on process
  fork of that package, to: return the rooted answer for su-path probes
  (`/system/bin/su`, `/system/xbin/su`, `which su`), RootBeer-style native
  checks, `getprop ro.debuggable`/`ro.secure`/`ro.build.tags`, and Magisk
  presence probes; and to bind-mount a real `su` into that process's namespace.

## Audit log (the differentiator)
Every privileged call funnels through the server's Binder, so a single
`BinderInterceptor` records **before dispatch**:

```
ts | caller_uid | caller_pkg | target (service#method | shell cmd) | args_digest | verdict(allow/deny) | latency_us
```

- Append-only JSONL under the server's private dir; rotated by size.
- `PermissionStore` checks the grant *after* logging, so denials are audited too.
- Manager UI: live stream, filter by app, per-app "what did it do" rollup, export.

## Bootstrap
`starter/start.sh` (run via `adb shell sh /sdcard/Android/.../start.sh`) execs
`app_process` on the server dex with the `shell` identity; on root it re-execs
under `su`. The dex hosts `WardenService extends IWarden.Stub`, registers itself
so the app + clients can reach it via a ContentProvider-published Binder.

## AIDL surface (`:api`)
```
interface IWarden {
  int    apiVersion();
  IBinder transactAs(in IBinder target);        // wrap a system-service binder
  IRemoteProcess newProcess(in String[] cmd, in String[] env, String dir);
  int    checkGrant(String pkg);                 // permission state
  void   setRootedEntry(in RootedEntry e);       // rooted-list write (manager only)
  ParcelFileDescriptor auditTail();              // stream the audit log
}
```

## Android 17 readiness
- **Hidden-API bypass:** the server runs as `shell`/root so it is exempt from the
  dark-greylist; client-side reflection uses `HiddenApiBypass` where needed.
- **Version-adaptive:** system-service AIDL shapes drift per API level; keep
  per-level shims in `server/compat/` keyed on `Build.VERSION.SDK_INT` (target
  API 37 / Android 17, min API 26).
- **Zygisk on 17:** layer C depends on Magisk/Zygisk supporting the running
  Android 17 build; module declares `minApi`/`maxApi` and no-ops cleanly if the
  Zygisk API is absent.

## Module layout
```
:api      client library + AIDL (apps depend on this)
:server   privileged process (broker + interceptor + audit + stores)
:app      Compose manager (Nocturne theme): Apps, Audit, Rooted-list, Bootstrap
:module   Zygisk C++ module (layer C) + CMake
:sushim   fake su (layer B)
starter/  bootstrap scripts (ADB + root paths)
```

## Consent & safety
Deny-by-default. Every app must be granted once in the manager. Rooted-list
entries are explicit per-app opt-ins. The audit log is the accountability
backstop: nothing the broker does is invisible to the device owner.

---

## Emulator validation (2026-09-27, API 36 google_apis, rooted)

Tested end-to-end on a rootable emulator. Results:

- Server starts via `app_process` as **uid 0**, pins the manager signing cert,
  brings up the exec socket, and registers the `warden` binder in ServiceManager.
- Broker answers over binder: `apiVersion()` → 1, `serverUid()` → 0.
- Manager app connects and renders **"running as root (uid 0) — all layers"**.
- The audited gate denies an ungranted caller (`SecurityException`) and writes a
  hash-chained audit line (`verdict":"deny","outcome":"no-scope:exec"`).

**Key finding — SELinux.** Under enforcing SELinux a normal app cannot `find`
the custom service:
`avc: denied { find } name=warden tclass=service_manager scontext=untrusted_app`.
So the ServiceManager discovery path only works for shell/system callers, not an
untrusted app. Fixes:
  - **Root path:** `module/post-fs-data.sh` injects a `magiskpolicy --live` rule
    allowing `untrusted_app` to `find default_android_service` — added.
  - **Non-root (ADB) path:** the binder-handoff via WardenProvider
    (`ManagerHandshake`) sidesteps ServiceManager entirely — still the TODO.

With SELinux set permissive the manager connected cleanly, confirming everything
except the service-lookup label works; the sepolicy rule closes that gap on rooted
devices.
