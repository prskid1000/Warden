package app.warden.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Nocturne, borrowed from Vessel (app.vessel.ui.theme.VesselTheme) — deep-indigo
 * dark palette, deliberately not black, not Material dynamic colour. Values
 * transcribed from Vessel's VColors so the two apps read as one family.
 */
object N {
    val bg = Color(0xFF161826)
    val surface = Color(0xFF232532)
    val text = Color(0xFFE9E9ED)
    val accent = Color(0xFF9184D9)
    val accent2 = Color(0xFFA7A1DB)
    val textMuted = text.copy(alpha = 0.55f)
    val divider = text.copy(alpha = 0.16f)
    val allow = Color(0xFF7FB69A)   // audit "allow"
    val deny = Color(0xFFD98A8A)    // audit "deny"
}

private val WardenColors = darkColorScheme(
    background = N.bg,
    surface = N.surface,
    primary = N.accent,
    secondary = N.accent2,
    onBackground = N.text,
    onSurface = N.text,
    onPrimary = N.bg,
)

@Composable
fun WardenTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = WardenColors, content = content)
