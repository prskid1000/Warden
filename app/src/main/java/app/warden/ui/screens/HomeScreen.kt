package app.warden.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.warden.ui.StartUi
import app.warden.ui.components.*
import app.warden.ui.theme.*

/**
 * Home = broker status hero + (when offline) the one-tap start flow + (always)
 * the live activity feed. Combines the old Start and Audit tabs, Sundown-style:
 * a big hero on top, ringed cards below, fading-rule section headers.
 */
@Composable
fun HomeScreen(connected: Boolean, root: Boolean, start: StartUi) {
    val lines = if (connected) rememberAuditLines() else emptyList()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(connected, root, lines.size) }

        if (!connected) {
            item { StartPanel(start) }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Kicker("Activity")
                    Spacer(Modifier.width(10.dp))
                    WRule(Modifier.weight(1f))
                }
            }
            if (lines.isEmpty()) item {
                Text("No privileged calls yet — they'll appear here as apps use the broker.",
                    style = T.bodySmall.copy(color = N.textMuted), modifier = Modifier.padding(vertical = 8.dp))
            }
            items(lines) { AuditRow(it) }
        }
    }
}

@Composable
private fun Hero(connected: Boolean, root: Boolean, events: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Kicker(if (!connected) "Broker" else "Broker · running")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusDot(if (!connected) N.textMuted else if (root) N.ok else N.warn, Modifier.size(12.dp))
            Text(if (!connected) "Not running" else "Active", style = T.h3)
            if (connected) WTag(if (root) "root" else "shell", if (root) Tone.Ok else Tone.Warn)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                !connected -> "Start the broker to grant apps privileged access — no computer needed."
                root -> "${events} ${if (events==1) "event" else "events"} · full access, incl. rooted-list spoofing."
                else -> "${events} ${if (events==1) "event" else "events"} · grant apps on the Apps tab."
            },
            style = T.bodySmall.copy(color = N.textMuted),
        )
    }
}

@Composable
private fun StartPanel(start: StartUi) {
    val ctx = LocalContext.current
    var advanced by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().vCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            start.working -> Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = N.accent, strokeWidth = 2.dp)
                Text(start.msg ?: "Working…", style = T.body.copy(color = N.textMuted))
            }
            start.needCode -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("First time only — pair this phone", style = T.subtitle)
                Step("1", "Open settings → Wireless debugging → \"Pair device with pairing code\". Keep that screen open.")
                Step("2", "Swipe down and type the 6-digit code into the Warden notification (not here — the code changes if you leave that screen).")
                WButton("Open settings", Tone.Accent) {
                    runCatching { ctx.startActivity(Intent("android.settings.ADB_WIRELESS_SETTINGS")) }
                        .onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                }
            }
            else -> {
                Text("Start the broker on this phone.", style = T.bodySmall.copy(color = N.textMuted))
                BigButton("Start server") { start.onStart() }
            }
        }
        start.msg?.takeIf { !start.working }?.let { Text(it, style = T.bodySmall.copy(color = N.accent2)) }

        Text(if (advanced) "Hide advanced" else "Advanced",
            style = T.label.copy(color = N.textMuted),
            modifier = Modifier.clickable { advanced = !advanced })
        AnimatedVisibility(advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From a PC, or as root:", style = T.bodySmall.copy(color = N.textMuted))
                Text("adb shell sh /sdcard/Android/data/app.warden/files/start.sh",
                    style = T.monoSmall, modifier = Modifier.fillMaxWidth().vInset().padding(10.dp))
                Text("su -c sh /data/local/tmp/warden/start.sh",
                    style = T.monoSmall, modifier = Modifier.fillMaxWidth().vInset().padding(10.dp))
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(n, style = T.control.copy(color = N.accent))
        Text(text, style = T.bodySmall.copy(color = N.textMuted))
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Text(label, style = T.control.copy(color = N.accent),
        modifier = Modifier.fillMaxWidth().clip(N.shapeMd)
            .background(N.accent.copy(alpha = 0.10f)).border(1.dp, N.accent, N.shapeMd)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        textAlign = TextAlign.Center)
}
