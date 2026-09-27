package app.warden.server

import android.content.pm.PackageInfo
import android.os.IBinder

/**
 * Reflection bridge to the @hide framework APIs the privileged server needs.
 *
 * These classes/methods exist at runtime (the server runs inside app_process
 * with the full framework) but are not in the public android.jar, so calling
 * them directly would fail to compile. Reflection keeps the whole project
 * buildable against the public SDK while still reaching them on-device.
 *
 * Every lookup is cached and every call is failure-tolerant: on a device/API
 * where a shape differs, the caller gets null and degrades (audited as uid-only).
 */
object Hidden {

    private val serviceManager by lazy { Class.forName("android.os.ServiceManager") }
    private val getServiceM by lazy {
        serviceManager.getMethod("getService", String::class.java)
    }
    private val addServiceM by lazy {
        serviceManager.getMethod("addService", String::class.java, IBinder::class.java)
    }

    private val pkgManager: Any? by lazy {
        runCatching {
            val binder = getService("package") ?: return@runCatching null
            val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        }.getOrNull()
    }
    private val ipmClass by lazy { Class.forName("android.content.pm.IPackageManager") }

    fun getService(name: String): IBinder? =
        runCatching { getServiceM.invoke(null, name) as? IBinder }.getOrNull()

    fun addService(name: String, binder: IBinder) {
        addServiceM.invoke(null, name, binder)   // throws on non-root; caller handles
    }

    @Suppress("UNCHECKED_CAST")
    fun packagesForUid(uid: Int): Array<String>? = runCatching {
        val m = ipmClass.getMethod("getPackagesForUid", Int::class.javaPrimitiveType)
        m.invoke(pkgManager, uid) as? Array<String>
    }.getOrNull()

    /** getPackageInfo across API levels: params are (String, long|int, int). */
    fun getPackageInfo(pkg: String, flags: Long, userId: Int): PackageInfo? = runCatching {
        val m = ipmClass.methods.firstOrNull {
            it.name == "getPackageInfo" && it.parameterTypes.size == 3 &&
                it.parameterTypes[0] == String::class.java
        } ?: return@runCatching null
        val flagsArg: Any =
            if (m.parameterTypes[1] == Long::class.javaPrimitiveType) flags else flags.toInt()
        m.invoke(pkgManager, pkg, flagsArg, userId) as? PackageInfo
    }.getOrNull()
}
