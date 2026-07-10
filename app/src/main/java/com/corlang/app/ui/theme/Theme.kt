package com.corlang.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
 * Corlang palette — "Adriatic": deep sea blue (primary), terracotta roof-tile
 * (secondary), warm sand (tertiary). Every Material role is specified for BOTH
 * light and dark so no default (purple) role can leak into the UI.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF135E8C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE5F5),
    onPrimaryContainer = Color(0xFF07344E),
    secondary = Color(0xFF9E4A38),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADCD3),
    onSecondaryContainer = Color(0xFF3E120A),
    tertiary = Color(0xFF6D5C00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7E7B4),
    onTertiaryContainer = Color(0xFF3A3000),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE3EAF0),
    onSurfaceVariant = Color(0xFF42505A),
    outline = Color(0xFF71808B),
    outlineVariant = Color(0xFFC2CDD6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

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

private val LightFeedback = FeedbackColors(
    correct = Color(0xFF2E7D32),
    correctContainer = Color(0xFFD7EDD6),
    onCorrectContainer = Color(0xFF10461A),
    wrong = Color(0xFFC62828),
    wrongContainer = Color(0xFFFFDAD6),
    onWrongContainer = Color(0xFF5F1412),
)

private val DarkFeedback = FeedbackColors(
    correct = Color(0xFF8FD694),
    correctContainer = Color(0xFF1F3A25),
    onCorrectContainer = Color(0xFFC0E5C1),
    wrong = Color(0xFFF3A29E),
    wrongContainer = Color(0xFF4A2022),
    onWrongContainer = Color(0xFFFFD1CE),
)

val LocalFeedbackColors = staticCompositionLocalOf { LightFeedback }

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

@Composable
fun CorlangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val feedback = if (darkTheme) DarkFeedback else LightFeedback
    // Status bar matches the top app bar: primary blue in light, surface in dark.
    val statusBar = if (darkTheme) colors.surface else colors.primary
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = statusBar.toArgb()
            // Both bar colors are dark → always light (white) status-bar icons.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    CompositionLocalProvider(LocalFeedbackColors provides feedback) {
        MaterialTheme(
            colorScheme = colors,
            typography = corlangTypography(),
            content = content
        )
    }
}
