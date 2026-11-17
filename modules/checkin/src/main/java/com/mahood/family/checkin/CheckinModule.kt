package com.mahood.family.checkin

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import com.mahood.family.core.ui.clockTime
import com.mahood.family.core.ui.relativeTime
import com.mahood.family.core.ui.rememberNow

/**
 * Quick check-in: one tap to tell everyone "I'm home", "On my way", "Running late"...
 * Collections: "checkins" (one record per person, id = their email) {status, at}
 * and "checkin_log" {by, status, at} for the history.
 */
class CheckinModule : FamilyModule {
    override val id = "checkin"
    override val title = "Check-in"
    override val description = "One tap: I'm home, on my way, running late"
    override val icon: ImageVector = Icons.Filled.WavingHand

    private val LATEST = "checkins"
    private val LOG = "checkin_log"

    /** Check-ins older than this are treated as stale and not shown on Home. */
    private val FRESH_MS = 12 * 3600_000L

    private val presets = listOf(
        "🏠 I'm home", "🚗 On my way", "⏰ Running late", "💼 Leaving work",
        "🏫 Kids dropped off", "🎒 Got the kids", "🛒 At the shops", "😴 Baby's asleep",
    )

    private fun checkIn(ctx: ModuleContext, status: String) {
        val me = ctx.me ?: return
        val now = System.currentTimeMillis()
        ctx.put(LATEST, me.email, mapOf("status" to status, "at" to now))
        ctx.put(LOG, ctx.store.newId(), mapOf("by" to me.email, "status" to status, "at" to now))
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PresetChips(ctx: ModuleContext, count: Int) {
        val context = LocalContext.current
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.take(count).forEach { p ->
                AssistChip(
                    onClick = {
                        checkIn(ctx, p)
                        Toast.makeText(context, "Sent to the family", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text(p) },
                )
            }
        }
    }

    @Composable
    private fun StatusRow(member: Member?, record: Record, now: Long) {
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(member, 26.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(record.string("status") ?: "", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(
                    "${member?.name ?: "Someone"} · ${relativeTime(record.long("at") ?: 0L, now)}",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val members = ctx.members()
        val now = rememberNow()
        val recent = ctx.records(LATEST)
            .filter { now - (it.long("at") ?: 0L) < FRESH_MS }
            .sortedByDescending { it.long("at") ?: 0L }
        HomeCardFrame(icon, "Quick check-in", "History ›", open) {
            recent.take(3).forEach { r -> StatusRow(members.firstOrNull { it.email == r.id }, r, now) }
            if (recent.isNotEmpty()) Spacer(Modifier.height(6.dp))
            PresetChips(ctx, 4)
        }
    }

    @Composable
    override fun Screen(ctx: ModuleContext) {
        val members = ctx.members()
        val now = rememberNow()
        val latest = ctx.records(LATEST).associateBy { it.id }
        val log = ctx.records(LOG).sortedByDescending { it.long("at") ?: 0L }.take(40)
        var custom by rememberSaveable { mutableStateOf("") }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item { ScreenTitle("Check-in", "Let everyone know where you are") }
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        members.forEach { m ->
                            val r = latest[m.email]
                            if (r != null) StatusRow(m, r, now) else Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(m, 26.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("${m.name} hasn't checked in yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            item { SectionLabel("CHECK IN") }
            item { PresetChips(ctx, presets.size) }
            item {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        custom, { custom = it }, Modifier.weight(1f),
                        placeholder = { Text("Or type a message, e.g. Home by 7") }, singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                    )
                    IconButton(enabled = custom.isNotBlank(), onClick = { checkIn(ctx, "💬 ${custom.trim()}"); custom = "" }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
            item { SectionLabel("HISTORY", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (log.isEmpty()) item { EmptyHint("No check-ins yet.") }
            items(log, key = { it.id }) { r ->
                val who = members.firstOrNull { it.email == r.string("by") }
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(clockTime(r.long("at") ?: 0L), Modifier.width(52.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Avatar(who, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(r.string("status") ?: "", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(relativeTime(r.long("at") ?: 0L, now), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    override fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> {
        val members = Member.all(ctx.store)
        return remoteOps.filter { it.collection == LATEST && it.field == "status" }
            .map { op -> "${members.firstOrNull { it.email == op.id }?.name ?: "Someone"}: ${op.value as? String ?: ""}" }
    }
}
