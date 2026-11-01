package com.mahood.family.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahood.family.core.FamilyModule
import com.mahood.family.core.ModuleContext
import com.mahood.family.core.ui.HomeCardFrame
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Family calendar on top of the phone's synced Google calendars. */
class CalendarModule : FamilyModule {
    override val id = "calendar"
    override val title = "Calendar"
    override val description = "Shared Google Calendar"
    override val icon: ImageVector = Icons.Filled.CalendarMonth

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFmt)

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Loads calendars and events off the main thread, refreshing every minute and when [refresh] changes. */
    @Composable
    private fun rememberCalendarData(repo: CalendarRepo, from: LocalDate, days: Int, refresh: Int): Triple<List<CalendarRepo.Cal>, Set<Long>, List<CalendarRepo.Event>> {
        var data by remember { mutableStateOf(Triple(emptyList<CalendarRepo.Cal>(), emptySet<Long>(), emptyList<CalendarRepo.Event>())) }
        LaunchedEffect(from, days, refresh) {
            while (true) {
                data = withContext(Dispatchers.IO) {
                    val cals = repo.calendars()
                    val shown = repo.shownIds(cals)
                    Triple(cals, shown, repo.events(from, days, shown))
                }
                delay(60_000)
            }
        }
        return data
    }

    @Composable
    private fun PermissionGate(repo: CalendarRepo, content: @Composable (onChange: () -> Unit) -> Unit) {
        var granted by remember { mutableStateOf(repo.hasPermission()) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = repo.hasPermission()
        }
        if (granted) {
            content { granted = repo.hasPermission() }
        } else {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Show your Google calendars", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The app reads the calendars already synced to this phone, including the shared family calendar.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = {
                    launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }) { Text("Allow calendar access") }
            }
        }
    }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val repo = remember { CalendarRepo(ctx.appContext, ctx.store) }
        HomeCardFrame(icon, "Today", "Calendar ›", open) {
            if (!repo.hasPermission()) {
                Text("Tap to connect your family calendar", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@HomeCardFrame
            }
            val today = LocalDate.now()
            val (_, _, events) = rememberCalendarData(repo, today, 1, 0)
            val upcoming = events.filter { it.allDay || it.end > System.currentTimeMillis() }
            if (upcoming.isEmpty()) {
                Text("Nothing else on today", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            upcoming.take(3).forEach { e ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (e.allDay) "All day" else time(e.begin), Modifier.width(56.dp), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                        if (!e.location.isNullOrBlank()) Text(e.location, fontSize = 12.5.sp, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (upcoming.size > 3) Text("+${upcoming.size - 3} more", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Screen(ctx: ModuleContext) {
        val repo = remember { CalendarRepo(ctx.appContext, ctx.store) }
        Box(Modifier.fillMaxSize()) {
            PermissionGate(repo) { _ ->
                var refresh by remember { mutableIntStateOf(0) }
                var selected by remember { mutableStateOf(LocalDate.now()) }
                var showAdd by remember { mutableStateOf(false) }
                var showPick by remember { mutableStateOf(false) }
                val weekStart = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val (cals, shown, weekEvents) = rememberCalendarData(repo, weekStart, 7, refresh)
                val (_, _, agenda) = rememberCalendarData(repo, selected, 14, refresh)
                val calById = cals.associateBy { it.id }

                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    ScreenTitle(
                        selected.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        repo.familyCalendar(cals)?.let { "New events go to ${it.name}" } ?: "No writable calendar found",
                    ) {
                        IconButton(onClick = { showPick = true }) { Icon(Icons.Filled.Tune, "Choose calendars") }
                    }

                    // Week strip
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = selected.minusWeeks(1) }, Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week")
                        }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (i in 0..6) {
                                val day = weekStart.plusDays(i.toLong())
                                val isSel = day == selected
                                val dots = weekEvents.filter { dateOf(it.begin) == day || (it.allDay && !day.isBefore(dateOf(it.begin)) && day.isBefore(dateOf(it.end))) }
                                    .map { it.color }.distinct().take(3)
                                Column(
                                    Modifier.clip(RoundedCornerShape(18.dp))
                                        .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { selected = day }
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 12.sp, color = fg.copy(alpha = 0.75f))
                                    Text("${day.dayOfMonth}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg,
                                        modifier = Modifier.padding(top = 2.dp))
                                    Row(Modifier.height(9.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        dots.forEach { c -> Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSel) fg else Color(c))) }
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { selected = selected.plusWeeks(1) }, Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week")
                        }
                    }

                    // Legend of shown calendars
                    LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(cals.filter { it.id in shown }, key = { it.id }) { c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(c.color)))
                                Spacer(Modifier.width(5.dp))
                                Text(c.name, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    LazyColumn(Modifier.fillMaxSize()) {
                        val byDay = agenda.groupBy { dateOf(it.begin).let { d -> if (d.isBefore(selected)) selected else d } }.toSortedMap()
                        if (byDay.isEmpty()) item {
                            Text("Nothing in the next two weeks.", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        for ((day, events) in byDay) {
                            item(key = "d-$day") {
                                SectionLabel(
                                    when (day) {
                                        LocalDate.now() -> "TODAY"
                                        LocalDate.now().plusDays(1) -> "TOMORROW"
                                        else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(events, key = { "e-${it.eventId}-${it.begin}" }) { e ->
                                Surface(
                                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ) {
                                    Row(Modifier.height(64.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.width(5.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Color(e.color)))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                                            val whenText = if (e.allDay) "All day" else "${time(e.begin)} – ${time(e.end)}"
                                            val where = e.location?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                                            Text(whenText + where, fontSize = 12.5.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(calById[e.calendarId]?.name ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }

                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Event") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                )

                if (showAdd) AddEventDialog(repo, cals, selected, onDone = { showAdd = false; refresh++ })
                if (showPick) PickCalendarsDialog(repo, cals, shown, onDone = { showPick = false; refresh++ })
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AddEventDialog(repo: CalendarRepo, cals: List<CalendarRepo.Cal>, initialDate: LocalDate, onDone: () -> Unit) {
        val writable = cals.filter { it.writable }
        var title by remember { mutableStateOf("") }
        var location by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(initialDate) }
        var allDay by remember { mutableStateOf(false) }
        var start by remember { mutableIntStateOf(9 * 60) }
        var end by remember { mutableIntStateOf(10 * 60) }
        var calId by remember { mutableStateOf(repo.familyCalendar(cals)?.id) }
        var pickDate by remember { mutableStateOf(false) }
        var pickTime by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("New event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("What") }, singleLine = true)
                    OutlinedTextField(location, { location = it }, label = { Text("Where (optional)") }, singleLine = true)
                    OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("All day", Modifier.weight(1f))
                        Switch(allDay, { allDay = it })
                    }
                    if (!allDay) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickTime = "start" }, modifier = Modifier.weight(1f)) { Text("From ${hm(start)}") }
                        OutlinedButton(onClick = { pickTime = "end" }, modifier = Modifier.weight(1f)) { Text("To ${hm(end)}") }
                    }
                    if (writable.size > 1) {
                        Text("Calendar", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(writable, key = { it.id }) { c ->
                                FilterChip(selected = c.id == calId, onClick = { calId = c.id }, label = { Text(c.name) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && calId != null, onClick = {
                    val id = calId ?: return@TextButton
                    try {
                        repo.addEvent(id, title.trim(), date, allDay, start, end, location.trim())
                    } catch (e: SecurityException) {
                        android.widget.Toast.makeText(context, "Calendar permission needed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDone()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )

        if (pickDate) {
            val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { pickDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        pickDate = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
            ) { DatePicker(state) }
        }

        pickTime?.let { which ->
            val initial = if (which == "start") start else end
            val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
            AlertDialog(
                onDismissRequest = { pickTime = null },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        val m = state.hour * 60 + state.minute
                        if (which == "start") {
                            val length = (end - start).coerceAtLeast(30)
                            start = m
                            end = (m + length).coerceAtMost(23 * 60 + 59)
                        } else end = m
                        pickTime = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Cancel") } },
            )
        }
    }

    @Composable
    private fun PickCalendarsDialog(repo: CalendarRepo, cals: List<CalendarRepo.Cal>, shown: Set<Long>, onDone: () -> Unit) {
        var chosen by remember { mutableStateOf(shown) }
        var family by remember { mutableStateOf(repo.familyCalendar(cals)?.id) }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Calendars on this phone") },
            text = {
                LazyColumn {
                    item { Text("Show", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp)) }
                    items(cals, key = { "s${it.id}" }) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            chosen = if (c.id in chosen) chosen - c.id else chosen + c.id
                        }) {
                            Checkbox(c.id in chosen, { chosen = if (it) chosen + c.id else chosen - c.id })
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.color)))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(c.name, fontSize = 14.sp)
                                Text(c.account, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Text("Add new events to", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                    items(cals.filter { it.writable }, key = { "w${it.id}" }) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { family = c.id }) {
                            RadioButton(family == c.id, { family = c.id })
                            Text(c.name, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.setShown(chosen)
                    family?.let(repo::setFamilyCalendar)
                    onDone()
                }) { Text("Done") }
            },
        )
    }
}
