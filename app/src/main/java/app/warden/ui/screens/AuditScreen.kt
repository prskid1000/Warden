package app.warden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.warden.data.WardenClient
import app.warden.ui.components.StatusDot
import app.warden.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun AuditScreen() {
    var lines by remember { mutableStateOf(listOf<JSONObject>()) }
    var filter by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(Unit) {
        while (true) {
            val fd = WardenClient.auditFd()
            if (fd != null) withContext(Dispatchers.IO) {
                runCatching {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().useLines { seq ->
                        val acc = ArrayList<JSONObject>()
                        seq.forEach { l -> runCatching { acc.add(JSONObject(l).getJSONObject("e")) } }
                        lines = acc.takeLast(500).reversed()
                    }
                }
            }
            delay(1500)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Overline("Audit trail · ${lines.size} events")
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().vInset().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (filter.text.isEmpty()) Text("Filter by package", style = T.body.copy(color = N.textMuted))
            BasicTextField(filter, { filter = it }, singleLine = true,
                textStyle = T.body, cursorBrush = SolidColor(N.accent))
        }
        Spacer(Modifier.height(12.dp))
        if (lines.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No events yet", style = T.subtitle.copy(color = N.textMuted))
                Spacer(Modifier.height(4.dp))
                Text("Privileged calls appear here as they happen", style = T.bodySmall.copy(color = N.textMuted))
            }
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines.filter { filter.text.isBlank() || it.optString("pkg").contains(filter.text) }) { o ->
                AuditRow(o)
            }
        }
    }
}

@Composable
private fun AuditRow(o: JSONObject) {
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
