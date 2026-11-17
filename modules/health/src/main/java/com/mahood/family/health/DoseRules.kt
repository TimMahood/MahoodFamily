package com.mahood.family.health

/** Works out when the next dose of a medicine is allowed, from the doses already given. */
object DoseRules {
    private const val DAY_MS = 24 * 3600_000L

    data class State(val givenLast24h: Int, val lastAt: Long?, val nextAllowedAt: Long?) {
        /** True when a dose now would be too soon or one too many for the last 24 hours. */
        fun tooSoon(now: Long) = nextAllowedAt != null && nextAllowedAt > now
    }

    /**
     * [doseTimes] are when doses were given. [gapHours] is the shortest time between
     * doses and [maxPer24h] the most doses in any 24 hours (0 = no limit).
     */
    fun state(doseTimes: List<Long>, gapHours: Double, maxPer24h: Int, now: Long): State {
        val recent = doseTimes.filter { it in (now - DAY_MS)..now }.sortedDescending()
        val last = doseTimes.filter { it <= now }.maxOrNull()
        var next = last?.let { it + (gapHours * 3600_000L).toLong() }
        if (maxPer24h > 0 && recent.size >= maxPer24h) {
            // Allowed again once the oldest dose that keeps us at the limit is 24 h old.
            val freesUp = recent[maxPer24h - 1] + DAY_MS
            next = maxOf(next ?: 0L, freesUp)
        }
        return State(recent.size, last, next?.takeIf { it > now })
    }
}
