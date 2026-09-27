package app.warden.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import app.warden.data.WardenClient
import app.warden.ui.components.StatusDot
import app.warden.ui.components.WRule
import app.warden.ui.screens.*
import app.warden.ui.theme.N
import app.warden.ui.theme.T
import app.warden.ui.theme.vCard
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { app.warden.ui.theme.WardenTheme { WardenApp() } }
    }
    override fun onResume() { super.onResume(); WardenClient.connect() }
}

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    BOOTSTRAP("Start", app.warden.ui.components.WIcons.Start),
    APPS("Apps", app.warden.ui.components.WIcons.Apps),
    ROOTED("Rooted", app.warden.ui.components.WIcons.Rooted),
    AUDIT("Audit", app.warden.ui.components.WIcons.Audit),
}

@Composable
private fun WardenApp() {
    var tab by remember { mutableStateOf(Tab.BOOTSTRAP) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ConnState.ensurePolling(scope) }
    val connected = ConnState.connected
    val root = ConnState.root
    // Section C (the rooted list / spoofing) only exists on a rooted device, so
    // its tab is hidden unless the broker is running as root.
    val tabs = Tab.entries.filter { it != Tab.ROOTED || root }
    LaunchedEffect(root) { if (tab == Tab.ROOTED && !root) tab = Tab.BOOTSTRAP }

    Column(
        Modifier.fillMaxSize().background(N.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Toolbar(connected, root)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.BOOTSTRAP -> BootstrapScreen()
                Tab.APPS -> AppsScreen()
                Tab.ROOTED -> RootedListScreen()
                Tab.AUDIT -> AuditScreen()
            }
        }
        BottomBar(tabs, tab) { tab = it }
    }
}

@Composable
private fun Toolbar(connected: Boolean, root: Boolean) {
    val (dot, word) = when {
        !connected -> N.textMuted to "offline"
        root -> N.ok to "root"
        else -> N.warn to "shell"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Warden", style = T.h3)
            Text("privilege broker · audit", style = T.mono)
        }
        Row(
            Modifier.vCard(N.shapeTag).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusDot(dot)
            Text(word, style = T.control.copy(color = dot))
        }
    }
}

@Composable
private fun BottomBar(tabs: List<Tab>, current: Tab, onSelect: (Tab) -> Unit) {
    // On-Device-AI style: a top fading rule, icon over label, accent when
    // selected and dimmed otherwise — no boxes, no fills.
    Column {
        WRule()
        Row(Modifier.fillMaxWidth().background(N.bg).padding(top = 8.dp, bottom = 6.dp)) {
            tabs.forEach { t ->
                val sel = t == current
                Column(
                    Modifier.weight(1f).clickable { onSelect(t) }
                        .padding(vertical = 5.dp)
                        .alpha(if (sel) 1f else 0.5f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    androidx.compose.material3.Icon(
                        t.icon, contentDescription = t.label,
                        tint = if (sel) N.accent else N.text,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(t.label, style = T.label.copy(color = if (sel) N.accent else N.text))
                }
            }
        }
    }
}
