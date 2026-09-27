package app.warden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.warden.data.WardenClient
import app.warden.ui.theme.N

/** Shows how to start the broker: ADB (shell) or root, and its live status. */
@Composable
fun BootstrapScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val status = when {
            !WardenClient.connected -> "not running"
            WardenClient.rootAvailable -> "running as root (uid 0) — all layers"
            else -> "running as shell (uid 2000) — layers A + B"
        }
        Surface(color = N.surface, shape = MaterialTheme.shapes.medium) {
            Text("Status: $status", color = N.text, fontSize = 14.sp,
                modifier = Modifier.padding(12.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Start via ADB (no root):", color = N.text, fontSize = 13.sp)
        Code("adb shell sh /sdcard/Android/data/app.warden/files/start.sh")
        Spacer(Modifier.height(10.dp))
        Text("Start via root:", color = N.text, fontSize = 13.sp)
        Code("su -c sh /data/local/tmp/warden/start.sh")
    }
}

@Composable
private fun Code(text: String) {
    Surface(color = N.bg, shape = MaterialTheme.shapes.small) {
        Text(text, color = N.accent, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            modifier = Modifier.padding(10.dp))
    }
}
