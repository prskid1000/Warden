package app.warden.server

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import java.io.File

/**
 * Backs the layer-B `su` shim. Listens on the abstract LocalSocket "warden_exec"
 * (matching sushim/su.c). For each connection it authenticates the peer by its
 * socket credentials (uid — unspoofable, from the kernel), checks the exec
 * scope, runs the requested command with the server's identity, and streams the
 * combined output back. Every command is audited.
 */
class ExecSocketServer(
    private val auth: CallerAuth,
    private val grants: GrantStore,
    private val audit: AuditSink,
    private val isManagerUid: (Int) -> Boolean,
) {
    companion object { const val NAME = "warden_exec" }

    fun start() {
        Thread({ loop() }, "warden-exec").apply { isDaemon = true }.start()
    }

    private fun loop() {
        val server = LocalServerSocket(NAME)
        Log.i("Warden", "exec socket up: @$NAME")
        while (true) {
            val client = runCatching { server.accept() }.getOrNull() ?: continue
            Thread { runCatching { handle(client) } }.apply { isDaemon = true }.start()
        }
    }

    private fun handle(sock: LocalSocket) = sock.use {
        val uid = sock.peerCredentials.uid
        val id = auth.identify(uid)
        val cmd = sock.inputStream.bufferedReader().readLine()?.trim().orEmpty()
        val allowed = isManagerUid(uid) || grants.authorize(id, Grant.SCOPE_EXEC)
        val t0 = System.nanoTime()
        audit.record(
            AuditSink.Event(
                ts = System.currentTimeMillis(), callerUid = uid, callerPkg = id.pkg,
                target = "su", argsDigest = cmd.take(120),
                verdict = if (allowed) "allow" else "deny",
            )
        )
        if (!allowed || cmd.isEmpty()) {
            sock.outputStream.write("warden-su: denied\n".toByteArray()); return@use
        }
        val proc = ProcessBuilder("sh", "-c", cmd)
            .redirectErrorStream(true).directory(File("/")).start()
        proc.inputStream.copyTo(sock.outputStream)
        val code = proc.waitFor()
        // Exit-code trailer the shim can parse (\u0000 + code + \n).
        sock.outputStream.write("\u0000$code\n".toByteArray())
        sock.outputStream.flush()
        audit.record(
            AuditSink.Event(
                ts = System.currentTimeMillis(), callerUid = uid, callerPkg = id.pkg,
                target = "su", argsDigest = cmd.take(120), verdict = "allow",
                outcome = "exit:$code", latencyUs = (System.nanoTime() - t0) / 1000,
            )
        )
    }
}
