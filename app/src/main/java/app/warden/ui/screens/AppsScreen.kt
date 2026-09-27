package app.warden.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.warden.api.RootedEntry
import app.warden.data.WardenClient
import app.warden.ui.theme.N

private data class AppRow(val pkg: String, val label: String)

/**
 * Installed-app list with per-app grant management. Deny-by-default: nothing is
 * granted until toggled here. "Broker" grants API access; "Su" adds the exec
 * scope; "Rooted" adds the app to the rooted list (see the Rooted tab for the
 * layer-C spoof toggle).
 */
@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var granted by remember { mutableStateOf(grantedPkgs()) }
    val apps = remember {
        val pm = ctx.packageManager
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { AppRow(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    val connected by androidx.compose.runtime.produceState(false) {
        while (true) { value = WardenClient.connected; kotlinx.coroutines.delay(1000) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (!connected) {
            NotConnected(); return
        }
        OutlinedTextField(query, { query = it }, singleLine = true,
            label = { Text("search apps") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps.filter {
                query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
            }, key = { it.pkg }) { app ->
                AppCard(app, app.pkg in granted) {
                    granted = grantedPkgs()
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: AppRow, isGranted: Boolean, onChanged: () -> Unit) {
    Surface(color = N.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text(app.label, color = N.text, fontSize = 15.sp)
            Text(app.pkg, color = N.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (isGranted) "Revoke" else "Grant broker") {
                    if (isGranted) WardenClient.revokeGrant(app.pkg)
                    else WardenClient.setGrant(app.pkg, arrayOf("*"))
                    onChanged()
                }
                Chip("+ Su") {
                    WardenClient.setGrant(app.pkg, arrayOf("*", "exec")); onChanged()
                }
                Chip("Add to rooted") {
                    WardenClient.setRooted(RootedEntry(app.pkg, giveBroker = true, giveSu = true))
                    onChanged()
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

@Composable
private fun NotConnected() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Broker not connected — see the Start tab.", color = N.textMuted, fontSize = 13.sp)
    }
}

private fun grantedPkgs(): Set<String> {
    val arr = WardenClient.grants()
    return buildSet { for (i in 0 until arr.length()) add(arr.getJSONObject(i).getString("pkg")) }
}
