package app.warden.data

import android.os.IBinder
import app.warden.api.IAuditListener
import app.warden.api.IWarden
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * Manager-side handle to the broker.
 *
 * Two acquisition paths:
 *   - Root start: the server registers itself in ServiceManager as "warden";
 *     [connect] resolves it by reflection.
 *   - ADB start: the server broadcasts its binder to BinderReceiver, which calls
 *     [attach] — at startup, and again whenever WardenProvider says hello.
 *
 * Connection state is pushed, not polled: [attach] raises [state] and a death
 * link on the binder drops it the moment the server process goes away.
 */
object WardenClient {
    @Volatile private var svc: IWarden? = null
    private val lock = Object()

    data class State(val connected: Boolean = false, val root: Boolean = false)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val connected: Boolean get() = svc?.asBinder()?.isBinderAlive == true

    fun attach(binder: IBinder) {
        if (svc?.asBinder() === binder && binder.isBinderAlive) return
        val s = IWarden.Stub.asInterface(binder)
        val linked = runCatching { binder.linkToDeath({ onDeath(binder) }, 0) }.isSuccess
        if (!linked) return   // already dead
        synchronized(lock) { svc = s; lock.notifyAll() }
        _state.value = State(true, runCatching { s.serverUid() == 0 }.getOrDefault(false))
    }

    private fun onDeath(binder: IBinder) {
        synchronized(lock) { if (svc?.asBinder() === binder) svc = null else return }
        _state.value = State()
    }

    /** The live broker binder, for relaying to granted client apps via the provider. */
    fun rawBinder(): IBinder? = svc?.asBinder()?.takeIf { it.isBinderAlive }

    /**
     * The live binder, waiting up to [timeoutMs] for the server's handshake to
     * land. Wakes on [attach], not on a timer. Never call on the main thread:
     * the handshake broadcast is delivered there.
     */
    fun awaitBinder(timeoutMs: Long): IBinder? {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (true) {
                rawBinder()?.let { return it }
                val left = end - System.currentTimeMillis()
                if (left <= 0) return null
                lock.wait(left)
            }
        }
    }

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

    fun grants(): JSONArray =
        runCatching { JSONArray(svc?.grantsJson() ?: "[]") }.getOrDefault(JSONArray())

    /** Grant [pkg]; returns why the broker refused (an app it can't verify), or null. */
    fun setGrant(pkg: String, scopes: Array<String>, ttlMillis: Long = 0L): String? =
        runCatching { svc?.setGrant(pkg, scopes, ttlMillis); null }.getOrElse { it.message ?: "couldn't grant $pkg" }

    fun revokeGrant(pkg: String) { runCatching { svc?.revokeGrant(pkg) } }

    fun auditFd() = runCatching { svc?.auditTail() }.getOrNull()

    /** Pushes each new audit line; false if not connected. */
    fun watchAudit(listener: IAuditListener): Boolean =
        runCatching { svc?.watchAudit(listener) != null }.getOrDefault(false)

    fun unwatchAudit(listener: IAuditListener) { runCatching { svc?.unwatchAudit(listener) } }

    fun shutdown() { runCatching { svc?.shutdown() } }

    fun clearAudit() { runCatching { svc?.clearAudit() } }
}
