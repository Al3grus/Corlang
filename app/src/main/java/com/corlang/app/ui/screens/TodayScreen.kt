package com.corlang.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.Fsrs
import com.corlang.app.data.WordsRepository
import com.corlang.app.ui.components.GoalRing
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.components.SectionTitle
import com.corlang.app.ui.navigation.Dest
import com.corlang.app.ui.theme.Motion
import com.corlang.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha

/**
 * The Lesson tab = one button. It lands on the day after your last completed one, shows the
 * streak and the next action, and "Start lesson" hands over to the guided SessionPlayer,
 * which walks through every task of the day step by step. No loose checklists, the app leads.
 */
/**
 * Text that occupies exactly its glyphs: no leading above the first line or below the last. Used
 * where a text block sits in a measured gap and the gap has to LOOK like the number it is.
 */
private val tightLines = androidx.compose.ui.text.style.LineHeightStyle(
    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
)

/** [TodayScreen]'s revisit state: the chooser is up, nothing picked yet. */
private const val REVISIT_CHOOSING = -1

/**
 * Everything the lesson card draws for one day, resolved into one value.
 *
 * Held as a unit deliberately. The card's slots animate independently, so a card assembled from
 * live reads would start each slot's transition on its own schedule as the day's data trickled
 * in — the title moving on the tap, the action label a frame or two later when Room answered.
 * One value means one moment where the card changes, and every slot moves together.
 */
private data class LessonCardState(
    val day: Int,
    val level: String,
    val breadcrumb: String,
    val title: String,
    val objective: String,
    /** Today's goal ring belongs to today's card only. */
    val showRing: Boolean,
    /** Ahead of the lesson you are up to: the action is replaced by the locked banner. */
    val locked: Boolean,
    /** Behind the paywall: the action opens the paywall instead of the lesson. */
    val levelLocked: Boolean,
    val actionLabel: String
)

/**
 * One slot of the lesson card changing what it holds: the app's fade-through, over a spring on
 * the slot's OWN height.
 *
 * The height is the point. A slot that simply swapped its contents would leave everything below
 * it to teleport — the action button jumping a line's worth of objective, the card's bottom
 * border landing somewhere new between two frames. Animating each slot's height instead carries
 * the rest of the card with it, so the button slides into its new position and the border
 * follows the button.
 *
 * Clipped, so a slot still growing never paints over the one beneath it, and bounce-free
 * ([Motion.snappy]) because a text box that overshoots its height reads as a wobble.
 */
private fun cardSlot(reduced: Boolean): ContentTransform =
    if (reduced) {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    } else {
        ContentTransform(
            targetContentEnter = fadeIn(tween(Motion.FADE_IN_MS, delayMillis = Motion.FADE_OUT_MS)),
            initialContentExit = fadeOut(tween(Motion.FADE_OUT_MS)),
            sizeTransform = SizeTransform(clip = true) { _, _ -> Motion.snappy() }
        )
    }

/**
 * [cardSlot] for text swapped INSIDE something that stays put — the action button's label. The
 * button never moves, so there is no height to carry anything: only the label's width animates,
 * and unclipped, so a label wider than the one it replaces is not briefly cut off on its way in.
 */
private fun labelSwap(reduced: Boolean): ContentTransform =
    if (reduced) {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    } else {
        ContentTransform(
            targetContentEnter = fadeIn(tween(Motion.FADE_IN_MS, delayMillis = Motion.FADE_OUT_MS)),
            initialContentExit = fadeOut(tween(Motion.FADE_OUT_MS)),
            sizeTransform = SizeTransform(clip = false) { _, _ -> Motion.snappy() }
        )
    }

@Composable
fun TodayScreen(
    container: AppContainer,
    lang: String,
    inLesson: Boolean = false,
    onInLessonChange: (Boolean) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onOpenPaywall: (String) -> Unit = {}
) {
    val plan = remember(lang) { container.content.plan(lang) }
    // End-of-level checkpoints drawn at the tail of each level's journey path:
    //  - quiz: every level that has a level quiz (extra practice + confirmation you learned it)
    //  - readiness: levels whose official exam has a readiness check (pass status + can-do)
    //  - exam flag: every level that has a mock exam in the official format
    val quizLevelIds = remember(lang) {
        container.content.quizzes(lang).quizzes.map { it.levelId }.toSet()
    }
    val readinessLevelIds = remember(lang) {
        container.content.levels(lang).levels.filter { it.exam != null }.map { it.id }.toSet()
    }
    val examLevelIds = remember(lang) {
        container.content.exams(lang).map { it.levelId }.toSet()
    }
    // Levels whose quiz has been completed at least once — fills the quiz checkpoint stone.
    val quizIdToLevel = remember(lang) {
        container.content.quizzes(lang).quizzes.associate { it.id to it.levelId }
    }
    val quizAttempts by container.progress.quizAttempts(lang).collectAsState(initial = emptyList())
    val quizDoneLevelIds = remember(quizAttempts, quizIdToLevel) {
        quizAttempts.mapNotNull { quizIdToLevel[it.quizId] }.toSet()
    }
    val progress by container.progress.progress(lang).collectAsState(initial = null)
    // Nullable-until-loaded so the load-gate below can tell "no completed days" apart from
    // "haven't loaded yet" — collectAsState(emptyList()) conflates the two and paints a stale frame.
    val rawCompleted by container.progress.completedDays(lang).collectAsState(initial = null)
    val completed = rawCompleted.orEmpty()

    val currentDay = progress?.currentDay ?: 1

    // Live due count ('today' computed fresh, no stale midnight state).
    val reviews by container.words.reviews(lang).collectAsState(initial = emptyList())
    val newPerDay by container.languagePrefs.newWordsPerDay.collectAsState(initial = 10)
    val today = WordsRepository.todayEpochDay()

    val dueNow = reviews.count { it.dueEpochDay <= today }
    // The page header needs the learner's name and the course they are in. Both were on screen
    // before and both left with the hero card: the top bar carries no flag either, so nothing on
    // Learn said WHICH course this is.
    val profile by container.languagePrefs.profile.collectAsState(initial = null)
    val meta = remember(lang) { container.content.meta(lang) }
    // The streak itself is no longer read here: it lives on the app bar chip and, in full, in
    // the streak sheet behind it. This screen is about the lesson.

    // The lesson to land on = the day AFTER your last completed one (also covers doing several
    // days at once). Robust even if the stored currentDay lags behind completions.
    val lastCompleted = completed.maxOrNull() ?: 0
    val targetDay = maxOf(currentDay, lastCompleted + 1).coerceIn(1, plan.days.size)

    // The current lesson's word load, split like the lesson's two steps: NEW words this lesson
    // unlocks (deck order, first targetDay * newPerDay, not yet introduced), and the REVIEW load
    // capped at the learner's own maximum. Lesson-scoped, so an earlier day never marks a later
    // day done, and a fresh lesson reads 0% until you actually do its words.
    val allWords = remember(lang) { container.words.allWords(lang) }
    val seenIds = remember(reviews) { reviews.map { it.wordId }.toSet() }
    // Placement offset: deck words the placement test skipped are never counted as unlocked.
    val deckStart by container.languagePrefs.wordDeckStart(lang).collectAsState(initial = 0)
    val unlockedNew = allWords.take(targetDay * newPerDay).drop(deckStart).count { it.id !in seenIds }
    val maxReviews by container.languagePrefs.maxReviewsPerDay
        .collectAsState(initial = Fsrs.REVIEW_CAP)
    val reviewPending = minOf(dueNow, maxReviews)

    // Which day is being viewed (defaults to the target; user can browse away with ‹ ›).
    // Saveable alongside inPlayer: after process death mid-"revisit an old day", the restored
    // player must show the same day, not silently swap to the current one.
    var viewedDay by rememberSaveable(lang) { mutableStateOf(targetDay) }
    var userBrowsed by rememberSaveable(lang) { mutableStateOf(false) }
    // lastAnchor detects a targetDay ADVANCE (day completed): browsing must not outlive it —
    // userBrowsed was previously never reset, so one journey tap froze the dashboard on the
    // browsed day forever ("Revisit Day N ✓" instead of "Start Day N+1" after every lesson).
    var lastAnchor by rememberSaveable(lang) { mutableStateOf(targetDay) }
    // NOT while a lesson is open: "Mark day complete" advances targetDay the instant the write
    // lands, and retargeting viewedDay mid-lesson swapped the open SessionPlayer to the next
    // day — killing the streak celebration after one frame. The inLesson key re-runs the
    // effect on exit, so the dashboard lands on the new day then.
    LaunchedEffect(targetDay, inLesson) {
        if (!inLesson) {
            if (targetDay != lastAnchor) {
                userBrowsed = false
                lastAnchor = targetDay
            }
            if (!userBrowsed) viewedDay = targetDay
        }
    }

    // Which part of a completed lesson the learner chose to redo: REVISIT_CHOOSING while the
    // chooser is up, REVISIT_WHOLE for a full replay, otherwise the step index to open at. Reset on
    // leaving the lesson, so the next tap on "Revisit" lands on the chooser again rather than
    // repeating the last pick.
    var revisitPick by rememberSaveable(lang, viewedDay) { mutableStateOf(REVISIT_CHOOSING) }
    LaunchedEffect(inLesson) { if (!inLesson) revisitPick = REVISIT_CHOOSING }

    val day = plan.days.firstOrNull { it.day == viewedDay } ?: plan.days.first()

    // One-time level gate: this course's first `freeLessons` lessons are free, and every level
    // beyond them needs a purchase for THIS language (or DEV_PREMIUM). A locked day's action
    // opens the paywall instead of the lesson. emptySet initial is fine — the load gate below
    // holds the first frame, and a paid level resolves within it.
    //
    // The window is measured on `d.day`, the lesson's own number in the course, NOT on how far
    // the learner has come. Placement writes a start day and `targetDay` above takes the max of
    // it, so "the next 15 lessons" would give someone placed at lesson 150 a free run through
    // 150-164 of a level they never bought.
    val unlockedLevels by container.premium.unlockedLevels.collectAsState(initial = emptySet())
    val freeLessons = remember(lang) { container.content.meta(lang).freeLessons }
    fun lockedFor(d: com.corlang.app.data.model.StudyDay) =
        !com.corlang.app.BuildConfig.DEV_PREMIUM &&
            com.corlang.app.billing.PremiumManager.dayLocked(
                lang, d.level, d.day, freeLessons, unlockedLevels
            )

    // A level's ASSESSMENTS are gated on its last lesson, not its first: sitting the A1 exam
    // means owning all of A1. For a course whose free window falls inside a level (Portuguese
    // A1) that is the difference between the exam being free and being sold with the level.
    val levelLastDay = remember(plan) {
        plan.days.groupBy { it.level }.mapValues { e -> e.value.maxOf { it.day } }
    }
    fun levelLocked(level: String) =
        !com.corlang.app.BuildConfig.DEV_PREMIUM &&
            com.corlang.app.billing.PremiumManager.dayLocked(
                lang, level, levelLastDay[level] ?: 0, freeLessons, unlockedLevels
            )

    // Placement seeds the run-up to its own point into Review, which is vocabulary the learner
    // may not have paid for. The seeder is idempotent and only ever moves its upper bound, so
    // re-running it whenever entitlement changes tops the seed up without leaving gaps: nothing
    // while the course is locked, the real window once it is bought.
    val placementDay by container.languagePrefs.placementDay(lang).collectAsState(initial = 0)
    LaunchedEffect(lang, placementDay, unlockedLevels) {
        if (placementDay <= 0) return@LaunchedEffect
        val through = if (com.corlang.app.BuildConfig.DEV_PREMIUM) plan.days.size
        else com.corlang.app.billing.PremiumManager.accessibleThroughDay(
            plan.days, lang, freeLessons, unlockedLevels
        )
        container.words.seedPrePlacementForReview(
            lang, placementDay,
            maxDeckIndex = through * com.corlang.app.data.Fsrs.NEW_WORDS_PER_DAY
        )
    }
    val dayLocked = lockedFor(day)

    // Guided session mode. inLesson is hoisted to the app scaffold so a bottom-nav tap (any tab,
    // including Today) exits the lesson back to the dashboard — progress is saved per step, so a
    // "Continue" resumes exactly where it left off.
    //
    // A COMPLETED lesson opens on the revisit chooser instead of straight into the player: revisits
    // are for redoing one part, and reaching part four used to mean tapping through parts one to
    // three. What the learner picked there lives here, because the chooser and the player it hands
    // over to are two screens of the same visit.
    if (inLesson) {
        // Whether this lesson is finished decides WHICH screen opens, so it has to be known before
        // either paints. Reading a still-loading empty list as "not completed" would drop a revisit
        // into the ordinary player for a frame — and that player, on a finished lesson, clears its
        // step marks on the way past. One blank frame instead. (The page's own load gate is below,
        // after this branch, because it must not blank a lesson in progress.)
        if (rawCompleted == null) {
            Column(Modifier.fillMaxSize()) {}
            return
        }
        /*
         * What kind of visit this is, decided ONCE when the lesson opens and not read live.
         *
         * "Mark lesson complete" writes the day into `completed` the moment it is tapped, and a
         * live read flipped this visit to a revisit while the celebration was still on screen:
         * the overlay was swapped after a frame for the section chooser, which is the screen for
         * a lesson you have ALREADY done. The learner finished a lesson and was shown the menu
         * for redoing it. Same class of bug as viewedDay above, one layer along - what a visit is
         * was settled when it opened, and the completion it ends with cannot rewrite it.
         */
        val revisiting = remember(day.day, inLesson) { completed.contains(day.day) }
        if (revisiting && revisitPick == REVISIT_CHOOSING) {
            // System back leaves the lesson; there is nothing in progress to lose.
            androidx.activity.compose.BackHandler { onInLessonChange(false) }
            LessonRevisit(
                container = container,
                lang = lang,
                day = day,
                onPickSection = { revisitPick = it },
                onExit = { onInLessonChange(false) }
            )
            return
        }
        // A section replay: opens at that step, marks nothing, and leaves back to the chooser so
        // the next part is one tap away.
        val replayAt = revisitPick.takeIf { revisiting && it >= 0 }
        androidx.activity.compose.BackHandler {
            if (replayAt != null) revisitPick = REVISIT_CHOOSING else onInLessonChange(false)
        }
        // BoxWithConstraints purely to measure: the player scrolls, so inside it the available
        // height is unknowable, and the wrap-up needs it to centre its question on the screen.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            SessionPlayer(
                container = container,
                lang = lang,
                day = day,
                totalDays = plan.days.size,
                onNavigate = onNavigate,
                onExit = {
                    if (replayAt != null) revisitPick = REVISIT_CHOOSING
                    else onInLessonChange(false)
                },
                startAt = replayAt,
                viewportHeight = maxHeight
            )
        }
        return
    }

    // The viewed day's step checks are collected INSIDE the lesson card (see below), per
    // animated card instance, so browsing days never blanks the rest of the dashboard.

    // Daily goal ring: today's guided session, measured on the day you're up to (not the one
    // being browsed). Closes fully once a lesson day has been completed today and stays closed.
    // Keyed on the epoch day: an unkeyed remember froze "today" — a process alive across
    // midnight kept counting yesterday's completion as today's and showed the ring done.
    val startOfToday = remember(today) {
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }
    val completedToday by container.progress.completionsSince(lang, startOfToday)
        .collectAsState(initial = 0)
    val targetDayObj = plan.days.firstOrNull { it.day == targetDay } ?: day
    val targetSteps = remember(lang, targetDay) {
        buildSessionSteps(targetDayObj, container.content.meta(lang).name)
    }
    // Same re-key as rawChecks above, for when targetDay moves (day completed / reconcile).
    val rawTargetChecks by androidx.compose.runtime.key(targetDay) {
        container.progress.dayTaskChecks(lang, targetDay).collectAsState(initial = null)
    }
    val targetChecks = rawTargetChecks.orEmpty()
    val targetDoneIds = targetChecks.map { it.itemId }.toSet()
    val targetAction = targetSteps.filter { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE }
    val targetStarted = targetDoneIds.isNotEmpty()
    val targetStepsDone = targetAction.count { s ->
        // Same started-gate as above: an untouched lesson must show a 0% ring.
        when (s.kind) {
            StepKind.WORDS -> s.id in targetDoneIds || (targetStarted && unlockedNew == 0)
            StepKind.REVIEW -> s.id in targetDoneIds || (targetStarted && reviewPending == 0)
            else -> s.id in targetDoneIds
        }
    }
    // Partial credit inside multi-exercise steps: clearing 3 of 8 exercises nudges the ring by
    // ~3/8 of one step. Reads the same persisted per-question "<stepId>::q<i>" checks (plus
    // legacy "::x<n>"; never "::missed") the SessionPlayer's resume + session bar use, so the
    // ring and the in-lesson bar always agree.
    val targetPartial = targetAction
        .filter { it.kind == StepKind.EXERCISE && it.id !in targetDoneIds }
        .sumOf { s ->
            val total = targetDayObj.activities.getOrNull(s.activityIndex)?.questions?.size ?: 0
            if (total <= 0) 0.0
            else targetDoneIds.count { it.startsWith("${s.id}::q") || it.startsWith("${s.id}::x") }
                .coerceAtMost(total).toDouble() / total
        }.toFloat()
    val ringProgress = when {
        completedToday > 0 -> 1f
        targetAction.isEmpty() -> 0f
        else -> ((targetStepsDone + targetPartial) / targetAction.size).coerceIn(0f, 1f)
    }

    // Load-then-show: hold the first paint until every flow that decides the ring and the
    // journey's completed stones has actually emitted. Without this a tab-return paints one
    // frame from the still-loading defaults before snapping to the real state — the flicker.
    // Deliberately NOT gated on the viewed day's checks: those re-key to null on every journey
    // tap, and blanking the whole screen for that frame destroyed LevelJourney's internal state
    // (selected level chip, scroll), which is why browsing to another level's day snapped the
    // journey straight back to the current level. The lesson card gates itself instead.
    // profile joins the gate for the same reason as the rest: without it the header paints
    // "Good evening" for a frame and then the name pops in beside it.
    if (progress == null || rawCompleted == null || rawTargetChecks == null || profile == null) {
        Column(Modifier.fillMaxSize()) {}
        return
    }

    // ONE gap value for the whole page, top padding included, and the header uses it INTERNALLY
    // too: top bar → flag, flag → greeting, greeting → lesson card are all 24.dp, so the top of
    // the screen reads as an even ladder rather than three arbitrary gaps.
    //
    // It was cut to 16 back when a streak hero stood above the card and the journey stones were
    // being cut mid-stone on a standard 360dp phone (field report 2026-07-27). Deleting that
    // hero freed far more height than this spends, so the stones still fit.
    // The gate above holds the first paint until every flow has emitted; without this the page
    // then SNAPS in at full opacity, which is most of what made arriving on this tab feel abrupt
    // - the tab's own fade had already played out over a blank screen. One fade on arrival.
    val pageAlpha = com.corlang.app.ui.theme.rememberAppearAlpha()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(pageAlpha)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            // 27 on top, not 24: see the header below. Everything else on the page is bordered,
            // so 24 between blocks measures and reads the same.
            .padding(top = 27.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // A page header, not a card: who is here, and which course this is.
        //
        // It names the course and NOTHING else on purpose. It first read "Day 31 of your
        // Croatian plan", which restated the two things directly below it — the card's own
        // action says "Start Lesson 31", and the journey stones are numbered by day under a
        // "A1 · 7 / 24 lessons done" caption. The course is the one thing Learn stopped naming
        // when the hero card went and the top bar dropped its flag.
        //
        // The greeting moves through the day, so the page is never identical two visits running.
        // A learner who skipped the name field just gets the bare greeting.
        Column(
            // 21 between the two lines, against 27 above the flag and 24 below the greeting —
            // three different numbers that produce the SAME ~30dp of visible air. Equal layout
            // gaps do not look equal here, and trimming the leading (below) only fixes half of
            // it: a glyph box still carries the font's ascent and descent, and those scale with
            // type size. The flag is 14sp, the greeting 24sp, and the middle gap is the only one
            // with a font box on BOTH sides, so at a flat 24 it measured ~33 to the ends' ~27
            // and ~30. These are Roboto's metrics; a very different system font shifts them a
            // little, which is the normal price of spacing type by eye rather than by ruler.
            verticalArrangement = Arrangement.spacedBy(21.dp)
        ) {
            val hour = java.time.LocalTime.now().hour
            val who = profile?.name?.trim().orEmpty()
            // Course first, as an overline. The lesson card below is built the same way — a
            // small label line above a bold title — so the page reads as one rhythm repeated
            // rather than two blocks organised opposite ways. It also puts the flag first
            // thing top-left, which is where the app bar used to carry the course identity.
            // trim = Both on both lines: a text box carries leading above and below its glyphs,
            // and the gap between two text lines gets BOTH of them while the gaps to the app bar
            // above and the card below get one each. Trimming makes the box hug the glyphs,
            // which removes the larger half of the discrepancy; the compensation above handles
            // the ascent and descent that trimming cannot touch.
            Text(
                "${meta.flagEmoji} ${meta.name}",
                style = MaterialTheme.typography.labelLarge.copy(lineHeightStyle = tightLines),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                buildString {
                    append(
                        when {
                            hour < 12 -> "Good morning"
                            hour < 18 -> "Good afternoon"
                            else -> "Good evening"
                        }
                    )
                    if (who.isNotEmpty()) append(", $who")
                },
                style = MaterialTheme.typography.headlineSmall.copy(lineHeightStyle = tightLines),
                fontWeight = FontWeight.Bold
            )
        }

        // The lesson card carries the whole page now. The old streak hero above it (flame,
        // goal ring, a rotating subtitle) was deleted: the streak moved to a tappable chip on
        // the app bar, the ring moved INSIDE this card, and the subtitle turned out to be
        // saying nothing worth a card — "starts with your N due words" was not even true, since
        // a lesson opens on its NEW words. One card, one thing to do.
        //
        // ONE card that reshapes, never a card that gets replaced. It used to be an
        // AnimatedContent wrapped around the whole Surface, and that is what made browsing the
        // journey feel abrupt: two days' cards were laid out at their OWN heights and
        // cross-faded, so the bottom border and the action button never TRAVELLED between the
        // two positions — they dissolved from one place to the other while the container height
        // slid underneath them, which is why the border cut across the button mid-transition.
        //
        // Now the Surface stays put and each SLOT inside it — breadcrumb, title, objective,
        // action — cross-fades its own text over a spring-animated height of its own. Anything
        // below a slot that grew or shrank is carried to its new position by that spring, so
        // the action button slides into place, and the bottom of the card glides open or shut
        // instead of jumping. The journey below rides the same springs, since the card is just
        // a taller or shorter child of the page column.
        val reducedMotion = rememberReducedMotion()
        // Seeded from the target day's checks, which the page gate above already waited for. An
        // unseeded null first frame reads as "not started", so the card would paint "Start
        // Lesson 31 →" and then cross-fade it straight back out the moment Room answered.
        val cardChecks by androidx.compose.runtime.key(day.day) {
            container.progress.dayTaskChecks(lang, day.day)
                .collectAsState(initial = if (day.day == targetDay) rawTargetChecks else null)
        }
        val doneIds = cardChecks.orEmpty().map { it.itemId }.toSet()
        val cardSteps = remember(lang, day.day) { buildSessionSteps(day, meta.name) }
        val actionSteps = cardSteps.filter { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE }
        // "Started" = you actually completed a step of THIS lesson (a persisted check), so
        // opening the lesson or a shared-word state never turns "Start" into "Continue".
        val lessonStarted = doneIds.isNotEmpty()
        val stepsDone = actionSteps.count { s ->
            // Words/review count when this day's block was completed, OR — only once the lesson
            // has been started — when the block is already cleared (capped blocks can leave a
            // backlog behind). Without the started gate a fresh course showed phantom progress.
            when (s.kind) {
                StepKind.WORDS -> s.id in doneIds || (lessonStarted && unlockedNew == 0)
                StepKind.REVIEW -> s.id in doneIds || (lessonStarted && reviewPending == 0)
                else -> s.id in doneIds
            }
        }
        val dayLevelLocked = lockedFor(day)
        val liveCard = LessonCardState(
            day = day.day,
            level = day.level,
            breadcrumb = "${day.phase} · Week ${day.week} · ${day.level}",
            title = day.title,
            objective = day.objective,
            showRing = day.day == targetDay,
            // Days ahead of the one you're up to stay locked.
            locked = day.day > targetDay,
            levelLocked = dayLevelLocked,
            actionLabel = when {
                day.day > targetDay -> "Locked, finish Lesson $targetDay first. No skipping ahead."
                dayLevelLocked -> "🔒 Unlock ${day.level} to continue"
                completed.contains(day.day) -> "Revisit Lesson ${day.day} ✓"
                lessonStarted -> "Continue Lesson ${day.day} ($stepsDone/${actionSteps.size} steps)"
                day.day == targetDay -> "Start Lesson ${day.day} →"
                else -> "Open Lesson ${day.day} →"
            }
        )
        // Room answers a journey tap a frame or two after it lands, and the card is only worth
        // animating if it changes ONCE per tap. Published live, a tap would fade in a lesson
        // labelled "Open Lesson 12 →" from the empty gap and fade it straight back out for
        // "Continue Lesson 12 (2/5 steps)" — two transitions for one tap, the second undoing
        // the first. So the card holds the previous lesson until the new day's checks land.
        // Keyed on cardChecks as well as the state: a day whose checks arrive EMPTY produces
        // the same state it already had from the null gap, and on the state alone the hold
        // would never lift.
        var shownCard by remember { mutableStateOf(liveCard) }
        LaunchedEffect(liveCard, cardChecks) { if (cardChecks != null) shownCard = liveCard }
        val card = shownCard
        // Held so the carry-over label does not read "0 more words" for the 90ms it spends
        // fading out — the count that hides the button is the same count the button prints.
        var lastDue by remember { mutableStateOf(dueNow) }
        LaunchedEffect(dueNow) { if (dueNow > 0) lastDue = dueNow }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 20dp of padding and 12 between children, not 16/6: at the old values the label,
            // title, objective and action sat on top of each other and the card read as dense
            // rather than calm. Deleting the hero above bought the room to spend here.
            //
            // The bottom is 32, not 20, so the card looks evenly padded. The first row is pinned
            // to the goal ring's 44dp and centres a ~20dp label inside it, which puts ~12dp of
            // empty row above that label on top of the 20dp of padding. Measured, the two ends
            // were equal; seen, the top had half again as much air as the bottom.
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // The level label and the ring share a row, centred on each other. The row is
                // pinned to the ring's height whether or not the ring is drawn, so browsing to a
                // day that has no ring does not change where the title sits.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    AnimatedContent(
                        targetState = card.breadcrumb,
                        transitionSpec = { cardSlot(reducedMotion) },
                        modifier = Modifier.weight(1f),
                        label = "lessonBreadcrumb"
                    ) { text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    // The ring is TODAY's goal, so it appears only on today's lesson card:
                    // browsing back to an old lesson hides it rather than implying that
                    // revisiting day 4 moves today's goal. It shrinks out sideways rather than
                    // blinking off, so the label beside it widens into the space it leaves.
                    AnimatedVisibility(
                        visible = card.showRing,
                        enter = if (reducedMotion) EnterTransition.None else
                            fadeIn(tween(Motion.FADE_IN_MS, delayMillis = Motion.FADE_OUT_MS)) +
                                expandHorizontally(Motion.snappy()),
                        exit = if (reducedMotion) ExitTransition.None else
                            fadeOut(tween(Motion.FADE_OUT_MS)) + shrinkHorizontally(Motion.snappy())
                    ) {
                        GoalRing(
                            progress = ringProgress,
                            label = if (ringProgress >= 1f) "✓"
                                    else "${(ringProgress * 100).toInt()}%",
                            size = 44.dp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                // Its own line, full width, so a long title is no longer squeezed into a column
                // beside the ring. The label centres in a 44dp row, which leaves ~12dp of that
                // row below the label text; with the card's 12dp between children that puts 24
                // of visible air between the two titles.
                AnimatedContent(
                    targetState = card.title,
                    transitionSpec = { cardSlot(reducedMotion) },
                    label = "lessonTitle"
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                SectionTitle("In this lesson you will")
                // bodyLarge + a 2-line cap keeps the card compact on standard phones (the full
                // objective is repeated inside the lesson itself, so a trim here loses nothing).
                // The cap is also what makes this slot worth animating: one day's objective
                // wraps to two lines and the next day's to one, and that single line of
                // difference is the distance everything below it has to travel.
                AnimatedContent(
                    targetState = card.objective,
                    transitionSpec = { cardSlot(reducedMotion) },
                    label = "lessonObjective"
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                // The lesson action lives HERE, with the lesson it acts on — never on the
                // streak hero. The locked banner is not a separate branch of the card but the
                // same slot holding something else: the two are different heights, and
                // animating between them is what keeps the bottom of the card from jumping
                // when you browse from a lesson you can open to one you cannot.
                AnimatedContent(
                    targetState = card.locked,
                    transitionSpec = { cardSlot(reducedMotion) },
                    modifier = Modifier.fillMaxWidth(),
                    label = "lessonAction"
                ) { locked ->
                    if (locked) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(
                                card.actionLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        // Outlined, not filled: a quiet bordered action that matches the card's
                        // calm style instead of a full-color block.
                        OutlinedButton(
                            // Locked level → paywall; otherwise open the guided lesson.
                            onClick = {
                                if (card.levelLocked) onOpenPaywall(card.level)
                                else onInLessonChange(true)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            // The label changes far more often than the button does — start,
                            // continue, revisit, unlock — so it cross-fades INSIDE a button that
                            // stays exactly where it is. Only its width animates, and unclipped,
                            // so a long label is never briefly truncated on its way in.
                            AnimatedContent(
                                targetState = card.actionLabel,
                                transitionSpec = { labelSwap(reducedMotion) },
                                contentAlignment = Alignment.Center,
                                label = "lessonActionLabel"
                            ) { text -> Text(text) }
                        }
                    }
                }
                // The CARRY-OVER review, and only that. The lesson hands the learner their due
                // words itself, but capped at maxReviewsPerDay — so a 40-card backlog with a cap
                // of 20 finishes the day with 20 still due, and the day is rightly complete
                // either way (the backlog must never hold the streak hostage). This button is
                // that remainder, offered once the day is banked. It stays hidden BEFORE the
                // lesson on purpose: draining the queue early would land the learner on the
                // lesson's review step with nothing left to review.
                //
                // It grows in under the action rather than appearing at full height: this is
                // the one thing that changes the card's height while the learner is looking at
                // it, and it arrives the moment a lesson is banked.
                AnimatedVisibility(
                    visible = card.day == targetDay && completedToday > 0 && dueNow > 0,
                    enter = Motion.revealEnter(reducedMotion),
                    exit = Motion.revealExit(reducedMotion)
                ) {
                    TextButton(
                        onClick = { onNavigate(Dest.WORDS.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Review $lastDue more word${if (lastDue == 1) "" else "s"} →") }
                }
            }
        }

        // The stepping-stones map: scroll your level's lessons, jump to any you've reached,
        // switch between completed levels to review.
        LevelJourney(
            plan = plan,
            completed = completed,
            targetDay = targetDay,
            viewedDay = viewedDay,
            quizLevelIds = quizLevelIds,
            readinessLevelIds = readinessLevelIds,
            examLevelIds = examLevelIds,
            quizDoneLevelIds = quizDoneLevelIds,
            levelLocked = ::levelLocked,
            onOpenQuiz = { level -> onNavigate("quiz/$level") },
            onOpenReadiness = { level -> onNavigate("readiness/$level") },
            onOpenExam = { level -> onNavigate("exam/$level") },
            // Tapping the CURRENT day's stone is not "browsing away" — the dashboard keeps
            // following the plan.
            onPickDay = { d -> viewedDay = d; userBrowsed = d != targetDay }
        )

        Spacer(Modifier.height(8.dp))
    }
}
