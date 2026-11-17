package com.mahood.family.baby

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.mahood.family.core.data.Record
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.EmptyHint
import com.mahood.family.core.ui.HomeCardFrame
import com.mahood.family.core.ui.NoKidsYet
import com.mahood.family.core.ui.PersonChips
import com.mahood.family.core.ui.PickTimeDialog
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import com.mahood.family.core.ui.atTime
import com.mahood.family.core.ui.clockTime
import com.mahood.family.core.ui.dayLabel
import com.mahood.family.core.ui.durationText
import com.mahood.family.core.ui.localDate
import com.mahood.family.core.ui.minutesOfDay
import com.mahood.family.core.ui.rememberNow
import com.mahood.family.core.ui.startOfDay
import java.time.LocalDate

/**
 * Baby log: feeds, naps and nappies, shared so whoever is on duty knows when
 * the last feed was. Collection "baby_log" {kid, kind, at, end, detail, by}
 * where kind is feed | wet | dirty | sleep (end is null while asleep).
 */
class BabyModule : FamilyModule {
    override val id = "baby"
    override val title = "Baby"
    override val description = "Feeds, naps and nappies for the little one"
    override val icon: ImageVector = Icons.Filled.ChildCare

    private val LOG = "baby_log"

    /** Children under three, or every child if no birthdays are set. */
    @Composable
    private fun babies(ctx: ModuleContext): List<Kid> {
        val kids = ctx.kids()
        val young = kids.filter { it.born != null && it.born!!.isAfter(LocalDate.now().minusYears(3)) }
        return young.ifEmpty { kids.filter { it.born == null } }
    }

    private data class Summary(
        val lastFeed: Record?,
        val sleeping: Record?,
        val lastWake: Long?,
        val feeds: Int,
        val nappies: Int,
        val sleptMs: Long,
    )

    private fun summary(log: List<Record>, kidId: String, now: Long): Summary {
        val mine = log.filter { it.string("kid") == kidId }
        val todayStart = startOfDay(LocalDate.now())
        val today = mine.filter { (it.long("at") ?: 0L) >= todayStart }
        val sleeps = mine.filter { it.string("kind") == "sleep" }
        return Summary(
            lastFeed = mine.filter { it.string("kind") == "feed" }.maxByOrNull { it.long("at") ?: 0L },
            sleeping = sleeps.filter { it.long("end") == null }.maxByOrNull { it.long("at") ?: 0L },
            lastWake = sleeps.mapNotNull { it.long("end") }.maxOrNull(),
            feeds = today.count { it.string("kind") == "feed" },
            nappies = today.count { it.string("kind") == "wet" || it.string("kind") == "dirty" },
            sleptMs = sleeps.sumOf { s ->
                val start = maxOf(s.long("at") ?: 0L, todayStart)
                val end = s.long("end") ?: now
                (end - start).coerceAtLeast(0L)
            },
        )
    }

    private fun log(ctx: ModuleContext, kid: String, kind: String, at: Long = System.currentTimeMillis(), detail: String? = null) {
        ctx.put(LOG, ctx.store.newId(), mapOf("kid" to kid, "kind" to kind, "at" to at, "detail" to detail, "by" to ctx.me?.email))
    }

    private fun describe(r: Record, now: Long): String {
        val detail = r.string("detail")?.takeIf { it.isNotBlank() }
        return when (r.string("kind")) {
            "feed" -> "🍼 Feed" + (detail?.let { " · $it" } ?: "")
            "wet" -> "💧 Wet nappy"
            "dirty" -> "💩 Dirty nappy"
            "sleep" -> {
                val end = r.long("end")
                if (end == null) "😴 Asleep · ${durationText(now - (r.long("at") ?: now))} so far"
                else "😴 Slept ${durationText(end - (r.long("at") ?: end))} (until ${clockTime(end)})"
            }
            else -> detail ?: (r.string("kind") ?: "")
        }
    }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val babies = babies(ctx)
        val log = ctx.records(LOG)
        val now = rememberNow()
        HomeCardFrame(icon, "Baby log", "Open ›", open) {
            if (babies.isEmpty()) {
                Text("Add your little one on the Family tab to track feeds, naps and nappies.", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@HomeCardFrame
            }
            babies.forEach { kid ->
                val s = summary(log, kid.id, now)
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(kid.asMember(), 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        val feed = s.lastFeed?.long("at")?.let { "Fed ${durationText(now - it)} ago" } ?: "No feeds logged"
                        val sleep = s.sleeping?.long("at")?.let { "asleep ${durationText(now - it)}" }
                            ?: s.lastWake?.let { "awake ${durationText(now - it)}" }
                        Text(listOfNotNull(feed, sleep).joinToString(" · "), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("Today: ${s.feeds} feeds · ${s.nappies} nappies · ${durationText(s.sleptMs)} sleep",
                            fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Screen(ctx: ModuleContext) {
        val babies = babies(ctx)
        val log = ctx.records(LOG)
        val members = ctx.members()
        val now = rememberNow()
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        val kid = babies.firstOrNull { it.id == selected } ?: babies.firstOrNull()
        var feeding by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<Record?>(null) }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item { ScreenTitle("Baby log", kid?.let { "${it.name} · ${it.ageText()}" } ?: "Feeds, naps and nappies") }
            if (kid == null) {
                item { NoKidsYet(ctx, "the baby log") }
                return@LazyColumn
            }
            if (babies.size > 1) item {
                PersonChips(babies.map { it.asMember() }, kid.id, { selected = it }, Modifier.padding(bottom = 8.dp))
            }
            val s = summary(log, kid.id, now)
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            s.sleeping?.long("at")?.let { "😴 Asleep for ${durationText(now - it)}" }
                                ?: s.lastWake?.let { "☀️ Awake for ${durationText(now - it)}" } ?: "☀️ Awake",
                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            s.lastFeed?.let { "Last feed ${durationText(now - (it.long("at") ?: now))} ago at ${clockTime(it.long("at") ?: now)}" +
                                (it.string("detail")?.takeIf { d -> d.isNotBlank() }?.let { d -> " · $d" } ?: "") } ?: "No feeds logged yet",
                            fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                        Text("Today: ${s.feeds} feeds · ${s.nappies} nappies · ${durationText(s.sleptMs)} asleep",
                            fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
            item {
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { feeding = true }) { Text("🍼 Feed") }
                    FilledTonalButton(onClick = { log(ctx, kid.id, "wet") }) { Text("💧 Wet") }
                    FilledTonalButton(onClick = { log(ctx, kid.id, "dirty") }) { Text("💩 Dirty") }
                    val sleeping = s.sleeping
                    if (sleeping == null) {
                        FilledTonalButton(onClick = { log(ctx, kid.id, "sleep") }) { Text("😴 Asleep") }
                    } else {
                        FilledTonalButton(onClick = { ctx.put(LOG, sleeping.id, mapOf("end" to System.currentTimeMillis())) }) { Text("☀️ Woke up") }
                    }
                }
            }
            val recent = log.filter { it.string("kid") == kid.id && (it.long("at") ?: 0L) > now - 2 * 86_400_000L }
                .sortedByDescending { it.long("at") ?: 0L }
            if (recent.isEmpty()) item { EmptyHint("Nothing logged in the last two days. Tap a button above.") }
            recent.groupBy { localDate(it.long("at") ?: 0L) }.forEach { (day, entries) ->
                item(key = "d-$day") { SectionLabel(dayLabel(day).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(entries, key = { it.id }) { r ->
                    val who = members.firstOrNull { it.email == r.string("by") }
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = r }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(clockTime(r.long("at") ?: 0L), Modifier.width(52.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(describe(r, now), Modifier.weight(1f), fontSize = 14.5.sp)
                        Avatar(who, 22.dp)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (feeding && kid != null) FeedDialog(onDone = { detail, at ->
            if (detail != null) log(ctx, kid.id, "feed", at ?: System.currentTimeMillis(), detail)
            feeding = false
        })
        editing?.let { r -> EditEntryDialog(ctx, r, onDone = { editing = null }) }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun FeedDialog(onDone: (detail: String?, at: Long?) -> Unit) {
        val types = listOf("Bottle", "Breast (left)", "Breast (right)", "Solids", "Snack")
        var type by remember { mutableStateOf(types[0]) }
        var amount by remember { mutableStateOf("") }
        var minutes by remember { mutableStateOf<Int?>(null) }
        var pickTime by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { onDone(null, null) },
            title = { Text("Log a feed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.forEach { t -> FilterChip(selected = t == type, onClick = { type = t }, label = { Text(t) }) }
                    }
                    OutlinedTextField(amount, { amount = it }, singleLine = true,
                        label = { Text(if (type == "Bottle") "Amount, e.g. 150 ml" else "Details (optional)") })
                    OutlinedButton(onClick = { pickTime = true }) {
                        Text(minutes?.let { "At %02d:%02d".format(it / 60, it % 60) } ?: "Now (change time)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val detail = listOf(type, amount.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                    onDone(detail, minutes?.let { atTime(LocalDate.now(), it) })
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { onDone(null, null) }) { Text("Cancel") } },
        )
        if (pickTime) PickTimeDialog(minutes ?: minutesOfDay(System.currentTimeMillis())) { m ->
            if (m != null) minutes = m
            pickTime = false
        }
    }

    /** Fix the time of an entry (or when a nap ended), or delete it. */
    @Composable
    private fun EditEntryDialog(ctx: ModuleContext, r: Record, onDone: () -> Unit) {
        var pick by remember { mutableStateOf<String?>(null) }
        val at = r.long("at") ?: System.currentTimeMillis()
        val end = r.long("end")
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(describe(r, System.currentTimeMillis())) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick = "at" }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (r.string("kind") == "sleep") "Fell asleep " else "At ") + clockTime(at))
                    }
                    if (r.string("kind") == "sleep" && end != null) {
                        OutlinedButton(onClick = { pick = "end" }, modifier = Modifier.fillMaxWidth()) { Text("Woke up ${clockTime(end)}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = { ctx.delete(LOG, r.id); onDone() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
        )
        pick?.let { which ->
            val base = if (which == "end") end ?: at else at
            PickTimeDialog(minutesOfDay(base)) { m ->
                if (m != null) {
                    var t = atTime(localDate(base), m)
                    // A nap that ends "earlier" than it started ran past midnight.
                    if (which == "end" && t < at) t += 86_400_000L
                    ctx.put(LOG, r.id, mapOf(which to t))
                }
                pick = null
            }
        }
    }
}
