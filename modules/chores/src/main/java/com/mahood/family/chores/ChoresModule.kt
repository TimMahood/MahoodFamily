package com.mahood.family.chores

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import com.mahood.family.core.ui.clockTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Chores and a star chart. Jobs repeat daily or weekly and belong to a grown-up,
 * a child or anyone. Children earn stars for their jobs (or a bonus star for being
 * kind) and spend them on rewards the family sets.
 *
 * Collections:
 *  "chores" {title, who, repeat (daily | weekly), day (1-7 for weekly), stars}
 *  "chore_done" (id = choreId_period) {done, by, at}
 *  "stars" {kid, n (+ earned / - spent), reason, at, by}   balance = sum of n
 *  "rewards" {kid (null = any child), title, cost}
 */
class ChoresModule : FamilyModule {
    override val id = "chores"
    override val title = "Chores"
    override val description = "Jobs rota and a star chart for the kids"
    override val icon: ImageVector = Icons.Filled.CleaningServices

    private val CHORES = "chores"
    private val DONE = "chore_done"
    private val STARS = "stars"
    private val REWARDS = "rewards"

    private val kidSuggestions = listOf("Tidy toys", "Make bed", "Brush teeth", "Get dressed", "Set the table", "Feed the pets", "Put clothes in the wash", "Water the plants", "Help with baby", "Reading")
    private val grownUpSuggestions = listOf("Bins out", "Recycling", "Hoover", "Bathroom", "Laundry", "Change the beds", "Dishwasher", "Bath time", "Night feed", "Batch cook")

    /** The id of this period for a chore: today's date for daily jobs, the week's Monday for weekly ones. */
    private fun period(chore: Record, today: LocalDate): String =
        if (chore.string("repeat") == "weekly") "w" + today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else today.toString()

    private fun dueToday(chore: Record, today: LocalDate): Boolean = when (chore.string("repeat")) {
        "weekly" -> {
            val day = chore.long("day")?.toInt()
            // Weekly jobs show from their day until the end of the week (or all week if no day was set).
            day == null || today.dayOfWeek.value >= day
        }
        else -> true
    }

    private fun doneKey(chore: Record, today: LocalDate) = "${chore.id}_${period(chore, today)}"

    private fun setDone(ctx: ModuleContext, chore: Record, today: LocalDate, done: Boolean, kids: List<Kid>) {
        val key = doneKey(chore, today)
        ctx.create(DONE, key, mapOf("done" to done, "by" to ctx.me?.email, "at" to System.currentTimeMillis()))
        val who = chore.string("who")
        val stars = chore.long("stars") ?: 0L
        if (stars > 0 && kids.any { it.id == who }) {
            // Star records are keyed by the job and day, so unticking takes the stars back.
            if (done) ctx.create(STARS, "job_$key", mapOf("kid" to who, "n" to stars, "reason" to chore.string("title"), "at" to System.currentTimeMillis(), "by" to ctx.me?.email))
            else ctx.delete(STARS, "job_$key")
        }
    }

    private fun balance(stars: List<Record>, kidId: String) =
        stars.filter { it.string("kid") == kidId }.sumOf { it.long("n") ?: 0L }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val today = LocalDate.now()
        val chores = ctx.records(CHORES).filter { dueToday(it, today) }
        val done = ctx.records(DONE).associateBy { it.id }
        val stars = ctx.records(STARS)
        val rewards = ctx.records(REWARDS)
        val kids = ctx.kids()
        val finished = chores.count { done[doneKey(it, today)]?.bool("done") == true }
        HomeCardFrame(icon, "Chores & stars", "Open ›", open) {
            if (chores.isEmpty() && kids.isEmpty()) {
                Text("Set up a jobs rota and a star chart for the kids.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@HomeCardFrame
            }
            if (chores.isNotEmpty()) {
                Text("$finished of ${chores.size} jobs done today", fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { finished.toFloat() / chores.size }, modifier = Modifier.fillMaxWidth().height(6.dp),
                    gapSize = 0.dp, drawStopIndicator = {})
            }
            kids.forEach { kid ->
                val b = balance(stars, kid.id)
                val goal = rewards.filter { it.string("kid") == null || it.string("kid") == kid.id }
                    .filter { (it.long("cost") ?: 0L) > b }.minByOrNull { it.long("cost") ?: 0L }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(kid.asMember(), 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${kid.name} ⭐ $b" + (goal?.let { " · ${(it.long("cost") ?: 0L) - b} to go for ${it.string("title")}" } ?: ""), fontSize = 14.sp)
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Screen(ctx: ModuleContext) {
        val context = LocalContext.current
        val today = LocalDate.now()
        val all = ctx.records(CHORES).sortedBy { it.string("title")?.lowercase() }
        val chores = all.filter { dueToday(it, today) }
        val done = ctx.records(DONE).associateBy { it.id }
        val stars = ctx.records(STARS)
        val rewards = ctx.records(REWARDS).sortedBy { it.long("cost") ?: 0L }
        val kids = ctx.kids()
        val members = ctx.members()
        val people: List<Member> = members + kids.map { it.asMember() }
        var addChore by remember { mutableStateOf(false) }
        var addReward by remember { mutableStateOf(false) }
        var removing by remember { mutableStateOf<Pair<String, Record>?>(null) }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item {
                ScreenTitle("Chores & stars", "${chores.count { done[doneKey(it, today)]?.bool("done") == true }} of ${chores.size} done today") {
                    IconButton(onClick = { addChore = true }) { Icon(Icons.Filled.Add, "Add a job") }
                }
            }
            if (kids.isNotEmpty()) {
                item { SectionLabel("STAR CHART") }
                items(kids, key = { "s-${it.id}" }) { kid ->
                    val b = balance(stars, kid.id)
                    Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(kid.asMember(), 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(kid.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Text("⭐ $b", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = {
                                    ctx.put(STARS, ctx.store.newId(), mapOf("kid" to kid.id, "n" to 1L, "reason" to "Bonus star", "at" to System.currentTimeMillis(), "by" to ctx.me?.email))
                                    Toast.makeText(context, "⭐ for ${kid.name}!", Toast.LENGTH_SHORT).show()
                                }) { Text("+1 bonus star") }
                            }
                            rewards.filter { it.string("kid") == null || it.string("kid") == kid.id }.forEach { r ->
                                val cost = r.long("cost") ?: 1L
                                Row(Modifier.padding(top = 8.dp).combinedClickable(onClick = {}, onLongClick = { removing = REWARDS to r }),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${r.string("title")} · ⭐ $cost", fontSize = 14.sp)
                                        LinearProgressIndicator(progress = { (b.toFloat() / cost).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp), gapSize = 0.dp, drawStopIndicator = {})
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Button(enabled = b >= cost, onClick = {
                                        ctx.put(STARS, ctx.store.newId(), mapOf("kid" to kid.id, "n" to -cost, "reason" to "Reward: ${r.string("title")}", "at" to System.currentTimeMillis(), "by" to ctx.me?.email))
                                        Toast.makeText(context, "🎉 ${kid.name} gets ${r.string("title")}!", Toast.LENGTH_LONG).show()
                                    }) { Text("Claim") }
                                }
                            }
                            TextButton(onClick = { addReward = true }) { Text("Add a reward") }
                        }
                    }
                }
            }
            item { SectionLabel("TODAY'S JOBS") }
            if (chores.isEmpty()) item { EmptyHint("No jobs yet. Tap + to add the bins, bath time or 'tidy toys'.") }
            items(chores, key = { it.id }) { c ->
                val isDone = done[doneKey(c, today)]?.bool("done") == true
                val who = people.firstOrNull { it.email == c.string("who") }
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = { setDone(ctx, c, today, !isDone, kids) }, onLongClick = { removing = CHORES to c }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(isDone, { setDone(ctx, c, today, it, kids) })
                    Column(Modifier.weight(1f)) {
                        Text(c.string("title") ?: "", fontSize = 15.sp, textDecoration = if (isDone) TextDecoration.LineThrough else null)
                        val repeat = if (c.string("repeat") == "weekly") {
                            "Weekly" + (c.long("day")?.let { d -> " from " + DayOfWeek.of(d.toInt()).getDisplayName(TextStyle.SHORT, Locale.getDefault()) } ?: "")
                        } else "Daily"
                        val starText = (c.long("stars") ?: 0L).takeIf { it > 0 && kids.any { k -> k.id == c.string("who") } }?.let { " · ⭐ $it" } ?: ""
                        val log = done[doneKey(c, today)]?.takeIf { isDone }
                        val doer = people.firstOrNull { it.email == log?.string("by") }?.name
                        val doneText = log?.let { l -> " · done" + (doer?.let { " by $it" } ?: "") + (l.long("at")?.let { " at ${clockTime(it)}" } ?: "") } ?: ""
                        Text("${who?.name ?: "Anyone"} · $repeat$starText$doneText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Avatar(who, 24.dp)
                }
            }
            val later = all.filter { !dueToday(it, today) }
            if (later.isNotEmpty()) {
                item { SectionLabel("LATER THIS WEEK", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(later, key = { "l-${it.id}" }) { c ->
                    Text("${c.string("title")} · ${DayOfWeek.of((c.long("day") ?: 1L).toInt()).getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                        fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp).combinedClickable(onClick = {}, onLongClick = { removing = CHORES to c }))
                }
            }
            item {
                Text("Long-press a job or reward to remove it.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (addChore) ChoreDialog(ctx, people, kids, onDone = { addChore = false })
        if (addReward) RewardDialog(ctx, kids, onDone = { addReward = false })
        removing?.let { (collection, r) ->
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { Text("Remove ${r.string("title")}?") },
                confirmButton = { TextButton(onClick = { ctx.delete(collection, r.id); removing = null }) { Text("Remove") } },
                dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
            )
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ChoreDialog(ctx: ModuleContext, people: List<Member>, kids: List<Kid>, onDone: () -> Unit) {
        var title by remember { mutableStateOf("") }
        var who by remember { mutableStateOf<String?>(null) }
        var weekly by remember { mutableStateOf(false) }
        var day by remember { mutableStateOf(LocalDate.now().dayOfWeek.value) }
        var stars by remember { mutableStateOf(1L) }
        val forKid = kids.any { it.id == who }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("New job") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Job") }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(if (forKid) kidSuggestions else grownUpSuggestions) { s -> SuggestionChip(onClick = { title = s }, label = { Text(s) }) }
                    }
                    Text("Whose job", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PersonChips(people, who, { who = it }, allowNone = "Anyone")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("Every day") })
                        FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Once a week") })
                    }
                    if (weekly) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 1..7) FilterChip(selected = d == day, onClick = { day = d },
                            label = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())) })
                    }
                    if (forKid) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stars", Modifier.weight(1f))
                        TextButton(onClick = { if (stars > 0) stars-- }) { Text("−") }
                        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { Text("⭐ $stars") }
                        TextButton(onClick = { if (stars < 10) stars++ }) { Text("+") }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    ctx.put(CHORES, ctx.store.newId(), mapOf(
                        "title" to title.trim(), "who" to who,
                        "repeat" to if (weekly) "weekly" else "daily",
                        "day" to if (weekly) day.toLong() else null,
                        "stars" to if (forKid) stars else 0L,
                    ))
                    onDone()
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )
    }

    @Composable
    private fun RewardDialog(ctx: ModuleContext, kids: List<Kid>, onDone: () -> Unit) {
        var title by remember { mutableStateOf("") }
        var cost by remember { mutableStateOf("10") }
        var kid by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("New reward") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Reward, e.g. Trip to the park") }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Pick the film", "Trip to the park", "Ice cream", "Stay up 15 min", "Small toy", "Baking together")) { s ->
                            SuggestionChip(onClick = { title = s }, label = { Text(s) })
                        }
                    }
                    OutlinedTextField(cost, { cost = it.filter(Char::isDigit).take(3) }, label = { Text("Stars needed") }, singleLine = true)
                    if (kids.size > 1) PersonChips(kids.map { it.asMember() }, kid, { kid = it }, allowNone = "Any child")
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && (cost.toLongOrNull() ?: 0L) > 0, onClick = {
                    ctx.put(REWARDS, ctx.store.newId(), mapOf("title" to title.trim(), "cost" to cost.toLong(), "kid" to kid))
                    onDone()
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )
    }

    override fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> {
        val kids = Kid.all(ctx.store)
        return remoteOps.filter { it.collection == STARS && it.field == "kid" }
            .groupBy { it.value as? String }
            .mapNotNull { (kidId, ops) ->
                val name = kids.firstOrNull { it.id == kidId }?.name ?: return@mapNotNull null
                val earned = ops.mapNotNull { op -> ctx.store.record(STARS, op.id)?.long("n") }.filter { it > 0 }.sum()
                if (earned > 0) "$name earned ⭐ $earned" else null
            }
    }
}
