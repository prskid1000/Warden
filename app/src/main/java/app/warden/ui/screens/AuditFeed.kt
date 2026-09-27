package app.warden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.warden.data.WardenClient
import app.warden.ui.components.StatusDot
import app.warden.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Tails the hash-chained audit log; reused by the Home screen's activity feed. */
@Composable
fun rememberAuditLines(): List<JSONObject> {
    var lines by remember { mutableStateOf(listOf<JSONObject>()) }
    LaunchedEffect(Unit) {
        while (true) {
            val fd = WardenClient.auditFd()
            if (fd != null) withContext(Dispatchers.IO) {
                runCatching {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().useLines { seq ->
                        val acc = ArrayList<JSONObject>()
                        seq.forEach { l -> runCatching { acc.add(JSONObject(l).getJSONObject("e")) } }
                        lines = acc.takeLast(300).reversed()
                    }
                }
            }
            delay(1500)
        }
    }
    return lines
}

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
