package app.warden.server

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue

/**
 * Append-only, tamper-evident audit trail written off the hot path.
 *
 * Design points (the improvements over the first cut):
 *  - Every privileged call is recorded BEFORE the permission check, so denials
 *    are logged too.
 *  - [record] only enqueues; a single writer thread does file I/O, so brokered
 *    binder calls never serialize on disk.
 *  - Each line carries `h = sha256(prevHash + payload)`, a hash chain: deleting
 *    or editing any line breaks verification of every line after it.
 */
class AuditSink(private val dir: File) {

    data class Event(
        val ts: Long,
        val callerUid: Int,
        val callerPkg: String?,
        val target: String,
        val argsDigest: String,
        val verdict: String,       // "allow" | "deny"
        val outcome: String = "",  // "ok" | "error:<msg>" | ""
        val latencyUs: Long = -1,
    )

    private val file = File(dir, "audit.jsonl").apply { parentFile?.mkdirs() }
    // Bounded: a flood can't run the broker out of memory (what doesn't fit is counted, not queued).
    private val queue = LinkedBlockingQueue<Event>(10_000)
    private val dropped = java.util.concurrent.atomic.AtomicLong()
    /** Per uid: when its last refusal was written, and how many since were folded into the next one. */
    private val denials = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, Int>>()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val maxBytes = 8L * 1024 * 1024
    @Volatile private var prevHash = seedHash()

    init {
        Thread({ drainLoop() }, "warden-audit").apply { isDaemon = true }.start()
    }

    /** Non-blocking: enqueue and return. */
    // Refusals are written at most once a second per caller (with a count of the rest): a hostile app looping on
    // refused calls otherwise rotated the whole history away within seconds.
    fun record(e: Event) {
        var ev = e
        if (e.verdict == "deny") {
            val now = System.currentTimeMillis()
            var write = false; var folded = 0
            denials.compute(e.callerUid) { _, last ->
                if (last == null || now - last.first >= 1_000) { write = true; folded = last?.second ?: 0; now to 0 }
                else last.first to last.second + 1
            }
            if (!write) return
            if (folded > 0) ev = e.copy(outcome = e.outcome + " (+$folded more refused)")
        }
        if (!queue.offer(ev)) dropped.incrementAndGet()
    }

    private fun drainLoop() {
        while (true) {
            val e = runCatching { queue.take() }.getOrNull() ?: continue
            runCatching { writeOne(e) }.onFailure { Log.e("Warden", "audit write", it) }
        }
    }

    // Same lock as clear(): an event written during a clear chained to the old hash and broke the new log's chain.
    @Synchronized
    private fun writeOne(e: Event) {
        val payload = JSONObject().apply {
            put("ts", e.ts); put("uid", e.callerUid)
            put("pkg", e.callerPkg ?: JSONObject.NULL)
            put("target", e.target); put("args", e.argsDigest)
            put("verdict", e.verdict); put("outcome", e.outcome); put("lat_us", e.latencyUs)
        }.toString()
        val h = sha256(prevHash + payload)
        val line = "{\"h\":\"$h\",\"e\":$payload}"
        if (file.length() > maxBytes) rotate()
        file.appendText(line + "\n")
        prevHash = h
        listeners.forEach { runCatching { it.onLine(line) } }
    }

    private fun rotate() {
        File(dir, "audit.1.jsonl").let { if (it.exists()) it.delete(); file.renameTo(it) }
    }

    interface Listener {
        fun onLine(line: String)
        fun onCleared()
    }

    fun subscribe(l: Listener): () -> Unit {
        listeners.add(l); return { listeners.remove(l) }
    }

    fun path(): String = file.absolutePath

    /** Wipe the log and reset the hash chain. */
    @Synchronized
    fun clear() {
        queue.clear()
        runCatching {
            file.writeText("")
            File(dir, "audit.1.jsonl").takeIf { it.exists() }?.delete()
        }
        prevHash = "0".repeat(64)
        listeners.forEach { runCatching { it.onCleared() } }
    }

    private fun seedHash(): String =
        runCatching {
            file.takeIf { it.exists() }?.useLines { it.lastOrNull() }
                ?.let { JSONObject(it).optString("h") }
        }.getOrNull()?.ifEmpty { null } ?: "0".repeat(64)

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
