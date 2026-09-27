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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import app.warden.data.WardenClient
import app.warden.ui.components.StatusDot
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

private enum class Tab(val label: String) {
    BOOTSTRAP("Start"), APPS("Apps"), ROOTED("Rooted"), AUDIT("Audit")
}

@Composable
private fun WardenApp() {
    var tab by remember { mutableStateOf(Tab.BOOTSTRAP) }
    val connected by produceState(false) {
        while (true) { WardenClient.connect(); value = WardenClient.connected; delay(1000) }
    }
    val root by produceState(false) {
        while (true) { value = WardenClient.rootAvailable; delay(1000) }
    }

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
        BottomBar(tab) { tab = it }
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
            Text("privilege broker · audit · rooted list", style = T.mono)
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
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    // Nocturne `.seg`: a divider-ringed track; the selected option carries an
    // inset accent ring + accent text rather than a filled ground.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(N.shapeMd).border(1.dp, N.divider, N.shapeMd),
    ) {
        Tab.entries.forEachIndexed { i, t ->
            val sel = t == current
            if (i > 0) Box(Modifier.width(1.dp).height(44.dp).background(N.divider))
            Box(
                Modifier.weight(1f)
                    .clickable { onSelect(t) }
                    .then(if (sel) Modifier.border(1.dp, N.accent, N.shapeMd) else Modifier)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, style = T.control.copy(color = if (sel) N.accent else N.textLabel),
                    textAlign = TextAlign.Center)
            }
        }
    }
}
