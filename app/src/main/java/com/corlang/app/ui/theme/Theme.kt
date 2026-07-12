package com.corlang.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * Corlang palette, "Adriatic" on ink: deep sea blue (primary), terracotta roof-tile
 * (secondary), warm sand (tertiary). Dark-only by design; every Material role is
 * specified so no default (purple) role can leak into the UI.
 */

private val DarkColors = darkColorScheme(
    primary = Color(0xFF92CBEC),
    onPrimary = Color(0xFF06344E),
    primaryContainer = Color(0xFF114A6E),
    onPrimaryContainer = Color(0xFFCDE5F5),
    secondary = Color(0xFFEFB3A3),
    onSecondary = Color(0xFF4A170C),
    secondaryContainer = Color(0xFF5E2B1E),
    onSecondaryContainer = Color(0xFFFADCD3),
    tertiary = Color(0xFFE2C86B),
    onTertiary = Color(0xFF382F00),
    tertiaryContainer = Color(0xFF524700),
    onTertiaryContainer = Color(0xFFF7E7B4),
    background = Color(0xFF111417),
    onBackground = Color(0xFFE1E3E6),
    surface = Color(0xFF171B1F),
    onSurface = Color(0xFFE1E3E6),
    surfaceVariant = Color(0xFF2C363E),
    onSurfaceVariant = Color(0xFFBCC8D1),
    outline = Color(0xFF86929C),
    outlineVariant = Color(0xFF42505A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * Semantic right/wrong feedback colors (quiz grading, match highlights).
 * Kept out of the Material scheme because "correct green" has no M3 role;
 * both variants are tuned for readable text in their theme.
 */
@Immutable
data class FeedbackColors(
    val correct: Color,             // strong accent: borders, "Correct" label
    val correctContainer: Color,    // surface behind correct feedback
    val onCorrectContainer: Color,
    val wrong: Color,               // strong accent: borders, "Not quite" label
    val wrongContainer: Color,      // surface behind wrong feedback
    val onWrongContainer: Color,
)

private val DarkFeedback = FeedbackColors(
    correct = Color(0xFF8FD694),
    correctContainer = Color(0xFF1F3A25),
    onCorrectContainer = Color(0xFFC0E5C1),
    wrong = Color(0xFFF3A29E),
    wrongContainer = Color(0xFF4A2022),
    onWrongContainer = Color(0xFFFFD1CE),
)

val LocalFeedbackColors = staticCompositionLocalOf { DarkFeedback }

/** Accessor mirroring MaterialTheme.colorScheme style. */
object CorlangColors {
    val feedback: FeedbackColors
        @Composable get() = LocalFeedbackColors.current
}

/** One deliberate type scale: slightly tighter headlines, roomier body text. */
private fun corlangTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.25).sp
        ),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
        labelLarge = base.labelLarge.copy(letterSpacing = 0.3.sp),
    )
}

/**
 * Corlang is dark-only by design (easier on the eyes; the brand lives on ink-navy).
 * The system light/dark setting is deliberately ignored, one look everywhere: launch
 * window, loader, onboarding, and the app all share the same dark surfaces.
 */
@Composable
fun CorlangTheme(content: @Composable () -> Unit) {
    val colors = DarkColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.surface.toArgb()
            // Dark bar → light (white) status-bar icons.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    CompositionLocalProvider(LocalFeedbackColors provides DarkFeedback) {
        MaterialTheme(
            colorScheme = colors,
            typography = corlangTypography(),
            content = content
        )
    }
}
