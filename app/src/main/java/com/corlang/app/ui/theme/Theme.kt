package com.corlang.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * Corlang palette, "Adriatic" on ink: a calm, muted sea-blue (primary) that reads as trust and
 * quiet rather than candy-bright; terracotta roof-tile (secondary) as the warm counterpoint;
 * warm sand (tertiary). Dark-only by design; every Material role is specified so no default
 * (purple) role can leak into the UI. The blue is deliberately desaturated — peace, not pop.
 */

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CBAD2),
    onPrimary = Color(0xFF06293D),
    primaryContainer = Color(0xFF123F5A),
    onPrimaryContainer = Color(0xFFC9E1F0),
    secondary = Color(0xFFE7AE9D),
    onSecondary = Color(0xFF48160B),
    secondaryContainer = Color(0xFF5A281B),
    onSecondaryContainer = Color(0xFFF9DAD0),
    tertiary = Color(0xFFDBC271),
    onTertiary = Color(0xFF362D00),
    tertiaryContainer = Color(0xFF4E4300),
    onTertiaryContainer = Color(0xFFF5E4AF),
    background = Color(0xFF0F1418),
    onBackground = Color(0xFFE0E3E6),
    surface = Color(0xFF161B20),
    onSurface = Color(0xFFE0E3E6),
    surfaceVariant = Color(0xFF29323B),
    onSurfaceVariant = Color(0xFFB8C4CE),
    outline = Color(0xFF7E8A95),
    outlineVariant = Color(0xFF3A4650),
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

/**
 * Display face: Fraunces (OFL), a warm editorial serif that gives the brand a voice beyond
 * Roboto. Variable font — weights come from the wght axis. Used bold for headlines and titles.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Fraunces = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(
        com.corlang.app.R.font.fraunces,
        weight = FontWeight.SemiBold,
        variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
            androidx.compose.ui.text.font.FontVariation.weight(600)
        )
    ),
    androidx.compose.ui.text.font.Font(
        com.corlang.app.R.font.fraunces,
        weight = FontWeight.Bold,
        variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
            androidx.compose.ui.text.font.FontVariation.weight(700)
        )
    )
)

/**
 * Reading face: the same Fraunces at book weights (regular + medium). Set lesson and reading
 * content in this — a serif for prose is the single biggest signal of "book, not toy," while
 * UI/chrome stays on the system sans. Exposed through [CorlangType.reading].
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val FrauncesReading = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(
        com.corlang.app.R.font.fraunces,
        weight = FontWeight.Normal,
        variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
            androidx.compose.ui.text.font.FontVariation.weight(430)
        )
    ),
    androidx.compose.ui.text.font.Font(
        com.corlang.app.R.font.fraunces,
        weight = FontWeight.Medium,
        variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
            androidx.compose.ui.text.font.FontVariation.weight(520)
        )
    )
)

/** Reading/prose style, mirroring MaterialTheme.typography access. Serif, roomy line-height. */
object CorlangType {
    val reading: androidx.compose.ui.text.TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FrauncesReading,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            lineHeight = 29.sp,
            letterSpacing = 0.sp
        )

    /** Larger reading style for the focal sentence/phrase of a lesson card. */
    val readingLarge: androidx.compose.ui.text.TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FrauncesReading,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        )
}

/** One deliberate type scale: Fraunces for display/headlines/titles, system sans for UI body. */
private fun corlangTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = base.displaySmall.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        ),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.Bold
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.25).sp
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold
        ),
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
            // Same radius scale as Radius.sm/md/lg so Cards and Material surfaces inherit it.
            shapes = Shapes(
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp)
            ),
            content = content
        )
    }
}
