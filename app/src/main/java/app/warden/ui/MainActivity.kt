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

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", app.warden.ui.components.WIcons.Start),
    APPS("Apps", app.warden.ui.components.WIcons.Apps),
    ROOTED("Rooted", app.warden.ui.components.WIcons.Rooted),
}

@Composable
private fun WardenApp() {
    var tab by remember { mutableStateOf(Tab.HOME) }
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
    val startUi = StartUi(working, needCode, msg, ::doStart)

    val tabs = Tab.entries.filter { it != Tab.ROOTED || root }
    LaunchedEffect(root) { if (tab == Tab.ROOTED && !root) tab = Tab.HOME }

    Column(Modifier.fillMaxSize().background(N.bg).windowInsetsPadding(WindowInsets.systemBars)) {
        Toolbar(connected, root, working) { doStart() }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.HOME -> HomeScreen(connected, root, startUi)
                Tab.APPS -> AppsScreen()
                Tab.ROOTED -> RootedListScreen()
            }
        }
        BottomBar(tabs, tab) { tab = it }
    }
}

@Composable
private fun Toolbar(connected: Boolean, root: Boolean, working: Boolean, onStart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Warden", style = T.h3)
            Text("privilege broker · audit", style = T.mono)
        }
        when {
            connected -> {
                val c = if (root) N.ok else N.warn
                Row(Modifier.vCard(N.shapeTag).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(c); Text(if (root) "root" else "active", style = T.control.copy(color = c))
                }
            }
            else -> {
                // Top-right = a Start action while offline.
                Text(if (working) "starting…" else "Start",
                    style = T.control.copy(color = N.accent),
                    modifier = Modifier.clip(N.shapeTag)
                        .background(N.accent.copy(alpha = 0.10f)).border(1.dp, N.accent, N.shapeTag)
                        .clickable(enabled = !working) { onStart() }
                        .padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun BottomBar(tabs: List<Tab>, current: Tab, onSelect: (Tab) -> Unit) {
    Column {
        WRule()
        Row(Modifier.fillMaxWidth().background(N.bg).padding(top = 8.dp, bottom = 6.dp)) {
            tabs.forEach { t ->
                val sel = t == current
                Column(
                    Modifier.weight(1f).clickable { onSelect(t) }
                        .padding(vertical = 5.dp).alpha(if (sel) 1f else 0.5f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    androidx.compose.material3.Icon(
                        t.icon, contentDescription = t.label,
                        tint = if (sel) N.accent else N.text, modifier = Modifier.size(22.dp))
                    Text(t.label, style = T.label.copy(color = if (sel) N.accent else N.text))
                }
            }
        }
    }
}
