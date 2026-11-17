package com.mahood.family.milestones

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.mahood.family.core.ModuleContext
import com.mahood.family.core.data.Op
import com.mahood.family.core.data.Record
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.EmptyHint
import com.mahood.family.core.ui.HomeCardFrame
import com.mahood.family.core.ui.NoKidsYet
import com.mahood.family.core.ui.PersonChips
import com.mahood.family.core.ui.PickDateDialog
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Milestones and memories: firsts, growth (height and weight) and the funny
 * things they say, dated and kept per child.
 * Collection "milestones" {kid, date (yyyy-MM-dd), kind (first | growth | quote | note), text, weight, height, by}.
 */
class MilestonesModule : FamilyModule {
    override val id = "milestones"
    override val title = "Milestones"
    override val description = "Firsts, growth and funny things they say"
    override val icon: ImageVector = Icons.Filled.EmojiEvents

    private val ENTRIES = "milestones"

    private val kinds = listOf("first" to "🌟 First", "growth" to "📏 Growth", "quote" to "💬 They said", "note" to "📝 Memory")

    private val babyFirsts = listOf(
        "First smile", "Rolled over", "Sat up", "First tooth", "Crawled", "Pulled to stand", "First word", "Waved bye-bye",
        "Clapped", "First steps", "Slept through", "First haircut", "Drank from a cup", "Said 'Mama'", "Said 'Dada'",
    )
    private val bigKidFirsts = listOf(
        "Lost first tooth", "Rode a bike", "Swam on their own", "Read a book alone", "Wrote their name", "Tied shoelaces",
        "First school play", "First sleepover", "Learned to whistle", "First certificate",
    )

    private fun dateOf(r: Record): LocalDate = r.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.MIN

    private fun line(r: Record): String {
        val emoji = kinds.firstOrNull { it.first == r.string("kind") }?.second?.substringBefore(' ') ?: "📝"
        val growth = listOfNotNull(r.string("height")?.takeIf { it.isNotBlank() }?.let { "$it cm" }, r.string("weight")?.takeIf { it.isNotBlank() }?.let { "$it kg" })
        val text = r.string("text")?.takeIf { it.isNotBlank() }
        return "$emoji " + listOfNotNull(text, growth.joinToString(" · ").ifEmpty { null }).joinToString(" · ")
    }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val kids = ctx.kids()
        val entries = ctx.records(ENTRIES)
        val today = LocalDate.now()
        val onThisDay = entries.filter { val d = dateOf(it); d.year < today.year && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth }
        val latest = entries.maxByOrNull { dateOf(it) }
        HomeCardFrame(icon, if (onThisDay.isNotEmpty()) "On this day" else "Milestones", "Open ›", open) {
            val show = onThisDay.firstOrNull() ?: latest
            if (show == null) {
                Text("Keep their firsts, growth and funny sayings in one place.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@HomeCardFrame
            }
            val kid = kids.firstOrNull { it.id == show.string("kid") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(kid?.asMember(), 26.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(line(show), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    val d = dateOf(show)
                    val ago = if (onThisDay.isNotEmpty()) "${today.year - d.year} year${if (today.year - d.year == 1) "" else "s"} ago" else d.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                    Text("${kid?.name ?: ""} · $ago", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Screen(ctx: ModuleContext) {
        val kids = ctx.kids()
        val entries = ctx.records(ENTRIES)
        val members = ctx.members()
        var filter by rememberSaveable { mutableStateOf<String?>(null) }
        var adding by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<Record?>(null) }
        val shown = entries.filter { filter == null || it.string("kid") == filter }.sortedByDescending { dateOf(it) }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item {
                ScreenTitle("Milestones", "${entries.size} memories") {
                    if (kids.isNotEmpty()) IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add") }
                }
            }
            if (kids.isEmpty()) {
                item { NoKidsYet(ctx, "milestones") }
                return@LazyColumn
            }
            if (kids.size > 1) item { PersonChips(kids.map { it.asMember() }, filter, { filter = it }, allowNone = "Everyone") }
            // Latest growth per child, for a quick look at how they're getting on.
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    kids.filter { filter == null || it.id == filter }.forEach { kid ->
                        val growth = entries.filter { it.string("kid") == kid.id && it.string("kind") == "growth" }.maxByOrNull { dateOf(it) }
                        Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(kid.asMember(), 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("${kid.name} · ${kid.ageText()}", fontWeight = FontWeight.SemiBold)
                                    Text(growth?.let { "Last measured ${dateOf(it).format(DateTimeFormatter.ofPattern("d MMM"))}: " + line(it).removePrefix("📏 ") } ?: "No height or weight logged yet",
                                        fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyHint("Nothing yet. Tap + to log a first, a measurement or something funny they said.") }
            shown.groupBy { dateOf(it).let { d -> if (d == LocalDate.MIN) "Undated" else d.format(DateTimeFormatter.ofPattern("MMMM yyyy")) } }.forEach { (month, list) ->
                item(key = "m-$month") { SectionLabel(month.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(list, key = { it.id }) { r ->
                    val kid = kids.firstOrNull { it.id == r.string("kid") }
                    val by = members.firstOrNull { it.email == r.string("by") }
                    Row(Modifier.fillMaxWidth().combinedClickable(onClick = { editing = r }, onLongClick = { editing = r }).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Avatar(kid?.asMember(), 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(line(r), fontSize = 15.sp)
                            val d = dateOf(r)
                            Text(listOfNotNull(kid?.name, kid?.born?.let { b -> if (d != LocalDate.MIN) "aged ${Kid.ageText(b, d)}" else null },
                                d.takeIf { it != LocalDate.MIN }?.format(DateTimeFormatter.ofPattern("d MMM")), by?.let { "by ${it.name}" }).joinToString(" · "),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (adding) EntryDialog(ctx, kids, null, filter ?: kids.firstOrNull()?.id, onDone = { adding = false })
        editing?.let { r -> EntryDialog(ctx, kids, r, r.string("kid"), onDone = { editing = null }) }
    }

    @Composable
    private fun EntryDialog(ctx: ModuleContext, kids: List<Kid>, entry: Record?, initialKid: String?, onDone: () -> Unit) {
        var kidId by remember { mutableStateOf(initialKid) }
        var kind by remember { mutableStateOf(entry?.string("kind") ?: "first") }
        var text by remember { mutableStateOf(entry?.string("text") ?: "") }
        var height by remember { mutableStateOf(entry?.string("height") ?: "") }
        var weight by remember { mutableStateOf(entry?.string("weight") ?: "") }
        var date by remember { mutableStateOf(entry?.let { dateOf(it).takeIf { d -> d != LocalDate.MIN } } ?: LocalDate.now()) }
        var pickDate by remember { mutableStateOf(false) }
        val kid = kids.firstOrNull { it.id == kidId }
        val small = kid?.born?.isAfter(LocalDate.now().minusYears(3)) == true
        val canSave = kidId != null && (text.isNotBlank() || (kind == "growth" && (height.isNotBlank() || weight.isNotBlank())))

        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(if (entry == null) "New memory" else "Edit memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (kids.size > 1) PersonChips(kids.map { it.asMember() }, kidId, { kidId = it })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(kinds) { (k, label) -> FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(label) }) }
                    }
                    if (kind == "growth") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(height, { height = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), label = { Text("Height cm") }, singleLine = true)
                            OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), label = { Text("Weight kg") }, singleLine = true)
                        }
                    }
                    OutlinedTextField(text, { text = it },
                        label = { Text(when (kind) { "quote" -> "What they said"; "growth" -> "Note (optional)"; "first" -> "What happened"; else -> "Memory" }) },
                        minLines = if (kind == "quote" || kind == "note") 2 else 1)
                    if (kind == "first" && entry == null) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(if (small) babyFirsts else bigKidFirsts) { s -> SuggestionChip(onClick = { text = s }, label = { Text(s) }) }
                        }
                    }
                    OutlinedButton(onClick = { pickDate = true }) { Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))) }
                }
            },
            confirmButton = {
                TextButton(enabled = canSave, onClick = {
                    ctx.put(ENTRIES, entry?.id ?: ctx.store.newId(), mapOf(
                        "kid" to kidId, "kind" to kind, "text" to text.trim(), "date" to date.toString(),
                        "height" to height.takeIf { kind == "growth" && it.isNotBlank() },
                        "weight" to weight.takeIf { kind == "growth" && it.isNotBlank() },
                        "by" to (entry?.string("by") ?: ctx.me?.email),
                    ))
                    onDone()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (entry != null) TextButton(onClick = { ctx.delete(ENTRIES, entry.id); onDone() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onDone) { Text("Cancel") }
                }
            },
        )
        if (pickDate) PickDateDialog(date) { d ->
            if (d != null) date = d
            pickDate = false
        }
    }

    override fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> {
        val kids = Kid.all(ctx.store)
        return remoteOps.filter { it.collection == ENTRIES && it.field == "kind" && it.value == "first" }
            .mapNotNull { op -> ctx.store.record(ENTRIES, op.id) }
            .map { r -> "${kids.firstOrNull { it.id == r.string("kid") }?.name ?: "New"} milestone: ${r.string("text")}" }
    }
}
