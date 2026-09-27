package app.warden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.warden.api.RootedEntry
import app.warden.data.WardenClient
import app.warden.ui.components.Tone
import app.warden.ui.components.WButton
import app.warden.ui.components.WTag
import app.warden.ui.theme.*
import kotlinx.coroutines.delay

private data class AppRow(val pkg: String, val label: String)

@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var granted by remember { mutableStateOf(grantedPkgs()) }
    val connected by produceState(false) { while (true) { value = WardenClient.connected; delay(1000) } }
    val apps = remember {
        val pm = ctx.packageManager
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { AppRow(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!connected) { NotConnected(); return }
        Overline("Grant access · ${granted.size} granted")
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().vInset().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (query.text.isEmpty()) Text("Search apps", style = T.body.copy(color = N.textMuted))
            BasicTextField(query, { query = it }, singleLine = true,
                textStyle = T.body, cursorBrush = androidx.compose.ui.graphics.SolidColor(N.accent))
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apps.filter {
                query.text.isBlank() || it.label.contains(query.text, true) || it.pkg.contains(query.text, true)
            }, key = { it.pkg }) { app ->
                AppCard(app, app.pkg in granted) { granted = grantedPkgs() }
            }
        }
    }
}

@Composable
private fun AppCard(app: AppRow, isGranted: Boolean, onChanged: () -> Unit) {
    Column(Modifier.fillMaxWidth().vCard().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.label, style = T.cardTitle)
                Text(app.pkg, style = T.monoSmall)
            }
            if (isGranted) WTag("granted", Tone.Ok)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isGranted) {
                WButton("Revoke", Tone.Danger) { WardenClient.revokeGrant(app.pkg); onChanged() }
            } else {
                WButton("Grant broker", Tone.Accent) { WardenClient.setGrant(app.pkg, arrayOf("*")); onChanged() }
            }
            WButton("+ Su") { WardenClient.setGrant(app.pkg, arrayOf("*", "exec")); onChanged() }
            WButton("+ Rooted") {
                WardenClient.setRooted(RootedEntry(app.pkg, giveBroker = true, giveSu = true)); onChanged()
            }
        }
    }
}

@Composable
private fun NotConnected() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Broker not connected", style = T.subtitle.copy(color = N.textMuted))
        Spacer(Modifier.height(4.dp))
        Text("See the Start tab", style = T.bodySmall.copy(color = N.textMuted))
    }
}

private fun grantedPkgs(): Set<String> {
    val arr = WardenClient.grants()
    return buildSet { for (i in 0 until arr.length()) add(arr.getJSONObject(i).getString("pkg")) }
}
