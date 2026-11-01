package com.mahood.family.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.mahood.family.core.data.Op

/**
 * The contract every feature implements. A module owns its own screen, its Home
 * card and the synced collections it stores data in. Sync, encryption, sign-in
 * and notifications are provided by the core through [ModuleContext].
 *
 * To add a feature: create a Gradle module under modules/, implement this
 * interface, and add it to the list in FamilyApplication.
 */
interface FamilyModule {
    /** Stable id, also used in navigation routes and settings. Never change it once released. */
    val id: String
    val title: String
    val description: String
    val icon: ImageVector

    /** Whether the module is switched on for a new family. */
    val enabledByDefault: Boolean get() = true

    /** The module's card on the Home dashboard. [open] navigates to the module's screen. */
    @Composable
    fun HomeCard(ctx: ModuleContext, open: () -> Unit)

    /** The module's full screen. */
    @Composable
    fun Screen(ctx: ModuleContext)

    /**
     * Called after a sync brings in changes made by other family members.
     * Return short lines to show in a notification, or an empty list for none.
     */
    fun notificationLines(ctx: ModuleContext, remoteOps: List<Op>): List<String> = emptyList()
}
