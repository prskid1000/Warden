package app.warden.server

import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.IInterface
import android.os.SystemClock
import android.util.Log
import app.warden.api.WardenContract

/**
 * Keeps the manager app running for as long as this server runs, and hands it
 * the binder whenever it (re)starts — driven by binder death, not a timer.
 *
 * It holds an external handle on the manager's provider, the same thing
 * `adb shell content` takes. Acquiring the handle starts the manager if it isn't
 * running, so the binder can be delivered the moment it returns. While the
 * handle is held, ActivityManager ranks the manager as foreground ("ext-provider"),
 * so it is neither killed nor frozen in the background. If it dies anyway
 * (force-stop, an update), the provider binder's death is the cue to take a new
 * handle — restarting it — and deliver again.
 *
 * The manager can't signal this process itself: SELinux keeps apps off shell
 * sockets, and ContentService refuses observers from processes it doesn't track.
 */
object ManagerTether {
    private const val TAG = "Warden"
    private val authority = WardenContract.AUTHORITY

    private val handler by lazy { Handler(HandlerThread("warden-tether").apply { start() }.looper) }
    private lateinit var broker: IBinder
    private var quickDeaths = 0

    /** Returns false if no handle could be taken (unsupported framework shape, manager missing). */
    fun hold(binder: IBinder): Boolean {
        broker = binder
        return attach()
    }

    private fun attach(): Boolean {
        val token = Binder()
        val provider = runCatching { acquire(token) }
            .onFailure { Log.w(TAG, "tether: can't take provider handle: ${it.message}", it) }
            .getOrNull() ?: return false
        val since = SystemClock.elapsedRealtime()
        val linked = runCatching {
            provider.linkToDeath({ handler.post { onManagerDied(token, since) } }, 0)
        }.isSuccess
        if (!linked) { handler.post { onManagerDied(token, since) }; return true }
        ManagerHandshake.deliver(broker)
        Log.i(TAG, "tether: holding $authority")
        return true
    }

    private fun onManagerDied(token: IBinder, since: Long) {
        runCatching { release(token) }
        // A manager stuck in a crash loop would otherwise be restarted in a tight
        // loop; back off once it has died soon after starting three times running.
        // Ordinary restarts (an update, a force-stop, a test run) re-attach at once.
        quickDeaths = if (SystemClock.elapsedRealtime() - since < 10_000) quickDeaths + 1 else 0
        val wait = if (quickDeaths < 3) 0L else minOf(60_000L, 1000L shl minOf(quickDeaths - 2, 6))
        Log.i(TAG, "tether: manager died; re-taking handle in ${wait}ms")
        handler.postDelayed({
            if (!attach()) Log.w(TAG, "tether: manager gone (uninstalled?); not retrying")
        }, wait)
    }

    // ---- hidden IActivityManager calls ---------------------------------------

    /** The provider's binder; starts the manager's process if needed. */
    private fun acquire(token: IBinder): IBinder {
        val am = ManagerHandshake.activityManager() ?: error("no IActivityManager")
        val m = am.javaClass.methods.firstOrNull { it.name == "getContentProviderExternal" }
            ?: error("no getContentProviderExternal")
        // (name, userId, token[, tag]) — the tag arrived in API 29.
        val holder = when (m.parameterCount) {
            4 -> m.invoke(am, authority, 0, token, "warden")
            3 -> m.invoke(am, authority, 0, token)
            else -> error("getContentProviderExternal has ${m.parameterCount} params")
        } ?: error("no provider $authority (manager not installed?)")
        val provider = holder.javaClass.getField("provider").get(holder) as? IInterface
            ?: error("holder without provider")
        return provider.asBinder()
    }

    private fun release(token: IBinder) {
        val am = ManagerHandshake.activityManager() ?: return
        am.javaClass.methods.firstOrNull { it.name == "removeContentProviderExternalAsUser" }
            ?.let { it.invoke(am, authority, token, 0); return }
        am.javaClass.methods.firstOrNull { it.name == "removeContentProviderExternal" }
            ?.invoke(am, authority, token)
    }
}
