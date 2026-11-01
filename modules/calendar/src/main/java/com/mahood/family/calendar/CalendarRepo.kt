package com.mahood.family.calendar

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.mahood.family.core.data.FamilyStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Reads and writes events through Android's calendar provider. Google accounts on
 * the phone already sync their calendars there, so the shared "family" Google
 * Calendar works offline and needs no API key. Which calendars to show is a
 * per-phone setting.
 */
class CalendarRepo(private val context: Context, private val store: FamilyStore) {
    data class Cal(val id: Long, val name: String, val account: String, val color: Int, val writable: Boolean)
    data class Event(
        val eventId: Long, val calendarId: Long, val title: String, val location: String?,
        val begin: Long, val end: Long, val allDay: Boolean, val color: Int,
    )

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<Cal> {
        if (!hasPermission()) return emptyList()
        val out = mutableListOf<Cal>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += Cal(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Calendar",
                    account = c.getString(2) ?: "",
                    color = c.getInt(3),
                    writable = c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }

    /** Calendars chosen to show on this phone; defaults to all of them. */
    fun shownIds(all: List<Cal>): Set<Long> =
        store.get("cal_shown")?.split(",")?.mapNotNull { it.toLongOrNull() }?.toSet() ?: all.map { it.id }.toSet()

    fun setShown(ids: Set<Long>) = store.set("cal_shown", ids.joinToString(","))

    /** The calendar new events go into: chosen, or one with "family" in its name, or the first writable one. */
    fun familyCalendar(all: List<Cal>): Cal? {
        val chosen = store.get("cal_family")?.toLongOrNull()
        return all.firstOrNull { it.id == chosen && it.writable }
            ?: all.firstOrNull { it.writable && it.name.contains("family", ignoreCase = true) }
            ?: all.firstOrNull { it.writable && it.account.contains("@") }
            ?: all.firstOrNull { it.writable }
    }

    fun setFamilyCalendar(id: Long) = store.set("cal_family", id.toString())

    fun events(from: LocalDate, days: Int, calendarIds: Set<Long>): List<Event> {
        if (!hasPermission()) return emptyList()
        val zone = ZoneId.systemDefault()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = from.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val out = mutableListOf<Event>()
        CalendarContract.Instances.query(context.contentResolver, projection, begin, end)?.use { c ->
            while (c.moveToNext()) {
                val calId = c.getLong(1)
                if (calId !in calendarIds) continue
                val allDay = c.getInt(6) == 1
                var b = c.getLong(4)
                var e = c.getLong(5)
                if (allDay) {
                    // All-day events are stored at UTC midnight; move them to local midnight.
                    b = utcToLocalMidnight(b, zone)
                    e = utcToLocalMidnight(e, zone)
                }
                out += Event(c.getLong(0), calId, c.getString(2) ?: "(No title)", c.getString(3), b, e, allDay, c.getInt(7))
            }
        }
        return out.sortedWith(compareBy<Event>({ it.begin }, { !it.allDay }))
    }

    private fun utcToLocalMidnight(millis: Long, zone: ZoneId): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun addEvent(calendarId: Long, title: String, date: LocalDate, allDay: Boolean, startMinutes: Int, endMinutes: Int, location: String?) {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            if (!location.isNullOrBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
            if (allDay) {
                val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, start + 24 * 3600_000L)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                val zone = ZoneId.systemDefault()
                val day = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMins = if (endMinutes <= startMinutes) startMinutes + 60 else endMinutes
                put(CalendarContract.Events.DTSTART, day + startMinutes * 60_000L)
                put(CalendarContract.Events.DTEND, day + endMins * 60_000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }
}
