package com.mahood.family.core.shopping

import com.mahood.family.core.ModuleContext
import com.mahood.family.core.data.Record

/**
 * The shared shopping list's storage, so other modules (meal planner, baby log...)
 * can add items without depending on the Lists module.
 *
 * Collections: "lists" {name, order}, "items" {list, name, qty, note, aisle, aisleManual, done, by, created},
 * "aisle_memory" {aisle} keyed by normalised item name (corrections the family made).
 */
object Shopping {
    const val LISTS = "lists"
    const val ITEMS = "items"
    const val LEARNED = "aisle_memory"
    const val DEFAULT_LIST = "weekly-shop"

    fun sortedLists(lists: List<Record>) =
        lists.sortedWith(compareBy<Record>({ it.long("order") ?: Long.MAX_VALUE }, { it.string("name") ?: "" }))

    /** The main shopping list, created if there isn't one yet. */
    fun mainListId(ctx: ModuleContext): String {
        val lists = sortedLists(ctx.store.query(LISTS))
        lists.firstOrNull { it.id == DEFAULT_LIST }?.let { return it.id }
        lists.firstOrNull()?.let { return it.id }
        ctx.create(LISTS, DEFAULT_LIST, mapOf("name" to "Weekly shop", "order" to 0L))
        return DEFAULT_LIST
    }

    fun learned(records: List<Record>): Map<String, String> =
        records.mapNotNull { r -> r.string("aisle")?.let { r.id to it } }.toMap()

    fun learned(ctx: ModuleContext): Map<String, String> = learned(ctx.store.query(LEARNED))

    /** The aisle to show an item under: a manual choice sticks, otherwise the (improving) guess. */
    fun aisleOf(item: Record, learned: Map<String, String>): String {
        val stored = item.string("aisle")
        if (item.bool("aisleManual") && stored != null && stored in Aisles.order) return stored
        return Aisles.guess(item.string("name") ?: "", learned)
    }

    /** Move an item to another aisle and remember it for next time, on every phone. */
    fun moveToAisle(ctx: ModuleContext, item: Record, aisle: String) {
        ctx.put(ITEMS, item.id, mapOf("aisle" to aisle, "aisleManual" to true))
        val key = Aisles.normalise(item.string("name") ?: "")
        if (key.isNotEmpty()) ctx.put(LEARNED, key, mapOf("aisle" to aisle))
    }

    /**
     * Add an item, or bring back the same item if it is already on the list
     * (unticking it if it was in the trolley). Returns the item's id.
     */
    fun add(ctx: ModuleContext, name: String, qty: String? = null, note: String? = null, listId: String? = null): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val list = listId ?: mainListId(ctx)
        val key = Aisles.normalise(clean)
        val existing = ctx.store.query(ITEMS).firstOrNull { it.string("list") == list && Aisles.normalise(it.string("name") ?: "") == key }
        if (existing != null) {
            val fields = mutableMapOf<String, Any?>()
            if (existing.bool("done")) {
                fields["done"] = false
                fields["created"] = System.currentTimeMillis()
                fields["by"] = ctx.me?.email
                fields["qty"] = qty
                fields["note"] = note
            } else {
                if (!qty.isNullOrBlank()) fields["qty"] = qty
                if (!note.isNullOrBlank() && note != existing.string("note")) {
                    fields["note"] = listOfNotNull(existing.string("note")?.takeIf { it.isNotBlank() }, note).joinToString(" · ")
                }
            }
            if (fields.isNotEmpty()) ctx.put(ITEMS, existing.id, fields)
            return existing.id
        }
        val id = ctx.store.newId()
        ctx.put(ITEMS, id, mapOf(
            "list" to list,
            "name" to clean,
            "qty" to qty,
            "note" to note,
            "aisle" to Aisles.guess(clean, learned(ctx)),
            "done" to false,
            "by" to ctx.me?.email,
            "created" to System.currentTimeMillis(),
        ))
        return id
    }
}
