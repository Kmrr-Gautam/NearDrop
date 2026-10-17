package com.neardrop.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Clay {
    val Lavender = Color(0xFFC9B8FF)
    val LavenderDeep = Color(0xFF5B45C9)
    val Mint = Color(0xFFB5EAD7)
    val Sky = Color(0xFFBFE3FF)
    val Cream = Color(0xFFFFF6E9)
    val Peach = Color(0xFFFFD3C0)
    val Charcoal = Color(0xFF2B2B38)
    val Danger = Color(0xFFE55353)

    // Sleek dark-mode specialized palettes
    val DarkSurface = Color(0xFF181624)
    val DarkCard = Color(0xFF211E30)

    val DarkLavender = Color(0xFF2B2544)
    val DarkLavenderAccent = Color(0xFF9D8BFF)
    val DarkLavenderText = Color(0xFFEADBFF)

    val DarkMint = Color(0xFF173027)
    val DarkMintAccent = Color(0xFF48D5A3)
    val DarkMintText = Color(0xFFCBF7E6)

    val DarkSky = Color(0xFF182C3D)
    val DarkSkyAccent = Color(0xFF4DB5F7)
    val DarkSkyText = Color(0xFFCEEBFF)

    val DarkPeach = Color(0xFF381F23)
    val DarkPeachAccent = Color(0xFFF27B84)
    val DarkPeachText = Color(0xFFFFD2D6)
}

private val Light = lightColorScheme(
    primary = Clay.LavenderDeep,
    onPrimary = Color.White,
    primaryContainer = Clay.Lavender,
    onPrimaryContainer = Clay.Charcoal,
    secondary = Color(0xFF1F7A5C),
    secondaryContainer = Clay.Mint,
    onSecondaryContainer = Clay.Charcoal,
    tertiary = Color(0xFF1C6AA5),
    tertiaryContainer = Clay.Sky,
    onTertiaryContainer = Clay.Charcoal,
    background = Clay.Cream,
    onBackground = Clay.Charcoal,
    surface = Color(0xFFFFFBF4),
    onSurface = Clay.Charcoal,
    surfaceVariant = Clay.Peach,
    onSurfaceVariant = Clay.Charcoal,
    error = Clay.Danger,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF13111C),
    primaryContainer = Color(0xFF4C3A9E),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFF5EEAD4),
    secondaryContainer = Color(0xFF134E48),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFF7DD3FC),
    tertiaryContainer = Color(0xFF0C4A6E),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0E0D14), // Ultra sleek deep obsidian-indigo
    onBackground = Color(0xFFF4F0FF),
    surface = Color(0xFF191724), // Smooth velvety dark surface
    onSurface = Color(0xFFF4F0FF),
    surfaceVariant = Color(0xFF242034),
    onSurfaceVariant = Color(0xFFDDD8F3),
    error = Color(0xFFF87171),
)

@Composable
fun NearDropTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
}

/**
 * Premium tactile clay surface:
 * In dark mode: Soft velvety gradient, ultra-fine 12% specular rim highlight (NO harsh white lines), deep soft shadow.
 * In light mode: Friendly pastel gradient with crisp top highlight and subtle soft drop shadow.
 */
fun Modifier.clay(
    color: Color,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 6.dp
): Modifier {
    val lum = color.luminance()
    val isDark = lum < 0.35f

    val shadowColor = if (isDark) {
        Color.Black.copy(alpha = 0.5f)
    } else {
        lerp(color, Color.Black, 0.25f)
    }

    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                lerp(color, Color.White, 0.05f),
                color,
                lerp(color, Color.Black, 0.12f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                lerp(color, Color.White, 0.18f),
                color,
                lerp(color, Color.Black, 0.04f)
            )
        )
    }

    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.13f),
                Color.White.copy(alpha = 0.02f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.75f),
                Color.Transparent
            )
        )
    }

    return this
        .shadow(elevation, shape, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(bgBrush)
        .border(1.dp, borderBrush, shape)
}
