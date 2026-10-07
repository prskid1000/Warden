package app.warden.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.warden.data.WardenClient
import app.warden.ui.StartUi
import app.warden.ui.components.*
import app.warden.ui.theme.*

private data class AppRow(val pkg: String, val label: String)
private enum class Page { APPS, ACTIVITY }

/**
 * The whole app on one page. Offline shows the start guidance; once running, an
 * in-page segmented toggle switches between Apps (grant access) and Activity
 * (the live audit log). No bottom nav.
 */
@Composable
fun DashboardScreen(connected: Boolean, start: StartUi) {
    val ctx = LocalContext.current
    var page by remember { mutableStateOf(Page.APPS) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    // Keyed on connected: grants can only be read once the broker is attached.
    var granted by remember(connected) { mutableStateOf(grantedPkgs()) }
    val lines = if (connected) rememberAuditLines() else emptyList()
    val apps = remember {
        val pm = ctx.packageManager
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { AppRow(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }
    fun refresh() { granted = grantedPkgs() }

    if (!connected) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BatteryNotice()
            OfflinePanel(start)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { BatteryNotice() }
        SegTabs(page, granted.size, lines.size) { page = it }
        when (page) {
            Page.APPS -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth().vInset().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        if (query.text.isEmpty()) Text("Search apps", style = T.body.copy(color = N.textMuted))
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = T.body,
                            cursorBrush = SolidColor(N.accent))
                    }
                }
                items(apps.filter {
                    query.text.isBlank() || it.label.contains(query.text, true) || it.pkg.contains(query.text, true)
                }.sortedByDescending { it.pkg in granted },   // granted apps float to the top
                    key = { it.pkg }) { app ->
                    AppCard(app, app.pkg in granted, ::refresh)
                }
            }
            Page.ACTIVITY -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (lines.isNotEmpty()) item {
                    Text("Clear log", style = T.label.copy(color = N.danger),
                        modifier = Modifier.clickable { WardenClient.clearAudit() }.padding(vertical = 4.dp))
                }
                if (lines.isEmpty()) item {
                    Text("No privileged calls yet — they appear here as apps use the broker.",
                        style = T.bodySmall.copy(color = N.textMuted), modifier = Modifier.padding(vertical = 8.dp))
                }
                items(lines) { AuditRow(it) }
            }
        }
    }
}

@Composable
private fun SegTabs(page: Page, access: Int, events: Int, onSelect: (Page) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp).clip(N.shapeMd).border(1.dp, N.divider, N.shapeMd)) {
        Seg("Apps", "$access", page == Page.APPS, Modifier.weight(1f)) { onSelect(Page.APPS) }
        Box(Modifier.width(1.dp).height(48.dp).background(N.divider))
        Seg("Activity", "$events", page == Page.ACTIVITY, Modifier.weight(1f)) { onSelect(Page.ACTIVITY) }
    }
}

@Composable
private fun Seg(label: String, count: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clickable(onClick = onClick)
        .then(if (selected) Modifier.background(N.accent.copy(alpha = 0.10f)) else Modifier)
        .padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.control.copy(color = if (selected) N.accent else N.textLabel))
        Spacer(Modifier.width(6.dp))
        Text(count, style = T.label.copy(color = if (selected) N.accent else N.textMuted))
    }
}

@Composable
private fun AppCard(app: AppRow, isGranted: Boolean, onChanged: () -> Unit) {
    Row(Modifier.fillMaxWidth().vCard().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Monogram(app.label)
        Column(Modifier.weight(1f)) {
            Text(app.label, style = T.cardTitle, maxLines = 1)
            Text(app.pkg, style = T.monoSmall, maxLines = 1)
        }
        NSwitch(isGranted) { on ->
            if (on) WardenClient.setGrant(app.pkg, arrayOf("*")) else WardenClient.revokeGrant(app.pkg)
            onChanged()
        }
    }
}

@Composable
private fun BatteryNotice() {
    val ctx = LocalContext.current
    val ok by produceState(batteryOk(ctx)) {
        while (true) { value = batteryOk(ctx); kotlinx.coroutines.delay(2000) }
    }
    if (ok) return
    Row(Modifier.fillMaxWidth().vCard().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Allow unrestricted battery", style = T.cardTitle)
            Text("Keeps Warden reachable in the background (Doze / battery saver).",
                style = T.bodySmall.copy(color = N.textMuted))
        }
        WButton("Allow", Tone.Accent) {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + ctx.packageName)))
            }.onFailure {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}

private fun batteryOk(ctx: android.content.Context): Boolean =
    runCatching {
        ctx.getSystemService(android.os.PowerManager::class.java)
            .isIgnoringBatteryOptimizations(ctx.packageName)
    }.getOrDefault(true)

@Composable
private fun Monogram(label: String) {
    Box(Modifier.size(38.dp).clip(N.shapeMd).background(N.accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center) {
        Text(label.firstOrNull()?.uppercase() ?: "?", style = T.cardTitle.copy(color = N.accent))
    }
}

@Composable
private fun NSwitch(checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    Switch(checked = checked, enabled = enabled, onCheckedChange = onToggle,
        colors = SwitchDefaults.colors(
            checkedTrackColor = N.accent, checkedThumbColor = N.bg,
            uncheckedTrackColor = N.surfaceHi, uncheckedBorderColor = N.divider))
}

@Composable
private fun OfflinePanel(start: StartUi) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().vCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            start.working -> Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = N.accent, strokeWidth = 2.dp)
                Text(start.msg ?: "Working…", style = T.body.copy(color = N.textMuted))
            }
            start.needCode -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("First time only — pair this phone", style = T.subtitle)
                Step("1", "Open settings → Wireless debugging → \"Pair device with pairing code\". Keep it open.")
                Step("2", "Swipe down and type the 6-digit code into the Warden notification (the code changes if you leave that screen).")
                WButton("Open settings", Tone.Accent) {
                    // NEW_TASK keeps Settings out of Warden's back stack. The fallback
                    // scrolls Developer options to (and highlights) the wireless row.
                    val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    runCatching {
                        ctx.startActivity(Intent("android.settings.ADB_WIRELESS_SETTINGS").addFlags(flags))
                    }.onFailure {
                        val args = android.os.Bundle().apply { putString(":settings:fragment_args_key", "toggle_adb_wireless") }
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(flags)
                            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                            .putExtra(":settings:show_fragment_args", args))
                    }
                }
            }
            else -> Text("Tap Start (top-right) to run the broker on this phone — no computer needed.",
                style = T.body.copy(color = N.textMuted))
        }
        start.msg?.takeIf { !start.working && !start.needCode }?.let {
            Text(it, style = T.bodySmall.copy(color = N.accent2))
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

private fun grantedPkgs(): Set<String> {
    val arr = WardenClient.grants()
    return buildSet { for (i in 0 until arr.length()) add(arr.getJSONObject(i).getString("pkg")) }
}
