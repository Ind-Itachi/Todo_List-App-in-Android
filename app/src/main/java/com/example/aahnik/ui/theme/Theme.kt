package com.example.aahnik.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AahnikColors {
    val NightTop = Color(0xFF0B1026)
    val NightBottom = Color(0xFF1B0F33)

    /** Saffron accent – used for "done" state and single-series charts. */
    val Saffron = Color(0xFFFFB347)
    val SaffronDeep = Color(0xFFFF7E3B)
    val Gold = Color(0xFFFFD86B)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xD9FFFFFF)
    val TextMuted = Color(0x99FFFFFF)

    /**
     * Categorical series colours for charts, in fixed order (validated for colour-vision
     * deficiency and contrast on the dark glass surface). Assigned by an aahnik's position
     * so a colour always follows the same aahnik.
     */
    val Series = listOf(
        Color(0xFF3987E5), // blue
        Color(0xFFD95926), // orange
        Color(0xFF199E70), // aqua
        Color(0xFFC98500), // yellow
        Color(0xFFD55181), // magenta
        Color(0xFF0CA30C), // green
        Color(0xFF9085E9), // violet
        Color(0xFFE66767), // red
    )

    fun series(position: Int): Color = Series[((position % Series.size) + Series.size) % Series.size]
}

private val scheme = darkColorScheme(
    primary = AahnikColors.Saffron,
    onPrimary = Color(0xFF2B1600),
    secondary = AahnikColors.Gold,
    background = AahnikColors.NightTop,
    surface = Color(0xFF1C1A3A),
    onSurface = AahnikColors.TextPrimary,
    onBackground = AahnikColors.TextPrimary,
    surfaceVariant = Color(0x33FFFFFF),
    onSurfaceVariant = AahnikColors.TextSecondary,
)

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

@Composable
fun AahnikTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
