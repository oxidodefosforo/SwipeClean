package com.swipeclean.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0D0E11)
val Surface1 = Color(0xFF16181D)
val Surface2 = Color(0xFF1F2228)
val TextDim = Color(0xFF8D919B)
val KeepGreen = Color(0xFF32D74B)
val DeleteRed = Color(0xFFFF453A)
val SkipBlue = Color(0xFF64A8FF)

private val scheme = darkColorScheme(
    primary = Color(0xFFF2F2F4),
    onPrimary = Color(0xFF0D0E11),
    secondary = KeepGreen,
    error = DeleteRed,
    background = Bg,
    onBackground = Color(0xFFF2F2F4),
    surface = Surface1,
    onSurface = Color(0xFFF2F2F4),
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    outline = Color(0xFF34373F),
)

@Composable
fun SwipeCleanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
