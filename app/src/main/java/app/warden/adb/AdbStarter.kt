package app.warden.adb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Starts the Warden broker with no PC: an in-app ADB client pairs with the
 * device's own adbd over Wireless Debugging (Android 11+) and runs the bootstrap
 * command as shell (uid 2000). The server then broadcasts its binder to the
 * manager via the existing handshake, so the app connects to itself.
 *
 * Host is always 127.0.0.1 — adbd binds loopback too, so no network exposure.
 */
object AdbStarter {
    private const val HOST = "127.0.0.1"
    private const val DATA_DIR = "/data/local/tmp/warden"

    @Volatile private var providerReady = false
    private fun ensureConscrypt() {
        if (providerReady) return
        runCatching {
            java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
        }
        providerReady = true
    }

    /** One-time pairing with a code from "Pair device with pairing code". */
    suspend fun pair(ctx: Context, port: Int, code: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureConscrypt()
                val ok = AdbConnectionManager.getInstance(ctx).pair(HOST, port, code)
                check(ok) { "Pairing rejected — check the port and code." }
            }
        }

    /**
     * Connect on the Wireless-debugging port and launch the server. Returns the
     * first line(s) of shell output for display.
     */
    suspend fun start(ctx: Context, connectPort: Int): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureConscrypt()
                val mgr = AdbConnectionManager.getInstance(ctx)
                check(mgr.connect(HOST, connectPort)) { "Could not connect on $connectPort. Pair first, and use the current port." }
                val apk = ctx.applicationInfo.sourceDir
                // Detach so the server outlives this ADB stream (else SIGHUP).
                val launch = "CLASSPATH=$apk nohup app_process /system/bin " +
                    "--nice-name=warden_server app.warden.server.Starter $DATA_DIR " +
                    ">$DATA_DIR/out.log 2>&1 </dev/null &"
                val cmd = "mkdir -p $DATA_DIR; ($launch) ; echo warden-launched"
                val stream = mgr.openStream("shell:$cmd")
                val out = withTimeoutOrNull(4000) {
                    stream.openInputStream().bufferedReader().readText()
                } ?: ""
                if (out.contains("warden-launched")) "Server launched." else out.ifBlank { "launched" }
            }
        }
}
