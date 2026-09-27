# Warden

**A privilege broker for Android with a per-app audit trail — no root.**

Warden is a [Shizuku](https://github.com/RikkaApps/Shizuku)-style privilege
broker: it runs a small service with ADB-shell (`uid 2000`) privileges and lets
apps you approve call system APIs through it — without rooting the phone. On top
of Shizuku's model it adds a **full audit log**: every privileged call (which app
→ which system service/method or command → allow/deny) is recorded and shown live
in the app.

It is single-purpose and consent-first: nothing is granted until you toggle it,
and the audit log is the accountability backstop.

## How it works

| Piece | What it does |
|-------|--------------|
| **Broker** | A service started with `shell` privileges (via ADB) that transacts against system services on behalf of granted apps. |
| **`su` shim** | An optional `su` for cooperating apps, routed through the broker's exec socket (still no root — it runs with the broker's `shell` identity). |
| **Audit log** | Append-only, hash-chained (tamper-evident) record of every call, tailing live in the app. |

Security: signature-bound, scoped, expiring grants (deny-by-default), per-uid
rate limiting, `clearCallingIdentity` on forwarded transactions, and
transaction-code → method-name resolution in the log.

## Starting it — no computer needed

The app starts the broker itself over **Wireless Debugging** (Android 11+): an
in-app ADB client pairs with the phone's own `adbd` over loopback and launches
the service. One-time setup:

1. Open Warden → **Start**.
2. Enable **Developer options → Wireless debugging → Pair device with pairing code** (keep it open).
3. Pull down the notification shade and type the 6-digit code into the Warden
   notification (the code changes if you leave that screen, which is why it's
   entered from the notification).

After pairing once, **Start** connects instantly. You can also start it from a PC
with `adb shell sh .../start.sh` (see the app's Advanced section).

## Layout

```
api/       client library + AIDL (apps depend on this)
server/    the privileged broker: audit interceptor, grant store, exec socket
app/       the manager app (single-page Nocturne UI): start · apps · activity
sushim/    the layer-B `su` shim
starter/   bootstrap script for the PC / manual path
docs/      DESIGN.md
```

The UI theme ("Nocturne") is shared with the sibling Vessel / On-Device-AI
projects so they read as one family.

## Build

```
ANDROID_HOME=... ./tools/package.sh     # signed APK + su shim -> dist/
# or just the app:
./gradlew :app:assembleRelease
```

CI (`.github/workflows/release.yml`) builds and attaches the APK to a GitHub
release on any `v*` tag.

## Status

Verified on device (Motorola, Android 17): PC-free wireless start + pairing,
the shell broker, per-app grants, the live tamper-evident audit log, and
Stop / Clear all work. `compileSdk`/`targetSdk` are 35 (AGP 8.7's max);
runtime support targets Android 17 via `SDK_INT` guards, `minSdk` 26.

See `docs/DESIGN.md` for the architecture.
