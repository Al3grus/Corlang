package com.corlang.app.ui.screens

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.launch

/**
 * The Lesson tab = one button. It lands on the day after your last completed one, shows the
 * streak and the next action, and "Start lesson" hands over to the guided SessionPlayer,
 * which walks through every task of the day step by step. No loose checklists, the app leads.
 */
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
    // Which CEFR levels end in an official exam — used to draw a checkpoint at the tail of each
    // such level's path (finish the level to unlock its mock exam).
    val examLevelIds = remember(lang) {
        container.content.levels(lang).levels.filter { it.exam != null }.map { it.id }.toSet()
    }
    val progress by container.progress.progress(lang).collectAsState(initial = null)
    // Nullable-until-loaded so the load-gate below can tell "no completed days" apart from
    // "haven't loaded yet" — collectAsState(emptyList()) conflates the two and paints a stale frame.
    val rawCompleted by container.progress.completedDays(lang).collectAsState(initial = null)
    val completed = rawCompleted.orEmpty()

    val currentDay = progress?.currentDay ?: 1
    val freezes = progress?.streakFreezes ?: 0

    // Live due count for the hero ('today' computed fresh, no stale midnight state).
    val reviews by container.words.reviews(lang).collectAsState(initial = emptyList())
    val newPerDay by container.languagePrefs.newWordsPerDay.collectAsState(initial = 10)
    val today = WordsRepository.todayEpochDay()

    // Streak decayed to right-now: a missed day (beyond a freeze) reads 0, not the stale stored
    // value — the stored streak only self-heals on the next completion.
    val streak = com.corlang.app.data.ProgressRepository.displayStreak(
        streak = progress?.streak ?: 0,
        lastStudiedEpochDay = progress?.lastStudiedEpochDay ?: 0L,
        freezes = freezes,
        today = today
    )
    val dueNow = reviews.count { it.dueEpochDay <= today }

    // The lesson to land on = the day AFTER your last completed one (also covers doing several
    // days at once). Robust even if the stored currentDay lags behind completions.
    val lastCompleted = completed.maxOrNull() ?: 0
    val targetDay = maxOf(currentDay, lastCompleted + 1).coerceIn(1, plan.days.size)

    // The current lesson's word load, split like the lesson's two steps: NEW words this lesson
    // unlocks (deck order, first targetDay * perLesson, not yet introduced), and the REVIEW load
    // capped at REVIEW_CAP. Lesson-scoped, so an earlier day never marks a later day done, and a
    // fresh lesson reads 0% until you actually do its words.
    val allWords = remember(lang) { container.words.allWords(lang) }
    val seenIds = remember(reviews) { reviews.map { it.wordId }.toSet() }
    // Placement offset: deck words the placement test skipped are never counted as unlocked.
    val deckStart by container.languagePrefs.wordDeckStart(lang).collectAsState(initial = 0)
    val unlockedNew = allWords.take(targetDay * newPerDay).drop(deckStart).count { it.id !in seenIds }
    val reviewPending = minOf(dueNow, Fsrs.REVIEW_CAP)

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

    val day = plan.days.firstOrNull { it.day == viewedDay } ?: plan.days.first()

    // One-time level gate: A0/A1 are free; A2+ need a purchase (or DEV_PREMIUM). A locked day's
    // action opens the paywall instead of the lesson. emptySet initial is fine — the load gate
    // below holds the first frame, and a paid level resolves within it.
    val unlockedLevels by container.premium.unlockedLevels.collectAsState(initial = emptySet())
    fun lockedFor(d: com.corlang.app.data.model.StudyDay) =
        !com.corlang.app.BuildConfig.DEV_PREMIUM &&
            d.level !in container.premium.freeLevels && d.level !in unlockedLevels
    val dayLocked = lockedFor(day)

    // Guided session mode. inLesson is hoisted to the app scaffold so a bottom-nav tap (any tab,
    // including Today) exits the lesson back to the dashboard — progress is saved per step, so a
    // "Continue" resumes exactly where it left off.
    if (inLesson) {
        // System back leaves the player (progress is persisted per step), not the app.
        androidx.activity.compose.BackHandler { onInLessonChange(false) }
        SessionPlayer(
            container = container,
            lang = lang,
            day = day,
            totalDays = plan.days.size,
            onNavigate = onNavigate,
            onExit = { onInLessonChange(false) }
        )
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
    if (progress == null || rawCompleted == null || rawTargetChecks == null) {
        Column(Modifier.fillMaxSize()) {}
        return
    }

    // Generous, even rhythm so the three blocks read as distinct bands — streak up top, the
    // lesson card mid, the journey anchoring the bottom — instead of clustering under the hero.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Hero: streak + daily goal ring + ONE unmissable next action. A quiet bordered surface
        // rather than a loud coloured banner — the streak is a calm signal, not a trophy.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Grey until today's lesson banks the streak, then lit — and its
                        // colors escalate with streak length (7/30/100 tiers).
                        com.corlang.app.ui.components.StreakFlame(
                            streak = streak,
                            lit = completedToday > 0,
                            size = 20.dp
                        )
                        Text(
                            buildString {
                                append(
                                    if (streak > 0) " $streak-day streak"
                                    else " Start your streak today"
                                )
                                if (freezes > 0) append("  ·  $freezes freeze${if (freezes > 1) "s" else ""}")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Only speak up when there's something worth saying; no filler line.
                    val heroSubtitle = when {
                        completedToday > 0 -> "Today's goal is done ✓. Anything more is bonus depth."
                        dueNow > 0 -> "Starts with your $dueNow due words."
                        else -> ""
                    }
                    if (heroSubtitle.isNotBlank()) {
                        Text(
                            heroSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    // The streak hero only ever offers REVIEW: with the day done and words due,
                    // one button; day done and nothing due, none (new words only enter through
                    // lessons). Starting/continuing a lesson lives on the lesson card below —
                    // it's a lesson action, not a streak action.
                    if (completedToday > 0 && dueNow > 0) {
                        Button(
                            onClick = { onNavigate(Dest.WORDS.route) },
                            modifier = Modifier.padding(top = 10.dp)
                        ) { Text("Review $dueNow words →") }
                    }
                }
                GoalRing(
                    progress = ringProgress,
                    label = if (ringProgress >= 1f) "✓"
                            else "${(ringProgress * 100).toInt()}%",
                    size = 64.dp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        // Lesson header + objective, in the same calm bordered card as the streak hero, so the
        // screen reads as two matching cards sitting above the open journey path.
        //
        // The card cross-fades between days while the hero above and the journey below stay
        // put — browsing the journey changes only the thing the tap is about. Each animated
        // instance collects ITS OWN day's step checks (keyed per day), so the outgoing card
        // fades out with the old day's label and the incoming one fades in with the new day's
        // — no shared state to flash the wrong ticks, and no full-screen load gate needed.
        androidx.compose.animation.AnimatedContent(
            targetState = day,
            transitionSpec = {
                // The explicit SizeTransform is the half that keeps the JOURNEY smooth: two
                // days' cards differ in height (objective length, locked banner vs button), and
                // without an animated size the container snaps to the new height, jolting
                // everything below. With it, the card glides and the journey rides along.
                ContentTransform(
                    targetContentEnter = androidx.compose.animation.fadeIn(tween(220)),
                    initialContentExit = androidx.compose.animation.fadeOut(tween(120)),
                    sizeTransform = SizeTransform(clip = false) { _, _ -> tween(250) }
                )
            },
            label = "lessonCard"
        ) { d ->
        val cardChecks by androidx.compose.runtime.key(d.day) {
            container.progress.dayTaskChecks(lang, d.day).collectAsState(initial = null)
        }
        val doneIds = cardChecks.orEmpty().map { it.itemId }.toSet()
        val cardSteps = remember(lang, d.day) {
            buildSessionSteps(d, container.content.meta(lang).name)
        }
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
        val dDone = completed.contains(d.day)
        val dLocked = lockedFor(d)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${d.phase} · Week ${d.week} · ${d.level}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    d.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                SectionTitle("In this lesson you will")
                Text(d.objective, style = com.corlang.app.ui.theme.CorlangType.reading)

                // The lesson action lives HERE, with the lesson it acts on — never on the
                // streak hero. Days ahead of the one you're up to stay locked.
                if (d.day > targetDay) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            "Locked — finish Day $targetDay first. No skipping ahead.",
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
                            if (dLocked) onOpenPaywall(d.level) else onInLessonChange(true)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            when {
                                dLocked -> "🔒 Unlock ${d.level} to continue"
                                dDone -> "Revisit Day ${d.day} ✓"
                                lessonStarted -> "Continue Day ${d.day} ($stepsDone/${actionSteps.size} steps)"
                                d.day == targetDay -> "Start Day ${d.day} →"
                                else -> "Open Day ${d.day} →"
                            }
                        )
                    }
                }
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
            examLevelIds = examLevelIds,
            onOpenExam = { onNavigate(Dest.PRACTICE.route) },
            // Tapping the CURRENT day's stone is not "browsing away" — the dashboard keeps
            // following the plan.
            onPickDay = { d -> viewedDay = d; userBrowsed = d != targetDay }
        )

        Spacer(Modifier.height(8.dp))
    }
}
