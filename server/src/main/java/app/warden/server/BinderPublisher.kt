package app.warden.server

import android.os.IBinder
import android.util.Log
import app.warden.api.WardenContract

/**
 * Publishes the broker binder so the manager can reach it, on both start paths:
 *   - Root:  register in ServiceManager as "warden" (needs the sepolicy rule
 *            from the Magisk module for untrusted apps to `find` it).
 *   - ADB:   broadcast the binder to the manager's BinderReceiver (shell uid is
 *            allowed to broadcast; no ServiceManager, no root).
 * Both are attempted; whichever the current identity permits succeeds.
 */
object BinderPublisher {
    private const val TAG = "Warden"
    const val MANAGER_PACKAGE = WardenContract.MANAGER_PACKAGE

    fun publish(binder: IBinder) {
        runCatching { ServiceManagerCompat.addService("warden", binder) }
            .onFailure { Log.w(TAG, "addService unavailable (non-root start): ${it.message}") }
        // Keep offering the binder indefinitely: the manager app can be killed and
        // relaunched while the (separate) server process keeps running, and on a
        // non-root start ServiceManager is blocked by SELinux, so this broadcast is
        // the only way a restarted manager re-acquires the binder. Fast at first,
        // then a steady low-frequency heartbeat.
        Thread({
            var i = 0
            while (true) {
                ManagerHandshake.deliver(binder)
                Thread.sleep(if (i++ < 15) 2000L else 5000L)
            }
        }, "warden-handshake").apply { isDaemon = true }.start()
    }
}
