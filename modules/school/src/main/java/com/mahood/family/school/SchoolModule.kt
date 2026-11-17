package com.mahood.family.school

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahood.family.core.FamilyModule
import com.mahood.family.core.Kid
import com.mahood.family.core.ModuleContext
import com.mahood.family.core.data.Record
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.EmptyHint
import com.mahood.family.core.ui.HomeCardFrame
import com.mahood.family.core.ui.NoKidsYet
import com.mahood.family.core.ui.PersonChips
import com.mahood.family.core.ui.PickDateDialog
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import com.mahood.family.core.ui.clockTime
import com.mahood.family.core.ui.dayLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * School and nursery bags: what each child needs on which day (PE kit on
 * Tuesdays, library book on Thursdays, nappies for nursery) plus one-off
 * reminders (non-uniform day). Each item is ticked off once it's packed.
 * Collections: "school_items" {kid, text, days ("1,3" = Mon, Wed) or date (yyyy-MM-dd)}
 * and "school_packed" (id = itemId_date) {packed, by}.
 */
class SchoolModule : FamilyModule {
    override val id = "school"
    override val title = "School"
    override val description = "School and nursery bags: what to pack each day"
    override val icon: ImageVector = Icons.Filled.Backpack

    private val ITEMS = "school_items"
    private val PACKED = "school_packed"

    private val schoolSuggestions = listOf(
        "PE kit", "Library book", "Reading folder", "Homework", "Water bottle", "Snack", "Packed lunch",
        "Show and tell", "Swimming kit", "Non-uniform day", "Dinner money", "Wellies", "Forest school clothes",
    )
    private val nurserySuggestions = listOf(
        "Nappies", "Wipes", "Spare clothes", "Milk / bottle", "Dummy", "Comforter", "Sun hat", "Sun cream", "Coat",
    )

    private fun days(r: Record): Set<Int> =
        (r.string("days") ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    private fun dueOn(r: Record, date: LocalDate): Boolean =
        r.string("date") == date.toString() || date.dayOfWeek.value in days(r)

    /** After 3pm the next school day is what matters; before that, today. */
    private fun focusDay(): LocalDate {
        var d = LocalDate.now()
        if (LocalTime.now().hour >= 15) d = d.plusDays(1)
        return d
    }

    private fun isPacked(packed: Map<String, Record>, item: Record, date: LocalDate) =
        packed["${item.id}_$date"]?.bool("packed") == true

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val kids = ctx.kids()
        val items = ctx.records(ITEMS)
        val packed = ctx.records(PACKED).associateBy { it.id }
        val day = focusDay()
        val due = items.filter { dueOn(it, day) }
        HomeCardFrame(icon, "Bags for ${dayLabel(day).lowercase()}", "Open ›", open) {
            if (due.isEmpty()) {
                Text(if (items.isEmpty()) "Add PE days, library books or the nursery bag list." else "Nothing special to pack.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@HomeCardFrame
            }
            kids.forEach { kid ->
                val mine = due.filter { it.string("kid") == kid.id }
                if (mine.isEmpty()) return@forEach
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(kid.asMember(), 24.dp)
                    Spacer(Modifier.width(10.dp))
                    val left = mine.filter { !isPacked(packed, it, day) }
                    Text(
                        if (left.isEmpty()) "${kid.name}: all packed ✓" else "${kid.name}: ${left.joinToString(", ") { it.string("text") ?: "" }}",
                        fontSize = 14.5.sp, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Screen(ctx: ModuleContext) {
        val kids = ctx.kids()
        val members = ctx.members()
        val items = ctx.records(ITEMS)
        val packed = ctx.records(PACKED).associateBy { it.id }
        var day by remember { mutableStateOf(focusDay()) }
        var adding by remember { mutableStateOf<Kid?>(null) }
        var removing by remember { mutableStateOf<Record?>(null) }
        val week = (0..6).map { LocalDate.now().plusDays(it.toLong()) }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item { ScreenTitle("School & nursery", "What to pack, and who's packed it") }
            if (kids.isEmpty()) {
                item { NoKidsYet(ctx, "school bags") }
                return@LazyColumn
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(week, key = { it.toString() }) { d ->
                        val n = items.count { dueOn(it, d) }
                        FilterChip(selected = d == day, onClick = { day = d },
                            label = { Text(dayLabel(d) + if (n > 0) " · $n" else "") })
                    }
                }
            }
            kids.forEach { kid ->
                val due = items.filter { it.string("kid") == kid.id && dueOn(it, day) }
                item(key = "k-${kid.id}") {
                    Row(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(kid.asMember(), 26.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(kid.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { adding = kid }) { Icon(Icons.Filled.Add, "Add for ${kid.name}") }
                    }
                }
                if (due.isEmpty()) item(key = "e-${kid.id}") { EmptyHint("Nothing extra for ${dayLabel(day).lowercase()}.") }
                items(due, key = { "${kid.id}-${it.id}" }) { item ->
                    val isPacked = isPacked(packed, item, day)
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(
                            onClick = { ctx.create(PACKED, "${item.id}_$day", mapOf("packed" to !isPacked, "by" to ctx.me?.email, "at" to System.currentTimeMillis())) },
                            onLongClick = { removing = item },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(isPacked, { ctx.create(PACKED, "${item.id}_$day", mapOf("packed" to it, "by" to ctx.me?.email, "at" to System.currentTimeMillis())) })
                        Column(Modifier.weight(1f)) {
                            Text(item.string("text") ?: "", fontSize = 15.sp,
                                textDecoration = if (isPacked) TextDecoration.LineThrough else null)
                            val log = packed["${item.id}_$day"]?.takeIf { isPacked }
                            val packer = members.firstOrNull { it.email == log?.string("by") }?.name
                            Text(repeatText(item) + (log?.let { l -> " · packed" + (packer?.let { " by $it" } ?: "") + (l.long("at")?.let { " at ${clockTime(it)}" } ?: "") } ?: ""),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionLabel("EVERY WEEK", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
            val repeating = items.filter { it.string("date") == null }
            if (repeating.isEmpty()) item { EmptyHint("Nothing set up yet. Tap + next to a name.") }
            items(repeating, key = { "r-${it.id}" }) { item ->
                val kid = kids.firstOrNull { it.id == item.string("kid") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(kid?.asMember(), 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${item.string("text")} · ${repeatText(item)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { removing = item }) { Icon(Icons.Filled.Close, "Remove") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        adding?.let { kid -> AddDialog(ctx, kid, kids, day, onDone = { adding = null }) }
        removing?.let { item ->
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { Text("Remove ${item.string("text")}?") },
                text = { Text(repeatText(item)) },
                confirmButton = { TextButton(onClick = { ctx.delete(ITEMS, item.id); removing = null }) { Text("Remove") } },
                dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
            )
        }
    }

    private fun repeatText(item: Record): String {
        item.string("date")?.let { d -> return runCatching { "Just ${dayLabel(LocalDate.parse(d))}" }.getOrDefault(d) }
        val d = days(item).sorted()
        return when (d) {
            listOf(1, 2, 3, 4, 5) -> "Every school day"
            (1..7).toList() -> "Every day"
            else -> "Every " + d.joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun AddDialog(ctx: ModuleContext, initialKid: Kid, kids: List<Kid>, initialDay: LocalDate, onDone: () -> Unit) {
        var kidId by remember { mutableStateOf<String?>(initialKid.id) }
        var text by remember { mutableStateOf("") }
        var weekly by remember { mutableStateOf(true) }
        var chosenDays by remember { mutableStateOf(setOf(initialDay.dayOfWeek.value)) }
        var date by remember { mutableStateOf(initialDay) }
        var pickDate by remember { mutableStateOf(false) }
        val kid = kids.firstOrNull { it.id == kidId }
        val small = kid?.born?.isAfter(LocalDate.now().minusYears(3)) == true
        val suggestions = if (small) nurserySuggestions else schoolSuggestions

        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Something to pack") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (kids.size > 1) PersonChips(kids.map { it.asMember() }, kidId, { kidId = it })
                    OutlinedTextField(text, { text = it }, label = { Text("What") }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions) { s -> SuggestionChip(onClick = { text = s }, label = { Text(s) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Every week") })
                        FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("Just once") })
                    }
                    if (weekly) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (d in 1..7) {
                                FilterChip(
                                    selected = d in chosenDays,
                                    onClick = { chosenDays = if (d in chosenDays) chosenDays - d else chosenDays + d },
                                    label = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                                )
                            }
                        }
                        TextButton(onClick = { chosenDays = (1..5).toSet() }) { Text("Every school day") }
                    } else {
                        OutlinedButton(onClick = { pickDate = true }) { Text(dayLabel(date)) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank() && kidId != null && (!weekly || chosenDays.isNotEmpty()), onClick = {
                    ctx.put(ITEMS, ctx.store.newId(), mapOf(
                        "kid" to kidId,
                        "text" to text.trim(),
                        "days" to if (weekly) chosenDays.sorted().joinToString(",") else null,
                        "date" to if (weekly) null else date.toString(),
                    ))
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
}
