package com.mahood.family

import android.app.Application
import com.mahood.family.board.BoardModule
import com.mahood.family.calendar.CalendarModule
import com.mahood.family.core.Family
import com.mahood.family.core.sync.Notifier
import com.mahood.family.core.sync.SyncScheduler
import com.mahood.family.lists.ListsModule

class FamilyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Every feature module is registered here, in bottom-bar order.
        // A new feature = a new Gradle module + one line in this list.
        Family.init(
            this,
            listOf(
                CalendarModule(),
                ListsModule(),
                BoardModule(),
            ),
        )
        Notifier.launchActivity = MainActivity::class.java
        Notifier.appIcon = R.drawable.ic_notification
        Notifier.ensureChannel(this)
        if (Family.isSetUp) SyncScheduler.schedulePeriodic(this)
    }
}
