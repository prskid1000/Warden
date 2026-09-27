package app.warden.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.warden.adb.AdbStarter
import app.warden.data.WardenClient
import app.warden.ui.components.*
import app.warden.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        StatusCard(connected, root)
        if (!connected) ServerControl()
        ManualCard()
    }
}

@Composable
private fun StatusCard(connected: Boolean, root: Boolean) {
    Column(Modifier.fillMaxWidth().vCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker("Broker status")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(if (!connected) N.textMuted else if (root) N.ok else N.warn)
            Text(when {
                !connected -> "Not running"
                root -> "Running as root — uid 0"
                else -> "Running as shell — uid 2000"
            }, style = T.cardTitle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WTag("A · broker", if (connected) Tone.Ok else Tone.Outline)
            WTag("B · su", if (connected) Tone.Ok else Tone.Outline)
            WTag("C · spoof", if (connected && root) Tone.Ok else Tone.Outline)
        }
    }
}

/** One-tap, PC-free start via Wireless Debugging. Pair once, then Start. */
@Composable
private fun ServerControl() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pairPort by remember { mutableStateOf(TextFieldValue("")) }
    var pairCode by remember { mutableStateOf(TextFieldValue("")) }
    var connPort by remember { mutableStateOf(TextFieldValue("")) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().vCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker("Start server · no PC")
        Text("Enable Wireless debugging, pair once, then Start.", style = T.bodySmall.copy(color = N.textMuted))
        WButton("Open Wireless debugging", Tone.Neutral) {
            runCatching { ctx.startActivity(Intent("android.settings.ADB_WIRELESS_SETTINGS")) }
                .onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        }

        WRule()
        Overline("1 · Pair (first time only)")
        Text("From \"Pair device with pairing code\" — enter its port and 6-digit code.",
            style = T.bodySmall.copy(color = N.textMuted))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(pairPort, { pairPort = it }, "pair port", Modifier.weight(1f), number = true)
            Field(pairCode, { pairCode = it }, "code", Modifier.weight(1f), number = true)
        }
        WButton("Pair", Tone.Accent) {
            busy = true; msg = "Pairing…"
            scope.launch {
                val r = AdbStarter.pair(ctx, pairPort.text.trim().toIntOrNull() ?: 0, pairCode.text.trim())
                msg = r.fold({ "Paired ✓ — now enter the connect port and Start." }, { it.message })
                busy = false
            }
        }

        WRule()
        Overline("2 · Start")
        Text("The port shown on the Wireless debugging screen (IP address & Port).",
            style = T.bodySmall.copy(color = N.textMuted))
        Field(connPort, { connPort = it }, "connect port", Modifier.fillMaxWidth(), number = true)
        WButton("Start server", Tone.Accent) {
            busy = true; msg = "Connecting…"
            scope.launch {
                val r = AdbStarter.start(ctx, connPort.text.trim().toIntOrNull() ?: 0)
                msg = r.fold({ "$it  Connecting to broker…" }, { it.message })
                busy = false
            }
        }

        msg?.let {
            Text(it, style = T.bodySmall.copy(color = if (busy) N.textMuted else N.accent2),
                modifier = Modifier.fillMaxWidth().vInset().padding(10.dp))
        }
    }
}

@Composable
private fun Field(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, hint: String,
                  modifier: Modifier = Modifier, number: Boolean = false) {
    Box(modifier.vInset().padding(horizontal = 12.dp, vertical = 11.dp)) {
        if (value.text.isEmpty()) Text(hint, style = T.body.copy(color = N.textMuted))
        BasicTextField(value, onChange, singleLine = true, textStyle = T.body,
            cursorBrush = SolidColor(N.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (number) KeyboardType.Number else KeyboardType.Text))
    }
}

@Composable
private fun ManualCard() {
    Column(Modifier.fillMaxWidth().vCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker("Manual / root")
        Text("From a PC, or as root:", style = T.bodySmall.copy(color = N.textMuted))
        Text("adb shell sh /sdcard/Android/data/app.warden/files/start.sh",
            style = T.mono.copy(color = N.accent2), modifier = Modifier.fillMaxWidth().vInset().padding(12.dp))
        Text("su -c sh /data/local/tmp/warden/start.sh",
            style = T.mono.copy(color = N.accent2), modifier = Modifier.fillMaxWidth().vInset().padding(12.dp))
    }
}
