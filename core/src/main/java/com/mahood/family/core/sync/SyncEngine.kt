package com.mahood.family.core.sync

import android.content.Context
import com.mahood.family.core.Family
import com.mahood.family.core.data.Crypto
import com.mahood.family.core.data.Op
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sync through the shared Google Drive folder, which acts as a dumb mailbox:
 *  - push: this phone's unsent changes are encrypted and uploaded as a new file
 *    named ops-<device>-<time>.bin. Phones never edit each other's files.
 *  - pull: any file we have not seen yet is downloaded, decrypted and merged.
 * Because merging is last-writer-wins per field, the order files arrive in does
 * not matter and every phone ends up with the same data.
 */
object SyncEngine {
    sealed interface Status {
        data object Idle : Status
        data object Syncing : Status
        data class Done(val atMillis: Long) : Status
        data class Failed(val message: String, val needsSignIn: Boolean = false) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    private val mutex = Mutex()

    /** Returns the changes that arrived from other phones. */
    suspend fun sync(context: Context): List<Op> = mutex.withLock {
        if (!Family.isSetUp) return emptyList()
        _status.value = Status.Syncing
        try {
            val token = GoogleAuth.tokenSilently(context) ?: throw NeedsSignInException()
            val remote = withContext(Dispatchers.IO) { syncWith(DriveClient(token)) }
            _status.value = Status.Done(System.currentTimeMillis())
            remote
        } catch (e: NeedsSignInException) {
            _status.value = Status.Failed("Sign in to Google again to sync", needsSignIn = true)
            emptyList()
        } catch (e: Exception) {
            _status.value = Status.Failed(e.message ?: "Sync failed")
            emptyList()
        }
    }

    private fun syncWith(drive: DriveClient): List<Op> {
        val store = Family.store
        val folder = Family.folderId!!
        val key = Family.familyKey!!

        // Push
        val outbox = store.outbox()
        if (outbox.ops.isNotEmpty()) {
            val payload = JSONObject()
                .put("v", 1)
                .put("device", store.deviceId)
                .put("author", Family.myEmail)
                .put("ops", JSONArray(store.encodeOps(outbox.ops)))
            val name = "ops-${store.deviceId}-${System.currentTimeMillis()}.bin"
            val id = drive.upload(folder, name, Crypto.encrypt(key, payload.toString().toByteArray()))
            store.markSeen(id)
            store.clearOutbox(outbox.upToSeq)
        }

        // Pull
        val remote = mutableListOf<Op>()
        for (file in drive.list(folder)) {
            if (!file.name.startsWith("ops-") || store.isSeen(file.id)) continue
            try {
                val plain = Crypto.decrypt(key, drive.download(file.id))
                val o = JSONObject(String(plain))
                val arr = o.getJSONArray("ops")
                val ops = (0 until arr.length()).map { Op.fromJson(arr.getJSONObject(it)) }
                val applied = store.applyRemote(ops)
                if (o.optString("device") != store.deviceId) remote += applied
            } catch (e: DriveException) {
                throw e
            } catch (e: Exception) {
                // Unreadable file (wrong key or corrupt): skip it for good rather than retrying forever.
            }
            store.markSeen(file.id)
        }
        return remote
    }
}
