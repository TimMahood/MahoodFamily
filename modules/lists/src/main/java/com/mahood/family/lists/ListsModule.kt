package com.mahood.family.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.mahood.family.core.shopping.Aisles
import com.mahood.family.core.shopping.Quantity
import com.mahood.family.core.shopping.Shopping
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.HomeCardFrame
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import com.mahood.family.core.ui.clockTime
import com.mahood.family.core.ui.relativeTime

/**
 * Shared lists: the weekly shop plus any other list (DIY, Christmas...).
 * Storage lives in core's [Shopping] so other modules (e.g. the meal planner) can add items.
 */
class ListsModule : FamilyModule {
    override val id = "lists"
    override val title = "Lists"
    override val description = "Shopping and any other shared list"
    override val icon: ImageVector = Icons.Filled.ShoppingCart

    private val LISTS = Shopping.LISTS
    private val ITEMS = Shopping.ITEMS

    private fun sortedLists(lists: List<Record>) = Shopping.sortedLists(lists)

    /** Make sure there is always a "Weekly shop" list to start with. */
    private fun ensureDefault(ctx: ModuleContext, lists: List<Record>) {
        if (lists.isEmpty() && ctx.store.query(LISTS).isEmpty()) Shopping.mainListId(ctx)
    }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val lists = sortedLists(ctx.records(LISTS))
        val items = ctx.records(ITEMS)
        val members = ctx.members()
        val first = lists.firstOrNull { it.id == Shopping.DEFAULT_LIST } ?: lists.firstOrNull()
        val inList = items.filter { it.string("list") == (first?.id ?: Shopping.DEFAULT_LIST) }
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
            Spacer(Modifier.height(10.dp))
            ShopProgress(inList.size - left, inList.size)
        }
    }

    @Composable
    override fun Screen(ctx: ModuleContext) {
        val allLists = ctx.records(LISTS)
        LaunchedEffect(allLists.isEmpty()) { ensureDefault(ctx, allLists) }
        val lists = sortedLists(allLists)
        val items = ctx.records(ITEMS)
        val learned = Shopping.learned(ctx.records(Shopping.LEARNED))
        val members = ctx.members()
        var editing by remember { mutableStateOf<Record?>(null) }
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
            val (qty, name) = Quantity.parse(text)
            Shopping.add(ctx, name, qty, listId = current.id)
            newText = ""
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            ScreenTitle(current?.string("name") ?: "Lists", if (inList.isEmpty()) "Shared with the family" else "${todo.size} to get · ${done.size} in the trolley") {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "List options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("New list") }, onClick = { menu = false; showNewList = true })
                        DropdownMenuItem(text = { Text("Untick everything") }, onClick = {
                            menu = false
                            done.forEach { ctx.put(ITEMS, it.id, mapOf("done" to false)) }
                        })
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
            if (inList.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ShopProgress(done.size, inList.size)
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
                val grouped = todo.groupBy { Shopping.aisleOf(it, learned) }
                for (aisle in Aisles.order) {
                    val group = grouped[aisle] ?: continue
                    item(key = "h-$aisle") { SectionLabel("${Aisles.emoji[aisle] ?: ""} $aisle".trim()) }
                    items(group.sortedBy { it.long("created") ?: 0L }, key = { it.id }) { item ->
                        ItemRow(item, members, onToggle = { ctx.put(ITEMS, item.id, mapOf("done" to true, "doneBy" to ctx.me?.email, "doneAt" to System.currentTimeMillis())) }, onDelete = null,
                            onEdit = { editing = item })
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "h-done") { SectionLabel("✓ In the trolley · ${done.size}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(done, key = { it.id }) { item ->
                        ItemRow(item, members,
                            onToggle = { ctx.put(ITEMS, item.id, mapOf("done" to false)) },
                            onDelete = { ctx.delete(ITEMS, item.id) },
                            onEdit = { editing = item })
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

        editing?.let { item ->
            EditItemDialog(item, Shopping.aisleOf(item, learned), onDone = { editing = null }, onSave = { name, qty, note, aisle ->
                val fields = mutableMapOf<String, Any?>()
                if (name != item.string("name")) fields["name"] = name
                if (qty != (item.string("qty") ?: "")) fields["qty"] = qty.ifBlank { null }
                if (note != (item.string("note") ?: "")) fields["note"] = note.ifBlank { null }
                if (fields.isNotEmpty()) ctx.put(ITEMS, item.id, fields)
                if (aisle != Shopping.aisleOf(item, learned)) Shopping.moveToAisle(ctx, item.copy(fields = item.fields + fields), aisle)
                editing = null
            }, onDelete = { ctx.delete(ITEMS, item.id); editing = null })
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
    private fun ShopProgress(got: Int, total: Int) {
        if (total == 0) return
        Column {
            LinearProgressIndicator(
                progress = { got.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(
                if (got == total) "All done 🎉" else "$got of $total in the trolley",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ItemRow(item: Record, members: List<Member>, onToggle: () -> Unit, onDelete: (() -> Unit)?, onEdit: () -> Unit) {
        val done = item.bool("done")
        val who = members.firstOrNull { it.email == item.string("by") }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).combinedClickable(onClick = onToggle, onLongClick = onEdit),
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
                    val detail = listOfNotNull(item.string("qty"), item.string("note")).filter { it.isNotBlank() }.joinToString(" · ")
                    if (detail.isNotEmpty() && !done) Text(detail, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (done) {
                        // Small log line: who ticked it and when.
                        val ticker = members.firstOrNull { it.email == item.string("doneBy") }
                        val at = item.long("doneAt")
                        if (ticker != null || at != null) Text(
                            "✓ " + listOfNotNull(ticker?.name, at?.let { "${clockTime(it)} · ${relativeTime(it)}" }).joinToString(" · "),
                            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

    @Composable
    private fun EditItemDialog(
        item: Record,
        currentAisle: String,
        onDone: () -> Unit,
        onSave: (name: String, qty: String, note: String, aisle: String) -> Unit,
        onDelete: () -> Unit,
    ) {
        var name by remember { mutableStateOf(item.string("name") ?: "") }
        var qty by remember { mutableStateOf(item.string("qty") ?: "") }
        var note by remember { mutableStateOf(item.string("note") ?: "") }
        var aisle by remember { mutableStateOf(currentAisle) }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Edit item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Item") }, singleLine = true)
                    OutlinedTextField(qty, { qty = it }, label = { Text("How many (optional)") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                    Text("Aisle", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(Aisles.order) { a ->
                            FilterChip(selected = a == aisle, onClick = { aisle = a }, label = { Text("${Aisles.emoji[a] ?: ""} $a".trim()) })
                        }
                    }
                    if (aisle != currentAisle) {
                        Text("The app will remember this for next time.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), qty.trim(), note.trim(), aisle) }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onDone) { Text("Cancel") }
                }
            },
        )
    }

    override fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> {
        val added = remoteOps.filter { it.collection == ITEMS && it.field == "name" }
        if (added.isEmpty()) return emptyList()
        val names = added.mapNotNull { it.value as? String }.distinct()
        return listOf("Shopping: ${names.take(4).joinToString(", ")}${if (names.size > 4) " +${names.size - 4} more" else ""} added")
    }
}
