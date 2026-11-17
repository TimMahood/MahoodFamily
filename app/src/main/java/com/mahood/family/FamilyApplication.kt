package com.mahood.family

import android.app.Application
import com.mahood.family.baby.BabyModule
import com.mahood.family.board.BoardModule
import com.mahood.family.checkin.CheckinModule
import com.mahood.family.chores.ChoresModule
import com.mahood.family.health.HealthModule
import com.mahood.family.calendar.CalendarModule
import com.mahood.family.core.Family
import com.mahood.family.core.sync.Notifier
import com.mahood.family.core.sync.SyncScheduler
import com.mahood.family.lists.ListsModule
import com.mahood.family.meals.MealsModule
import com.mahood.family.milestones.MilestonesModule
import com.mahood.family.school.SchoolModule

class FamilyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Every feature module is registered here. The order is the order of the
        // Home cards; the first few enabled ones also get a bottom-bar tab.
        // A new feature = a new Gradle module + one line in this list.
        Family.init(
            this,
            listOf(
                CalendarModule(),
                ListsModule(),
                BoardModule(),
                CheckinModule(),
                MealsModule(),
                BabyModule(),
                SchoolModule(),
                ChoresModule(),
                HealthModule(),
                MilestonesModule(),
            ),
        )
        Notifier.launchActivity = MainActivity::class.java
        Notifier.appIcon = R.drawable.ic_notification
        Notifier.ensureChannel(this)
        if (Family.isSetUp) SyncScheduler.schedulePeriodic(this)
    }
}
