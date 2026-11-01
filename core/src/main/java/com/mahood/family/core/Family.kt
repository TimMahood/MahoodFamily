package com.mahood.family.core

import android.content.Context
import android.net.Uri
import com.mahood.family.core.data.FamilyStore
import com.mahood.family.core.sync.SyncScheduler

/** App-wide wiring: the store, the registered modules and this phone's family settings. */
object Family {
    lateinit var store: FamilyStore
        private set
    lateinit var modules: List<FamilyModule>
        private set
    lateinit var context: ModuleContext
        private set
    private lateinit var appContext: Context

    fun init(appContext: Context, modules: List<FamilyModule>) {
        this.appContext = appContext.applicationContext
        store = FamilyStore(this.appContext)
        this.modules = modules
        context = ModuleContext(this.appContext, store)
    }

    // Local (per-phone) settings keys
    private const val K_FAMILY_NAME = "family_name"
    private const val K_FOLDER = "drive_folder_id"
    private const val K_KEY = "family_key"
    private const val K_EMAIL = "my_email"
    private const val K_ENABLED = "enabled_modules"

    val isSetUp: Boolean get() = folderId != null && familyKey != null && myEmail != null

    var familyName: String?
        get() = store.get(K_FAMILY_NAME)
        set(v) = store.set(K_FAMILY_NAME, v)
    var folderId: String?
        get() = store.get(K_FOLDER)
        set(v) = store.set(K_FOLDER, v)
    var familyKey: String?
        get() = store.get(K_KEY)
        set(v) = store.set(K_KEY, v)
    var myEmail: String?
        get() = store.get(K_EMAIL)
        set(v) = store.set(K_EMAIL, v)

    fun me(store: FamilyStore): Member? {
        val email = store.get(K_EMAIL) ?: return null
        return store.record(Member.COLLECTION, email)?.let(Member::from)
    }

    fun enabledModules(): List<FamilyModule> {
        val saved = store.get(K_ENABLED)?.split(",")?.filter { it.isNotBlank() }?.toSet()
        return modules.filter { if (saved == null) it.enabledByDefault else it.id in saved }
    }

    fun setEnabled(module: FamilyModule, enabled: Boolean) {
        val current = enabledModules().map { it.id }.toMutableSet()
        if (enabled) current += module.id else current -= module.id
        store.set(K_ENABLED, current.joinToString(","))
    }

    fun requestSync() = SyncScheduler.syncSoon(appContext)

    // ---------- Invites ----------

    data class Invite(val familyName: String, val folderId: String, val key: String)

    fun inviteText(): String {
        val uri = Uri.Builder().scheme("mahoodfamily").authority("join")
            .appendQueryParameter("n", familyName ?: "Family")
            .appendQueryParameter("f", folderId)
            .appendQueryParameter("k", familyKey)
            .build()
        return uri.toString()
    }

    fun parseInvite(text: String): Invite? {
        val start = text.indexOf("mahoodfamily://")
        if (start < 0) return null
        val uri = Uri.parse(text.substring(start).trim().split(Regex("\\s")).first())
        val folder = uri.getQueryParameter("f") ?: return null
        val key = uri.getQueryParameter("k") ?: return null
        return Invite(uri.getQueryParameter("n") ?: "Family", folder, key)
    }

    fun leave() {
        SyncScheduler.cancel(appContext)
        store.reset()
    }
}
