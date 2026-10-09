package app.warden.api

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * The su shim's wire format, shared by the broker's exec socket and the in-app relay: the command arrives ended by
 * a NUL (so a multi-line script comes whole), the output streams back, and a trailer — NUL, exit code, newline —
 * ends it. The shim reads the trailer only from the very end, so NULs inside the output are fine.
 */
object SuWire {
    /** The command, up to its NUL (or the end), at most [max] bytes; null if longer or empty. */
    fun readCommand(input: InputStream, max: Int = 65_536): String? {
        val buf = java.io.ByteArrayOutputStream()
        while (buf.size() <= max) {
            val b = input.read()
            if (b < 0 || b == 0) return buf.toString(Charsets.UTF_8.name()).takeIf { it.isNotBlank() }
            buf.write(b)
        }
        return null
    }

    fun trailer(code: Int): ByteArray = "\u0000$code\n".toByteArray()

    /**
     * Copy a command's output to the client on its own thread. If the client goes away (a write fails) [onClientGone]
     * ends the command. Call [finish] after the command exits.
     */
    class Pump(private val from: InputStream, to: OutputStream, onClientGone: () -> Unit) {
        private val progress = AtomicLong(System.currentTimeMillis())
        // Declared before the thread, which starts during construction and uses them.
        @Volatile private var closedByUs = false
        /** In a write to the client: a slow reader, not an idle pipe — never cut off. */
        @Volatile private var writing = false
        private val thread = Thread {
            runCatching {
                val chunk = ByteArray(65_536)
                while (true) {
                    val n = from.read(chunk); if (n < 0) break
                    progress.set(System.currentTimeMillis())
                    writing = true; to.write(chunk, 0, n); writing = false
                    progress.set(System.currentTimeMillis())
                }
                to.flush()
            }.onFailure { if (it !is java.io.IOException || !closedByUs) onClientGone() }
        }.apply { isDaemon = true; start() }

        /**
         * Wait for the rest of the output. It may still be flowing to a slow reader (a pager): keep waiting while
         * bytes move. Only when nothing has moved for 2 s (a background child holding the pipe open) is the input
         * closed, so the trailer can follow.
         */
        fun finish() {
            while (thread.isAlive) {
                thread.join(500)
                if (thread.isAlive && !writing && System.currentTimeMillis() - progress.get() > 2_000) {
                    closedByUs = true; runCatching { from.close() }; thread.join(2_000); break
                }
            }
        }
    }
}
