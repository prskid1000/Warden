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
        // The manager can be killed and relaunched while this (separate) process
        // keeps running, and on a non-root start ServiceManager is blocked by
        // SELinux, so the broadcast is the only way a restarted manager gets the
        // binder back. The tether delivers it on every (re)start of the manager.
        if (ManagerTether.hold(binder)) return

        // Can't hold the manager on this framework: offer the binder on a heartbeat.
        ManagerHandshake.deliver(binder)
        Thread({
            while (true) {
                Thread.sleep(5000L)
                ManagerHandshake.deliver(binder)
            }
        }, "warden-handshake").apply { isDaemon = true }.start()
    }
}
