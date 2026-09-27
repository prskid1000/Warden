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
        // Rebroadcast for a short window so the manager receives the binder even
        // if it is opened a few seconds after the adb command is run.
        Thread({
            repeat(15) {
                ManagerHandshake.deliver(binder)
                Thread.sleep(2000)
            }
        }, "warden-handshake").apply { isDaemon = true }.start()
    }
}
