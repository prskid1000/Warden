package app.warden.server

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * ADB-start delivery path: locate the manager app's published provider binder
 * (via ActivityManager.getContentProviderExternal for the manager authority)
 * and invoke [txn] to hand it `binder`. The manager stores it and relays to
 * granted clients through WardenProvider.
 *
 * The ActivityManager lookup differs across API levels (26..37); keep the
 * per-level shim in compat/ and select on Build.VERSION.SDK_INT. This stub
 * captures the contract; the on-device build fills the AM call.
 */
object ManagerHandshake {
    fun deliver(binder: IBinder, managerPkg: String, txn: Int) {
        runCatching {
            val provider = resolveManagerProvider(managerPkg) ?: return
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeStrongBinder(binder)
                provider.transact(txn, data, reply, 0)
            } finally { data.recycle(); reply.recycle() }
        }.onFailure { Log.w("Warden", "manager handshake failed: ${it.message}") }
    }

    // Filled per API level in compat/; returns the manager provider's binder.
    private fun resolveManagerProvider(managerPkg: String): IBinder? = null
}
