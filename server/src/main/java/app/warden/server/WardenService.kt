package app.warden.server

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import app.warden.api.IAuditListener
import app.warden.api.IRemoteProcess
import app.warden.api.IWarden
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The privileged broker. Runs inside the server process (uid = shell or root).
 *
 * Access control: callers are identified by signing certificate ([auth]) and
 * gated by scoped, expiring grants ([grants]) plus a per-uid rate limit. Every
 * entry point records an audit event BEFORE the check, with outcome + latency
 * recorded after, so the log is a complete record of attempts and results.
 */
class WardenService(
    dataDir: File,
    managerCertSha256: String?,
    private val resolver: PackageResolver = PackageResolver(),
) : IWarden.Stub() {

    private val stateDir = File(dataDir, "state").apply { mkdirs() }
    val audit = AuditSink(File(dataDir, "log"))
    private val grants = GrantStore(stateDir)
    private val auth = CallerAuth(managerCertSha256)
    private val limiter = RateLimiter()

    fun isManagerUid(uid: Int): Boolean = auth.isManager(uid)
    fun grantStore() = grants

    override fun apiVersion(): Int = 1
    override fun serverUid(): Int = Process.myUid()

    // ---- audited, scoped, rate-limited gate --------------------------------

    private inline fun <T> gated(scope: String, target: String, argsDigest: String, body: () -> T): T {
        val uid = Binder.getCallingUid()
        val id = auth.identify(uid)
        val manager = auth.isManager(uid)
        val within = limiter.allow(uid)
        val allowed = within && (manager || grants.authorize(id, scope))
        val t0 = System.nanoTime()
        if (!allowed) {
            audit.record(ev(uid, id.pkg, target, argsDigest, "deny",
                if (!within) "rate-limited" else "no-scope:$scope"))
            throw SecurityException("Warden: ${id.pkg} denied for $target ($scope)")
        }
        return try {
            val r = body()
            audit.record(ev(uid, id.pkg, target, argsDigest, "allow", "ok",
                (System.nanoTime() - t0) / 1000))
            r
        } catch (t: Throwable) {
            audit.record(ev(uid, id.pkg, target, argsDigest, "allow", "error:${t.message}",
                (System.nanoTime() - t0) / 1000))
            throw t
        }
    }

    private fun managerOnly() {
        if (!auth.isManager(Binder.getCallingUid()))
            throw SecurityException("Warden: manager-only call")
    }

    private fun ev(uid: Int, pkg: String?, target: String, args: String,
                   verdict: String, outcome: String = "", lat: Long = -1) =
        AuditSink.Event(System.currentTimeMillis(), uid, pkg, target, args, verdict, outcome, lat)

    // ---- broker surface ----------------------------------------------------

    override fun transactAs(target: IBinder): IBinder {
        val desc = describe(target)
        return gated(Grant.service(desc), "binder#$desc", "wrap") { BrokeredBinder(target, desc) }
    }

    override fun newProcess(cmd: Array<String>, env: Array<String>, dir: String): IRemoteProcess =
        gated(Grant.SCOPE_EXEC, "exec", cmd.joinToString(" ").take(120)) {
            RemoteProcessImpl(cmd, env, dir)
        }

    // Granted only if authorize would let that app in now: the grant's certificate is the installed app's.
    override fun checkGrant(pkg: String): Int =
        if (grants.get(pkg)?.certSha256?.let { c -> c.equals(certOf(pkg), true) } == true) 1 else 0

    // ---- manager-only ------------------------------------------------------

    override fun setGrant(pkg: String, scopes: Array<String>, ttlMillis: Long) {
        managerOnly()
        val exp = if (ttlMillis <= 0) 0L else System.currentTimeMillis() + ttlMillis
        // A grant is bound to the app's signing certificate. Without one (not installed yet, another profile) any
        // app later installed under that name would inherit it: refuse instead.
        val cert = certOf(pkg) ?: throw IllegalArgumentException("$pkg isn't installed for this user — install it, then grant")
        grants.put(Grant(pkg, cert, scopes.toSet(), exp))
    }

    override fun revokeGrant(pkg: String) { managerOnly(); grants.revoke(pkg) }

    override fun grantsJson(): String {
        managerOnly()
        val arr = JSONArray()
        grants.all().forEach { g ->
            arr.put(JSONObject().apply {
                put("pkg", g.pkg); put("scopes", JSONArray(g.scopes.toList()))
                put("exp", g.expiresAt); put("cert", g.certSha256 ?: "")
                // Whether it works now (live, and the installed app is the one granted): the switch shows this.
                put("active", g.isLive() && g.certSha256 != null && g.certSha256.equals(certOf(g.pkg), true))
            })
        }
        return arr.toString()
    }

    override fun auditTail(): ParcelFileDescriptor {
        managerOnly()
        return ParcelFileDescriptor.open(File(audit.path()), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun clearAudit() { managerOnly(); audit.clear() }

    // Keyed by the listener's binder: the proxy object differs per call.
    private val watchers = java.util.concurrent.ConcurrentHashMap<IBinder, () -> Unit>()

    override fun watchAudit(listener: IAuditListener) {
        managerOnly()
        val key = listener.asBinder()
        if (watchers.containsKey(key)) return
        val unsubscribe = audit.subscribe(object : AuditSink.Listener {
            override fun onLine(line: String) = listener.onLine(line)
            override fun onCleared() = listener.onCleared()
        })
        watchers[key] = unsubscribe
        // The manager process can die without unwatching; drop it with the process.
        runCatching { key.linkToDeath({ watchers.remove(key)?.invoke() }, 0) }
            .onFailure { watchers.remove(key)?.invoke() }
    }

    override fun unwatchAudit(listener: IAuditListener) {
        managerOnly()
        watchers.remove(listener.asBinder())?.invoke()
    }

    override fun shutdown() {
        managerOnly()
        // Exit shortly after this call returns so the binder reply is delivered.
        Thread {
            Thread.sleep(200)
            android.os.Process.killProcess(android.os.Process.myPid())
        }.apply { isDaemon = true }.start()
    }

    // ---- helpers -----------------------------------------------------------

    private fun describe(b: IBinder): String =
        runCatching { b.interfaceDescriptor ?: "unknown" }.getOrDefault("unknown")

    private fun certOf(pkg: String): String? =
        // Best-effort: bind the grant to the package's current signing cert.
        runCatching { auth.identify(pkgUid(pkg)).certSha256 }.getOrNull()
    private fun pkgUid(pkg: String): Int =
        Hidden.getPackageInfo(pkg, 0, 0)?.applicationInfo?.uid ?: -1

    /**
     * A binder proxy whose transactions run with the server's identity. Each
     * forwarded transaction is scope-checked, audited with the resolved method
     * name, and executed under a cleared calling identity.
     */
    private inner class BrokeredBinder(private val real: IBinder, private val desc: String) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val uid = Binder.getCallingUid()
            val id = auth.identify(uid)
            val method = TransactionNames.resolve(desc, code)
            val allowed = auth.isManager(uid) ||
                (limiter.allow(uid) && grants.authorize(id, Grant.service(desc)))
            audit.record(ev(uid, id.pkg, "$desc#$method", "flags=$flags",
                if (allowed) "allow" else "deny"))
            if (!allowed) return false
            val token = Binder.clearCallingIdentity()
            return try {
                real.transact(code, data, reply, flags)   // runs as server identity
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }
    }
}
