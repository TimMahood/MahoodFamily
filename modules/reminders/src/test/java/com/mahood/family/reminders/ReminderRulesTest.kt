package com.mahood.family.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderRulesTest {
    private val mon = LocalDate.of(2026, 11, 16) // a Monday

    @Test
    fun weeklyBinsComeRoundEveryMonday() {
        assertEquals(LocalDate.of(2026, 11, 23), ReminderRules.firstOnOrAfter(mon, "weekly", LocalDate.of(2026, 11, 17)))
        assertEquals(mon, ReminderRules.firstOnOrAfter(mon, "weekly", mon))
    }

    @Test
    fun fortnightlyRecycling() {
        assertEquals(LocalDate.of(2026, 11, 30), ReminderRules.firstOnOrAfter(mon, "fortnightly", LocalDate.of(2026, 11, 17)))
    }

    @Test
    fun monthlyKeepsTheDayOfMonth() {
        val start = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), ReminderRules.firstOnOrAfter(start, "monthly", LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), ReminderRules.firstOnOrAfter(start, "monthly", LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun doneMovesToNextOccurrence() {
        assertEquals(mon, ReminderRules.current(mon, "weekly", null))
        assertEquals(LocalDate.of(2026, 11, 23), ReminderRules.current(mon, "weekly", mon))
        assertNull(ReminderRules.current(mon, "once", mon))
    }

    @Test
    fun alerts() {
        val m = 19 * 60
        // Before 7pm on bin day: tonight.
        assertEquals(mon.atTime(19, 0), ReminderRules.nextAlertAfter(mon, "weekly", m, null, mon.atTime(8, 0)))
        // Already alerted tonight but not done: next week.
        assertEquals(mon.plusWeeks(1).atTime(19, 0), ReminderRules.nextAlertAfter(mon, "weekly", m, null, mon.atTime(19, 0)))
        // Done today: next week.
        assertEquals(mon.plusWeeks(1).atTime(19, 0), ReminderRules.nextAlertAfter(mon, "weekly", m, mon, mon.atTime(8, 0)))
        // One-off already alerted.
        assertNull(ReminderRules.nextAlertAfter(mon, "once", m, null, LocalDateTime.of(2026, 11, 20, 9, 0)))
    }
}
