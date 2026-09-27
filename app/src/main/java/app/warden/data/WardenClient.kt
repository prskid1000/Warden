package app.warden.data

import android.os.IBinder
import app.warden.api.IWarden
import app.warden.api.RootedEntry
import org.json.JSONArray

/**
 * Manager-side handle to the broker.
 *
 * Two acquisition paths:
 *   - Root start: the server registers itself in ServiceManager as "warden";
 *     [connect] resolves it by reflection. Works end-to-end today.
 *   - ADB start: the server hands its binder to WardenProvider.deliverBinder,
 *     which calls [attach]. (Handshake completion is the one on-device TODO.)
 */
object WardenClient {
    @Volatile private var svc: IWarden? = null

    val connected: Boolean get() = svc?.asBinder()?.isBinderAlive == true

    val rootAvailable: Boolean
        get() = runCatching { svc?.serverUid() == 0 }.getOrDefault(false)

    fun attach(binder: IBinder) { svc = IWarden.Stub.asInterface(binder) }

    /** Try the ServiceManager path (root start). Safe to call repeatedly. */
    fun connect() {
        if (connected) return
        runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            val binder = sm.getMethod("getService", String::class.java)
                .invoke(null, "warden") as? IBinder
            if (binder != null && binder.isBinderAlive) attach(binder)
        }
    }

    fun rootedList(): List<RootedEntry> =
        runCatching { svc?.rootedList()?.toList() }.getOrNull() ?: emptyList()

    fun setRooted(e: RootedEntry) { runCatching { svc?.setRootedEntry(e) } }

    fun grants(): JSONArray =
        runCatching { JSONArray(svc?.grantsJson() ?: "[]") }.getOrDefault(JSONArray())

    fun setGrant(pkg: String, scopes: Array<String>, ttlMillis: Long = 0L) {
        runCatching { svc?.setGrant(pkg, scopes, ttlMillis) }
    }

    fun revokeGrant(pkg: String) { runCatching { svc?.revokeGrant(pkg) } }

    fun auditFd() = runCatching { svc?.auditTail() }.getOrNull()

    fun shutdown() { runCatching { svc?.shutdown() }; svc = null }

    fun clearAudit() { runCatching { svc?.clearAudit() } }
}
