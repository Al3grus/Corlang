package com.corlang.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.billing.BillingManager
import com.corlang.app.billing.PremiumManager
import com.corlang.app.data.Fsrs

/** Walk up the Context wrappers to the hosting Activity (needed by launchBillingFlow). */
private fun Context.activity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * The purchase surface. Two modes:
 *  - [levelId] non-null → the one-time unlock for that CEFR level of [lang], and that course's
 *    bundle when it would not make the learner pay twice (see below).
 *  - [levelId] null      → the AI tutor subscription (monthly, no trial).
 *
 * Unlocks are per language and CUMULATIVE: buying A2 grants A1 as well, so the top level's
 * product is also the whole-course bundle. A course is a ladder and owning a rung without the
 * ones below it is incoherent — the deck introduces words in course order, so an A2 learner's
 * reviews are full of A1 vocabulary either way.
 *
 * The cost is Play's missing upgrade pricing for one-time products: someone who buys A1 and
 * later the whole course pays for A1 twice. That is why every tier this course sells is shown
 * at the FIRST paywall a learner meets, rather than revealed one rung at a time — the choice to
 * climb has to be an informed one, made once.
 *
 * Prices are read LIVE from Play (BillingManager.prices); if a product isn't resolved yet
 * (Play Console products not created, or billing still connecting) its button is disabled with a
 * gentle placeholder rather than showing a wrong or free price.
 */
@Composable
fun PaywallScreen(
    container: AppContainer,
    lang: String,
    levelId: String?,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx.activity()
    val prices by container.billing.prices.collectAsState()
    val meta = remember(lang) { container.content.meta(lang) }
    // The levels this course charges for, in course order, so `.last()` names the finish line.
    val paidLevels = remember(lang) {
        container.content.plan(lang).days.sortedBy { it.day }
            .filter { it.day > meta.freeLessons }.map { it.level }.distinct()
    }
    // What each tier already grants, so an owned rung can say so instead of offering itself again.
    val unlockedLevels by container.premium.unlockedLevels.collectAsState(initial = emptySet())
    /**
     * PAID lessons a tier hands over: everything up to and including that level, minus the free
     * window. Cumulative because the unlocks are — the number under "Through A2" has to be what
     * that one payment opens in total, not what A2 adds on its own.
     */
    val paidLessonsThrough = remember(lang) {
        val days = container.content.plan(lang).days
        val lastDayOf = days.groupBy { it.level }.mapValues { e -> e.value.maxOf { it.day } }
        paidLevels.associateWith { level ->
            ((lastDayOf[level] ?: 0) - meta.freeLessons).coerceAtLeast(0)
        }
    }
    val lastDayThrough = remember(lang) {
        val days = container.content.plan(lang).days
        val lastDayOf = days.groupBy { it.level }.mapValues { e -> e.value.maxOf { it.day } }
        paidLevels.associateWith { lastDayOf[it] ?: 0 }
    }
    /**
     * The rest of what a tier hands over, counted rather than described.
     *
     * "Its words, quizzes and exams" is the kind of phrase a buyer's eye slides off; a number is
     * something they can weigh against a price, and check against the tier above on the same
     * screen. All three are derived from the same content the app serves, so they cannot drift
     * from what is actually delivered.
     */
    val wordsThrough = remember(lang) {
        val deck = container.words.allWords(lang).size
        val free = (meta.freeLessons * Fsrs.NEW_WORDS_PER_DAY).coerceAtMost(deck)
        paidLevels.associateWith { level ->
            val through = ((lastDayThrough[level] ?: 0) * Fsrs.NEW_WORDS_PER_DAY).coerceAtMost(deck)
            (through - free).coerceAtLeast(0)
        }
    }
    val quizzesThrough = remember(lang) {
        val order = container.content.plan(lang).days.sortedBy { it.day }.map { it.level }.distinct()
        val ids = container.content.quizzes(lang).quizzes.map { it.levelId }
        paidLevels.associateWith { level ->
            val cap = order.indexOf(level)
            ids.count { order.indexOf(it) in 0..cap }
        }
    }
    val examsThrough = remember(lang) {
        val order = container.content.plan(lang).days.sortedBy { it.day }.map { it.level }.distinct()
        val ids = container.content.exams(lang).map { it.levelId }
        paidLevels.associateWith { level ->
            val cap = order.indexOf(level)
            ids.count { order.indexOf(it) in 0..cap }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (levelId != null) {
            val top = paidLevels.lastOrNull()
            Text("Unlock ${meta.name}", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            // What the MODEL is, in one line, because that is the question a one-time purchase
            // has to answer before price: am I buying this or renting it? The free window is not
            // mentioned again — the learner has used it to get here.
            Text(
                "One payment. Yours for good, on any device you sign in to.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Every tier at once, cheapest first, rather than one rung at a time. Unlocks are
            // cumulative and Play has no upgrade pricing for one-time products, so a learner who
            // climbs pays for each rung again. The only way that is a fair deal is if the whole
            // ladder was on screen when they chose — hence the shelf, with what each tier
            // includes stated on the card itself.
            paidLevels.forEach { level ->
                val product = BillingManager.levelProduct(lang, level)
                val owned = PremiumManager.key(lang, level) in unlockedLevels
                val isTop = level == top
                val lessons = paidLessonsThrough[level] ?: 0
                PurchaseCard(
                    title = if (isTop) "The whole ${meta.name} course" else "Through $level",
                    subtitle = if (isTop) "Everything the course teaches, to $level."
                               else "$level is not the end of the course.",
                    bullets = tierBullets(level, isTop, lessons, meta.freeLessons,
                        lastDayThrough[level] ?: 0, wordsThrough[level] ?: 0,
                        quizzesThrough[level] ?: 0, examsThrough[level] ?: 0),
                    // Not "most popular": nothing has sold yet, so that would be an invented
                    // claim. This one is arithmetic the learner can check against the other
                    // cards on the same screen.
                    badge = if (isTop && !owned) "Best value" else null,
                    price = prices[product],
                    primary = isTop,
                    owned = owned,
                    onBuy = { activity?.let { container.billing.purchaseLevel(it, product) } }
                )
            }
        } else {
            Text("AI tutor", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(
                "Someone to speak ${meta.name} with, at your level, whenever you want to practise.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Monthly ONLY, deliberately: AI models and costs can shift within a year, and a
            // sold annual locks us into serving 12 months at 2026 economics. Monthly keeps
            // repricing freedom on both sides.
            //
            // NO free trial, also deliberately. Every tutor message costs us real inference, so
            // unlike a normal software trial this one is not free to serve: three days at the
            // 30-a-day cap is up to 90 paid-for messages from an account that may never convert,
            // and repeatable with a new Google account. A three-day window is a bad fit for a
            // daily-habit product anyway — its value shows over weeks, and a forgotten trial
            // converts nobody while still billing someone who then asks for a refund.
            //
            // The fair-use cap is disclosed HERE, before purchase: an undisclosed hard stop on a
            // paid AI tutor is refund-request and Play-policy material.
            PurchaseCard(
                title = "Monthly",
                subtitle = "Cancel any time.",
                bullets = listOf(
                    "Conversation in ${meta.name}, matched to the level you are studying",
                    "Examiner-style feedback on your written exam answers",
                    "Up to 30 messages a day",
                ),
                price = prices["${BillingManager.SUB_PREMIUM}:${BillingManager.BASE_MONTHLY}"],
                priceSuffix = "/month",
                primary = true,
                onBuy = {
                    activity?.let {
                        container.billing.purchaseSubscription(it, BillingManager.BASE_MONTHLY)
                    }
                }
            )
        }

        Spacer(Modifier.height(4.dp))
        // The only line the cards do not already carry. Everything a tier includes, and the
        // one-time-vs-monthly wording, lives inside the card the learner is reading; repeating
        // it in a paragraph under the shelf only pushed the exit button off the screen.
        Text(
            "Prices include tax and are set by Google Play for your region.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
    }
}

/**
 * The three lines under a tier: how much course, how much vocabulary, how much assessment.
 * Kept to three because a fourth stops being read, and in the order a learner values them.
 */
private fun tierBullets(
    level: String,
    isTop: Boolean,
    lessons: Int,
    freeLessons: Int,
    lastDay: Int,
    words: Int,
    quizzes: Int,
    exams: Int,
): List<String> {
    val range = if (lessons > 0) "$lessons lessons, ${freeLessons + 1} to $lastDay" else "No extra lessons"
    val out = mutableListOf(if (isTop) "$range: every level, to $level" else range)
    if (words > 0) out += "$words new words for your review deck"
    val assess = buildList {
        if (quizzes > 0) add("$quizzes ${if (quizzes == 1) "quiz" else "quizzes"}")
        if (exams > 0) add("$exams mock ${if (exams == 1) "exam" else "exams"}")
    }
    if (assess.isNotEmpty()) out += assess.joinToString(" and ") + ", up to $level"
    return out
}

@Composable
private fun PurchaseCard(
    title: String,
    price: String?,
    onBuy: () -> Unit,
    subtitle: String? = null,
    /**
     * What the payment actually hands over, one short line each. A list is read; the paragraph
     * it replaced was skipped, and a buyer who cannot see what they get does not buy.
     */
    bullets: List<String> = emptyList(),
    /** Fine print under the subtitle, on its own line and smaller (e.g. the fair-use cap). */
    footnote: String? = null,
    priceSuffix: String = "",
    primary: Boolean = false,
    /** Short label above the title, e.g. "Best value". Must be a claim we can substantiate. */
    badge: String? = null,
    /**
     * Already bought. The card stays on the shelf rather than disappearing — a learner deciding
     * how far up to buy needs to see what they already have to judge the tiers above it — but it
     * goes flat and loses its button, so it can never take a second payment for the same thing.
     */
    owned: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            owned -> MaterialTheme.colorScheme.surfaceVariant
            primary -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        contentColor = when {
            owned -> MaterialTheme.colorScheme.onSurfaceVariant
            primary -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (badge != null) {
                Text(badge.uppercase(), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            bullets.forEach { b ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                    Text("✓", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (primary) LocalContentColor.current
                                else MaterialTheme.colorScheme.primary)
                    Text(b, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (owned) {
                // No price and no button: this one is paid for. Naming it out loud is the whole
                // point — the learner asked "do I already have A1?" by opening this screen.
                Text("Already owned ✓", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            } else if (price != null) {
                Text("$price$priceSuffix", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            } else {
                // Product not resolved from Play yet — never show a wrong/free price.
                Text("Price unavailable", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Unavailable")
                }
            }
            // Fine print CLOSES the card, after the price and button, so the pitch reads
            // trial -> price -> act, with the cap as the last word rather than mid-pitch.
            if (footnote != null) {
                Text(footnote, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
