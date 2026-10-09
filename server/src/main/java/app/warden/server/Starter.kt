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
    /** The single-instance lock, kept referenced so it's never released while the server runs. */
    @Volatile private var heldLock: java.nio.channels.FileLock? = null

    @JvmStatic
    fun main(args: Array<String>) {
        val dataDir = File(args.firstOrNull() ?: "/data/local/tmp/warden").apply { mkdirs() }
        // Single-instance guard: a lock on a file only shell/root can reach. (The device-global socket name was the
        // guard before, and any app could bind it to stop Warden from ever starting.) Held for the process's life.
        val lock = runCatching { java.io.RandomAccessFile(File(dataDir, "server.lock"), "rw").channel.tryLock() }.getOrNull()
        if (lock == null) {
            Log.i(TAG, "already running; exiting")
            println("warden: already running")
            return
        }
        heldLock = lock
        // The su socket. If its name is taken by someone else (any app can bind an abstract name), Warden still runs —
        // only su is unavailable — and says who holds it.
        val execSocket = try { LocalServerSocket(ExecSocketServer.NAME) } catch (e: IOException) {
            val holder = runCatching {
                android.net.LocalSocket().use { s ->
                    s.connect(android.net.LocalSocketAddress(ExecSocketServer.NAME)); s.peerCredentials.uid
                }
            }.getOrNull()
            Log.e(TAG, "su socket @${ExecSocketServer.NAME} is held by uid $holder — su is unavailable until that app is removed")
            println("warden: su unavailable — its socket is held by uid $holder")
            null
        }
        Looper.prepareMainLooper()

        val managerCert = managerCertSha256()
        val service = WardenService(dataDir, managerCert)

        if (execSocket != null) ExecSocketServer(
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
