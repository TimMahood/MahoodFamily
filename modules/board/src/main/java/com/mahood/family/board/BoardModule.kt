package com.mahood.family.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahood.family.core.FamilyModule
import com.mahood.family.core.ModuleContext
import com.mahood.family.core.data.Op
import com.mahood.family.core.data.Record
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.HomeCardFrame
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.relativeTime

/** Family board: short notes and pinned info. Collection "notes" {text, by, created, pinned}. */
class BoardModule : FamilyModule {
    override val id = "board"
    override val title = "Board"
    override val description = "Notes, announcements and pinned info"
    override val icon: ImageVector = Icons.Filled.PushPin

    private val NOTES = "notes"

    private fun sorted(notes: List<Record>) =
        notes.sortedWith(compareByDescending<Record> { it.bool("pinned") }.thenByDescending { it.long("created") ?: 0L })

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val notes = sorted(ctx.records(NOTES))
        val members = ctx.members()
        val top = notes.firstOrNull()
        val recent = notes.count { (System.currentTimeMillis() - (it.long("created") ?: 0L)) < 24 * 3600_000L }
        HomeCardFrame(icon, "Family board", if (recent > 0) "$recent new ›" else "Open ›", open, tonal = true) {
            if (top == null) {
                Text("No notes yet. Pin the wifi password or bin day here.", fontSize = 14.sp)
            } else {
                Text(top.string("text") ?: "", fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 3)
                val who = members.firstOrNull { it.email == top.string("by") }?.name ?: "Someone"
                Text(
                    "$who · ${if (top.bool("pinned")) "pinned · " else ""}${relativeTime(top.long("created") ?: 0L)}",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }

    @Composable
    override fun Screen(ctx: ModuleContext) {
        val notes = sorted(ctx.records(NOTES))
        val members = ctx.members()
        val me = ctx.me
        var text by rememberSaveable { mutableStateOf("") }

        fun post() {
            if (text.isBlank()) return
            ctx.put(NOTES, ctx.store.newId(), mapOf(
                "text" to text.trim(), "by" to me?.email, "created" to System.currentTimeMillis(), "pinned" to false,
            ))
            text = ""
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).imePadding()) {
            ScreenTitle("Family board", "Notes everyone can see")
            LazyColumn(Modifier.weight(1f)) {
                if (notes.isEmpty()) item {
                    Text("Post a note below: bin day, the wifi password, who's picking up whom.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(notes, key = { it.id }) { note ->
                    val pinned = note.bool("pinned")
                    val author = members.firstOrNull { it.email == note.string("by") }
                    Card(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (pinned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                    ) {
                        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(author, 26.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${author?.name ?: "Someone"} · ${relativeTime(note.long("created") ?: 0L)}",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { ctx.put(NOTES, note.id, mapOf("pinned" to !pinned)) }) {
                                    Icon(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (pinned) "Unpin" else "Pin")
                                }
                                if (note.string("by") == me?.email) {
                                    IconButton(onClick = { ctx.delete(NOTES, note.id) }) { Icon(Icons.Filled.Delete, "Delete") }
                                }
                            }
                            Text(note.string("text") ?: "", fontSize = 15.sp, modifier = Modifier.padding(end = 12.dp, bottom = 10.dp))
                        }
                    }
                }
            }
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a note…") }, shape = RoundedCornerShape(24.dp), maxLines = 4,
                )
                IconButton(onClick = ::post, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Post") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    override fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> =
        remoteOps.filter { it.collection == NOTES && it.field == "text" }
            .mapNotNull { it.value as? String }
            .map { "Board: ${it.take(80)}" }
}
