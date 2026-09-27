package app.warden.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small hand-built line icons so the app needs no icon dependency. */
object WIcons {
    private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    val Start: ImageVector = icon("Start") {
        path(fill = SolidColor(Color.White)) {
            moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
        }
    }
    val Apps: ImageVector = icon("Apps") {
        path(fill = SolidColor(Color.White)) {
            moveTo(4f, 4f); horizontalLineToRelative(6f); verticalLineToRelative(6f); horizontalLineToRelative(-6f); close()
            moveTo(14f, 4f); horizontalLineToRelative(6f); verticalLineToRelative(6f); horizontalLineToRelative(-6f); close()
            moveTo(4f, 14f); horizontalLineToRelative(6f); verticalLineToRelative(6f); horizontalLineToRelative(-6f); close()
            moveTo(14f, 14f); horizontalLineToRelative(6f); verticalLineToRelative(6f); horizontalLineToRelative(-6f); close()
        }
    }
    val Rooted: ImageVector = icon("Rooted") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 3f); lineTo(20f, 6f); verticalLineToRelative(6f)
            curveTo(20f, 17f, 16.4f, 20f, 12f, 21f)
            curveTo(7.6f, 20f, 4f, 17f, 4f, 12f); verticalLineToRelative(-6f); close()
        }
    }
    val Audit: ImageVector = icon("Audit") {
        path(fill = SolidColor(Color.White)) {
            moveTo(4f, 5f); horizontalLineToRelative(16f); verticalLineToRelative(2.4f); horizontalLineToRelative(-16f); close()
            moveTo(4f, 10.8f); horizontalLineToRelative(16f); verticalLineToRelative(2.4f); horizontalLineToRelative(-16f); close()
            moveTo(4f, 16.6f); horizontalLineToRelative(11f); verticalLineToRelative(2.4f); horizontalLineToRelative(-11f); close()
        }
    }
}
