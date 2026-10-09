package app.warden.adb

import android.content.Context
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * PC-free, one-tap server start. Everything is auto-discovered over mDNS so the
 * user never types a port:
 *
 *   - [start]  : auto-connect to the device's own adbd (TLS, discovered) and
 *                launch the broker. If the device was never paired, adbd throws
 *                AdbPairingRequiredException and we surface [Outcome.PairNeeded].
 *   - [pairAndStart] : discover the transient pairing port over mDNS and pair
 *                with just the 6-digit code, then start.
 */
object AdbStarter {
    private const val DATA_DIR = "/data/local/tmp/warden"

    sealed interface Outcome {
        data class Launched(val log: String) : Outcome
        data object PairNeeded : Outcome
    }

    @Volatile private var providerReady = false
    private fun ensureConscrypt() {
        if (providerReady) return
        runCatching {
            java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
        }
        providerReady = true
    }

    suspend fun start(ctx: Context): Result<Outcome> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConscrypt()
            val mgr = AdbConnectionManager.getInstance(ctx)
            try {
                android.util.Log.i("Warden", "connectTls…")
                if (!mgr.connectTls(ctx, 8000)) {
                    android.util.Log.w("Warden", "connectTls returned false")
                    return@runCatching Outcome.PairNeeded
                }
            } catch (e: AdbPairingRequiredException) {
                android.util.Log.w("Warden", "pairing required")
                return@runCatching Outcome.PairNeeded
            } catch (e: InterruptedException) {
                // libadb's mDNS discovery timed out: adbd's wireless port isn't up.
                throw IllegalStateException(
                    if (!onWifi(ctx)) "Connect to Wi-Fi first — Android only runs Wireless debugging on Wi-Fi (not on mobile data or your own hotspot)."
                    else "Wireless debugging is off. Turn it on in Developer options → Wireless debugging, then tap Start.", e)
            }
            android.util.Log.i("Warden", "connected; launching server")
            val out = launch(ctx, mgr)
            android.util.Log.i("Warden", "launch output: ${out.take(200)}")
            Outcome.Launched(out)
        }.onFailure { android.util.Log.e("Warden", "start failed", it) }
    }

    suspend fun pairAndStart(ctx: Context, code: String): Result<Outcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureConscrypt()
                val port = discover(ctx, AdbMdns.SERVICE_TYPE_TLS_PAIRING)
                    ?: error("Couldn't find the pairing service. Open \"Pair device with pairing code\" and keep that screen open.")
                val mgr = AdbConnectionManager.getInstance(ctx)
                check(mgr.pair(port, code.trim())) { "Pairing failed — check the 6-digit code." }
                if (!mgr.connectTls(ctx, 8000))
                    return@runCatching Outcome.PairNeeded
                Outcome.Launched(launch(ctx, mgr))
            }
        }

    private fun launch(ctx: Context, mgr: AbsAdbConnectionManager): String {
        val apk = ctx.applicationInfo.sourceDir
        // setsid puts the server in its own session, so it survives libadb tearing
        // down the shell stream (which SIGKILLs the stream's process group).
        // chmod repairs a data dir that lost its search bit (seen in the field:
        // drw-r--r--), which otherwise makes the out.log redirect fail and the
        // server never start.
        val cmd = "mkdir -p $DATA_DIR; chmod -R u+rwX $DATA_DIR; " +
            "CLASSPATH=$apk setsid app_process /system/bin --nice-name=warden_server " +
            "app.warden.server.Starter $DATA_DIR >$DATA_DIR/out.log 2>&1 </dev/null & " +
            "echo warden-launched; sleep 1; cat $DATA_DIR/out.log 2>/dev/null | head -3"
        val stream = mgr.openStream("shell:$cmd")
        // libadb reports end-of-stream as IOException("Stream closed."), so keep
        // what was read instead of failing a launch that succeeded.
        val out = StringBuilder()
        try {
            stream.openInputStream().bufferedReader().forEachLine { out.appendLine(it) }
        } catch (e: java.io.IOException) {
            if (out.isEmpty()) throw e
        }
        return out.toString()
    }

    private fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        // Our own hotspot also shows up as a WIFI network (a LOCAL_NETWORK without
        // INTERNET). A joined Wi-Fi always has INTERNET, validated or not.
        return cm.allNetworks.any {
            val caps = cm.getNetworkCapabilities(it) ?: return@any false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    /** Discover a service's port over mDNS, first hit wins, with a timeout. */
    private suspend fun discover(ctx: Context, serviceType: String): Int? =
        withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine { cont ->
                val found = AtomicInteger(-1)
                // Stopped once found too (each pairing left a discovery running).
                lateinit var mdns: AdbMdns
                mdns = AdbMdns(ctx, serviceType) { _, port ->
                    if (port > 0 && found.compareAndSet(-1, port) && cont.isActive) { runCatching { mdns.stop() }; cont.resume(port) }
                }
                mdns.start()
                cont.invokeOnCancellation { runCatching { mdns.stop() } }
            }
        }
}
