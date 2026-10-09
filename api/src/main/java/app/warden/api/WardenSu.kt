package app.warden.api

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import java.io.File

/**
 * Makes `su -c CMD` work inside a cooperating app on a non-root (ADB-started)
 * broker.
 *
 * The broker runs in the shell SELinux domain and policy forbids apps from
 * connecting to its sockets, so the shim can't reach it directly. Instead the
 * shim connects to a relay hosted here, in the app's own process (same uid and
 * domain), which forwards the command over the broker binder, where it is
 * grant-checked and audited like any other call.
 *
 * Apps can't exec files from their data dir (W^X, targetSdk 29+), so the shim
 * ships as libwardensu.so in the native lib dir and `su` is a symlink to it.
 * The app must extract native libs: `packaging.jniLibs.useLegacyPackaging = true`.
 *
 * Usage: `val bin = WardenSu.install(context)`, then prepend `bin` to PATH of
 * the processes that should see `su`. Interactive `su` (no `-c`) isn't supported.
 */
object WardenSu {
    private const val SHIM = "libwardensu.so"
    @Volatile private var app: Context? = null

    /** Must match the shim's `warden_exec.<uid>` (sushim/su.c). */
    @JvmStatic
    fun socketName(uid: Int = Process.myUid()): String = "warden_exec.$uid"

    /** Starts the relay (idempotent) and returns the dir that holds `su`. */
    @JvmStatic
    @Synchronized
    fun install(context: Context): File {
        val shim = File(context.applicationInfo.nativeLibraryDir, SHIM)
        check(shim.exists()) { "$SHIM not extracted — set packaging.jniLibs.useLegacyPackaging = true" }
        val bin = File(context.filesDir, "warden-bin").apply { mkdirs() }
        // Re-link every time: nativeLibraryDir moves on each app update.
        val su = File(bin, "su").apply { delete() }
        Os.symlink(shim.absolutePath, su.absolutePath)
        if (app == null) {
            app = context.applicationContext
            val server = LocalServerSocket(socketName())
            Thread({
                while (true) {
                    val client = runCatching { server.accept() }.getOrNull() ?: continue
                    Thread({ runCatching { serve(client) } }, "warden-su").apply { isDaemon = true }.start()
                }
            }, "warden-su-relay").apply { isDaemon = true }.start()
        }
        return bin
    }

    private fun serve(sock: LocalSocket) = sock.use {
        val out = sock.outputStream
        // Abstract socket names are device-global: only our own uid may relay.
        if (sock.peerCredentials.uid != Process.myUid()) {
            out.write("warden-su: denied\n\u00001\n".toByteArray()); return   // exit code 1, not the shim's default 0
        }
        // The whole command, up to its NUL (a multi-line script arrives whole).
        val cmd = SuWire.readCommand(sock.inputStream)?.trim().orEmpty()
        var proc: app.warden.api.IRemoteProcess? = null
        val code = try {
            check(cmd.isNotEmpty()) { "interactive su isn't supported; use su -c CMD" }
            check(Warden.bind(app!!)) { "broker not running" }
            val p = Warden.newProcess(arrayOf("sh", "-c", "exec 2>&1; $cmd")).also { proc = it }
            p.outputStream.close()   // no stdin for -c commands
            // Copied beside the wait (a background child keeps the output open after sh exits). If the caller
            // leaves, the command is ended, which frees its broker slot. The pump finishes before the trailer.
            val pump = SuWire.Pump(ParcelFileDescriptor.AutoCloseInputStream(p.inputStream), out) { runCatching { p.destroy() } }
            p.waitFor().also { pump.finish() }
        } catch (e: Exception) {
            runCatching { proc?.destroy() }
            runCatching { out.write("warden-su: ${e.message}\n".toByteArray()) }
            1
        }
        out.write(SuWire.trailer(code))
        out.flush()
    }
}
