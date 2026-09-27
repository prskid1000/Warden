package app.warden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.warden.data.WardenClient
import app.warden.ui.theme.N
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader

/**
 * Live audit stream: which app touched which system surface, allow or deny.
 * Reads the JSONL fd the broker exposes and tails it. This is Warden's reason
 * to exist over Shizuku — nothing the broker does is invisible.
 */
@Composable
fun AuditScreen() {
    var lines by remember { mutableStateOf(listOf<JSONObject>()) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val fd = WardenClient.auditFd() ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)
                .bufferedReader().useLines { seq ->
                    val acc = ArrayList<JSONObject>()
                    seq.forEach { runCatching { acc.add(JSONObject(it)) } }
                    lines = acc.takeLast(500).reversed()
                }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = filter, onValueChange = { filter = it },
            label = { Text("filter by package") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(lines.filter { filter.isBlank() || it.optString("pkg").contains(filter) }) { o ->
                val allow = o.optString("verdict") == "allow"
                Surface(color = N.surface, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            o.optString("pkg", "uid ${o.optInt("uid")}"),
                            color = if (allow) N.allow else N.deny, fontSize = 13.sp
                        )
                        Text(
                            o.optString("target") + "  " + o.optString("args"),
                            color = N.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
