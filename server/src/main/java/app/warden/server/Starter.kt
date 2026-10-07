package app.warden.server

import android.content.pm.PackageManager
import android.net.LocalServerSocket
import android.os.Build
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Entry point that `app_process` invokes from starter/start.sh. Runs as shell
 * (uid 2000) or root (uid 0).
 *
 *   1. Prepare Looper + data dir.
 *   2. Pin the manager's signing cert (whatever currently signs app.warden), so
 *      manager-only calls are authenticated by key, not by package name alone.
 *   3. Build the broker, publish its binder, start the exec socket (layer B).
 */
object Starter {
    private const val TAG = "Warden"

    @JvmStatic
    fun main(args: Array<String>) {
        // Single-instance guard. The socket name is device-global, so if it's taken
        // a broker is already running (e.g. Start tapped before the app re-attached).
        // Claim it before touching the audit log or publishing a second binder.
        val execSocket = try {
            LocalServerSocket(ExecSocketServer.NAME)
        } catch (e: IOException) {
            Log.i(TAG, "already running; exiting")
            println("warden: already running")
            return
        }
        Looper.prepareMainLooper()
        val dataDir = File(args.firstOrNull() ?: "/data/local/tmp/warden").apply { mkdirs() }

        val managerCert = managerCertSha256()
        val service = WardenService(dataDir, managerCert)

        ExecSocketServer(
            server = execSocket,
            auth = CallerAuth(managerCert),
            grants = service.grantStore(),
            audit = service.audit,
            isManagerUid = service::isManagerUid,
        ).start()

        BinderPublisher.publish(service)
        Log.i(TAG, "Warden up: uid=${service.serverUid()} data=$dataDir managerCert=${managerCert?.take(12)}")

        Looper.loop()
    }

    private fun managerCertSha256(): String? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES.toLong()
        val info = Hidden.getPackageInfo(BinderPublisher.MANAGER_PACKAGE, flags, 0) ?: return null
        val sig = if (Build.VERSION.SDK_INT >= 28)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        else @Suppress("DEPRECATION") info.signatures?.firstOrNull()?.toByteArray()
        sig ?: return null
        MessageDigest.getInstance("SHA-256").digest(sig)
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
