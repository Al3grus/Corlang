package com.corlang.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.billing.PremiumManager
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

/**
 * Profile = the app's control panel: four uniform rows — Settings, Language, Course & tutor,
 * References. Progress-related stats live on the separate Progress tab; this tab is where you
 * change how the app works, not where you check how you're doing.
 */
@Composable
fun ProfileScreen(
    container: AppContainer,
    lang: String,
    resetTick: Int = 0,
    onSelectLanguage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onGetPremium: () -> Unit = {},
    /** Opens the level paywall (the course tiers), as opposed to the AI subscription. */
    onUnlockCourse: () -> Unit = {}
) {
    // Sub-page routing within the tab (null = the menu). Menu to sub-page is a screen change
    // like any other, so it spends the app's fade-through at the same length a tab change does:
    // stepping into Language from this menu and stepping from Learn to Review are the same move
    // and have no business looking different. It was a simultaneous Crossfade, which is the one
    // shape this vocabulary does not use - both screens painted at once means the old one lingers
    // over the new, which is why the fade-through is sequential (see Motion).
    // Plain remember, NOT rememberSaveable: the NavHost saves/restores each tab's saveable
    // state across switches, so a saveable page re-opened the sub-page (Premium, References)
    // when you came BACK to Profile from another tab. Leaving the tab should reset to the
    // Profile main view, so this state is meant to die with the visit.
    var page by remember(lang) { mutableStateOf<String?>(null) }
    // A Profile tab tap closes whatever sub-page is open, landing back on the menu.
    LaunchedEffect(resetTick) { if (resetTick > 0) page = null }

    val reducedMotion = com.corlang.app.ui.theme.rememberReducedMotion()
    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = {
            androidx.compose.animation.ContentTransform(
                targetContentEnter = com.corlang.app.ui.theme.Motion.enter(reducedMotion),
                initialContentExit = com.corlang.app.ui.theme.Motion.exit(reducedMotion),
                // The pages are all full-screen: there is no box to grow or shrink between them,
                // and letting one animate would only wobble the page during the fade.
                sizeTransform = null
            )
        },
        label = "profile-page"
    ) { p -> when (p) {
        "language" -> SubPage("Language", onBack = { page = null }) {
            // Choosing a language returns to the Profile menu (like closing Settings does).
            LanguagePage(container, lang) { code -> onSelectLanguage(code); page = null }
        }
        "premium" -> SubPage("Course & tutor", onBack = { page = null }) {
            PremiumPage(container, lang, onGetPremium, onUnlockCourse)
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // Just the title: the menu rows below already name everything here, so a subtitle
            // listing them again added nothing. Same size and air as the Settings header.
            Text(
                "Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val entitled by container.premium.entitled.collectAsState(initial = false)
            val meta = remember(lang) { container.content.meta(lang) }
            val context = androidx.compose.ui.platform.LocalContext.current

            // Names cards that are ALWAYS in Settings. "Voice" was in this list and is not: that
            // card only appears when text-to-speech is missing or unavailable, so on a healthy
            // phone the row advertised a setting that was not there when you arrived. The names
            // match the card titles, so the row predicts what you will actually find.
            MenuRow(Icons.Outlined.Settings, "Settings",
                "Reminder, review limit, appearance, backup", onClick = onOpenSettings)
            MenuRow(Icons.Outlined.Language, "Language",
                "${meta.flagEmoji} ${meta.name} · tap to switch", onClick = { page = "language" })
            // Names BOTH things that can be bought. The row used to say "Get Premium" and speak
            // only of the AI tutor, which left the course unlocks with no home in the app at
            // all: the only way to reach them was to walk into a locked lesson.
            val unlockedLevels by container.premium.unlockedLevels.collectAsState(initial = emptySet())
            val owned = remember(lang, unlockedLevels) { courseOwnership(container, lang, unlockedLevels) }
            // Names the two things sold, so the label never goes stale: the subtitle carries
            // the state. "Full access" was the alternative and is subtly wrong in the commonest
            // paying state - owning the course is not full access, the tutor is sold separately.
            //
            // Six states, because the two purchases are independent and the course has a middle
            // ground: a learner can own through A1 or A2 without owning the course. Collapsing
            // that middle into "owned" would tell somebody with one level of four that they had
            // the course, which is the sentence they would quote in a refund request.
            MenuRow(Icons.Outlined.WorkspacePremium, "Course & tutor",
                when {
                    owned.isWholeCourse && entitled -> "Course and tutor · full access ✓"
                    owned.isWholeCourse -> "Full ${meta.name} course owned ✓ · tutor available"
                    owned.top != null && entitled ->
                        "${meta.name} course (${owned.top}) owned ✓ · tutor active ✓"
                    owned.top != null ->
                        "${meta.name} course (${owned.top}) owned ✓ · tutor available"
                    entitled -> "Tutor active ✓ · unlock the course"
                    else -> "Unlock the course, or the tutor"
                },
                onClick = { page = "premium" })
            // No "Request a language" row here. Asking everyone which course to build next
            // invites the answer from people who have not finished the one they have; the
            // question is worth asking of exactly one person, the learner who topped out the
            // placement test and has genuinely run out of course. It lives on that screen
            // (PlacementScreen, the at-ceiling branch) and nowhere else.
        }
    } }
}

/**
 * What this install owns of one course.
 *
 * [top] is the HIGHEST level owned, or null for none. Unlocks are cumulative, so the highest one
 * is the whole story: owning A2 means owning A1 too, and there is no state where a rung is owned
 * without the ones below it. [isWholeCourse] is that level being the course's last, which is the
 * only case that may be described as owning the course.
 */
private data class CourseOwnership(val top: String?, val isWholeCourse: Boolean)

private fun courseOwnership(
    container: AppContainer,
    lang: String,
    unlocked: Set<String>,
): CourseOwnership {
    // sortedBy(day) because "the last level" must mean the course's final level, not whichever
    // one happens to sit last in the loaded plan files. Same ordering the paywall derives its
    // tiers from, so the two agree about which product is the whole course.
    val levels = container.content.plan(lang).days.sortedBy { it.day }.map { it.level }.distinct()
    val top = levels.lastOrNull { PremiumManager.key(lang, it) in unlocked }
    return CourseOwnership(top, top != null && top == levels.lastOrNull())
}

/** One uniform Profile menu row: icon · title/subtitle · chevron. */
@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A titled sub-page with a back button; system back returns to the menu too.
 *  Header matches the Settings screen exactly: arrow + headlineSmall bold, 16dp above and
 *  below so the title sits centred in its band. */
/**
 * One product, one card: what it is, one sentence, one way in. Deliberately says nothing about
 * price — a number with no offer around it invites a decision the learner cannot make yet, and
 * the offer screen behind the button is where the case gets made.
 */
@Composable
private fun OfferCard(
    title: String,
    teaser: String,
    owned: Boolean,
    cta: String?,
    onClick: () -> Unit,
) {
    InfoCard {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (owned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
        Text(
            teaser,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (cta != null) {
            androidx.compose.material3.Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            ) { Text(cta) }
        }
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // top = 16 mirrors the Settings screen, whose whole column carries 16dp padding;
            // 4dp here left these headers sitting visibly higher than the Settings one.
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, end = 16.dp)
        ) {
            // A bare arrow, no box or text: system back works too, this is just the visible way.
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        // 16dp under the header mirrors the 16dp above it, centring the title in its band.
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * Choose the language you're learning — the ONLY place to switch (top bar no longer picks).
 *
 * Split in two, because these are different decisions wearing the same shape: RESUMING a course
 * you have days banked in, and STARTING one you have never opened. A single flat list made a
 * six-course build read as six equal options every time, and buried the one or two the learner
 * actually studies among four they have never touched.
 */
@Composable
private fun LanguagePage(container: AppContainer, lang: String, onSelect: (String) -> Unit) {
    val all = remember { container.content.allMeta() }
    // A course counts as started once a day is banked or the learner has been moved off day 1
    // (placement does that without completing anything). Read per language and remembered as one
    // map, so the two lists below cannot disagree about where a course belongs.
    // null until Room answers. Load-then-show, the same rule Today and Progress follow: with an
    // empty map as the initial value every language except the selected one was classified as
    // never started for one frame, so a course you HAVE started visibly jumped from "Not started
    // yet" up to "Your courses" as the page opened.
    val startedDays by produceState<Map<String, Int>?>(initialValue = null, all) {
        value = all.associate { m ->
            val done = container.progress.completedDayCount(m.code).first()
            val day = container.progress.progress(m.code).first()?.currentDay ?: 1
            m.code to if (done > 0 || day > 1) maxOf(day, 1) else 0
        }
    }
    val days = startedDays
    val started = days?.let { d -> all.filter { (d[it.code] ?: 0) > 0 || it.code == lang } }
    val fresh = started?.let { s -> all.filterNot { it in s } }

    /*
     * The WHOLE page fades in once the read lands, rather than the heading painting immediately
     * and the lists snapping in underneath it a frame later. Withholding the lists stopped a
     * language appearing in the wrong group, but it left the pop: the page arrived in two
     * pieces. One short fade covers the gap, and because it is driven by the data rather than by
     * a timer, it is exactly as long as the wait actually is.
     */
    val ready = days != null
    val fade by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = if (com.corlang.app.ui.theme.rememberReducedMotion())
            androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(durationMillis = 220),
        label = "languagePageFade"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            .alpha(fade)
    ) {
        Text("Your progress is kept separately for each language, so switching never loses anything.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp))

        // Both lists appear together or not at all: a language must never be seen in the wrong
        // group, however briefly.
        if (days != null && started != null && fresh != null) {
            if (started.isNotEmpty()) {
                LanguageGroupHeading("Your courses")
                started.forEach { m ->
                    LanguageRow(
                        meta = m,
                        chosen = m.code == lang,
                        // The current language can be here without a day banked yet (it was just
                        // picked in onboarding), so name it without claiming a lesson.
                        subtitle = (days[m.code] ?: 0).let {
                            if (it > 0) "${m.nativeName} · Lesson $it" else m.nativeName
                        },
                        onSelect = onSelect
                    )
                }
            }
            if (fresh.isNotEmpty()) {
                LanguageGroupHeading("Not started yet")
                fresh.forEach { m ->
                    LanguageRow(meta = m, chosen = false, subtitle = m.nativeName, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun LanguageGroupHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun LanguageRow(
    meta: com.corlang.app.data.model.LanguageMeta,
    chosen: Boolean,
    subtitle: String,
    onSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (chosen) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (chosen) 2.dp else 1.dp,
            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clickable { onSelect(meta.code) }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(meta.flagEmoji, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(meta.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (chosen) Text("✓", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * The two things Corlang sells, on one page, because a learner wondering what they have to pay
 * for should not have to discover the answer by hitting a wall.
 *
 *  - The COURSE: one-time, per language, cumulative levels. Bought outright, never rented.
 *  - The AI TUTOR: a monthly subscription, the only recurring charge in the app.
 */
@Composable
private fun PremiumPage(
    container: AppContainer,
    lang: String,
    onGetPremium: () -> Unit,
    onUnlockCourse: () -> Unit,
) {
    val entitled by container.premium.entitled.collectAsState(initial = false)
    val unlockedLevels by container.premium.unlockedLevels.collectAsState(initial = emptySet())
    val meta = remember(lang) { container.content.meta(lang) }
    // Same source of truth as the menu row that leads here, so the two can never disagree about
    // what the learner owns.
    val owned = remember(lang, unlockedLevels) { courseOwnership(container, lang, unlockedLevels) }
    val ownedTop = owned.top
    val courseComplete = owned.isWholeCourse

    /*
     * Two products, two identical cards: a status line, one sentence of what it is, and a button
     * into its own offer screen.
     *
     * It used to be lopsided. The course card said "See the course options" and hid everything,
     * while the tutor card spilled its whole feature list here and THEN sent you elsewhere to
     * buy. So one product looked like a door and the other like a leaflet, the tutor's pitch was
     * spent before the screen that has to close it, and the two read as unrelated things
     * bolted together rather than two ways to spend money with us.
     *
     * The detail belongs where the price is. This screen only has to make someone want to look.
     */
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OfferCard(
            title = when {
                courseComplete -> "You own the whole ${meta.name} course ✓"
                ownedTop != null -> "${meta.name}: you own through $ownedTop ✓"
                else -> "The ${meta.name} course"
            },
            owned = ownedTop != null,
            teaser = when {
                courseComplete ->
                    "Every lesson, word, quiz and exam in this course is yours."
                ownedTop != null ->
                    "The levels above $ownedTop are still locked, and each unlock includes the " +
                        "ones beneath it."
                else ->
                    "Every lesson to B1, the words that go with them, and the quizzes and mock " +
                        "exams that close each level. One payment, no subscription."
            },
            cta = if (courseComplete) null
                  else if (ownedTop != null) "See the remaining levels" else "See course options",
            onClick = onUnlockCourse
        )

        Spacer(Modifier.height(14.dp))
        OfferCard(
            title = if (entitled) "AI tutor is active ✓" else "AI tutor",
            owned = entitled,
            teaser = if (entitled)
                "It is in your bottom bar, under Tutor."
            else
                "Someone to speak ${meta.name} with at your level, and to mark your exam " +
                    "writing like an examiner would. Monthly, cancel any time.",
            cta = if (entitled) null else "See the tutor plan",
            onClick = onGetPremium
        )

        Spacer(Modifier.height(6.dp))
        // Restore also runs automatically on every app resume; this is the explicit affordance
        // (a reinstall / new device picks the purchase back up from the Play account).
        OutlinedButton(
            onClick = { container.billing.start() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Restore purchases") }
        Spacer(Modifier.height(24.dp))
    }
}

