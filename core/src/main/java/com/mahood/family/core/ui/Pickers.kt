package com.mahood.family.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahood.family.core.Member
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A row of chips to pick one person (grown-up or child). */
@Composable
fun PersonChips(
    people: List<Member>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: String? = null,
) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowNone != null) item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(allowNone) })
        }
        items(people, key = { it.email }) { p ->
            FilterChip(
                selected = p.email == selected,
                onClick = { onSelect(p.email) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p, 20.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(p.name)
                    }
                },
            )
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(vertical = 12.dp, horizontal = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDateDialog(initial: LocalDate, onPick: (LocalDate?) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { onPick(null) },
        confirmButton = {
            TextButton(onClick = {
                onPick(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("Cancel") } },
    ) { DatePicker(state) }
}

/** Pick a time of day; returns minutes after midnight, or null if cancelled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickTimeDialog(initialMinutes: Int, onPick: (Int?) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = { onPick(null) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("Cancel") } },
    )
}

private val hmFmt = DateTimeFormatter.ofPattern("HH:mm")

fun clockTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(hmFmt)

fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

fun startOfDay(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Millis for [date] at [minutes] after midnight, local time. */
fun atTime(date: LocalDate, minutes: Int): Long =
    LocalDateTime.of(date, java.time.LocalTime.of(minutes / 60, minutes % 60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun minutesOfDay(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }

/** "45 min", "2 h 10 min". */
fun durationText(millis: Long): String {
    val mins = (millis / 60_000).coerceAtLeast(0)
    return when {
        mins < 60 -> "$mins min"
        mins % 60 == 0L -> "${mins / 60} h"
        else -> "${mins / 60} h ${mins % 60} min"
    }
}

/** "Today", "Yesterday", "Tomorrow" or "Tue 18 Nov". */
fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}

/** The current time, refreshed every [periodMs] so "asleep for 35 min" keeps counting. */
@Composable
fun rememberNow(periodMs: Long = 30_000): Long {
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(periodMs) {
        while (true) {
            kotlinx.coroutines.delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}
