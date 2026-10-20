package org.fossify.calendar.qa

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsDB
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.helpers.DAY
import org.fossify.calendar.helpers.FLAG_ALL_DAY
import org.fossify.calendar.helpers.LOCAL_CALENDAR_ID
import org.fossify.calendar.helpers.MONTH
import org.fossify.calendar.helpers.MONTHLY_VIEW
import org.fossify.calendar.helpers.REPEAT_SAME_DAY
import org.fossify.calendar.helpers.WEEK
import org.fossify.calendar.helpers.YEAR
import org.fossify.calendar.helpers.REMINDER_OFF
import org.fossify.calendar.helpers.TYPE_EVENT
import org.fossify.calendar.helpers.TYPE_TASK
import org.fossify.calendar.models.CalendarEntity
import org.fossify.calendar.models.Event
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

/**
 * mqa QA seeds (debug source set only). Loads one named scenario into the local database:
 *
 *   adb shell am broadcast -a org.fossify.calendar.QA_SEED -p org.fossify.calendar.debug --es scenario calendar-mixed
 *
 * Scenarios (every one is idempotent: it clears the data it owns, then writes it fresh):
 * - calendar-mixed: calendars "Regular event" (built in) + "Work"; one record of every kind the calendar stores:
 *   single and repeating events (daily, weekly, monthly, yearly, and one daily with a repeat limit: each repeat kind
 *   opens a different repeat-rule dialog in the editor), today / yesterday / future, an all-day event, a 30-minute
 *   reminder, a location, a description, a custom event colour, and two tasks (one with a reminder).
 * - calendar-long: calendar-mixed + 60 one-per-day "Meeting NN" events (for the event list view).
 * - calendar-empty: no events, no "Work" calendar.
 *
 * Fixed texts, ids, colours and clock times. The DAY is the device's current day: a calendar always opens on
 * today, so a fixed calendar date would put every event off screen. Every scenario also resets the view to
 * Month, so the first screen is fixed.
 */
class QaSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scenario = intent.getStringExtra("scenario").orEmpty()
        val pending = goAsync()
        Thread {
            val reply = try {
                when (scenario) {
                    "calendar-mixed" -> seed(context, long = false, empty = false)
                    "calendar-long" -> seed(context, long = true, empty = false)
                    "calendar-empty" -> seed(context, long = false, empty = true)
                    else -> null
                }
            } catch (e: Exception) {
                "failed $scenario: ${e.javaClass.simpleName}"
            }
            if (reply == null) {
                pending.resultCode = Activity.RESULT_CANCELED
                pending.resultData = "unknown scenario $scenario"
            } else {
                pending.resultCode = if (reply.startsWith("seeded")) Activity.RESULT_OK else Activity.RESULT_CANCELED
                pending.resultData = reply
            }
            pending.finish()
        }.start()
    }

    private fun seed(context: Context, long: Boolean, empty: Boolean): String {
        val helper = context.eventsHelper
        val config = context.config

        // Clear what the scenarios own: every event, and every local calendar except the built-in one.
        helper.deleteEvents(context.eventsDB.getEventIds().toMutableList(), deleteFromCalDAV = false, updateWidgets = false)
        val extra = context.calendarsDB.getCalendars().filter { it.id != LOCAL_CALENDAR_ID && it.caldavCalendarId == 0 }
        if (extra.isNotEmpty()) context.calendarsDB.deleteCalendars(extra)
        config.storedView = MONTHLY_VIEW

        if (empty) return "seeded calendar-empty"

        context.calendarsDB.insertOrUpdate(CalendarEntity(id = WORK_CALENDAR_ID, title = "Work", color = WORK_COLOR))
        config.addDisplayCalendar(LOCAL_CALENDAR_ID.toString())
        config.addDisplayCalendar(WORK_CALENDAR_ID.toString())

        val today = DateTime.now().withTimeAtStartOfDay()
        val events = arrayListOf(
            event(1001, "Team standup", today.withHourOfDay(9), minutes = 15, calendar = WORK_CALENDAR_ID).apply {
                repeatInterval = DAY
            },
            event(1002, "Dentist appointment", today.withHourOfDay(15), minutes = 60).apply {
                location = "Main Street 12"
                reminder1Minutes = 30
            },
            event(1003, "Project review", today.plusDays(1).withHourOfDay(11), minutes = 60, calendar = WORK_CALENDAR_ID).apply {
                description = "Quarterly review"
            },
            event(1004, "Birthday party", today.plusDays(2), minutes = 0).apply {
                endTS = today.plusDays(2).withHourOfDay(12).seconds()
                flags = FLAG_ALL_DAY
            },
            event(1005, "Pay rent", today.withHourOfDay(18), minutes = 0).apply { type = TYPE_TASK },
            event(1006, "Lunch with Sam", today.minusDays(1).withHourOfDay(12), minutes = 60),
            event(1007, "Gym", today.withHourOfDay(7), minutes = 60).apply {
                repeatInterval = WEEK
                repeatRule = 1 shl (today.dayOfWeek - 1)
            },
            event(1008, "Rent due", today.withHourOfDay(8), minutes = 15).apply {
                repeatInterval = MONTH
                repeatRule = REPEAT_SAME_DAY
            },
            event(1009, "Anniversary", today.plusDays(3).withHourOfDay(19), minutes = 120).apply {
                repeatInterval = YEAR
                repeatRule = REPEAT_SAME_DAY
            },
            event(1010, "Course week", today.withHourOfDay(17), minutes = 60).apply {
                repeatInterval = DAY
                repeatLimit = today.plusDays(5).seconds()
            },
            event(1011, "Concert", today.plusDays(1).withHourOfDay(20), minutes = 90).apply {
                color = CONCERT_COLOR
            },
            event(1012, "Call the bank", today.plusDays(1).withHourOfDay(10), minutes = 0).apply {
                type = TYPE_TASK
                reminder1Minutes = 10
            },
        )
        if (long) {
            for (n in 1..60) {
                events += event(1100L + n, "Meeting %02d".format(n), today.plusDays(n).withHourOfDay(10), minutes = 30)
            }
        }
        // insertEvents also makes each calendar visible and schedules the reminder.
        helper.insertEvents(events, addToCalDAV = false)
        return if (long) "seeded calendar-long" else "seeded calendar-mixed"
    }

    private fun event(id: Long, title: String, start: DateTime, minutes: Int, calendar: Long = LOCAL_CALENDAR_ID) = Event(
        id = id,
        startTS = start.seconds(),
        endTS = start.plusMinutes(minutes).seconds(),
        title = title,
        reminder1Minutes = REMINDER_OFF,
        importId = "mqa-qa-$id",
        timeZone = DateTimeZone.getDefault().id,
        calendarId = calendar,
        type = TYPE_EVENT,
    )

    private fun DateTime.seconds() = millis / 1000L

    private companion object {
        const val WORK_CALENDAR_ID = 100L
        const val WORK_COLOR = -0xbf7f01 // a fixed blue
        const val CONCERT_COLOR = -0x16e19d // a fixed red
    }
}
