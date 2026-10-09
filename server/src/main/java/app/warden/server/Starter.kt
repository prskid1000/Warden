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
        val lockFile = File(dataDir, "server.lock")
        // A file a root start left behind is made the shell user's, so a later ADB start can open it.
        if (android.os.Process.myUid() == 0) runCatching {
            lockFile.createNewFile(); android.system.Os.chown(dataDir.path, 2000, 2000); android.system.Os.chown(lockFile.path, 2000, 2000)
        }
        // Can't open it (a root-owned file from an earlier root start) is not "already running": say what's wrong.
        val channel = runCatching { java.io.RandomAccessFile(lockFile, "rw").channel }.getOrElse {
            Log.e(TAG, "can't open ${lockFile.path}: ${it.message}")
            println("warden: can't open ${lockFile.path} (${it.message}) — delete it, or start once as root to fix its owner")
            return
        }
        val lock = runCatching { channel.tryLock() }.getOrNull()
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
            // Held by shell or root: another Warden (an older one without the lock file). Don't run two brokers.
            if (holder == 0 || holder == 2000) {
                Log.i(TAG, "another Warden holds the su socket; exiting")
                println("warden: already running")
                return
            }
            Log.e(TAG, "su socket @${ExecSocketServer.NAME} is held by uid $holder — su is unavailable until that app is removed")
            println("warden: su unavailable — its socket is held by uid $holder")
            null
        }
        Looper.prepareMainLooper()

        val managerCert = managerCertSha256()
        val service = WardenService(dataDir, managerCert)
        // As root, everything in the data folder stays the shell user's (state/, grants.json, log/, rotated audit
        // logs): a later ADB start (as shell) otherwise silently couldn't save grants or write the audit log.
        if (android.os.Process.myUid() == 0) Thread({
            while (true) {
                runCatching { dataDir.walkTopDown().forEach { f -> runCatching { android.system.Os.chown(f.path, 2000, 2000) } } }
                Thread.sleep(30_000)
            }
        }, "warden-owner").apply { isDaemon = true; start() }

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
