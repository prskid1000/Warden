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

    /** One line of at most [max] bytes (null if longer or the stream ends first). */
    private fun readLineBounded(input: java.io.InputStream, max: Int): String? {
        val buf = java.io.ByteArrayOutputStream()
        while (buf.size() <= max) {
            val b = input.read()
            if (b < 0) return if (buf.size() > 0) buf.toString(Charsets.UTF_8.name()) else null
            if (b == '\n'.code) return buf.toString(Charsets.UTF_8.name())
            buf.write(b)
        }
        return null
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
        val cmd = readLineBounded(sock.inputStream, 65_536)?.trim().orEmpty()
        val t0 = System.nanoTime()
        audit.record(
            AuditSink.Event(
                ts = System.currentTimeMillis(), callerUid = uid, callerPkg = id.pkg,
                target = "su", argsDigest = cmd.take(120),
                verdict = if (allowed) "allow" else "deny",
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
        // Output is copied on its own thread while this one waits for the exit: a background child (`daemon &`)
        // keeps the pipe open after sh exits, and reading to EOF here never reached the exit code.
        // The client left (a write failed): end the command, which also ends the wait below.
        val copier = Thread { runCatching { proc.inputStream.copyTo(sock.outputStream) }.onFailure { proc.destroyForcibly() } }
            .apply { isDaemon = true; start() }
        val code = try {
            // After closing the input, wait for the copier to stop: the trailer must come after the last output byte.
            proc.waitFor().also { copier.join(2_000); runCatching { proc.inputStream.close() }; copier.join(2_000) }
        } finally { if (proc.isAlive) proc.destroyForcibly() }
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
