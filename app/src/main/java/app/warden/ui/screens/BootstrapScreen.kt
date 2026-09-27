package app.warden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.warden.data.WardenClient
import app.warden.ui.components.StatusDot
import app.warden.ui.components.Tone
import app.warden.ui.components.WTag
import app.warden.ui.theme.N
import app.warden.ui.theme.Overline
import app.warden.ui.theme.T
import app.warden.ui.theme.vCard
import app.warden.ui.theme.vInset
import kotlinx.coroutines.delay

@Composable
fun BootstrapScreen() {
    val connected by produceState(false) {
        while (true) { WardenClient.connect(); value = WardenClient.connected; delay(1000) }
    }
    val root by produceState(false) { while (true) { value = WardenClient.rootAvailable; delay(1000) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Status card
        Column(Modifier.fillMaxWidth().vCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline("Broker status")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(if (!connected) N.textMuted else if (root) N.ok else N.warn)
                Text(
                    when {
                        !connected -> "Not running"
                        root -> "Running as root — uid 0"
                        else -> "Running as shell — uid 2000"
                    },
                    style = T.subtitle,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LayerTag("A · broker", connected)
                LayerTag("B · su", connected)
                LayerTag("C · spoof", connected && root)
            }
        }

        StartCard("Start via ADB", "No root. Runs as shell (uid 2000) — layers A + B.",
            "adb shell sh /sdcard/Android/data/app.warden/files/start.sh")
        StartCard("Start via root", "Runs as uid 0 — all layers, incl. rooted-list spoofing.",
            "su -c sh /data/local/tmp/warden/start.sh")
    }
}

@Composable
private fun LayerTag(label: String, on: Boolean) =
    WTag(label, if (on) Tone.Ok else Tone.Outline)

@Composable
private fun StartCard(title: String, subtitle: String, cmd: String) {
    Column(Modifier.fillMaxWidth().vCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = T.cardTitle)
        Text(subtitle, style = T.bodySmall.copy(color = N.textMuted))
        Text(cmd, style = T.mono.copy(color = N.accent2),
            modifier = Modifier.fillMaxWidth().vInset().padding(12.dp))
    }
}
