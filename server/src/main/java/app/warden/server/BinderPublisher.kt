package app.warden.server

import android.os.IBinder
import android.util.Log

/**
 * Publishes the broker binder so client apps can obtain it.
 *
 * Strategy (see docs/DESIGN.md): the manager app exposes a ContentProvider that
 * client apps query; the manager holds the binder it received from this server
 * over an initial handshake and relays it. On rooted devices the Zygisk module
 * can instead inject the binder straight into a rooted-list app's process.
 *
 * The handshake itself reuses Shizuku's proven trick: the server calls a
 * pre-agreed transaction on the manager's provider binder (obtained via
 * ActivityManager) to deliver `this`. Implemented in the on-device build; this
 * stub documents the contract and the two delivery paths.
 */
object BinderPublisher {
    private const val TAG = "Warden"
    const val TRANSACTION_deliverBinder = IBinder.FIRST_CALL_TRANSACTION + 1
    const val MANAGER_PACKAGE = "app.warden"

    fun publish(binder: IBinder) {
        // 1. Root/Zygisk path: register under ServiceManager for module pickup.
        runCatching { ServiceManagerCompat.addService("warden", binder) }
            .onFailure { Log.w(TAG, "addService unavailable (non-root start): ${it.message}") }
        // 2. ADB path: hand the binder to the manager's provider via the
        //    deliverBinder transaction. See ManagerHandshake on the client side.
        ManagerHandshake.deliver(binder, MANAGER_PACKAGE, TRANSACTION_deliverBinder)
    }
}
