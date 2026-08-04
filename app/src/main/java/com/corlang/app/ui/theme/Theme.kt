package com.corlang.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * Corlang palette, "Adriatic": a calm, muted sea-blue (primary) that reads as trust and quiet
 * rather than candy-bright; terracotta roof-tile (secondary) as the warm counterpoint; warm
 * sand (tertiary). Every Material role is specified in BOTH schemes so no default (purple) role
 * can leak into the UI, and so no screen can accidentally depend on one theme's fallbacks.
 *
 * Two looks, one brand:
 *  - dark  = Adriatic on ink. The sea at night; the original and still the default.
 *  - light = Umber on paper. Warm beige surfaces, brown-umber ink, and a walnut primary, with
 *            terracotta and ochre as the accents. Entirely warm: no blue anywhere in the UI, only
 *            in the brand mark, which is fixed. It is a warm-neutral theme, NOT an inverted grey
 *            one, and not the dark theme with its blue dropped onto beige.
 *
 * The theme is the learner's explicit choice (asked once on first run, changeable in Settings),
 * never the system setting — see [CorlangTheme].
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
    // Same reasoning as the light scheme below: Material's defaults for these are neutral grey
    // (and purple, in inversePrimary), which is not the ink-navy this theme is built on.
    surfaceDim = Color(0xFF0F1418),
    surfaceBright = Color(0xFF343A40),
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = Color(0xFF171C21),
    surfaceContainer = Color(0xFF1B2127),
    surfaceContainerHigh = Color(0xFF262C33),
    surfaceContainerHighest = Color(0xFF31373E),
    inverseSurface = Color(0xFFE0E3E6),
    inverseOnSurface = Color(0xFF2C3238),
    inversePrimary = Color(0xFF2A6183),
    scrim = Color(0xFF000000),
)

/*
 * Light: "Umber on paper". Fully warm, with NO blue in it — the Adriatic primary was carried over
 * from the dark theme at first and read as a cold spot dropped onto beige: the one element that
 * did not belong to the page it sat on. The primary is now walnut brown, so buttons, selection
 * and focus all come from the same earth family as the surfaces, with terracotta and ochre as the
 * warm accents beside it. The blue survives only in the brand mark, which is fixed in both themes
 * and reads as a mark rather than as UI.
 *
 * Backgrounds are beige rather than white, outlines brown rather than grey, body text dark umber.
 * Every accent is darkened well past its dark-theme counterpart: white-on-primary clears 7:1.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4F32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADCC6),
    onPrimaryContainer = Color(0xFF2A1D0C),
    secondary = Color(0xFF9A4A31),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7DCD0),
    onSecondaryContainer = Color(0xFF4A1B0C),
    tertiary = Color(0xFF785A12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1E2B4),
    onTertiaryContainer = Color(0xFF382A00),
    background = Color(0xFFF6F0E6),
    onBackground = Color(0xFF2B2118),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF2B2118),
    surfaceVariant = Color(0xFFEADFCC),
    onSurfaceVariant = Color(0xFF574A38),
    outline = Color(0xFF8A7357),
    outlineVariant = Color(0xFFD5C7AE),
    error = Color(0xFFA02F26),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF8DDD8),
    onErrorContainer = Color(0xFF410E0B),
    // The surface ladder Material uses for cards, dialogs, menus and sheets. Specified because
    // its defaults are COOL neutral greys: left alone, an AlertDialog or a Card would sit on the
    // beige page as a grey rectangle. Each step here is the same paper, dimmed or lifted.
    surfaceDim = Color(0xFFE3DACB),
    surfaceBright = Color(0xFFFFFBF3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF5EA),
    surfaceContainer = Color(0xFFF4EDE0),
    surfaceContainerHigh = Color(0xFFEFE6D7),
    surfaceContainerHighest = Color(0xFFE9E0CE),
    // The inverse trio (snackbars, and anything drawn on the opposite ground) and the scrim
    // behind dialogs. Also cool-grey by default, and also purple-tinted in inversePrimary.
    inverseSurface = Color(0xFF382F25),
    inverseOnSurface = Color(0xFFF6F0E6),
    inversePrimary = Color(0xFFD9BE9A),
    scrim = Color(0xFF000000),
)

/** The scheme behind a theme choice. Exposed so the first-run picker can paint both at once. */
fun corlangColorScheme(dark: Boolean): ColorScheme = if (dark) DarkColors else LightColors

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

/**
 * Light-theme feedback: the same two meanings, re-tuned for paper. The dark theme's mint and
 * salmon are near-invisible on beige, so both accents darken and both containers become warm
 * tints rather than deep blocks.
 */
private val LightFeedback = FeedbackColors(
    correct = Color(0xFF2E6B36),
    correctContainer = Color(0xFFDDEBD9),
    onCorrectContainer = Color(0xFF17351C),
    wrong = Color(0xFF9E332A),
    wrongContainer = Color(0xFFF7DDD8),
    onWrongContainer = Color(0xFF43110D),
)

val LocalFeedbackColors = staticCompositionLocalOf { DarkFeedback }

/**
 * Whether the dark theme is active. A few things are drawn outside the Material roles — the
 * streak flame's tiers, confetti — and need to know which ground they sit on. Reading this beats
 * inspecting a background's luminance: it is the actual choice, not a guess about it.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** Accessor mirroring MaterialTheme.colorScheme style. */
object CorlangColors {
    val feedback: FeedbackColors
        @Composable get() = LocalFeedbackColors.current

    val isDark: Boolean
        @Composable get() = LocalIsDarkTheme.current
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
 * Corlang has two looks and the learner picks one: ink (dark) or paper (light). The SYSTEM
 * light/dark setting is still deliberately ignored — the choice is the app's own, asked once on
 * first run and changeable in Settings, so one look runs everywhere: launch window, loader,
 * onboarding, and the app all share it.
 *
 * [dark] defaults to true so any preview or test that renders a screen without threading the
 * preference through still gets the original theme.
 *
 * The scheme SNAPS: no role is animated here. Easing the colors meant recomposing every
 * composable on screen once per frame, which is what made switching feel like a repaint in
 * progress. The visible transition belongs to [CorlangThemeSwap], which crossfades a frozen
 * frame over the finished new one instead — read that file before adding animation back here.
 *
 * [systemBarsDark] exists for the same reason: during a swap the bar ICONS must keep suiting the
 * frame the learner is still looking at, not the theme already rendered beneath it.
 */
@Composable
fun CorlangTheme(
    dark: Boolean = true,
    systemBarsDark: Boolean = dark,
    content: @Composable () -> Unit
) {
    val colors = corlangColorScheme(dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.surface.toArgb()
            // Dark bar → light (white) icons; paper bar → dark icons. Both bars, because
            // edge-to-edge draws content under the gesture/navigation bar as well.
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !systemBarsDark
            controller.isAppearanceLightNavigationBars = !systemBarsDark
        }
    }
    CompositionLocalProvider(
        LocalFeedbackColors provides if (dark) DarkFeedback else LightFeedback,
        LocalIsDarkTheme provides dark
    ) {
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
