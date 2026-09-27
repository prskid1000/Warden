package app.warden.api

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
