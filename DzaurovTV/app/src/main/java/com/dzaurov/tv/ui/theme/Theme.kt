package com.dzaurov.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.dzaurov.tv.R
import kotlin.math.max

val Geist = FontFamily(
    Font(R.font.geist_thin, FontWeight.Thin),
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

object Dz {
    val Room = Color(0xFF07090F)
    val Ink = Color.White
    val Ink2 = Color(0xC7FFFFFF)
    val Ink3 = Color(0x99FFFFFF)
    val Dark = Color(0xFF0B0E14)
    val Panel = Color(0xF5141922)
    val Blue = Color(0xFF7FB2FF)
}

fun dzStyle(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Dz.Ink,
    spacing: TextUnit = TextUnit.Unspecified,
) = TextStyle(fontFamily = Geist, fontSize = size, fontWeight = weight, color = color, letterSpacing = spacing)

/**
 * The whole UI is laid out on a 960 × 540 grid (the standard TV "dp" size of a 1080p screen)
 * and scaled to the real screen, so it looks identical whatever density the box reports.
 */
@Composable
fun DzaurovTheme(content: @Composable () -> Unit) {
    val metrics = LocalContext.current.resources.displayMetrics
    val scale = max(metrics.widthPixels, metrics.heightPixels) / 960f
    CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            content()
        }
    }
}
