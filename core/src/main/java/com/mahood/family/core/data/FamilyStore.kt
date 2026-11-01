package com.mahood.family.core.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.UUID

/**
 * The local, offline-first database every module reads and writes.
 *
 * Data is stored as (collection, record id, field) -> value with a timestamp.
 * Every local change is also queued in an outbox for the sync engine to upload,
 * and remote changes are merged field by field: newest timestamp wins.
 * Records are never hard-deleted; a "_deleted" field hides them.
 */
class FamilyStore(context: Context) : SQLiteOpenHelper(context, "family.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE records (collection TEXT NOT NULL, id TEXT NOT NULL, field TEXT NOT NULL, value TEXT, ts TEXT NOT NULL, PRIMARY KEY(collection, id, field))")
        db.execSQL("CREATE TABLE outbox (seq INTEGER PRIMARY KEY AUTOINCREMENT, op TEXT NOT NULL)")
        db.execSQL("CREATE TABLE seen_files (file_id TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private val version = MutableStateFlow(0L)

    /** Bumps whenever data changes; UIs re-query when it does. */
    val changes: StateFlow<Long> = version.asStateFlow()

    val deviceId: String by lazy {
        get("device_id") ?: UUID.randomUUID().toString().replace("-", "").take(12).also { set("device_id", it) }
    }

    val clock: Hlc by lazy { Hlc(deviceId) }

    // ---------- Simple local settings (not synced) ----------

    fun get(key: String): String? = readableDatabase.rawQuery("SELECT value FROM kv WHERE key = ?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    fun set(key: String, value: String?) {
        if (value == null) writableDatabase.delete("kv", "key = ?", arrayOf(key))
        else writableDatabase.insertWithOnConflict("kv", null, ContentValues().apply {
            put("key", key); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        bump()
    }

    // ---------- Synced records ----------

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    /** Set fields on a record (creating it if needed). Applied locally at once and queued for sync. */
    fun put(collection: String, id: String, fields: Map<String, Any?>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((field, value) in fields) {
                val op = Op(collection, id, field, value, clock.next())
                applyOp(db, op)
                db.insert("outbox", null, ContentValues().apply { put("op", op.toJson().toString()) })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    fun delete(collection: String, id: String) = put(collection, id, mapOf(DELETED to true))

    /** Merge changes that came from other phones. Returns the ops that actually changed something. */
    fun applyRemote(ops: List<Op>): List<Op> {
        val applied = mutableListOf<Op>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (op in ops) {
                clock.observe(op.ts)
                if (applyOp(db, op)) applied += op
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (applied.isNotEmpty()) bump()
        return applied
    }

    private fun applyOp(db: SQLiteDatabase, op: Op): Boolean {
        val current = db.rawQuery(
            "SELECT ts FROM records WHERE collection = ? AND id = ? AND field = ?",
            arrayOf(op.collection, op.id, op.field)
        ).use { if (it.moveToFirst()) it.getString(0) else null }
        if (!Op.wins(op.ts, current)) return false
        db.insertWithOnConflict("records", null, ContentValues().apply {
            put("collection", op.collection)
            put("id", op.id)
            put("field", op.field)
            put("value", Op.encodeValue(op.value))
            put("ts", op.ts)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    /** All live (not deleted) records in a collection. */
    fun query(collection: String): List<Record> {
        val byId = linkedMapOf<String, MutableMap<String, Any?>>()
        val latest = mutableMapOf<String, String>()
        readableDatabase.rawQuery(
            "SELECT id, field, value, ts FROM records WHERE collection = ?", arrayOf(collection)
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val ts = c.getString(3)
                byId.getOrPut(id) { mutableMapOf() }[c.getString(1)] = Op.decodeValue(c.getString(2))
                if (ts > (latest[id] ?: "")) latest[id] = ts
            }
        }
        return byId.filterValues { it[DELETED] != true }
            .map { (id, fields) -> Record(id, fields, latest[id] ?: "") }
    }

    fun record(collection: String, id: String): Record? = query(collection).firstOrNull { it.id == id }

    fun observe(collection: String): Flow<List<Record>> =
        version.map { query(collection) }.flowOn(Dispatchers.IO)

    // ---------- Sync bookkeeping ----------

    data class Outbox(val upToSeq: Long, val ops: List<Op>)

    fun outbox(): Outbox {
        var max = 0L
        val ops = mutableListOf<Op>()
        readableDatabase.rawQuery("SELECT seq, op FROM outbox ORDER BY seq", null).use { c ->
            while (c.moveToNext()) {
                max = c.getLong(0)
                ops += Op.fromJson(org.json.JSONObject(c.getString(1)))
            }
        }
        return Outbox(max, ops)
    }

    fun clearOutbox(upToSeq: Long) {
        writableDatabase.delete("outbox", "seq <= ?", arrayOf(upToSeq.toString()))
    }

    fun isSeen(fileId: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM seen_files WHERE file_id = ?", arrayOf(fileId)).use { it.moveToFirst() }

    fun markSeen(fileId: String) {
        writableDatabase.insertWithOnConflict("seen_files", null, ContentValues().apply { put("file_id", fileId) }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Wipe everything (used when leaving a family). */
    fun reset() {
        val db = writableDatabase
        listOf("records", "outbox", "seen_files", "kv").forEach { db.delete(it, null, null) }
        bump()
    }

    fun encodeOps(ops: List<Op>): String = JSONArray().apply { ops.forEach { put(it.toJson()) } }.toString()

    private fun bump() {
        version.value = version.value + 1
    }

    companion object {
        const val DELETED = "_deleted"
    }
}
