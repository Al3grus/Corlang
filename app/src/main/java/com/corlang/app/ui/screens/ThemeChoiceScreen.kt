package com.corlang.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.components.CorlangBrand
import com.corlang.app.ui.components.CorlangCore
import com.corlang.app.ui.theme.corlangColorScheme

/*
 * First run, before anything else is asked: ink or paper.
 *
 * It is a real question, not a settings row hidden in a menu — Corlang has exactly one look at a
 * time (it never follows the system setting), so the app cannot guess this the way it can guess
 * a sensible review limit. Asking once, up front, is cheaper for the learner than discovering
 * later that the app they have been squinting at had another mode all along.
 *
 * Two things make it answerable rather than abstract:
 *  - each option shows a MINIATURE of the app painted in that theme's own colors, so the choice
 *    is between two pictures rather than two words;
 *  - tapping one re-themes THIS SCREEN immediately (the flag is hoisted to MainActivity, and
 *    CorlangTheme eases every Material role), so the learner is standing inside the theme they
 *    are choosing before they confirm it.
 *
 * Nothing is persisted until Confirm: tapping around is free, and the app opens onboarding only
 * once the learner has committed.
 */

@Composable
fun ThemeChoiceScreen(
    /** The theme currently on screen — the live preview, not the saved value (nothing is saved yet). */
    dark: Boolean,
    /** Re-themes the app in place. Called on every tap, including re-taps of the current choice. */
    onPreview: (Boolean) -> Unit,
    /** Commit: persist [dark] and hand over to onboarding. */
    onConfirm: () -> Unit
) {
    val context = LocalContext.current

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Choose your look",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Tap one to try it: the app changes as you do. You can switch any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )

            Spacer(Modifier.height(36.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                ThemeOption(
                    label = "Dark",
                    caption = "Adriatic on ink",
                    optionIsDark = true,
                    selected = dark,
                    onClick = { onPreview(true); Haptics.confirm(context) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(16.dp))
                ThemeOption(
                    label = "Light",
                    caption = "Umber on paper",
                    optionIsDark = false,
                    selected = !dark,
                    onClick = { onPreview(false); Haptics.confirm(context) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(36.dp))

            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Confirm ✓")
            }
        }
    }
}

/**
 * One choice: a miniature of the app in that theme, its name, and a selected state. The preview
 * is painted from [corlangColorScheme] directly rather than from MaterialTheme, because the whole
 * point is to show the theme you are NOT currently in.
 */
@Composable
private fun ThemeOption(
    label: String,
    caption: String,
    optionIsDark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = corlangColorScheme(optionIsDark)
    // The frame reads as selected from the current theme's palette (it belongs to the live UI),
    // while the mini inside stays in its own colors (it is a picture of the other world).
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.background,
            border = border,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clickable(onClick = onClick)
        ) {
            MiniApp(
                background = scheme.background,
                surface = scheme.surface,
                onSurface = scheme.onSurface,
                onSurfaceVariant = scheme.onSurfaceVariant,
                outlineVariant = scheme.outlineVariant,
                primary = scheme.primary,
                onPrimary = scheme.onPrimary,
                secondary = scheme.secondary,
                tertiary = scheme.tertiary
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (selected) "$label ✓" else label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A wordless miniature of a Corlang screen: brand mark and title, a lesson card with two lines of
 * text, an accent row, and a primary button. Bars rather than real text — at this size type would
 * be unreadable noise, while the block rhythm is what the eye actually compares between themes.
 */
@Composable
private fun MiniApp(
    background: Color,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    outlineVariant: Color,
    primary: Color,
    onPrimary: Color,
    secondary: Color,
    tertiary: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(10.dp)
    ) {
        // Header: the brand mark (fixed colors in both themes — it is the logo) and a title bar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(CorlangBrand),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(CorlangCore))
            }
            Spacer(Modifier.width(6.dp))
            Bar(width = 0.55f, height = 7.dp, color = onSurface)
        }

        Spacer(Modifier.height(10.dp))

        // Lesson card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(surface)
                .border(1.dp, outlineVariant, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Bar(width = 0.7f, height = 6.dp, color = onSurface)
            Spacer(Modifier.height(6.dp))
            Bar(width = 1f, height = 4.dp, color = onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Bar(width = 0.8f, height = 4.dp, color = onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))

        // Accent row: the warm counterpoints, the part that most distinguishes the two themes.
        Row {
            Chip(secondary)
            Spacer(Modifier.width(5.dp))
            Chip(tertiary)
            Spacer(Modifier.width(5.dp))
            Chip(primary)
        }

        Spacer(Modifier.weight(1f))

        // Primary button, with its label bar in the on-primary color so contrast is on show.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(primary),
            contentAlignment = Alignment.Center
        ) {
            Bar(width = 0.4f, height = 4.dp, color = onPrimary, alpha = 1f)
        }
    }
}

/** A stand-in for a line of text: a rounded bar at [width] of the available space. */
@Composable
private fun Bar(
    width: Float,
    height: androidx.compose.ui.unit.Dp,
    color: Color,
    alpha: Float = 0.75f
) {
    Box(
        Modifier
            .fillMaxWidth(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color.copy(alpha = alpha))
    )
}

/** A small accent pill in the mini preview. */
@Composable
private fun Chip(color: Color) {
    Box(
        Modifier
            .width(20.dp)
            .height(9.dp)
            .clip(RoundedCornerShape(4.5.dp))
            .background(color)
    )
}
