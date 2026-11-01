package com.mahood.family.core.data

import org.json.JSONArray
import org.json.JSONObject

/** One field change: set [field] of record [id] in [collection] to [value] at time [ts]. */
data class Op(
    val collection: String,
    val id: String,
    val field: String,
    val value: Any?,
    val ts: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("c", collection)
        .put("i", id)
        .put("f", field)
        .put("v", encodeValue(value))
        .put("t", ts)

    companion object {
        fun fromJson(o: JSONObject) = Op(
            collection = o.getString("c"),
            id = o.getString("i"),
            field = o.getString("f"),
            value = decodeValue(o.getString("v")),
            ts = o.getString("t"),
        )

        /** Values are stored as a one-element JSON array so strings, numbers, booleans and null all round-trip. */
        fun encodeValue(v: Any?): String = JSONArray().put(v ?: JSONObject.NULL).toString()

        fun decodeValue(s: String): Any? {
            val v = JSONArray(s).get(0)
            return if (v == JSONObject.NULL) null else v
        }

        /** Last-writer-wins: should an incoming op with [incomingTs] replace a field last written at [currentTs]? */
        fun wins(incomingTs: String, currentTs: String?): Boolean = currentTs == null || incomingTs > currentTs
    }
}

/** A record as seen by modules: its id, current field values and the time it was last changed. */
data class Record(val id: String, val fields: Map<String, Any?>, val updated: String) {
    fun string(name: String): String? = fields[name] as? String
    fun bool(name: String): Boolean = fields[name] as? Boolean ?: false
    fun long(name: String): Long? = (fields[name] as? Number)?.toLong()
    val updatedMillis: Long get() = Hlc.millisOf(updated)
}
