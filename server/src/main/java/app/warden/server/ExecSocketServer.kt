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
    private val server: LocalServerSocket,
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
        Log.i("Warden", "exec socket up: @$NAME")
        while (true) {
            val client = runCatching { server.accept() }.getOrNull() ?: continue
            Thread { runCatching { handle(client) } }.apply { isDaemon = true }.start()
        }
    }

    private fun handle(sock: LocalSocket) = sock.use {
        val uid = sock.peerCredentials.uid
        val id = auth.identify(uid)
        // The caller is checked before anything is read from it, and the read is bounded (64 KB, 10 s): an app
        // without the grant could otherwise send an endless line or hold threads with idle connections.
        val allowed = isManagerUid(uid) || grants.authorize(id, Grant.SCOPE_EXEC)
        if (!allowed) {
            audit.record(AuditSink.Event(ts = System.currentTimeMillis(), callerUid = uid, callerPkg = id.pkg,
                target = "su", argsDigest = "", verdict = "deny"))
            // With an exit code (1): the shim's default is 0, so "su -c x && y" carried on after a refusal.
            sock.outputStream.write("warden-su: denied\n\u00001\n".toByteArray()); return@use
        }
        sock.soTimeout = 10_000
        // The whole command, up to its NUL: a multi-line script arrives whole (a line read ran only its first line).
        val cmd = app.warden.api.SuWire.readCommand(sock.inputStream)?.trim().orEmpty()
        val t0 = System.nanoTime()
        // An empty (interactive su) or too-long command is refused: logged as that, not as allowed.
        audit.record(
            AuditSink.Event(
                ts = System.currentTimeMillis(), callerUid = uid, callerPkg = id.pkg,
                target = "su", argsDigest = cmd.take(120),
                verdict = if (cmd.isEmpty()) "deny" else "allow",
                outcome = if (cmd.isEmpty()) "empty or too-long command" else "",
            )
        )
        if (!allowed || cmd.isEmpty()) {
            sock.outputStream.write("warden-su: denied\n\u00001\n".toByteArray()); return@use
        }
        sock.soTimeout = 0   // the command itself may run long
        val proc = ProcessBuilder("sh", "-c", cmd)
            .redirectErrorStream(true).directory(File("/")).start()
        // No stdin is ever sent: close it so a command that reads it ends instead of waiting forever. If the client
        // goes away mid-output, the child is killed rather than left blocked on a full pipe.
        runCatching { proc.outputStream.close() }
        // Output is copied on its own thread while this one waits for the exit (a background child keeps the pipe
        // open after sh exits); if the client leaves, the command is ended. The pump finishes before the trailer.
        val pump = app.warden.api.SuWire.Pump(proc.inputStream, sock.outputStream) { proc.destroyForcibly() }
        val code = try { proc.waitFor().also { pump.finish() } } finally { if (proc.isAlive) proc.destroyForcibly() }
        sock.outputStream.write(app.warden.api.SuWire.trailer(code))
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
