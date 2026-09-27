package app.warden.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.warden.ui.theme.N
import app.warden.ui.theme.T

enum class Tone { Accent, Neutral, Ok, Danger, Warn, Outline }

/** Nocturne `.tag` — 11px, 3×10 padding, radius 6, ramp ground + ramp ink. */
@Composable
fun WTag(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val (ground, ink, ringed) = when (tone) {
        Tone.Accent -> Triple(N.accent800, N.accent100, false)
        Tone.Neutral -> Triple(N.neutral800, N.neutral100, false)
        Tone.Ok -> Triple(N.ok.copy(alpha = N.statusGround), N.ok, false)
        Tone.Danger -> Triple(N.danger.copy(alpha = N.statusGround), N.danger, false)
        Tone.Warn -> Triple(N.warn.copy(alpha = N.statusGround), N.warn, false)
        Tone.Outline -> Triple(Color.Transparent, N.accent, true)
    }
    Text(
        text, style = T.label.copy(color = ink, fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)),
        modifier = modifier
            .clip(N.shapeTag)
            .then(if (ringed) Modifier.border(1.dp, N.accent, N.shapeTag) else Modifier.background(ground))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) =
    Box(modifier.size(8.dp).clip(CircleShape).background(color))

/** Nocturne `.btn-primary` (outlined accent) / `.btn-secondary` (divider). */
@Composable
fun WButton(
    label: String,
    tone: Tone = Tone.Neutral,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (ink, ring, bg) = when (tone) {
        Tone.Accent -> Triple(N.accent, N.accent, N.accent.copy(alpha = 0.10f))
        Tone.Danger -> Triple(N.danger, N.danger.copy(alpha = 0.6f), Color.Transparent)
        else -> Triple(N.textLabel, N.divider, Color.Transparent)
    }
    Text(
        label, style = T.control.copy(color = ink),
        modifier = modifier
            .clip(N.shapeMd)
            .background(bg)
            .border(1.dp, ring, N.shapeMd)
            .clickable(onClick = onClick)
            .padding(horizontal = N.s4, vertical = N.s3),
    )
}

/** Freestanding rule that fades to transparent at both ends — a Nocturne mark. */
@Composable
fun WRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                val fade = 48f
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        (fade / size.width) to N.divider,
                        (1f - fade / size.width) to N.divider,
                        1f to Color.Transparent,
                    ),
                    topLeft = Offset.Zero,
                )
            }
    )
}
