@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.warden.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.warden.R

/**
 * Nocturne, as Warden expresses it — transcribed from Vessel's VesselTheme so
 * the two apps read as one family. Deep-indigo dark, lavender accent, Inter +
 * JetBrains Mono variable fonts, tight headings, hairline rings on surfaces.
 */
object N {
    // core roles
    val bg = Color(0xFF161826)
    val surface = Color(0xFF232532)
    val surfaceHi = Color(0xFF2A2C3A)
    val text = Color(0xFFE9E9ED)
    val accent = Color(0xFF9184D9)
    val accent2 = Color(0xFFA7A1DB)
    // ramps (subset used by tags/chips)
    val neutral800 = Color(0xFF3F424D)
    val neutral100 = Color(0xFFF3F5FE)
    val accent800 = Color(0xFF423A6A)
    val accent100 = Color(0xFFF5F4FF)
    // status
    val ok = Color(0xFF7FB69A)
    val danger = Color(0xFFD98A8A)
    val warn = Color(0xFFD9C48A)
    // derived
    val textMuted = text.copy(alpha = 0.55f)
    val textLabel = text.copy(alpha = 0.70f)
    val divider = text.copy(alpha = 0.14f)
    val ring = text.copy(alpha = 0.10f)
    val statusGround = 0.16f

    // shapes
    val shapeSm: Shape = RoundedCornerShape(6.dp)
    val shapeMd: Shape = RoundedCornerShape(10.dp)
    val shapeLg: Shape = RoundedCornerShape(14.dp)
    val shapeTag: Shape = RoundedCornerShape(6.dp)
}

private val Heading = FontWeight.W600

private val Sans = FontFamily(
    Font(R.font.inter_variable, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, Heading, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)
private val Mono = FontFamily(
    Font(R.font.jetbrains_mono_variable, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jetbrains_mono_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

/** Warden's type scale (mirrors Vessel's VType). */
object T {
    val display = TextStyle(fontFamily = Sans, fontWeight = Heading, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.015).em, color = N.text)
    val title = TextStyle(fontFamily = Sans, fontWeight = Heading, fontSize = 19.sp, lineHeight = 23.sp, letterSpacing = (-0.015).em, color = N.text)
    val subtitle = TextStyle(fontFamily = Sans, fontWeight = Heading, fontSize = 15.sp, lineHeight = 19.sp, color = N.text)
    val cardTitle = TextStyle(fontFamily = Sans, fontWeight = Heading, fontSize = 14.sp, lineHeight = 18.sp, color = N.text)
    val body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp, color = N.text)
    val bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, color = N.text)
    val label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, color = N.textLabel)
    val overline = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.09.em, color = N.textMuted)
    val mono = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 16.sp, color = N.textLabel)
    val monoSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp, color = N.textMuted)
    val control = TextStyle(fontFamily = Sans, fontWeight = Heading, fontSize = 12.5.sp, lineHeight = 16.sp, color = N.text)
}

/** `vCard()` — a surface with a hairline ring, the product's primary container. */
fun Modifier.vCard(shape: Shape = N.shapeLg): Modifier =
    this.clip(shape).background(N.surface).border(1.dp, N.ring, shape)

fun Modifier.vInset(shape: Shape = N.shapeMd): Modifier =
    this.clip(shape).background(N.bg).border(1.dp, N.ring, shape)

/** A small uppercase section header. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = T.overline, modifier = modifier)

private val WardenColors = darkColorScheme(
    background = N.bg, surface = N.surface, primary = N.accent, secondary = N.accent2,
    onBackground = N.text, onSurface = N.text, onPrimary = N.bg,
)

@Composable
fun WardenTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = WardenColors, content = content)
