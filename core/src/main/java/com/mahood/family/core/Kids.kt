package com.mahood.family.core

import com.mahood.family.core.data.FamilyStore
import com.mahood.family.core.data.Record
import java.time.LocalDate
import java.time.Period

/**
 * Children in the family. They don't have phones, but modules (chores,
 * school bags, milestones, health) record things about them.
 * Collection "kids" {name, born (yyyy-MM-dd), color}.
 */
data class Kid(val id: String, val name: String, val born: LocalDate?, val colorHex: String) {
    /** So kids can be shown with the same avatar as grown-ups. */
    fun asMember(): Member = Member(id, name, colorHex)

    fun ageText(today: LocalDate = LocalDate.now()): String = born?.let { ageText(it, today) } ?: ""

    companion object {
        const val COLLECTION = "kids"

        fun from(r: Record) = Kid(
            r.id,
            r.string("name") ?: "Child",
            r.string("born")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            r.string("color") ?: Member.palette[2],
        )

        fun all(store: FamilyStore): List<Kid> = store.query(COLLECTION).map(::from).sortedWith(
            compareBy<Kid> { it.born ?: LocalDate.MAX }.thenBy { it.name.lowercase() },
        )

        /** "9 weeks", "14 months", "5 years". */
        fun ageText(born: LocalDate, today: LocalDate = LocalDate.now()): String {
            if (today.isBefore(born)) return "not born yet"
            val p = Period.between(born, today)
            val months = p.years * 12 + p.months
            return when {
                months < 2 -> "${java.time.temporal.ChronoUnit.WEEKS.between(born, today)} weeks"
                months < 24 -> "$months months"
                else -> "${p.years} years"
            }
        }
    }
}
