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
    private val queue = LinkedBlockingQueue<Event>()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val maxBytes = 8L * 1024 * 1024
    @Volatile private var prevHash = seedHash()

    init {
        Thread({ drainLoop() }, "warden-audit").apply { isDaemon = true }.start()
    }

    /** Non-blocking: enqueue and return. */
    fun record(e: Event) { queue.offer(e) }

    private fun drainLoop() {
        while (true) {
            val e = runCatching { queue.take() }.getOrNull() ?: continue
            runCatching { writeOne(e) }.onFailure { Log.e("Warden", "audit write", it) }
        }
    }

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
