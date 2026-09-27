package app.warden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.warden.api.RootedEntry
import app.warden.data.WardenClient
import app.warden.ui.components.Tone
import app.warden.ui.components.WTag
import app.warden.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun RootedListScreen() {
    val rootOk by produceState(false) { while (true) { value = WardenClient.rootAvailable; delay(1000) } }
    var entries by remember { mutableStateOf(WardenClient.rootedList()) }
    LaunchedEffect(Unit) { while (true) { entries = WardenClient.rootedList(); delay(1500) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Overline("Rooted list · ${entries.size} apps")
        Spacer(Modifier.height(10.dp))
        if (!rootOk) {
            Row(Modifier.fillMaxWidth().vCard().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WTag("shell", Tone.Warn)
                Text(
                    "Broker + Su work for cooperating apps. \"Spoof root detection\" " +
                        "needs the Zygisk module on a rooted device and is disabled here.",
                    style = T.bodySmall.copy(color = N.textMuted),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No apps on the list", style = T.subtitle.copy(color = N.textMuted))
                Spacer(Modifier.height(4.dp))
                Text("Add one from the Apps tab (+ Rooted)", style = T.bodySmall.copy(color = N.textMuted))
            }
        } else {
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
}

@Composable
private fun RootedCard(e: RootedEntry, rootOk: Boolean, onChange: (RootedEntry) -> Unit) {
    Column(Modifier.fillMaxWidth().vCard().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.pkg, style = T.mono.copy(color = N.text), modifier = Modifier.weight(1f))
            if (e.spoofDetection && rootOk) WTag("spoofing", Tone.Accent)
        }
        Spacer(Modifier.height(6.dp))
        ToggleRow("Broker · API access", e.giveBroker, true) { onChange(e.copy(giveBroker = it)) }
        ToggleRow("Su · shell-out", e.giveSu, true) { onChange(e.copy(giveSu = it)) }
        ToggleRow("Spoof root detection", e.spoofDetection, rootOk) {
            onChange(e.copy(spoofDetection = it, propsProfile = if (it) "rooted" else ""))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = T.body.copy(color = if (enabled) N.text else N.textMuted), modifier = Modifier.weight(1f))
        Switch(checked = checked && enabled, enabled = enabled, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = N.accent, checkedThumbColor = N.bg,
                uncheckedTrackColor = N.surfaceHi, uncheckedBorderColor = N.ring,
            ))
    }
}
