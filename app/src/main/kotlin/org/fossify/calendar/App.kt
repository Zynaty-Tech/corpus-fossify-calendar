package org.fossify.calendar

import org.fossify.calendar.extensions.hasDummyAlarm
import org.fossify.calendar.jobs.AppStartupWorker
import org.fossify.calendar.qa.ScreenTags
import org.fossify.commons.FossifyApp

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        ScreenTags.register(this)
        if (!hasDummyAlarm()) {
            AppStartupWorker.start(this)
        }
    }
}
