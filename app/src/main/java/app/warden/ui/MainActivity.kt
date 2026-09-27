package app.warden.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.warden.ui.screens.*
import app.warden.ui.theme.N
import app.warden.ui.theme.WardenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WardenTheme { WardenApp() } }
    }

    override fun onResume() {
        super.onResume()
        app.warden.data.WardenClient.connect()   // pick up a root-started server
    }
}

private enum class Tab(val label: String) {
    BOOTSTRAP("Start"), APPS("Apps"), ROOTED("Rooted list"), AUDIT("Audit")
}

@Composable
private fun WardenApp() {
    var tab by remember { mutableStateOf(Tab.BOOTSTRAP) }
    Scaffold(
        containerColor = N.bg,
        topBar = {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Warden", color = N.text, fontSize = 22.sp)
                Text(
                    "privilege broker · per-app audit · rooted list",
                    color = N.textMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = N.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {},
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.BOOTSTRAP -> BootstrapScreen()
                Tab.APPS -> AppsScreen()
                Tab.ROOTED -> RootedListScreen()
                Tab.AUDIT -> AuditScreen()
            }
        }
    }
}
