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
        // Each running process keeps a binder thread in waitFor: stay under the ~15-thread pool, or destroy() and
        // every other call would block behind them.
        private const val MAX_CONCURRENT = 12
        private val live = AtomicInteger(0)
        private val pumps = Executors.newCachedThreadPool { r ->
            Thread(r, "warden-pipe").apply { isDaemon = true }
        }
    }

    // This process's slot in [live], given back exactly once: waitFor and destroy both end it (a client that times
    // out calls both), and a failed start must return it too.
    private val released = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun release() { if (released.compareAndSet(false, true)) live.decrementAndGet() }

    private val process: Process = run {
        check(live.incrementAndGet() <= MAX_CONCURRENT) {
            live.decrementAndGet(); "Warden: too many concurrent processes"
        }
        try {
            ProcessBuilder(*cmd)
                .directory(File(dir))
                .also { pb -> env.forEach { e -> pb.environment()[e.substringBefore('=')] = e.substringAfter('=', "") } }
                .start()
        } catch (e: Exception) { live.decrementAndGet(); released.set(true); throw e }
    }

    // A client that drops the process (dies, or never calls waitFor/destroy) still gets its slot back when it exits.
    init { pumps.execute { runCatching { process.waitFor() }; release() } }

    override fun getOutputStream(): ParcelFileDescriptor = pipeTo(process.outputStream)
    override fun getInputStream(): ParcelFileDescriptor = pipeFrom(process.inputStream)
    override fun getErrorStream(): ParcelFileDescriptor = pipeFrom(process.errorStream)

    override fun waitFor(): Int = process.waitFor().also { release() }
    override fun exitValue(): Int = process.exitValue()
    override fun alive(): Boolean = process.isAlive
    override fun destroy() {
        if (process.isAlive) process.destroyForcibly()
        release()
    }

    private fun pipeFrom(input: java.io.InputStream): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        pumps.execute {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                // The client went away (its end closed): keep draining so the child never blocks on a full pipe.
                runCatching { input.copyTo(out) }.onFailure { runCatching { input.copyTo(java.io.OutputStream.nullOutputStream()) } }
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
