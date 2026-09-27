package app.warden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.warden.api.RootedEntry
import app.warden.data.WardenClient
import app.warden.ui.theme.N

/**
 * The headline feature. Add an app here and toggle what it gets:
 *  · Broker  — elevated API access (layer A, any device)
 *  · Su      — working `su` when it shells out (layer B, cooperating apps)
 *  · Spoof   — its own root-detection reports "rooted" (layer C, ROOT ONLY)
 *
 * When the broker is not running as root, the Spoof switch is disabled and the
 * card says why — the app never pretends layer C works when it can't.
 */
@Composable
fun RootedListScreen() {
    val rootOk = WardenClient.rootAvailable
    var entries by remember { mutableStateOf(WardenClient.rootedList()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (!rootOk) {
            Surface(color = N.surface, shape = MaterialTheme.shapes.medium) {
                Text(
                    "Broker is running as shell (uid 2000). Broker + Su work for " +
                        "cooperating apps; \"Spoof root detection\" needs the Zygisk " +
                        "module on a rooted device and is disabled.",
                    color = N.textMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.pkg }) { e ->
                RootedCard(e, rootOk) { updated ->
                    WardenClient.setRooted(updated)
                    entries = entries.map { if (it.pkg == updated.pkg) updated else it }
                }
            }
        }
    }
}

@Composable
private fun RootedCard(e: RootedEntry, rootOk: Boolean, onChange: (RootedEntry) -> Unit) {
    Surface(color = N.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp)) {
            Text(e.pkg, color = N.text, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            ToggleRow("Broker (API access)", e.giveBroker, true) { onChange(e.copy(giveBroker = it)) }
            ToggleRow("Su (shell-out)", e.giveSu, true) { onChange(e.copy(giveSu = it)) }
            ToggleRow("Spoof root detection", e.spoofDetection, rootOk) {
                onChange(e.copy(spoofDetection = it, propsProfile = if (it) "rooted" else ""))
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label, color = if (enabled) N.text else N.textMuted,
            fontSize = 13.sp, modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked && enabled, enabled = enabled, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = N.accent)
        )
    }
}
