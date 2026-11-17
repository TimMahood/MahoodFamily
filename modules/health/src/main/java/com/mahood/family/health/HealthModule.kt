package com.mahood.family.health

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahood.family.core.FamilyModule
import com.mahood.family.core.Kid
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
import com.mahood.family.core.ui.atTime
import com.mahood.family.core.ui.clockTime
import com.mahood.family.core.ui.dayLabel
import com.mahood.family.core.ui.localDate
import com.mahood.family.core.ui.minutesOfDay
import com.mahood.family.core.ui.rememberNow
import java.time.LocalDate

/**
 * Health: who had which medicine when (so two parents never double-dose a
 * poorly child), plus vaccinations and appointments.
 * Collections: "medicines" {name, gapHours, maxPerDay} (custom ones; two common ones are built in),
 * "doses" {person, med, medName, amount, at, by}, "health_events" {person, title, date, done}.
 */
class HealthModule : FamilyModule {
    override val id = "health"
    override val title = "Health"
    override val description = "Medicine doses, vaccines and appointments"
    override val icon: ImageVector = Icons.Filled.MedicalServices

    private val MEDS = "medicines"
    private val DOSES = "doses"
    private val EVENTS = "health_events"
    private val DAY_MS = 86_400_000L

    data class Med(val id: String, val name: String, val gapHours: Double, val maxPerDay: Int)

    /** Usual limits on UK children's packets. People can add others with their own limits. */
    private val builtIn = listOf(
        Med("paracetamol", "Paracetamol (Calpol)", 4.0, 4),
        Med("ibuprofen", "Ibuprofen (Nurofen)", 6.0, 3),
    )

    private fun meds(records: List<Record>): List<Med> = builtIn + records.map {
        Med(it.id, it.string("name") ?: "Medicine", (it.fields["gapHours"] as? Number)?.toDouble() ?: 4.0, it.long("maxPerDay")?.toInt() ?: 0)
    }

    private fun state(med: Med, doses: List<Record>, person: String, now: Long) = DoseRules.state(
        doses.filter { it.string("person") == person && it.string("med") == med.id }.mapNotNull { it.long("at") },
        med.gapHours, med.maxPerDay, now,
    )

    private fun timeText(millis: Long): String {
        val d = localDate(millis)
        return if (d == LocalDate.now()) clockTime(millis) else "${dayLabel(d)} ${clockTime(millis)}"
    }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val now = rememberNow()
        val people = ctx.kids().map { it.asMember() } + ctx.members()
        val doses = ctx.records(DOSES)
        val meds = meds(ctx.records(MEDS))
        val events = ctx.records(EVENTS).filter { !it.bool("done") }
            .filter { e -> e.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { !it.isAfter(LocalDate.now().plusDays(14)) } == true }
            .sortedBy { it.string("date") }
        val active = doses.filter { (it.long("at") ?: 0L) > now - DAY_MS }
            .map { it.string("person") to it.string("med") }.distinct()
        if (active.isEmpty() && events.isEmpty()) return
        HomeCardFrame(icon, "Health", "Open ›", open) {
            active.forEach { (personId, medId) ->
                val person = people.firstOrNull { it.email == personId } ?: return@forEach
                val med = meds.firstOrNull { it.id == medId } ?: return@forEach
                val s = state(med, doses, person.email, now)
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${person.name}: ${med.name.substringBefore(" (")} " +
                        (s.nextAllowedAt?.let { "next from ${timeText(it)}" } ?: "can have more now"), fontSize = 14.sp)
                }
            }
            events.take(2).forEach { e ->
                val person = people.firstOrNull { it.email == e.string("person") }
                Text("📅 ${e.string("title")}${person?.let { " · ${it.name}" } ?: ""} · ${runCatching { dayLabel(LocalDate.parse(e.string("date"))) }.getOrDefault("")}",
                    fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Screen(ctx: ModuleContext) {
        val now = rememberNow()
        val kids = ctx.kids()
        val members = ctx.members()
        val people: List<Member> = kids.map { it.asMember() } + members
        val doses = ctx.records(DOSES)
        val meds = meds(ctx.records(MEDS))
        val events = ctx.records(EVENTS)
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        val person = people.firstOrNull { it.email == selected } ?: people.firstOrNull()
        var giving by remember { mutableStateOf<Med?>(null) }
        var pickMed by remember { mutableStateOf(false) }
        var addEvent by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf<Pair<String, Record>?>(null) }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item { ScreenTitle("Health", "Medicine, vaccines and appointments") }
            if (person == null) {
                item { EmptyHint("Nobody in the family yet.") }
                return@LazyColumn
            }
            item { PersonChips(people, person.email, { selected = it }, Modifier.padding(bottom = 8.dp)) }

            item { SectionLabel("MEDICINE") }
            val mine = doses.filter { it.string("person") == person.email }
            val activeMeds = meds.filter { m -> mine.any { it.string("med") == m.id && (it.long("at") ?: 0L) > now - DAY_MS } }
            if (activeMeds.isEmpty()) item { EmptyHint("No medicine in the last 24 hours.") }
            items(activeMeds, key = { "m-${it.id}" }) { med ->
                val s = state(med, doses, person.email, now)
                val last = mine.filter { it.string("med") == med.id }.maxByOrNull { it.long("at") ?: 0L }
                val by = members.firstOrNull { it.email == last?.string("by") }
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(18.dp),
                    color = if (s.nextAllowedAt != null) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(14.dp)) {
                        Text(med.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Last: ${last?.string("amount")?.takeIf { it.isNotBlank() }?.let { "$it at " } ?: ""}${s.lastAt?.let(::timeText) ?: "?"}${by?.let { " by ${it.name}" } ?: ""}",
                            fontSize = 13.5.sp)
                        Text("${s.givenLast24h}${if (med.maxPerDay > 0) " of ${med.maxPerDay}" else ""} doses in 24 h", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.nextAllowedAt?.let { "Next dose from ${timeText(it)}" } ?: "OK to give another now",
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                                color = if (s.nextAllowedAt != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            FilledTonalButton(onClick = { giving = med }) { Text("Give dose") }
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = { pickMed = true }) { Text("Log a medicine") } }

            item { SectionLabel("VACCINES & APPOINTMENTS") }
            val theirs = events.filter { it.string("person") == person.email }.sortedWith(compareBy<Record> { it.bool("done") }.thenBy { it.string("date") })
            if (theirs.isEmpty()) item { EmptyHint("Add the next jabs from the red book, check-ups or the dentist.") }
            items(theirs, key = { "e-${it.id}" }) { e ->
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { deleting = EVENTS to e }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(e.bool("done"), { ctx.put(EVENTS, e.id, mapOf("done" to it)) })
                    Column(Modifier.weight(1f)) {
                        Text(e.string("title") ?: "", fontSize = 15.sp)
                        Text(runCatching { dayLabel(LocalDate.parse(e.string("date"))) }.getOrDefault("No date"), fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { OutlinedButton(onClick = { addEvent = true }) { Text("Add vaccine or appointment") } }

            item { SectionLabel("HISTORY", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val history = mine.sortedByDescending { it.long("at") ?: 0L }.take(30)
            if (history.isEmpty()) item { EmptyHint("No doses logged.") }
            items(history, key = { "h-${it.id}" }) { d ->
                val by = members.firstOrNull { it.email == d.string("by") }
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { deleting = DOSES to d }).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(timeText(d.long("at") ?: 0L), Modifier.width(110.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(listOfNotNull(d.string("medName"), d.string("amount")?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        Modifier.weight(1f), fontSize = 14.sp)
                    Avatar(by, 20.dp)
                }
            }
            item {
                Text("Limits are the usual ones on children's packets. Always check the label or ask a pharmacist. Long-press to delete an entry.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (pickMed) PickMedDialog(ctx, meds, onDone = { m -> pickMed = false; giving = m })
        if (person != null) giving?.let { med -> DoseDialog(ctx, med, person, doses, onDone = { giving = null }) }
        if (addEvent && person != null) EventDialog(ctx, person, onDone = { addEvent = false })
        deleting?.let { (collection, r) ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Delete this entry?") },
                confirmButton = { TextButton(onClick = { ctx.delete(collection, r.id); deleting = null }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            )
        }
    }

    @Composable
    private fun PickMedDialog(ctx: ModuleContext, meds: List<Med>, onDone: (Med?) -> Unit) {
        var custom by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("") }
        var gap by remember { mutableStateOf("8") }
        var max by remember { mutableStateOf("3") }
        AlertDialog(
            onDismissRequest = { onDone(null) },
            title = { Text("Which medicine?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!custom) {
                        meds.forEach { m ->
                            OutlinedButton(onClick = { onDone(m) }, modifier = Modifier.fillMaxWidth()) {
                                Text("${m.name} · every ${m.gapHours.toString().removeSuffix(".0")} h" + if (m.maxPerDay > 0) ", max ${m.maxPerDay}/day" else "")
                            }
                        }
                        TextButton(onClick = { custom = true }) { Text("Another medicine…") }
                    } else {
                        OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Amoxicillin") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(gap, { gap = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), label = { Text("Hours apart") }, singleLine = true)
                            OutlinedTextField(max, { max = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Max a day") }, singleLine = true)
                        }
                        Text("Use the numbers on the label or prescription. 0 = no daily limit.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                if (custom) TextButton(enabled = name.isNotBlank() && (gap.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                    val id = ctx.store.newId()
                    val med = Med(id, name.trim(), gap.toDouble(), max.toIntOrNull() ?: 0)
                    ctx.put(MEDS, id, mapOf("name" to med.name, "gapHours" to med.gapHours, "maxPerDay" to med.maxPerDay.toLong()))
                    onDone(med)
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
        )
    }

    @Composable
    private fun DoseDialog(ctx: ModuleContext, med: Med, person: Member, doses: List<Record>, onDone: () -> Unit) {
        val previous = doses.filter { it.string("person") == person.email && it.string("med") == med.id }.maxByOrNull { it.long("at") ?: 0L }
        var amount by remember { mutableStateOf(previous?.string("amount") ?: "") }
        var at by remember { mutableStateOf(System.currentTimeMillis()) }
        var pickTime by remember { mutableStateOf(false) }
        val s = state(med, doses, person.email, at)
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("${med.name} for ${person.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount, e.g. 5 ml") }, singleLine = true)
                    OutlinedButton(onClick = { pickTime = true }) { Text("Given at ${timeText(at)}") }
                    if (s.tooSoon(at)) {
                        Text("Too soon: the next dose is due from ${timeText(s.nextAllowedAt!!)} " +
                            "(${s.givenLast24h} given in the last 24 h). Check the packet before giving more.",
                            color = MaterialTheme.colorScheme.error, fontSize = 13.5.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ctx.put(DOSES, ctx.store.newId(), mapOf(
                        "person" to person.email, "med" to med.id, "medName" to med.name,
                        "amount" to amount.trim(), "at" to at, "by" to ctx.me?.email,
                    ))
                    onDone()
                }) { Text(if (s.tooSoon(at)) "Log anyway" else "Log dose") }
            },
            dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )
        if (pickTime) PickTimeDialog(minutesOfDay(at)) { m ->
            if (m != null) {
                var t = atTime(LocalDate.now(), m)
                if (t > System.currentTimeMillis() + 60_000L) t -= DAY_MS // a time later than now means last night
                at = t
            }
            pickTime = false
        }
    }

    @Composable
    private fun EventDialog(ctx: ModuleContext, person: Member, onDone: () -> Unit) {
        var title by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now().plusWeeks(1)) }
        var pickDate by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("For ${person.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("What") }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Vaccinations", "Health visitor review", "Doctor", "Dentist", "Flu vaccine", "Eye test", "Hearing test", "Hospital appointment")) { s ->
                            SuggestionChip(onClick = { title = s }, label = { Text(s) })
                        }
                    }
                    OutlinedButton(onClick = { pickDate = true }) { Text(dayLabel(date)) }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    ctx.put(EVENTS, ctx.store.newId(), mapOf("person" to person.email, "title" to title.trim(), "date" to date.toString(), "done" to false))
                    onDone()
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )
        if (pickDate) PickDateDialog(date) { d ->
            if (d != null) date = d
            pickDate = false
        }
    }

    override fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> {
        val ids = remoteOps.filter { it.collection == DOSES && it.field == "at" }.map { it.id }.distinct()
        if (ids.isEmpty()) return emptyList()
        val people = Member.all(ctx.store) + Kid.all(ctx.store).map { it.asMember() }
        return ids.mapNotNull { ctx.store.record(DOSES, it) }.map { d ->
            val who = people.firstOrNull { it.email == d.string("by") }?.name ?: "Someone"
            val patient = people.firstOrNull { it.email == d.string("person") }?.name ?: "someone"
            "$who gave $patient ${d.string("medName")} ${d.string("amount") ?: ""} at ${clockTime(d.long("at") ?: 0L)}".replace("  ", " ")
        }
    }
}
