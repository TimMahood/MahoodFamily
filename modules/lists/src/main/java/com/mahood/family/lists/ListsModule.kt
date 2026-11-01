package com.mahood.family.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahood.family.core.FamilyModule
import com.mahood.family.core.Member
import com.mahood.family.core.ModuleContext
import com.mahood.family.core.data.Op
import com.mahood.family.core.data.Record
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.HomeCardFrame
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import com.mahood.family.core.ui.relativeTime

/**
 * Shared lists: the weekly shop plus any other list (DIY, Christmas...).
 * Collections: "lists" {name, order} and "items" {list, name, qty, aisle, done, by, created}.
 */
class ListsModule : FamilyModule {
    override val id = "lists"
    override val title = "Lists"
    override val description = "Shopping and any other shared list"
    override val icon: ImageVector = Icons.Filled.ShoppingCart

    private val LISTS = "lists"
    private val ITEMS = "items"

    private fun sortedLists(lists: List<Record>) =
        lists.sortedWith(compareBy<Record>({ it.long("order") ?: Long.MAX_VALUE }, { it.string("name") }))

    /** Make sure there is always a "Weekly shop" list to start with. */
    private fun ensureDefault(ctx: ModuleContext, lists: List<Record>) {
        if (lists.isEmpty() && ctx.store.query(LISTS).isEmpty()) {
            ctx.put(LISTS, "weekly-shop", mapOf("name" to "Weekly shop", "order" to 0L))
        }
    }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val lists = sortedLists(ctx.records(LISTS))
        val items = ctx.records(ITEMS)
        val members = ctx.members()
        val first = lists.firstOrNull()
        val inList = items.filter { it.string("list") == (first?.id ?: "weekly-shop") }
        val left = inList.count { !it.bool("done") }
        val latest = inList.filter { !it.bool("done") }.maxByOrNull { it.long("created") ?: 0L }
        HomeCardFrame(icon, first?.string("name") ?: "Weekly shop", "Open ›", open) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$left", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                val who = members.firstOrNull { it.email == latest?.string("by") }?.name
                val detail = buildString {
                    append(if (left == 1) "item left" else "items left")
                    if (inList.isNotEmpty()) append(" of ${inList.size}")
                    if (latest != null) {
                        append(" · ")
                        append(if (who != null) "$who added ${latest.string("name")}" else "${latest.string("name")} added")
                        latest.long("created")?.let { append(" ").append(relativeTime(it)) }
                    }
                }
                Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (inList.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (inList.size - left).toFloat() / inList.size },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
        }
    }

    @Composable
    override fun Screen(ctx: ModuleContext) {
        val allLists = ctx.records(LISTS)
        LaunchedEffect(allLists.isEmpty()) { ensureDefault(ctx, allLists) }
        val lists = sortedLists(allLists)
        val items = ctx.records(ITEMS)
        val members = ctx.members()
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        val current = lists.firstOrNull { it.id == selected } ?: lists.firstOrNull()
        var newText by rememberSaveable { mutableStateOf("") }
        var showNewList by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf(false) }

        val inList = items.filter { it.string("list") == current?.id }
        val todo = inList.filter { !it.bool("done") }
        val done = inList.filter { it.bool("done") }.sortedByDescending { it.updated }

        fun add() {
            val text = newText.trim()
            if (text.isEmpty() || current == null) return
            val (qty, name) = parseQuantity(text)
            ctx.put(ITEMS, ctx.store.newId(), mapOf(
                "list" to current.id,
                "name" to name,
                "qty" to qty,
                "aisle" to Aisles.guess(name),
                "done" to false,
                "by" to ctx.me?.email,
                "created" to System.currentTimeMillis(),
            ))
            newText = ""
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            ScreenTitle(current?.string("name") ?: "Lists", "${todo.size} to get · shared with the family") {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "List options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("New list") }, onClick = { menu = false; showNewList = true })
                        DropdownMenuItem(text = { Text("Clear ticked items") }, onClick = {
                            menu = false
                            done.forEach { ctx.delete(ITEMS, it.id) }
                        })
                        if (current != null && lists.size > 1) {
                            DropdownMenuItem(text = { Text("Delete this list") }, onClick = {
                                menu = false
                                inList.forEach { ctx.delete(ITEMS, it.id) }
                                ctx.delete(LISTS, current.id)
                                selected = null
                            })
                        }
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lists, key = { it.id }) { l ->
                    val count = items.count { it.string("list") == l.id && !it.bool("done") }
                    FilterChip(
                        selected = l.id == current?.id,
                        onClick = { selected = l.id },
                        label = { Text("${l.string("name")} · $count") },
                    )
                }
                item {
                    FilterChip(selected = false, onClick = { showNewList = true }, label = { Text("New list") },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
                }
            }
            Spacer(Modifier.height(10.dp))

            TextField(
                value = newText,
                onValueChange = { newText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add an item… (e.g. 2 milk)") },
                leadingIcon = { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (newText.isNotBlank()) IconButton(onClick = ::add) { Icon(Icons.AutoMirrored.Filled.Send, "Add") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )

            LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp)) {
                val grouped = todo.groupBy { it.string("aisle") ?: "Other" }
                for (aisle in Aisles.order) {
                    val group = grouped[aisle] ?: continue
                    item(key = "h-$aisle") { SectionLabel(aisle) }
                    items(group.sortedBy { it.long("created") ?: 0L }, key = { it.id }) { item ->
                        ItemRow(item, members, onToggle = { ctx.put(ITEMS, item.id, mapOf("done" to true)) }, onDelete = null)
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "h-done") { SectionLabel("✓ In the trolley · ${done.size}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(done, key = { it.id }) { item ->
                        ItemRow(item, members,
                            onToggle = { ctx.put(ITEMS, item.id, mapOf("done" to false)) },
                            onDelete = { ctx.delete(ITEMS, item.id) })
                    }
                }
                if (inList.isEmpty()) {
                    item {
                        Text(
                            "Nothing on this list yet. Add something above and it appears on everyone's phone.",
                            modifier = Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        if (showNewList) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNewList = false },
                title = { Text("New list") },
                text = { OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. DIY") }, singleLine = true) },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        val id = ctx.store.newId()
                        ctx.put(LISTS, id, mapOf("name" to name.trim(), "order" to (lists.size.toLong())))
                        selected = id
                        showNewList = false
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showNewList = false }) { Text("Cancel") } },
            )
        }
    }

    @Composable
    private fun ItemRow(item: Record, members: List<Member>, onToggle: () -> Unit, onDelete: (() -> Unit)?) {
        val done = item.bool("done")
        val who = members.firstOrNull { it.email == item.string("by") }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable(onClick = onToggle),
            shape = RoundedCornerShape(14.dp),
            color = if (done) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = done, onCheckedChange = { onToggle() })
                Column(Modifier.weight(1f)) {
                    Text(
                        item.string("name") ?: "",
                        fontSize = 15.sp,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    val qty = item.string("qty")
                    if (!qty.isNullOrBlank() && !done) Text(qty, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else if (who != null) {
                    Avatar(who, 24.dp)
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
    }

    /** "2 milk", "2x milk", "milk x2" -> ("2", "milk"). */
    private fun parseQuantity(text: String): Pair<String?, String> {
        Regex("^(\\d+)\\s*[xX]?\\s+(.+)$").find(text)?.let { return it.groupValues[1] to it.groupValues[2] }
        Regex("^(.+?)\\s+[xX]\\s*(\\d+)$").find(text)?.let { return it.groupValues[2] to it.groupValues[1] }
        return null to text
    }

    override fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> {
        val added = remoteOps.filter { it.collection == ITEMS && it.field == "name" }
        if (added.isEmpty()) return emptyList()
        val names = added.mapNotNull { it.value as? String }.distinct()
        return listOf("Shopping: ${names.take(4).joinToString(", ")}${if (names.size > 4) " +${names.size - 4} more" else ""} added")
    }
}
