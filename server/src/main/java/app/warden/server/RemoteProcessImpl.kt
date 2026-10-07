package app.warden.server

import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * A real OS process launched with the server's identity. Improvements over the
 * first cut: pipe pumps run on a shared bounded pool (no unbounded thread churn),
 * concurrent processes are capped, and destroy is forcible.
 *
 * `waitFor` is still a blocking binder call by contract; the client is expected
 * to invoke it off its main/binder thread. The cap below protects the server.
 */
class RemoteProcessImpl(
    cmd: Array<String>,
    env: Array<String>,
    dir: String,
) : app.warden.api.IRemoteProcess.Stub() {

    companion object {
        private const val MAX_CONCURRENT = 32
        private val live = AtomicInteger(0)
        private val pumps = Executors.newCachedThreadPool { r ->
            Thread(r, "warden-pipe").apply { isDaemon = true }
        }
    }

    private val process: Process = run {
        check(live.incrementAndGet() <= MAX_CONCURRENT) {
            live.decrementAndGet(); "Warden: too many concurrent processes"
        }
        ProcessBuilder(*cmd)
            .directory(File(dir))
            .also { pb -> env.forEach { e -> pb.environment()[e.substringBefore('=')] = e.substringAfter('=', "") } }
            .start()
    }

    override fun getOutputStream(): ParcelFileDescriptor = pipeTo(process.outputStream)
    override fun getInputStream(): ParcelFileDescriptor = pipeFrom(process.inputStream)
    override fun getErrorStream(): ParcelFileDescriptor = pipeFrom(process.errorStream)

    override fun waitFor(): Int = process.waitFor().also { live.decrementAndGet() }
    override fun exitValue(): Int = process.exitValue()
    override fun alive(): Boolean = process.isAlive
    override fun destroy() {
        if (process.isAlive) process.destroyForcibly()
        live.decrementAndGet()
    }

    private fun pipeFrom(input: java.io.InputStream): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        pumps.execute {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                runCatching { input.copyTo(out) }
            }
        }
        return pipe[0]
    }

    private fun pipeTo(output: java.io.OutputStream): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        pumps.execute {
            // Closing the process's stdin when the client closes its end is what gives
            // the child EOF — and flushes the last buffered bytes. Without it, a child
            // reading stdin (e.g. `cmd package install -S <size>`) waits forever on the
            // tail still sitting in the process stream's 8 KB buffer.
            ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { runCatching { it.copyTo(output) } }
            runCatching { output.close() }
        }
        return pipe[1]
    }
}
