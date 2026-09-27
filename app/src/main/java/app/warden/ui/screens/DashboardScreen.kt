package app.warden.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import app.warden.api.RootedEntry
import app.warden.data.WardenClient
import app.warden.ui.StartUi
import app.warden.ui.components.*
import app.warden.ui.theme.*

private data class AppRow(val pkg: String, val label: String)

/**
 * The whole app on one page. Offline shows the start guidance; running shows two
 * collapsible sections — Apps (grant access) and Activity (audit) — so there is
 * no bottom nav to reason about. Same elements on every device; root-only bits
 * are present but disabled without root.
 */
@Composable
fun DashboardScreen(connected: Boolean, root: Boolean, start: StartUi) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var granted by remember { mutableStateOf(grantedPkgs()) }
    var rooted by remember { mutableStateOf(rootedPkgs()) }
    var appsOpen by remember { mutableStateOf(true) }
    var actOpen by remember { mutableStateOf(false) }
    val lines = if (connected) rememberAuditLines() else emptyList()
    val apps = remember {
        val pm = ctx.packageManager
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { AppRow(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }
    fun refresh() { granted = grantedPkgs(); rooted = rootedPkgs() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!connected) {
            item { OfflinePanel(start) }
            return@LazyColumn
        }

        item { StatRow(root, granted.size, lines.size) }

        // ── Apps ──────────────────────────────────────────────
        item {
            SectionHeader("Apps", "${granted.size} with access", appsOpen, onToggle = { appsOpen = !appsOpen })
        }
        if (appsOpen) {
            item {
                Box(Modifier.fillMaxWidth().vInset().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (query.text.isEmpty()) Text("Search apps", style = T.body.copy(color = N.textMuted))
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = T.body,
                        cursorBrush = SolidColor(N.accent))
                }
            }
            items(apps.filter {
                query.text.isBlank() || it.label.contains(query.text, true) || it.pkg.contains(query.text, true)
            }, key = { it.pkg }) { app ->
                AppCard(app, app.pkg in granted, app.pkg in rooted, root, ::refresh)
            }
        }

        // ── Activity ──────────────────────────────────────────
        item {
            SectionHeader("Activity", "${lines.size} ${if (lines.size == 1) "event" else "events"}",
                actOpen, onToggle = { actOpen = !actOpen },
                trailing = if (lines.isNotEmpty()) ({
                    Text("Clear", style = T.label.copy(color = N.danger),
                        modifier = Modifier.clickable { WardenClient.clearAudit() })
                }) else null)
        }
        if (actOpen) {
            if (lines.isEmpty()) item {
                Text("No privileged calls yet — they appear here as apps use the broker.",
                    style = T.bodySmall.copy(color = N.textMuted), modifier = Modifier.padding(vertical = 6.dp))
            }
            items(lines) { AuditRow(it) }
        }
    }
}

@Composable
private fun StatRow(root: Boolean, access: Int, events: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Mode", if (root) "root" else "shell", if (root) N.ok else N.warn, Modifier.weight(1f))
        StatTile("Access", "$access", N.accent, Modifier.weight(1f))
        StatTile("Events", "$events", N.accent2, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.vCard().padding(vertical = 12.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = T.h4.copy(color = color))
        Text(label.uppercase(), style = T.overline)
    }
}

@Composable
private fun Monogram(label: String) {
    Box(Modifier.size(38.dp).clip(N.shapeMd).background(N.accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center) {
        Text(label.firstOrNull()?.uppercase() ?: "?", style = T.cardTitle.copy(color = N.accent))
    }
}

@Composable
private fun SectionHeader(title: String, meta: String, expanded: Boolean,
                          onToggle: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Chevron(expanded)
        Spacer(Modifier.width(8.dp))
        Text(title, style = T.subtitle)
        Spacer(Modifier.width(8.dp))
        Text(meta, style = T.label.copy(color = N.textMuted))
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun Chevron(expanded: Boolean) {
    Text(if (expanded) "▾" else "▸", style = T.subtitle.copy(color = N.accent))
}

@Composable
private fun AppCard(app: AppRow, isGranted: Boolean, isRooted: Boolean, root: Boolean, onChanged: () -> Unit) {
    Column(Modifier.fillMaxWidth().vCard().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Monogram(app.label)
            Column(Modifier.weight(1f)) {
                Text(app.label, style = T.cardTitle, maxLines = 1)
                Text(app.pkg, style = T.monoSmall, maxLines = 1)
            }
            NSwitch(isGranted) { on ->
                if (on) WardenClient.setGrant(app.pkg, arrayOf("*"))
                else { WardenClient.revokeGrant(app.pkg); if (isRooted) WardenClient.setRooted(RootedEntry(app.pkg)) }
                onChanged()
            }
        }
        AnimatedVisibility(isGranted) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Appear rooted to this app", style = T.body.copy(color = if (root) N.text else N.textMuted))
                    Text(if (root) "Spoof root detection + give su" else "Requires a rooted device", style = T.monoSmall)
                }
                NSwitch(isRooted && root, enabled = root) { on ->
                    WardenClient.setRooted(RootedEntry(app.pkg, giveBroker = true, giveSu = true,
                        spoofDetection = on, propsProfile = if (on) "rooted" else ""))
                    onChanged()
                }
            }
        }
    }
}

@Composable
private fun NSwitch(checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    Switch(checked = checked, enabled = enabled, onCheckedChange = onToggle,
        colors = SwitchDefaults.colors(
            checkedTrackColor = N.accent, checkedThumbColor = N.bg,
            uncheckedTrackColor = N.surfaceHi, uncheckedBorderColor = N.divider,
            disabledUncheckedTrackColor = N.surfaceHi, disabledUncheckedBorderColor = N.divider))
}

@Composable
private fun OfflinePanel(start: StartUi) {
    val ctx = LocalContext.current
    var advanced by remember { mutableStateOf(false) }
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
                    runCatching { ctx.startActivity(Intent("android.settings.ADB_WIRELESS_SETTINGS")) }
                        .onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                }
            }
            else -> Text("Tap Start (top-right) to run the broker on this phone — no computer needed.",
                style = T.body.copy(color = N.textMuted))
        }
        start.msg?.takeIf { !start.working && !start.needCode }?.let {
            Text(it, style = T.bodySmall.copy(color = N.accent2))
        }
        Text(if (advanced) "Hide advanced" else "Advanced", style = T.label.copy(color = N.textMuted),
            modifier = Modifier.clickable { advanced = !advanced })
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

private fun grantedPkgs(): Set<String> {
    val arr = WardenClient.grants()
    return buildSet { for (i in 0 until arr.length()) add(arr.getJSONObject(i).getString("pkg")) }
}

private fun rootedPkgs(): Set<String> = buildSet {
    WardenClient.rootedList().forEach { if (it.spoofDetection) add(it.pkg) }
}
