package com.corlang.app

import com.corlang.app.billing.BillingManager
import com.corlang.app.billing.PremiumManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paywall boundary, pinned.
 *
 * These are pure-function tests on purpose. The gate itself is two lines, but it sits at the one
 * place where a mistake is silently expensive in both directions: too tight and a paying learner
 * is locked out of what they bought, too loose and the course is given away. Neither shows up in
 * a build log.
 */
class PaywallGateTest {

    // ---- The free window is absolute, not relative to the learner ----

    /**
     * The regression this suite exists for. Placement drops an advanced learner deep into the
     * course, and `TodayScreen` takes `maxOf(currentDay, lastCompleted + 1)` as their day. A
     * window counted from that start would hand out 15 free lessons of a paid level; counted
     * from lesson 1, the very first lesson they see is already behind the paywall.
     */
    @Test
    fun `placement deep in the course does not carry the free window with it`() {
        val placedAt = 150
        (placedAt until placedAt + 15).forEach { day ->
            assertTrue(
                "lesson $day is inside a paid level and must stay locked after placement",
                PremiumManager.dayLocked("hr", "A2", day, freeLessons = 16, unlocked = emptySet())
            )
        }
    }

    @Test
    fun `the window ends exactly on its last free lesson`() {
        assertFalse(PremiumManager.dayLocked("hr", "A0", 16, 16, emptySet()))
        assertTrue(PremiumManager.dayLocked("hr", "A1", 17, 16, emptySet()))
    }

    @Test
    fun `a course whose window falls inside a level still sells that level's remainder`() {
        // Portuguese: no A0, so the cut lands mid-A1 and lessons 16..45 are the A1 product.
        assertFalse(PremiumManager.dayLocked("pt", "A1", 15, 15, emptySet()))
        assertTrue(PremiumManager.dayLocked("pt", "A1", 16, 15, emptySet()))
        assertFalse(
            PremiumManager.dayLocked("pt", "A1", 16, 15, setOf(PremiumManager.key("pt", "A1")))
        )
    }

    // ---- Unlocks do not cross languages ----

    @Test
    fun `buying a level in one course does not unlock it in another`() {
        val boughtCroatianA2 = setOf(PremiumManager.key("hr", "A2"))
        assertFalse(PremiumManager.dayLocked("hr", "A2", 200, 16, boughtCroatianA2))
        assertTrue(
            "Portuguese A2 must stay locked: unlocks are per language",
            PremiumManager.dayLocked("pt", "A2", 200, 15, boughtCroatianA2)
        )
    }

    // ---- Product ids round-trip ----

    @Test
    fun `product ids parse back to the language and level that made them`() {
        listOf("hr" to "A1", "hr" to "A2", "pt" to "B1").forEach { (lang, level) ->
            val id = BillingManager.levelProduct(lang, level)
            assertEquals("unlock_${lang}_${level.lowercase()}", id)
            val (parsedLang, parsedLevel) = BillingManager.parseUnlock(id)!!
            assertEquals(lang, parsedLang)
            assertEquals(level, parsedLevel.uppercase())
        }
        val bundle = BillingManager.bundleProduct("pt")
        assertEquals("unlock_pt_all", bundle)
        assertEquals("pt" to BillingManager.ALL, BillingManager.parseUnlock(bundle))
    }

    /**
     * Anything that is not one of ours must parse to null rather than to a guess. The
     * subscription id shares no shape with the unlocks, but a future product might, and
     * "grant whatever the middle segment says" would be an entitlement bug.
     */
    @Test
    fun `foreign product ids grant nothing`() {
        listOf(
            BillingManager.SUB_PREMIUM,
            "unlock_all",            // the old global bundle, retired in v0.49.0
            "unlock_a2",             // the old global level product
            "unlock_hr_a2_extra",
            "",
        ).forEach { assertNull("'$it' must not parse as an unlock", BillingManager.parseUnlock(it)) }
    }
}
