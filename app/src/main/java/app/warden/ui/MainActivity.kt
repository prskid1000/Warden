package app.warden.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import app.warden.adb.AdbStarter
import app.warden.adb.PairNotification
import app.warden.data.WardenClient
import app.warden.ui.components.StatusDot
import app.warden.ui.components.WRule
import app.warden.ui.screens.*
import app.warden.ui.theme.N
import app.warden.ui.theme.T
import app.warden.ui.theme.vCard
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { app.warden.ui.theme.WardenTheme { WardenApp() } }
    }
    override fun onResume() { super.onResume(); WardenClient.connect() }
}


@Composable
private fun WardenApp() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { ConnState.ensurePolling(scope) }
    val connected = ConnState.connected
    val root = ConnState.root

    // Shared start controller (drives the Home panel and the toolbar chip).
    var working by remember { mutableStateOf(false) }
    var needCode by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { }
    fun doStart() {
        if (working) return
        working = true; needCode = false; msg = "Looking for the device…"
        scope.launch {
            AdbStarter.start(ctx).fold(
                onSuccess = {
                    when (it) {
                        is AdbStarter.Outcome.Launched -> { working = false; needCode = false; msg = "Started. Connecting…" }
                        AdbStarter.Outcome.PairNeeded -> {
                            working = false; needCode = true; msg = null
                            if (Build.VERSION.SDK_INT >= 33)
                                notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            PairNotification.prompt(ctx)
                        }
                    }
                },
                onFailure = { working = false; msg = it.message },
            )
        }
    }
    fun doStop() {
        WardenClient.shutdown()
        working = false; needCode = false; msg = null
    }
    val startUi = StartUi(working, needCode, msg, ::doStart, ::doStop)

    Column(Modifier.fillMaxSize().background(N.bg).windowInsetsPadding(WindowInsets.systemBars)) {
        Toolbar(connected, root, working, onStart = ::doStart, onStop = ::doStop)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DashboardScreen(connected, root, startUi)
        }
    }
}

@Composable
private fun Toolbar(connected: Boolean, root: Boolean, working: Boolean,
                   onStart: () -> Unit, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to N.section.copy(alpha = 0.45f), 1f to Color.Transparent))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Warden", style = T.h3)
            Text("privilege broker · audit", style = T.mono)
        }
        // Single control lives here: Stop when running, Start when not. When
        // running, a small "C" badge shows whether layer-C (root spoofing) is live.
        if (connected) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (root) app.warden.ui.components.WTag("root", app.warden.ui.components.Tone.Ok)
                ControlChip("Stop", N.danger, enabled = true, onClick = onStop)
            }
        } else {
            ControlChip(if (working) "starting…" else "Start", N.accent, enabled = !working, onClick = onStart)
        }
    }
}

@Composable
private fun ControlChip(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(label, style = T.control.copy(color = color),
        modifier = Modifier.clip(N.shapeTag)
            .background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.7f), N.shapeTag)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp))
}

