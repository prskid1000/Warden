package app.warden.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.warden.adb.AdbStarter
import app.warden.adb.PairNotification
import app.warden.data.WardenClient
import app.warden.ui.components.*
import app.warden.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BootstrapScreen() {
    val connected = app.warden.ui.ConnState.connected
    val root = app.warden.ui.ConnState.root

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusCard(connected, root)
        if (!connected) StartCard()
    }
}

@Composable
private fun StatusCard(connected: Boolean, root: Boolean) {
    Column(Modifier.fillMaxWidth().vCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker("Status")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(if (!connected) N.textMuted else if (root) N.ok else N.warn)
            Text(when {
                !connected -> "Not running"
                root -> "Active · full access"
                else -> "Active"
            }, style = T.cardTitle)
        }
        if (connected) Text(
            if (root) "All features available." else "Broker running. Grant apps on the Apps tab.",
            style = T.bodySmall.copy(color = N.textMuted))
    }
}

private enum class Phase { Idle, Working, NeedCode }

@Composable
private fun StartCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.Idle) }
    var msg by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }

    val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    fun handle(outcome: Result<AdbStarter.Outcome>) {
        outcome.fold(
            onSuccess = {
                when (it) {
                    is AdbStarter.Outcome.Launched -> { phase = Phase.Idle; msg = "Started. Connecting…" }
                    AdbStarter.Outcome.PairNeeded -> {
                        phase = Phase.NeedCode; msg = null
                        if (Build.VERSION.SDK_INT >= 33)
                            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        PairNotification.prompt(ctx)   // the code goes in here, not the app
                    }
                }
            },
            onFailure = { phase = if (phase == Phase.NeedCode) Phase.NeedCode else Phase.Idle; msg = it.message },
        )
    }

    Column(Modifier.fillMaxWidth().vCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker("Start")
        Text("Start the broker on this phone — no computer needed.",
            style = T.bodySmall.copy(color = N.textMuted))

        when (phase) {
            Phase.Idle -> {
                BigButton("Start server") {
                    phase = Phase.Working; msg = "Looking for the device…"
                    scope.launch { handle(AdbStarter.start(ctx)) }
                }
            }
            Phase.Working -> Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = N.accent, strokeWidth = 2.dp)
                Text(msg ?: "Working…", style = T.body.copy(color = N.textMuted))
            }
            Phase.NeedCode -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("First time only — pair this phone:", style = T.subtitle)
                Step("1", "Open settings → turn on Wireless debugging → tap \"Pair device with pairing code\". Keep that screen open.")
                Step("2", "Swipe down the notification shade (don't leave the pairing screen) and type the 6-digit code into the Warden notification.")
                WButton("Open settings", Tone.Accent) {
                    runCatching { ctx.startActivity(Intent("android.settings.ADB_WIRELESS_SETTINGS")) }
                        .onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                }
                Text("The code changes if you leave that screen — that's why you enter it from the notification, not here.",
                    style = T.bodySmall.copy(color = N.textMuted))
            }
        }

        msg?.let { Text(it, style = T.bodySmall.copy(color = N.accent2)) }

        Text(if (advanced) "Hide advanced" else "Advanced",
            style = T.label.copy(color = N.textMuted),
            modifier = Modifier.clickableText { advanced = !advanced })
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
        modifier = Modifier.fillMaxWidth()
            .clip(N.shapeMd)
            .then(Modifier.vInsetAccent())
            .clickableText(onClick)
            .padding(vertical = 14.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}


private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

private fun Modifier.vInsetAccent(): Modifier =
    this.background(N.accent.copy(alpha = 0.10f)).border(1.dp, N.accent, N.shapeMd)
