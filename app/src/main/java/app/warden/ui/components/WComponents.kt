package app.warden.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.warden.ui.theme.N
import app.warden.ui.theme.T

enum class Tone { Accent, Neutral, Ok, Danger, Warn, Outline }

@Composable
fun WTag(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val (ground, ink) = when (tone) {
        Tone.Accent -> N.accent800 to N.accent100
        Tone.Neutral -> N.neutral800 to N.neutral100
        Tone.Ok -> N.ok.copy(alpha = N.statusGround) to N.ok
        Tone.Danger -> N.danger.copy(alpha = N.statusGround) to N.danger
        Tone.Warn -> N.warn.copy(alpha = N.statusGround) to N.warn
        Tone.Outline -> Color.Transparent to N.textMuted
    }
    Text(
        text, style = T.overline.copy(color = ink),
        modifier = modifier
            .clip(N.shapeTag)
            .then(if (tone == Tone.Outline) Modifier.border(1.dp, N.ring, N.shapeTag) else Modifier.background(ground))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) =
    Box(modifier.size(8.dp).clip(CircleShape).background(color))

/** A compact outlined chip button in the Nocturne idiom. */
@Composable
fun WChip(label: String, tone: Tone = Tone.Neutral, onClick: () -> Unit) {
    val ink = when (tone) {
        Tone.Accent -> N.accent
        Tone.Danger -> N.danger
        else -> N.textLabel
    }
    Text(
        label, style = T.control.copy(color = ink),
        modifier = Modifier
            .clip(N.shapeSm)
            .border(1.dp, if (tone == Tone.Accent) N.accent.copy(alpha = 0.5f) else N.ring, N.shapeSm)
            .background(if (tone == Tone.Accent) N.accent.copy(alpha = 0.10f) else N.surfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
