package com.hossa.distorsion

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val Bone = Color(0xFFECE6DD)
val Muted = Color(0xFF8E867C)
val Crimson = Color(0xFFE1252F)
val Graphite = Color(0xFF17171A)
val Ink = Color(0xFF070708)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium)
)
val InterDisplayThin = FontFamily(
    Font(R.font.inter_display_thin, FontWeight.Thin)
)

@Composable
fun DistorsionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Crimson,
            background = Ink,
            surface = Graphite,
            onSurface = Bone,
            surfaceContainer = Graphite
        ),
        content = content
    )
}
