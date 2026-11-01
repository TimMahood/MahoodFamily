package com.mahood.family.core

import androidx.compose.ui.graphics.Color
import com.mahood.family.core.data.FamilyStore
import com.mahood.family.core.data.Record

data class Member(val email: String, val name: String, val colorHex: String) {
    val color: Color get() = parseColor(colorHex)
    val initial: String get() = name.trim().take(1).uppercase().ifEmpty { "?" }

    companion object {
        const val COLLECTION = "members"

        val palette = listOf("#3D7BE0", "#E8684F", "#8A63D2", "#D99A1E", "#2F9E8F", "#D6457A", "#5C8A2E", "#7A6A58")

        fun from(r: Record) = Member(r.id, r.string("name") ?: r.id, r.string("color") ?: palette[0])

        fun parseColor(hex: String): Color = try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: IllegalArgumentException) {
            Color(0xFF2F6A4F)
        }

        fun all(store: FamilyStore): List<Member> = store.query(COLLECTION).map(::from).sortedBy { it.name.lowercase() }
    }
}
