package com.mahood.family.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseRulesTest {
    private val h = 3600_000L
    private val now = 100 * h

    @Test
    fun firstDoseIsFine() {
        val s = DoseRules.state(emptyList(), 4.0, 4, now)
        assertNull(s.nextAllowedAt)
        assertFalse(s.tooSoon(now))
    }

    @Test
    fun waitsForTheGap() {
        val s = DoseRules.state(listOf(now - 3 * h), 4.0, 4, now)
        assertEquals(now + h, s.nextAllowedAt)
        assertTrue(s.tooSoon(now))
        assertEquals(1, s.givenLast24h)
    }

    @Test
    fun gapPassed() {
        assertNull(DoseRules.state(listOf(now - 5 * h), 4.0, 4, now).nextAllowedAt)
    }

    @Test
    fun maxPerDayHoldsTheNextDose() {
        // Four doses 4 h apart, the last 5 h ago: the gap is fine but it's the 5th in 24 h.
        val doses = listOf(now - 17 * h, now - 13 * h, now - 9 * h, now - 5 * h)
        val s = DoseRules.state(doses, 4.0, 4, now)
        assertEquals(4, s.givenLast24h)
        assertEquals(now - 17 * h + 24 * h, s.nextAllowedAt)
    }

    @Test
    fun oldDosesDropOut() {
        val doses = listOf(now - 30 * h, now - 26 * h, now - 9 * h)
        val s = DoseRules.state(doses, 6.0, 3, now)
        assertEquals(1, s.givenLast24h)
        assertNull(s.nextAllowedAt)
    }
}
