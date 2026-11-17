package com.mahood.family.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahood.family.core.Kid
import com.mahood.family.core.Member
import com.mahood.family.core.ModuleContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Add or edit a child. Pass [kid] to edit; null to add. */
@Composable
fun KidDialog(ctx: ModuleContext, kid: Kid?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(kid?.name ?: "") }
    var born by remember { mutableStateOf(kid?.born) }
    var pick by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (kid == null) "Add a child" else "Edit ${kid.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(born?.let { "Born ${it.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}" } ?: "Birthday")
                }
                born?.let { Text(Kid.ageText(it), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    "Children don't need a phone. Chores, school bags, milestones and health use this list.",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val id = kid?.id ?: "kid-${ctx.store.newId()}"
                val color = kid?.colorHex ?: Member.palette[(2 + ctx.store.query(Kid.COLLECTION).size * 3) % Member.palette.size]
                ctx.put(Kid.COLLECTION, id, mapOf("name" to name.trim(), "born" to born?.toString(), "color" to color))
                onDone()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (kid != null) TextButton(onClick = { confirmDelete = true }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDone) { Text("Cancel") }
            }
        },
    )

    if (pick) PickDateDialog(born ?: LocalDate.now().minusYears(1)) { d ->
        if (d != null) born = d
        pick = false
    }

    if (confirmDelete && kid != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove ${kid.name}?") },
        text = { Text("Their entries in other features stay, but they won't show up in pickers any more.") },
        confirmButton = { TextButton(onClick = { ctx.delete(Kid.COLLECTION, kid.id); confirmDelete = false; onDone() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

/** Shown by child-focused features when no children have been added yet. */
@Composable
fun NoKidsYet(ctx: ModuleContext, what: String) {
    var add by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        EmptyHint("Add your children first to use $what. They don't need phones.")
        OutlinedButton(onClick = { add = true }) { Text("Add a child") }
    }
    if (add) KidDialog(ctx, null) { add = false }
}
