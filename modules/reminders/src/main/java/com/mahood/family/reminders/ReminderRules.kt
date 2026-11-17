package com.mahood.family.reminders

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure date logic for repeating reminders (no Android), so it can be unit tested. */
object ReminderRules {
    val repeats = listOf("once", "daily", "weekly", "fortnightly", "monthly", "yearly")

    /** The [n]th occurrence (0 = the start date). */
    fun occurrence(start: LocalDate, repeat: String, n: Long): LocalDate = when (repeat) {
        "daily" -> start.plusDays(n)
        "weekly" -> start.plusWeeks(n)
        "fortnightly" -> start.plusWeeks(2 * n)
        // Counted from the start each time, so the 31st stays the 31st (or the month's last day).
        "monthly" -> start.plusMonths(n)
        "yearly" -> start.plusYears(n)
        else -> start
    }

    /** First occurrence on or after [from], or null if a one-off is already past. */
    fun firstOnOrAfter(start: LocalDate, repeat: String, from: LocalDate): LocalDate? {
        if (!start.isBefore(from)) return start
        if (repeat == "once" || repeat !in repeats) return null
        // Jump close, then step: cheap even for daily reminders started years ago.
        val approxDays = java.time.temporal.ChronoUnit.DAYS.between(start, from)
        var n = when (repeat) {
            "daily" -> approxDays
            "weekly" -> approxDays / 7
            "fortnightly" -> approxDays / 14
            "monthly" -> approxDays / 31
            else -> approxDays / 366
        }.coerceAtLeast(0)
        while (true) {
            val d = occurrence(start, repeat, n)
            if (!d.isBefore(from)) return d
            n++
        }
    }

    /**
     * The occurrence that still needs doing: the first one after [doneThrough]
     * (the last occurrence someone ticked off). Null when a one-off is done.
     */
    fun current(start: LocalDate, repeat: String, doneThrough: LocalDate?): LocalDate? =
        if (doneThrough == null) start else firstOnOrAfter(start, repeat, doneThrough.plusDays(1))

    /** When to alert for the next occurrence (not yet done) strictly after [after]. */
    fun nextAlertAfter(start: LocalDate, repeat: String, minutes: Int, doneThrough: LocalDate?, after: LocalDateTime): LocalDateTime? {
        val time = LocalTime.of(minutes / 60, minutes % 60)
        var d = current(start, repeat, doneThrough) ?: return null
        if (d.atTime(time).isAfter(after)) return d.atTime(time)
        d = firstOnOrAfter(start, repeat, maxOf(d.plusDays(1), after.toLocalDate())) ?: return null
        if (!d.atTime(time).isAfter(after)) d = firstOnOrAfter(start, repeat, d.plusDays(1)) ?: return null
        return d.atTime(time)
    }
}
