package com.mahood.family.meals

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahood.family.core.FamilyModule
import com.mahood.family.core.ModuleContext
import com.mahood.family.core.data.Op
import com.mahood.family.core.data.Record
import com.mahood.family.core.shopping.Quantity
import com.mahood.family.core.shopping.Shopping
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.HomeCardFrame
import com.mahood.family.core.ui.PersonChips
import com.mahood.family.core.ui.ScreenTitle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Meal planner: which dinner on which day, who's cooking, and the ingredients
 * sent to the shopping list in one tap.
 * Collection "meals", one record per day (id = yyyy-MM-dd) {dinner, cook, ingredients, shopped}.
 */
class MealsModule : FamilyModule {
    override val id = "meals"
    override val title = "Meals"
    override val description = "Dinner for each day → shopping list"
    override val icon: ImageVector = Icons.Filled.Restaurant

    private val MEALS = "meals"
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d")

    private fun ingredientsOf(r: Record): List<String> =
        (r.string("ingredients") ?: "").split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Put the ingredients of [meals] on the shopping list, noting which dinner
     * they are for. Returns how many items were added or brought back.
     */
    private fun shop(ctx: ModuleContext, meals: List<Record>): Int {
        var count = 0
        for (m in meals) {
            val day = runCatching { LocalDate.parse(m.id) }.getOrNull() ?: continue
            val note = "For ${day.format(DateTimeFormatter.ofPattern("EEEE"))}'s ${m.string("dinner") ?: "dinner"}"
            for (line in ingredientsOf(m)) {
                val (qty, name) = Quantity.parse(line)
                if (Shopping.add(ctx, name, qty, note) != null) count++
            }
            ctx.put(MEALS, m.id, mapOf("shopped" to true))
        }
        return count
    }

    @Composable
    override fun HomeCard(ctx: ModuleContext, open: () -> Unit) {
        val meals = ctx.records(MEALS).associateBy { it.id }
        val members = ctx.members()
        val today = LocalDate.now()
        HomeCardFrame(icon, "Dinners", "Plan ›", open) {
            listOf(today to "Tonight", today.plusDays(1) to "Tomorrow").forEach { (day, label) ->
                val m = meals[day.toString()]
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.width(78.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val dinner = m?.string("dinner")
                    Text(dinner ?: "Not planned yet", Modifier.weight(1f), fontSize = 15.sp,
                        fontWeight = if (dinner != null) FontWeight.Medium else FontWeight.Normal,
                        color = if (dinner != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    members.firstOrNull { it.email == m?.string("cook") }?.let { Avatar(it, 22.dp) }
                }
            }
        }
    }

    @Composable
    override fun Screen(ctx: ModuleContext) {
        val context = LocalContext.current
        val all = ctx.records(MEALS)
        val meals = all.associateBy { it.id }
        val members = ctx.members()
        var weekStart by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
        var editing by remember { mutableStateOf<LocalDate?>(null) }
        val days = (0..6).map { weekStart.plusDays(it.toLong()) }
        val weekMeals = days.mapNotNull { meals[it.toString()] }
        val toShop = weekMeals.filter { !it.bool("shopped") && ingredientsOf(it).isNotEmpty() && !LocalDate.parse(it.id).isBefore(LocalDate.now()) }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item {
                ScreenTitle("Meal plan", "Week of ${weekStart.format(DateTimeFormatter.ofPattern("d MMMM"))}") {
                    Row {
                        IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") }
                        IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week") }
                    }
                }
            }
            items(days, key = { it.toString() }) { day ->
                val m = meals[day.toString()]
                val isToday = day == LocalDate.now()
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { editing = day },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Row(Modifier.heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(day.format(dayFmt), Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Column(Modifier.weight(1f)) {
                            Text(m?.string("dinner") ?: "Tap to plan", fontSize = 15.sp,
                                fontWeight = if (m != null) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (m != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            val cook = members.firstOrNull { it.email == m?.string("cook") }
                            val bits = listOfNotNull(
                                cook?.let { "${it.name} cooking" },
                                m?.let { ingredientsOf(it).size.takeIf { n -> n > 0 }?.let { n -> "$n ingredients" } },
                                if (m?.bool("shopped") == true) "on the list ✓" else null,
                            )
                            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        members.firstOrNull { it.email == m?.string("cook") }?.let { Avatar(it, 26.dp) }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        val n = shop(ctx, toShop)
                        Toast.makeText(context, "$n items added to the shopping list", Toast.LENGTH_SHORT).show()
                    },
                    enabled = toShop.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Icon(Icons.Filled.AddShoppingCart, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (toShop.isEmpty()) "Ingredients are on the list" else "Add ${toShop.size} dinners' ingredients to the list")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        editing?.let { day -> EditMealDialog(ctx, day, meals[day.toString()], all, onDone = { editing = null }) }
    }

    @Composable
    private fun EditMealDialog(ctx: ModuleContext, day: LocalDate, meal: Record?, all: List<Record>, onDone: () -> Unit) {
        var dinner by remember { mutableStateOf(meal?.string("dinner") ?: "") }
        var cook by remember { mutableStateOf(meal?.string("cook")) }
        var ingredients by remember { mutableStateOf(meal?.string("ingredients") ?: "") }
        val members = ctx.members()
        // Past dinners, most recent first, so favourites are one tap away (with their ingredients).
        val favourites = all.filter { !it.string("dinner").isNullOrBlank() }
            .sortedByDescending { it.id }
            .distinctBy { it.string("dinner")!!.trim().lowercase() }
            .take(12)

        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(day.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(dinner, { dinner = it }, label = { Text("Dinner") }, singleLine = true)
                    if (favourites.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(favourites, key = { it.id }) { f ->
                                SuggestionChip(onClick = {
                                    dinner = f.string("dinner") ?: ""
                                    if (ingredients.isBlank()) ingredients = f.string("ingredients") ?: ""
                                }, label = { Text(f.string("dinner") ?: "") })
                            }
                        }
                    }
                    Text("Who's cooking", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PersonChips(members, cook, { cook = it }, allowNone = "Anyone")
                    OutlinedTextField(
                        ingredients, { ingredients = it },
                        label = { Text("Ingredients to buy") },
                        placeholder = { Text("One per line or comma separated, e.g. 500g mince, pasta, 2 onions") },
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dinner.isBlank()) {
                        if (meal != null) ctx.delete(MEALS, day.toString())
                    } else {
                        val changedIngredients = ingredients.trim() != (meal?.string("ingredients") ?: "")
                        ctx.create(MEALS, day.toString(), buildMap<String, Any?> {
                            put("dinner", dinner.trim())
                            put("cook", cook)
                            put("ingredients", ingredients.trim())
                            if (meal == null || changedIngredients) put("shopped", false)
                        })
                    }
                    onDone()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (meal != null) TextButton(onClick = { ctx.delete(MEALS, day.toString()); onDone() }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDone) { Text("Cancel") }
                }
            },
        )
    }

    override fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> =
        remoteOps.filter { it.collection == MEALS && it.field == "dinner" }
            .mapNotNull { op ->
                val day = runCatching { LocalDate.parse(op.id) }.getOrNull() ?: return@mapNotNull null
                "Dinner ${day.format(DateTimeFormatter.ofPattern("EEE d"))}: ${op.value as? String ?: ""}"
            }
}
