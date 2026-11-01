package com.mahood.family.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.mahood.family.core.data.FamilyStore
import com.mahood.family.core.data.Record
import kotlinx.coroutines.flow.map

/** Everything a module may use. Modules never talk to Google Drive or the network directly. */
class ModuleContext(
    val appContext: Context,
    val store: FamilyStore,
) {
    val me: Member? get() = Family.me(store)

    /** Ask the sync engine to push and pull soon (after a local change, for example). */
    fun requestSync() = Family.requestSync()

    @Composable
    fun records(collection: String): List<Record> {
        val flow = remember(collection) { store.observe(collection) }
        val list by flow.collectAsState(initial = emptyList())
        return list
    }

    @Composable
    fun members(): List<Member> {
        val flow = remember { store.observe(Member.COLLECTION).map { rs -> rs.map(Member::from).sortedBy { it.name.lowercase() } } }
        val list by flow.collectAsState(initial = emptyList())
        return list
    }

    /** Write fields to a synced record and schedule a sync. */
    fun put(collection: String, id: String, fields: Map<String, Any?>) {
        store.put(collection, id, fields)
        requestSync()
    }

    fun delete(collection: String, id: String) {
        store.delete(collection, id)
        requestSync()
    }
}
