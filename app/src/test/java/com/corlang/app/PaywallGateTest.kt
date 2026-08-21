package com.corlang.app

import com.corlang.app.billing.BillingManager
import com.corlang.app.billing.PremiumManager
import com.corlang.app.data.Fsrs
import com.corlang.app.data.WordsRepository
import com.corlang.app.data.model.LanguageMeta
import com.corlang.app.data.model.StudyDay
import com.corlang.app.data.model.StudyPlan
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The paywall boundary, pinned against the REAL shipped courses.
 *
 * These are pure-function tests over the actual plan and meta files rather than synthetic
 * ladders, because the interesting cases come from the shapes the courses really have: Croatian
 * owns an A0 that the free window covers exactly, Portuguese has no A0 at all and its window
 * falls inside A1. A synthetic fixture would agree with itself and prove nothing.
 *
 * The gate is a handful of lines, but it sits where a mistake is silently expensive in both
 * directions — too tight and a paying learner is locked out of what they bought, too loose and
 * the course is given away — and neither shows up in a build log.
 */
class PaywallGateTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    private val contentRoot: File by lazy {
        listOf("src/main/assets/content", "app/src/main/assets/content")
            .map { File(it) }
            .firstOrNull { it.isDirectory }
            ?: error("content assets directory not found from ${File(".").absolutePath}")
    }

    private fun meta(lang: String): LanguageMeta =
        json.decodeFromString(File(contentRoot, "$lang/meta.json").readText(Charsets.UTF_8))

    private fun days(lang: String): List<StudyDay> {
        val dir = File(contentRoot, "$lang/plan")
        val index = json.decodeFromString<List<String>>(
            File(dir, "_index.json").readText(Charsets.UTF_8)
        )
        return index.flatMap {
            json.decodeFromString<StudyPlan>(File(dir, it).readText(Charsets.UTF_8)).days
        }.sortedBy { it.day }
    }

    /** The levels a course charges for, in course order — exactly what AppContainer supplies. */
    private fun paidLevels(lang: String): List<String> {
        val free = meta(lang).freeLessons
        return days(lang).filter { it.day > free }.map { it.level }.distinct()
    }

    /** What one purchase of [level] leaves this learner holding, as entitlement keys. */
    private fun buy(lang: String, level: String): Set<String> =
        PremiumManager.levelsThrough(paidLevels(lang), level)
            .map { PremiumManager.key(lang, it) }.toSet()

    private fun locked(lang: String, day: Int, owned: Set<String>): Boolean {
        val d = days(lang).first { it.day == day }
        return PremiumManager.dayLocked(lang, d.level, d.day, meta(lang).freeLessons, owned)
    }

    private fun lastDayOf(lang: String, level: String) =
        days(lang).filter { it.level == level }.maxOf { it.day }

    private fun firstDayOf(lang: String, level: String) =
        days(lang).filter { it.level == level }.minOf { it.day }

    // ================= The free window =================

    @Test
    fun `a new learner gets exactly the free window and then meets the paywall`() {
        listOf("hr", "pt").forEach { lang ->
            val free = meta(lang).freeLessons
            (1..free).forEach {
                assertFalse("$lang lesson $it should be free", locked(lang, it, emptySet()))
            }
            assertTrue(
                "$lang lesson ${free + 1} should be the first paid lesson",
                locked(lang, free + 1, emptySet())
            )
        }
    }

    /**
     * In BOTH courses the free tier is exactly level A0, and nothing below a level's floor was
     * borrowed to make that true.
     *
     * Portuguese got its A0 by authoring ten lessons ON TOP of the course, not by relabelling
     * A1's first ten. The distinction is the whole point: `docs/language-standard.md` sets a
     * floor of 45 lessons for pt A1, and that floor is a claim about how much teaching reaching
     * A1 takes, not a bookkeeping total. Relabelling would leave A1 claiming to deliver A1 in 35.
     * The two assertions on A1 size below are what would catch someone "simplifying" it that way
     * later; `everyCourseMeetsTheWeightedLessonFloor` is the other half of that guard.
     *
     * The onramps are different sizes on purpose. Croatian's is 16 because Croatian cannot be
     * read until you know 30 letters and eight digraphs. Portuguese can be read on sight, so its
     * onramp teaches survival transactions instead and needs fewer lessons to do it.
     */
    @Test
    fun `in both courses the free tier is exactly level A0`() {
        listOf("hr" to 16, "pt" to 10).forEach { (lang, expected) ->
            assertEquals("$lang free window", expected, meta(lang).freeLessons)
            assertEquals(
                "$lang free window must land exactly on the end of A0",
                lastDayOf(lang, "A0"), meta(lang).freeLessons
            )
            assertEquals(
                "$lang A0 must start at lesson 1", 1, firstDayOf(lang, "A0")
            )
            assertEquals(
                "$lang sells A1 upward, never A0", listOf("A1", "A2", "B1"), paidLevels(lang)
            )
        }

        // The floors, restated where the free-tier change would break them. A0 was ADDED, so
        // every paid level kept every lesson it was authored with.
        assertEquals(45, days("pt").count { it.level == "A1" })
        assertEquals(70, days("pt").count { it.level == "A2" })
        assertEquals(125, days("pt").count { it.level == "B1" })
        assertEquals(61, days("hr").count { it.level == "A1" })
        assertEquals(250, days("pt").size)

        listOf("hr", "pt").forEach { lang ->
            val share = meta(lang).freeLessons.toDouble() / days(lang).size
            assertTrue(
                "$lang gives away ${"%.1f".format(share * 100)}% of its course, over the 6% " +
                    "the two courses are held to",
                share <= 0.06
            )
        }
    }

    /**
     * The regression this suite exists for. Placement drops an advanced learner deep into the
     * course, and `TodayScreen` takes `maxOf(currentDay, lastCompleted + 1)` as their day. A
     * window counted from that start would hand out free lessons of a paid level.
     */
    @Test
    fun `placement deep in the course does not carry the free window with it`() {
        val placedAt = firstDayOf("hr", "A2") + 20
        (placedAt until placedAt + meta("hr").freeLessons).forEach { day ->
            assertTrue(
                "hr lesson $day must stay locked after placement",
                locked("hr", day, emptySet())
            )
        }
    }

    // ================= Cumulative unlocks =================

    /**
     * The scenario asked for directly: land in A2, buy A2, and A1 comes with it. It has to,
     * because the deck introduces vocabulary in course order — an A2 learner's daily reviews
     * are full of A1 words whether or not they own the A1 lessons those words came from.
     */
    @Test
    fun `buying A2 unlocks A1 as well`() {
        listOf("hr", "pt").forEach { lang ->
            val owned = buy(lang, "A2")
            assertFalse(
                "$lang A1 must open when A2 is bought",
                locked(lang, lastDayOf(lang, "A1"), owned)
            )
            assertFalse(
                "$lang A2 must open when A2 is bought",
                locked(lang, lastDayOf(lang, "A2"), owned)
            )
            assertTrue(
                "$lang B1 must stay closed: it is above what was bought",
                locked(lang, firstDayOf(lang, "B1"), owned)
            )
        }
    }

    @Test
    fun `buying a level opens every lesson in it, including the ones already skipped past`() {
        // "Placed at A1 lesson 30, bought A1" — they paid for the level, so the half they were
        // placed over is theirs too, not just the half in front of them.
        listOf("hr", "pt").forEach { lang ->
            val owned = buy(lang, "A1")
            days(lang).filter { it.level == "A1" }.forEach {
                assertFalse("$lang A1 lesson ${it.day} must open", locked(lang, it.day, owned))
            }
        }
    }

    @Test
    fun `the top level's product is the whole course`() {
        listOf("hr", "pt").forEach { lang ->
            val top = paidLevels(lang).last()
            val owned = buy(lang, top)
            days(lang).forEach {
                assertFalse(
                    "$lang lesson ${it.day} must open with the top-level purchase",
                    locked(lang, it.day, owned)
                )
            }
        }
    }

    @Test
    fun `an unknown level grants only itself rather than inventing an order`() {
        assertEquals(setOf("C2"), PremiumManager.levelsThrough(listOf("A1", "A2", "B1"), "C2"))
        assertEquals(setOf("A1"), PremiumManager.levelsThrough(emptyList(), "A1"))
    }

    // ================= Unlocks do not cross languages =================

    @Test
    fun `buying a course in one language does not unlock the other`() {
        val boughtCroatian = buy("hr", "B1")
        assertFalse(locked("hr", days("hr").last().day, boughtCroatian))
        assertTrue(
            "Portuguese must stay locked: unlocks are per language",
            locked("pt", firstDayOf("pt", "A2"), boughtCroatian)
        )
    }

    // ================= Level assessments =================

    /**
     * `LevelJourney` treats "placement put you past this level" as clearing it, so its quiz,
     * readiness check and full mock exam unlock. That predates the paywall, and on its own it
     * made the free placement test a bypass: a learner placed at B1 was past A1 and A2, which
     * handed them both levels' assessments for nothing. The exam gate is the last lesson of the
     * level, because sitting the A1 exam means owning all of A1.
     */
    @Test
    fun `a level's exam stays locked until the level is paid for`() {
        listOf("hr", "pt").forEach { lang ->
            val free = meta(lang).freeLessons
            fun levelLocked(level: String, owned: Set<String>) = PremiumManager.dayLocked(
                lang, level, lastDayOf(lang, level), free, owned
            )
            assertTrue("$lang A1 exam must be locked while unpaid", levelLocked("A1", emptySet()))
            assertTrue("$lang A2 exam must be locked while unpaid", levelLocked("A2", emptySet()))
            assertFalse("$lang A1 exam opens with A1", levelLocked("A1", buy(lang, "A1")))
            assertTrue("$lang A2 exam still shut with only A1", levelLocked("A2", buy(lang, "A1")))
            assertFalse("$lang A2 exam opens with A2", levelLocked("A2", buy(lang, "A2")))
        }
        // Croatian's A0 is entirely inside the free window, so its exam is free — that is the
        // whole point of landing the window on a level boundary.
        assertFalse(
            "hr A0 exam must be free",
            PremiumManager.dayLocked("hr", "A0", lastDayOf("hr", "A0"), 16, emptySet())
        )
    }

    // ================= How far the course goes for this learner =================

    @Test
    fun `accessibleThroughDay walks the prefix and stops at the first locked lesson`() {
        listOf("hr", "pt").forEach { lang ->
            val d = days(lang)
            val free = meta(lang).freeLessons
            assertEquals(
                free,
                PremiumManager.accessibleThroughDay(d, lang, free, emptySet())
            )
            assertEquals(
                lastDayOf(lang, "A1"),
                PremiumManager.accessibleThroughDay(d, lang, free, buy(lang, "A1"))
            )
            assertEquals(
                d.last().day,
                PremiumManager.accessibleThroughDay(d, lang, free, buy(lang, paidLevels(lang).last()))
            )
        }
    }

    // ================= The placement review seed =================

    /**
     * Placement queues the 60 lessons before its own point into Review. At 10 new words a lesson
     * that is 600 deck words, the test is free and offered during onboarding — so without a
     * ceiling, answering well was a way to collect most of a paid course's vocabulary for
     * nothing. The seed may not reach past the last lesson the learner can open.
     */
    @Test
    fun `the placement seed cannot reach past what the learner has paid for`() {
        val lang = "hr"
        val free = meta(lang).freeLessons
        val placedAt = firstDayOf(lang, "B1") + 40
        val (from, until) = WordsRepository.prePlacementRange(placedAt)
        assertTrue("the seed window must be non-trivial to test", until - from > 500)

        fun ceilingFor(owned: Set<String>) =
            PremiumManager.accessibleThroughDay(days(lang), lang, free, owned) *
                Fsrs.NEW_WORDS_PER_DAY

        // Free: the accessible deck stops far below the seed window, so nothing may be queued.
        assertTrue(
            "a free learner placed at B1 must be seeded nothing",
            minOf(until, ceilingFor(emptySet())) <= from
        )
        // Bought only A1: still nowhere near a B1 placement's run-up.
        assertTrue(
            "an A1 buyer placed at B1 must still be seeded nothing",
            minOf(until, ceilingFor(buy(lang, "A1"))) <= from
        )
        // Bought the course: the full window, unclamped.
        assertTrue(
            "the whole-course buyer gets the real seed window",
            minOf(until, ceilingFor(buy(lang, paidLevels(lang).last()))) == until
        )
    }

    @Test
    fun `topping the seed up after a purchase leaves no unseeded gap`() {
        // `from` is measured BACK from the window's end, so a ceiling that slid the start as
        // well would leave a learner who was seeded once while free and again after buying with
        // an unseeded band in the middle. The clamp must therefore only ever lower `until`, and
        // successive seeds must be nested ranges sharing one start.
        val placedAt = 300
        val (from, until) = WordsRepository.prePlacementRange(placedAt)
        val ceilings = listOf(0, 200, 2_000, Int.MAX_VALUE)
        val bands = ceilings.map { from until minOf(until, it).coerceAtLeast(from) }

        bands.zipWithNext().forEach { (narrow, wide) ->
            assertEquals("every seed starts at the same deck index", from, narrow.first)
            assertTrue(
                "a later, wider seed must contain the earlier one",
                wide.first == narrow.first && wide.last >= narrow.last
            )
        }
        assertTrue("the widest band is the unclamped window", bands.last().last == until - 1)
    }

    // ================= Product ids =================

    @Test
    fun `product ids round-trip, and there is one per paid level`() {
        listOf("hr", "pt").forEach { lang ->
            paidLevels(lang).forEach { level ->
                val id = BillingManager.levelProduct(lang, level)
                assertEquals("unlock_${lang}_${level.lowercase()}", id)
                val (parsedLang, parsedLevel) = BillingManager.parseUnlock(id)!!
                assertEquals(lang, parsedLang)
                assertEquals(level, parsedLevel.uppercase())
            }
        }
    }

    @Test
    fun `no product is offered for a level with no lessons`() {
        // `levels.json` declares B2 and C1 for Croatian; the PLAN is the only thing that says
        // whether lessons exist. Selling `unlock_hr_b2` would have charged for nothing.
        listOf("hr", "pt").forEach { lang ->
            assertFalse(
                "$lang must not sell B2",
                paidLevels(lang).contains("B2")
            )
            paidLevels(lang).forEach { level ->
                assertTrue(
                    "$lang $level must have lessons behind it",
                    days(lang).any { it.level == level }
                )
            }
        }
    }

    /**
     * Anything that is not one of ours must parse to null rather than to a guess. The retired
     * global ids matter most: an install that still holds one must not be re-granted under the
     * new scheme, and "grant whatever the middle segment says" would be an entitlement bug.
     */
    @Test
    fun `foreign product ids do not parse as unlocks`() {
        listOf(
            BillingManager.SUB_PREMIUM,
            "unlock_all",            // the retired global bundle: two segments, not three
            "unlock_a2",             // the retired global level product
            "unlock_hr_a2_extra",
            "unlock",
            "",
        ).forEach { assertNull("'$it' must not parse as an unlock", BillingManager.parseUnlock(it)) }
    }

    /**
     * `unlock_hr_all` was a real product id one revision ago, and it still has the three-segment
     * shape the parser accepts — so it parses, and the safety has to come from the GRANT rather
     * than the parse. "all" is not a level of any course, so it expands to itself and unlocks no
     * lesson. This is the test that would catch someone "helpfully" making levelsThrough fall
     * back to the whole ladder for an unrecognised name.
     */
    @Test
    fun `the retired bundle id parses but unlocks nothing`() {
        val (lang, what) = BillingManager.parseUnlock("unlock_hr_all")!!
        assertEquals("hr", lang)
        val granted = PremiumManager.levelsThrough(paidLevels(lang), what.uppercase())
            .map { PremiumManager.key(lang, it) }.toSet()
        days("hr").filter { it.day > meta("hr").freeLessons }.forEach {
            assertTrue(
                "hr lesson ${it.day} must stay locked for a retired bundle id",
                locked("hr", it.day, granted)
            )
        }
    }
}
