package com.mahood.family.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahood.family.core.FamilyModule
import com.mahood.family.core.Member
import com.mahood.family.core.ModuleContext
import com.mahood.family.core.data.Op
import com.mahood.family.core.data.Record
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.EmptyHint
import com.mahood.family.core.ui.HomeCardFrame
import com.mahood.family.core.ui.PersonChips
import com.mahood.family.core.ui.PickDateDialog
import com.mahood.family.core.ui.PickTimeDialog
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import com.mahood.family.core.ui.dayLabel
import com.mahood.family.core.ui.relativeTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Shared reminders (bins, school dinner money, council tax...) with a phone
 * notification at the set time. "Done" on either phone ticks it off for both.
 * Collection "reminders" {title, note, who (email, empty = both), repeat, start (yyyy-MM-dd),
 * time (minutes after midnight), doneThrough (last occurrence ticked off), by}.
 */
class RemindersModule : FamilyModule {
    override val id = "reminders"
    override val title = "Reminders"
    override val description = "Bins, dinner money, bills: a nudge on both phones"
    override val icon: ImageVector = Icons.Filled.Alarm

    private val R = ReminderAlarms.COLLECTION

    private data class Preset(val title: String, val repeat: String, val day: DayOfWeek?, val minutes: Int)

    private val presets = listOf(
        Preset("Bins out", "weekly", DayOfWeek.MONDAY, 19 * 60),
        Preset("Recycling out", "fortnightly", DayOfWeek.MONDAY, 19 * 60),
        Preset("Top up school dinner money", "weekly", DayOfWeek.SUNDAY, 19 * 60),
        Preset("Pay nursery", "monthly", null, 9 * 60),
        Preset("Swimming lesson", "weekly", DayOfWeek.SATURDAY, 8 * 60),
        Preset("Order prescription", "monthly", null, 9 * 60),
        Preset("Council tax", "monthly", null, 9 * 60),
        Preset("Car MOT", "yearly", null, 9 * 60),
        Preset("Water the plants", "weekly", DayOfWeek.WEDNESDAY, 18 * 60),
        Preset("Birthday card to post", "yearly", null, 9 * 60),
    )

    private val repeatLabels = mapOf(
        "once" to "Once", "daily" to "Every day", "weekly" to "Every week", "fortnightly" to "Every 2 weeks",
        "monthly" to "Every month", "yearly" to "Every year",
    )

    private fun current(r: Record): LocalDate? {
        val s = ReminderAlarms.start(r) ?: return null
        return ReminderRules.current(s, ReminderAlarms.repeat(r), ReminderAlarms.doneThrough(r))
    }

    private fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun whenText(r: Record): String {
        val s = ReminderAlarms.start(r)
        val time = hm(ReminderAlarms.minutes(r))
        return when (val rep = ReminderAlarms.repeat(r)) {
            "weekly", "fortnightly" -> "${repeatLabels[rep]} on ${s?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: ""} at $time"
            "monthly" -> "Every month on the ${s?.dayOfMonth?.let(::ordinal) ?: ""} at $time"
            "yearly" -> "Every year on ${s?.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM")) ?: ""} at $time"
            "daily" -> "Every day at $time"
            else -> "${s?.let { dayLabel(it) } ?: ""} at $time"
        }
    }

    private fun ordinal(n: Int) = "$n" + if (n in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

    private fun done(ctx: ModuleContext, r: Record) {
        val c = current(r) ?: return
        ctx.put(R, r.id, mapOf("doneThrough" to c.toString(), "doneBy" to ctx.me?.email, "doneAt" to System.currentTimeMillis()))
        ReminderAlarms.schedule(ctx.appContext)
    }

    override fun onDataChanged(ctx: ModuleContext) = ReminderAlarms.schedule(ctx.appContext)

    @Composable
    private fun ReminderRow(ctx: ModuleContext, r: Record, members: List<Member>, onEdit: () -> Unit, compact: Boolean = false) {
        val c = current(r)
        val today = LocalDate.now()
        val due = c != null && !c.isAfter(today)
        val who = members.firstOrNull { it.email == r.string("who") }
        Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.string("title") ?: "", fontSize = 15.sp, fontWeight = if (due) FontWeight.SemiBold else FontWeight.Normal)
                val dueText = when {
                    c == null -> "Done"
                    c.isBefore(today) -> "Overdue since ${dayLabel(c)}"
                    else -> "${dayLabel(c)} at ${hm(ReminderAlarms.minutes(r))}"
                }
                val doer = members.firstOrNull { it.email == r.string("doneBy") }?.name
                val lastDone = if (compact || r.string("doneThrough") == null) "" else
                    " · last done" + (doer?.let { " by $it" } ?: "") + (r.long("doneAt")?.let { " ${relativeTime(it)}" } ?: "")
                Text(
                    (if (compact) dueText else "$dueText · ${whenText(r)}") + " · ${who?.name ?: "Both"}" + lastDone,
                    fontSize = 12.5.sp,
                    color = if (c != null && c.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (who != null) {
                Avatar(who, 22.dp)
                Spacer(Modifier.width(8.dp))
            }
            if (due) FilledTonalButton(onClick = { done(ctx, r) }) { Text("Done") }
        }
    }

    private fun sorted(records: List<Record>) = records.sortedBy { current(it) ?: LocalDate.MAX }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val all = sorted(ctx.records(R))
        val members = ctx.members()
        val today = LocalDate.now()
        val soon = all.filter { c -> current(c)?.let { !it.isAfter(today.plusDays(2)) } == true }
        HomeCardFrame(icon, "Reminders", "All ›", open) {
            when {
                all.isEmpty() -> Text("Bins, dinner money, bills: set a reminder and both phones get a nudge.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                soon.isEmpty() -> Text("Nothing in the next couple of days. Next: ${all.first().string("title")}, " +
                    (current(all.first())?.let { dayLabel(it) } ?: ""), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> soon.take(4).forEach { r -> ReminderRow(ctx, r, members, onEdit = open, compact = true) }
            }
        }
    }

    @Composable
    override fun Screen(ctx: ModuleContext) {
        val all = sorted(ctx.records(R))
        val members = ctx.members()
        val today = LocalDate.now()
        var editing by remember { mutableStateOf<Record?>(null) }
        var adding by remember { mutableStateOf(false) }
        LaunchedEffect(all.size) { ReminderAlarms.schedule(ctx.appContext) }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                item { ScreenTitle("Reminders", "A notification on both phones. Done on one ticks it off for both.") }
                val due = all.filter { current(it)?.let { c -> !c.isAfter(today) } == true }
                val upcoming = all.filter { current(it)?.isAfter(today) == true }
                val finished = all.filter { current(it) == null }
                if (all.isEmpty()) item { EmptyHint("No reminders yet. Tap New reminder and pick bins, dinner money or anything else.") }
                if (due.isNotEmpty()) {
                    item { SectionLabel("DUE NOW") }
                    items(due, key = { "d-${it.id}" }) { r -> ReminderRow(ctx, r, members, onEdit = { editing = r }) }
                }
                if (upcoming.isNotEmpty()) {
                    item { SectionLabel("COMING UP", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(upcoming, key = { "u-${it.id}" }) { r -> ReminderRow(ctx, r, members, onEdit = { editing = r }) }
                }
                if (finished.isNotEmpty()) {
                    item { SectionLabel("DONE", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(finished, key = { "f-${it.id}" }) { r -> ReminderRow(ctx, r, members, onEdit = { editing = r }) }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New reminder") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }

        if (adding) EditDialog(ctx, null, members, onDone = { adding = false })
        editing?.let { r -> EditDialog(ctx, r, members, onDone = { editing = null }) }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun EditDialog(ctx: ModuleContext, r: Record?, members: List<Member>, onDone: () -> Unit) {
        var title by remember { mutableStateOf(r?.string("title") ?: "") }
        var note by remember { mutableStateOf(r?.string("note") ?: "") }
        var who by remember { mutableStateOf(r?.string("who")?.takeIf { it.isNotEmpty() }) }
        var repeat by remember { mutableStateOf(r?.let { ReminderAlarms.repeat(it) } ?: "weekly") }
        var start by remember { mutableStateOf(r?.let { current(it) ?: ReminderAlarms.start(it) } ?: LocalDate.now()) }
        var minutes by remember { mutableStateOf(r?.let { ReminderAlarms.minutes(it) } ?: (19 * 60)) }
        var pickDate by remember { mutableStateOf(false) }
        var pickTime by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(if (r == null) "New reminder" else "Edit reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("What") }, singleLine = true)
                    if (r == null) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presets) { p ->
                            SuggestionChip(onClick = {
                                title = p.title
                                repeat = p.repeat
                                minutes = p.minutes
                                p.day?.let { d -> start = LocalDate.now().with(TemporalAdjusters.nextOrSame(d)) }
                            }, label = { Text(p.title) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReminderRules.repeats.forEach { rep ->
                            FilterChip(selected = rep == repeat, onClick = { repeat = rep }, label = { Text(repeatLabels[rep] ?: rep) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                            Text((if (repeat == "once") "" else "From ") + dayLabel(start))
                        }
                        OutlinedButton(onClick = { pickTime = true }) { Text(hm(minutes)) }
                    }
                    Text("Who gets the notification", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PersonChips(members, who, { who = it }, allowNone = "Both of us")
                    OutlinedTextField(note, { note = it }, label = { Text("Note (optional), e.g. ParentPay") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    val fields = mutableMapOf<String, Any?>(
                        "title" to title.trim(), "note" to note.trim(), "who" to (who ?: ""), "repeat" to repeat,
                        "time" to minutes.toLong(),
                    )
                    // A new start date restarts the cycle; keep the tick history otherwise.
                    if (r == null || start != current(r)) {
                        fields["start"] = start.toString()
                        fields["doneThrough"] = null
                    }
                    if (r == null) fields["by"] = ctx.me?.email
                    ctx.put(R, r?.id ?: ctx.store.newId(), fields)
                    ReminderAlarms.schedule(ctx.appContext)
                    onDone()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (r != null) TextButton(onClick = { ctx.delete(R, r.id); ReminderAlarms.schedule(ctx.appContext); onDone() }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDone) { Text("Cancel") }
                }
            },
        )
        if (pickDate) PickDateDialog(start) { d ->
            if (d != null) start = d
            pickDate = false
        }
        if (pickTime) PickTimeDialog(minutes) { m ->
            if (m != null) minutes = m
            pickTime = false
        }
    }

    override fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> {
        val members = Member.all(ctx.store)
        return remoteOps.filter { it.collection == R && it.field == "by" }
            .mapNotNull { op -> ctx.store.record(R, op.id) }
            .map { r -> "${members.firstOrNull { it.email == r.string("by") }?.name ?: "Someone"} added a reminder: ${r.string("title")}" }
    }
}
