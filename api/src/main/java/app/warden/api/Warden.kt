package app.warden.api

import android.content.Context
import android.net.Uri
import android.os.IBinder
import android.os.RemoteException

/**
 * Client entry point apps link against.
 *
 * Usage:
 *   Warden.bind(context)                        // obtain the broker binder
 *   val pm = IPackageManager.Stub.asInterface(
 *       Warden.wrap(ServiceManager.getService("package")))
 *   pm.someProtectedCall(...)                   // runs as the server's identity
 *
 * or, to shell out with elevated identity:
 *   val p = Warden.newProcess(arrayOf("sh","-c","settings put global x 1"))
 */
object Warden {
    @Volatile private var service: IWarden? = null

    /** Set by [WardenProvider] once the server binder is resolved. */
    @JvmStatic
    fun onBinderReceived(binder: IBinder?) {
        service = binder?.let { IWarden.Stub.asInterface(it) }
    }

    @JvmStatic
    fun isReady(): Boolean = service?.asBinder()?.isBinderAlive == true

    /**
     * Fetch the broker binder from the manager app's provider. Returns whether
     * Warden is now ready; false if the broker isn't running. Cheap when bound.
     */
    @JvmStatic
    fun bind(context: Context): Boolean {
        if (isReady()) return true
        // Only the real Warden's provider: an app that took the authority (or the package name, with Warden not
        // installed) would otherwise get every command and could answer anything.
        if (!genuine(context)) return false
        val binder = runCatching {
            context.contentResolver.call(Uri.parse(WardenContract.PROVIDER_URI), "getBinder", null, null)
                ?.getBinder("binder")
        }.getOrNull()
        onBinderReceived(binder)
        return isReady()
    }

    /** SHA-256 of Warden's signing certificate (debug and release builds share it). */
    private const val CERT_SHA256 = "b5b3cd575546a8bfa3829a00aaaeb559e37d20362c815978fbfba1b532f4985a"

    private fun genuine(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        if (pm.resolveContentProvider(WardenContract.AUTHORITY, 0)?.packageName != "app.warden") return false
        // API 28+: the platform checks, following the key's rotation history. Before that (minSdk 26): the signatures.
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val cert = CERT_SHA256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            pm.hasSigningCertificate("app.warden", cert, android.content.pm.PackageManager.CERT_INPUT_SHA256)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo("app.warden", android.content.pm.PackageManager.GET_SIGNATURES).signatures.orEmpty().any { s ->
                java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } == CERT_SHA256 }
        }
    }.getOrDefault(false)

    private fun require(): IWarden =
        service ?: throw IllegalStateException("Warden not bound; is the server running and granted?")

    /** Wrap a system-service binder so calls execute as the server. */
    @JvmStatic
    @Throws(RemoteException::class)
    fun wrap(target: IBinder): IBinder = require().transactAs(target)

    @JvmStatic
    @Throws(RemoteException::class)
    fun newProcess(
        cmd: Array<String>,
        env: Array<String> = emptyArray(),
        dir: String = "/",
    ): IRemoteProcess = require().newProcess(cmd, env, dir)

    @JvmStatic
    @Throws(RemoteException::class)
    fun serverUid(): Int = require().serverUid()

    @JvmStatic
    fun isRoot(): Boolean = runCatching { serverUid() == 0 }.getOrDefault(false)
}
