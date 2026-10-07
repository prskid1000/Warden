package app.warden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.warden.data.WardenClient
import app.warden.ui.components.StatusDot
import app.warden.ui.theme.*
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import app.warden.api.IAuditListener
import org.json.JSONObject

/**
 * The hash-chained audit log, newest first; reused by the Home screen's activity
 * feed. Read once, then kept current by lines the broker pushes as it writes them.
 */
@Composable
fun rememberAuditLines(): List<JSONObject> {
    var lines by remember { mutableStateOf(listOf<JSONObject>()) }
    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        // Lines pushed before the snapshot is in; merged into it by hash. Main thread only.
        var early: MutableList<JSONObject>? = mutableListOf()
        val listener = object : IAuditListener.Stub() {
            override fun onLine(line: String) {
                val e = parse(line) ?: return
                main.post {
                    early?.add(0, e)
                    lines = (listOf(e) + lines).take(MAX_LINES)
                }
            }
            override fun onCleared() { main.post { early?.clear(); lines = emptyList() } }
        }
        // Subscribe before reading the snapshot so nothing written in between is lost.
        WardenClient.watchAudit(listener)
        val read = Thread {
            val fd = WardenClient.auditFd() ?: return@Thread
            val snapshot = runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().useLines { seq ->
                    val acc = ArrayList<JSONObject>()
                    seq.forEach { l -> parse(l)?.let(acc::add) }
                    acc.takeLast(MAX_LINES).reversed()
                }
            }.getOrNull() ?: return@Thread
            main.post {
                val seen = snapshot.mapTo(HashSet()) { it.optString("h") }
                lines = (early.orEmpty().filter { it.optString("h") !in seen } + snapshot).take(MAX_LINES)
                early = null
            }
        }.apply { start() }
        onDispose {
            read.interrupt()
            WardenClient.unwatchAudit(listener)
        }
    }
    return lines
}

private const val MAX_LINES = 300

/** A log line's event, carrying the line's chain hash as its identity. */
private fun parse(line: String): JSONObject? = runCatching {
    val o = JSONObject(line)
    o.getJSONObject("e").put("h", o.optString("h"))
}.getOrNull()

@Composable
fun AuditRow(o: JSONObject) {
    val allow = o.optString("verdict") == "allow"
    Row(Modifier.fillMaxWidth().vCard(N.shapeMd).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusDot(if (allow) N.ok else N.danger, Modifier.padding(top = 4.dp))
        Column(Modifier.weight(1f)) {
            Text(o.optString("pkg").ifEmpty { "uid ${o.optInt("uid")}" },
                style = T.bodySmall.copy(color = if (allow) N.ok else N.danger))
            Text(o.optString("target"), style = T.mono.copy(color = N.text))
            val meta = buildList {
                o.optString("args").takeIf { it.isNotBlank() }?.let { add(it) }
                o.optString("outcome").takeIf { it.isNotBlank() }?.let { add(it) }
                o.optLong("lat_us", -1).takeIf { it >= 0 }?.let { add("${it}µs") }
            }.joinToString("  ·  ")
            if (meta.isNotBlank()) Text(meta, style = T.monoSmall)
        }
    }
}
