package com.rodin.messages.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.rodin.messages.data.Palette
import com.rodin.messages.data.ThemeMode

private fun hsl(c: Long): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(c.toInt(), it) }
private fun tone(h: FloatArray, s: Float, l: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(h[0], s, l)))

/** Builds a tonal scheme from a seed, matching the Dialer's soft blue-on-charcoal look. */
private fun seeded(seed: Long, dark: Boolean): ColorScheme {
    val h = hsl(seed)
    val s = h[1].coerceIn(0.35f, 0.9f)
    val neutral = floatArrayOf(h[0], 0.08f, 0f)
    val tert = floatArrayOf((h[0] + 60f) % 360f, s, 0f)
    return if (dark) darkColorScheme(
        primary = tone(h, s, 0.84f),
        onPrimary = tone(h, s, 0.18f),
        primaryContainer = tone(h, s * 0.45f, 0.36f),
        onPrimaryContainer = tone(h, s, 0.92f),
        secondary = tone(h, s * 0.3f, 0.78f),
        onSecondary = tone(h, s * 0.3f, 0.18f),
        secondaryContainer = tone(h, s * 0.25f, 0.27f),
        onSecondaryContainer = tone(h, s * 0.3f, 0.9f),
        tertiary = tone(tert, s * 0.5f, 0.8f),
        tertiaryContainer = tone(tert, s * 0.35f, 0.3f),
        onTertiaryContainer = tone(tert, s * 0.5f, 0.92f),
        background = tone(neutral, 0.1f, 0.075f),
        onBackground = tone(neutral, 0.06f, 0.9f),
        surface = tone(neutral, 0.1f, 0.075f),
        onSurface = tone(neutral, 0.06f, 0.9f),
        surfaceVariant = tone(neutral, 0.1f, 0.24f),
        onSurfaceVariant = tone(neutral, 0.1f, 0.78f),
        surfaceContainerLowest = tone(neutral, 0.1f, 0.05f),
        surfaceContainerLow = tone(neutral, 0.1f, 0.095f),
        surfaceContainer = tone(neutral, 0.1f, 0.115f),
        surfaceContainerHigh = tone(neutral, 0.1f, 0.145f),
        surfaceContainerHighest = tone(neutral, 0.1f, 0.18f),
        outline = tone(neutral, 0.08f, 0.55f),
        outlineVariant = tone(neutral, 0.08f, 0.3f),
    ) else lightColorScheme(
        primary = tone(h, s, 0.4f),
        onPrimary = Color.White,
        primaryContainer = tone(h, s, 0.88f),
        onPrimaryContainer = tone(h, s, 0.15f),
        secondary = tone(h, s * 0.3f, 0.4f),
        secondaryContainer = tone(h, s * 0.4f, 0.88f),
        onSecondaryContainer = tone(h, s * 0.4f, 0.15f),
        tertiary = tone(tert, s * 0.5f, 0.4f),
        tertiaryContainer = tone(tert, s * 0.5f, 0.88f),
        onTertiaryContainer = tone(tert, s * 0.5f, 0.15f),
        background = tone(neutral, 0.4f, 0.985f),
        surface = tone(neutral, 0.4f, 0.985f),
        surfaceVariant = tone(neutral, 0.2f, 0.9f),
        onSurfaceVariant = tone(neutral, 0.1f, 0.3f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = tone(neutral, 0.3f, 0.96f),
        surfaceContainer = tone(neutral, 0.3f, 0.94f),
        surfaceContainerHigh = tone(neutral, 0.3f, 0.92f),
        surfaceContainerHighest = tone(neutral, 0.3f, 0.9f),
        outline = tone(neutral, 0.1f, 0.5f),
        outlineVariant = tone(neutral, 0.1f, 0.8f),
    )
}

private fun ColorScheme.amoled() = copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF111215),
    surfaceContainerHigh = Color(0xFF17181C),
    surfaceContainerHighest = Color(0xFF1E1F24),
)

val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Normal),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MessagesTheme(mode: ThemeMode, palette: Palette, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    var scheme = if (palette == Palette.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else seeded(palette.seed, dark)
    if (mode == ThemeMode.AMOLED) scheme = scheme.amoled()

    // Smoothly animate palette / theme switches
    val anim = @Composable { c: Color -> animateColorAsState(c, tween(450), label = "c").value }
    val animated = scheme.copy(
        primary = anim(scheme.primary),
        primaryContainer = anim(scheme.primaryContainer),
        onPrimaryContainer = anim(scheme.onPrimaryContainer),
        secondaryContainer = anim(scheme.secondaryContainer),
        background = anim(scheme.background),
        surface = anim(scheme.surface),
        surfaceContainer = anim(scheme.surfaceContainer),
        surfaceContainerHigh = anim(scheme.surfaceContainerHigh),
        surfaceContainerHighest = anim(scheme.surfaceContainerHighest),
        onSurface = anim(scheme.onSurface),
    )
    // Status/navigation bar icons must follow the app's chosen theme, not the system's dark mode
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = animated, typography = AppTypography, shapes = AppShapes) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides animated.onSurface,
            content = content,
        )
    }
}

/** Deterministic avatar tint, matching the Dialer's two-tone squircle avatars. */
@Composable
fun avatarColors(key: String, highlighted: Boolean): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val variants = listOf(
        cs.primaryContainer to cs.onPrimaryContainer,
        cs.secondaryContainer to cs.onSecondaryContainer,
        cs.tertiaryContainer to cs.onTertiaryContainer,
        cs.surfaceContainerHighest to cs.onSurfaceVariant,
    )
    if (highlighted) return variants[0]
    return variants[(key.hashCode() and 0x7FFFFFFF) % variants.size]
}

