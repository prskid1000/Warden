package app.warden.server

import android.content.Intent
import android.os.IBinder
import android.util.Log
import app.warden.api.BinderContainer
import app.warden.api.WardenContract
import java.lang.reflect.Method

/**
 * Non-root (ADB) delivery: the shell-started server broadcasts its binder to the
 * manager's BinderReceiver. Shell uid is allowed to broadcast via AMS, so no
 * root and no ServiceManager registration is needed (which SELinux blocks for
 * untrusted apps anyway).
 *
 * broadcastIntent's signature drifts across API levels, so we resolve the method
 * reflectively and fill arguments by parameter type + int position. Param types
 * are logged once to make on-device iteration cheap.
 */
object ManagerHandshake {
    private const val TAG = "Warden"

    fun deliver(binder: IBinder) {
        runCatching { broadcast(binder) }
            .onFailure { Log.w(TAG, "handshake broadcast failed: ${it.message}", it) }
    }

    private fun broadcast(binder: IBinder) {
        val am = activityManager() ?: run { Log.w(TAG, "no IActivityManager"); return }
        val intent = Intent(WardenContract.ACTION_BINDER).apply {
            setPackage(WardenContract.MANAGER_PACKAGE)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra(WardenContract.EXTRA_BINDER, BinderContainer(binder))
        }

        val m = broadcastMethod(am.javaClass) ?: run { Log.w(TAG, "no broadcast method"); return }
        val types = m.parameterTypes
        Log.i(TAG, "using ${m.name}(${types.joinToString { it.simpleName }})")

        // Count int params so we can place resultCode/appOp/userId correctly.
        val intIdx = types.indices.filter { types[it] == Int::class.javaPrimitiveType }
        val args = arrayOfNulls<Any?>(types.size)
        types.forEachIndexed { i, t ->
            args[i] = when {
                t == Intent::class.java -> intent
                t == Boolean::class.javaPrimitiveType -> false
                t == Int::class.javaPrimitiveType -> when (i) {
                    intIdx.getOrNull(intIdx.size - 2) -> -1   // appOp = none
                    intIdx.lastOrNull() -> 0                   // userId = owner
                    else -> 0                                  // resultCode etc.
                }
                else -> null                                   // caller, strings, arrays, Bundle, receivers
            }
        }
        m.invoke(am, *args)
        Log.i(TAG, "binder broadcast sent to ${WardenContract.MANAGER_PACKAGE}")
    }

    private fun activityManager(): Any? = runCatching {
        // API 26+: ActivityManager.getService()
        Class.forName("android.app.ActivityManager")
            .getMethod("getService").invoke(null)
    }.getOrElse {
        runCatching {
            val amn = Class.forName("android.app.ActivityManagerNative")
            amn.getMethod("getDefault").invoke(null)
        }.getOrNull()
    }

    private fun broadcastMethod(cls: Class<*>): Method? {
        val methods = cls.methods.filter { it.name == "broadcastIntentWithFeature" }
            .ifEmpty { cls.methods.filter { it.name == "broadcastIntent" } }
        // Prefer the overload that actually takes an Intent and the most params.
        return methods.filter { m -> m.parameterTypes.any { it == Intent::class.java } }
            .maxByOrNull { it.parameterCount }
    }
}
