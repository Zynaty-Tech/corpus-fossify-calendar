package org.fossify.calendar.qa

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.RadioGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.timepicker.MaterialTimePicker
import org.fossify.calendar.BuildConfig
import org.fossify.calendar.R
import org.fossify.calendar.activities.CalendarPickerActivity
import org.fossify.calendar.activities.EventActivity
import org.fossify.calendar.activities.ManageCalendarsActivity
import org.fossify.calendar.activities.SelectTimeZoneActivity
import org.fossify.calendar.activities.SettingsActivity
import org.fossify.calendar.activities.SnoozeReminderActivity
import org.fossify.calendar.activities.TaskActivity
import org.fossify.calendar.activities.WidgetDateConfigureActivity
import org.fossify.calendar.activities.WidgetListConfigureActivity
import org.fossify.calendar.activities.WidgetMonthlyConfigureActivity
import org.fossify.calendar.fragments.DayFragmentsHolder
import org.fossify.calendar.fragments.EventListFragment
import org.fossify.calendar.fragments.MonthDayFragmentsHolder
import org.fossify.calendar.fragments.MonthFragmentsHolder
import org.fossify.calendar.fragments.WeekFragmentsHolder
import org.fossify.calendar.fragments.YearFragmentsHolder
import org.fossify.commons.activities.AboutActivity
import org.fossify.commons.activities.ContributorsActivity
import org.fossify.commons.activities.CustomizationActivity
import org.fossify.commons.activities.DonationActivity
import org.fossify.commons.activities.FAQActivity
import org.fossify.commons.activities.LicenseActivity

/**
 * mqa screen tags (debug only: every entry point returns at once unless BuildConfig.EXPOSE_TEST_TAGS).
 *
 * A tag is the View id `screen_tag_<Name>` (declared in res/values/mqa_screen_tags.xml, attached to nothing in
 * release). It is put on the DECOR VIEW of a window, never on an app view: the fragment roots of this app are
 * ViewPagers whose ids the pager adapters use, so overwriting them would change behaviour.
 *
 * - Activities: the decor of the activity window, by class.
 * - MainActivity: it is a router; its decor carries the tag of the calendar view fragment that is resumed now
 *   (Day, Week, Month, Month+day, Year, Event list). Pages inside a view's pager are not screens.
 * - Dialogs: each is its own window. Fossify dialogs are plain AlertDialogs shown synchronously in their
 *   constructor, so the call site calls [tagDialog] right after it: the newest application window gets the tag.
 * - DialogFragments (MaterialTimePicker): by class, when the fragment starts.
 *
 * Every name is a resource id literal, so it survives R8.
 */
object ScreenTags {
    val ACTIVITIES: Map<Class<out Activity>, Int> = mapOf(
        EventActivity::class.java to R.id.screen_tag_EventActivity,
        TaskActivity::class.java to R.id.screen_tag_TaskActivity,
        SettingsActivity::class.java to R.id.screen_tag_SettingsActivity,
        ManageCalendarsActivity::class.java to R.id.screen_tag_ManageCalendarsActivity,
        SelectTimeZoneActivity::class.java to R.id.screen_tag_SelectTimeZoneActivity,
        WidgetMonthlyConfigureActivity::class.java to R.id.screen_tag_WidgetMonthlyConfigureActivity,
        WidgetListConfigureActivity::class.java to R.id.screen_tag_WidgetListConfigureActivity,
        WidgetDateConfigureActivity::class.java to R.id.screen_tag_WidgetDateConfigureActivity,
        // Transparent hosts: they show only a dialog, which carries its own tag.
        CalendarPickerActivity::class.java to R.id.screen_tag_CalendarPickerActivity,
        SnoozeReminderActivity::class.java to R.id.screen_tag_SnoozeReminderActivity,
        // Fossify Commons library screens (the class is in a library; the tag is set from here).
        AboutActivity::class.java to R.id.screen_tag_AboutActivity,
        FAQActivity::class.java to R.id.screen_tag_FAQActivity,
        LicenseActivity::class.java to R.id.screen_tag_LicenseActivity,
        ContributorsActivity::class.java to R.id.screen_tag_ContributorsActivity,
        DonationActivity::class.java to R.id.screen_tag_DonationActivity,
        CustomizationActivity::class.java to R.id.screen_tag_CustomizationActivity,
    )

    /** The calendar views MainActivity swaps into R.id.fragments_holder. */
    val FRAGMENTS: Map<Class<out Fragment>, Int> = mapOf(
        DayFragmentsHolder::class.java to R.id.screen_tag_DayFragmentsHolder,
        WeekFragmentsHolder::class.java to R.id.screen_tag_WeekFragmentsHolder,
        MonthFragmentsHolder::class.java to R.id.screen_tag_MonthFragmentsHolder,
        MonthDayFragmentsHolder::class.java to R.id.screen_tag_MonthDayFragmentsHolder,
        YearFragmentsHolder::class.java to R.id.screen_tag_YearFragmentsHolder,
        EventListFragment::class.java to R.id.screen_tag_EventListFragment,
    )

    val DIALOG_FRAGMENTS: Map<Class<out DialogFragment>, Int> = mapOf(
        MaterialTimePicker::class.java to R.id.screen_tag_TimePickerDialog,
    )

    fun register(application: Application) {
        if (!BuildConfig.EXPOSE_TEST_TAGS) return
        application.registerActivityLifecycleCallbacks(activityCallbacks)
    }

    /**
     * Tags the dialog window that the caller has just shown. Call it on the line after the dialog's constructor.
     * Only an untagged application-dialog window is tagged, so a dialog that did not show never moves the tag
     * to the activity or to an older dialog.
     */
    @JvmStatic
    fun tagDialog(@IdRes id: Int) {
        if (!BuildConfig.EXPOSE_TEST_TAGS || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val root = WindowInspector.getGlobalWindowViews().lastOrNull() ?: return
        val type = (root.layoutParams as? WindowManager.LayoutParams)?.type ?: return
        if (type == WindowManager.LayoutParams.TYPE_APPLICATION && root.id == View.NO_ID) {
            root.id = id
        }
    }

    /**
     * Control ids for the options of a Fossify [org.fossify.commons.dialogs.RadioGroupDialog] the caller has just
     * shown (call it after [tagDialog]). The library gives each option the id of its INDEX, which the scanner
     * cannot read, so options that open DIFFERENT screens or dialogs (a view, "Custom", Event vs Task) were found
     * only by position. [idOf] maps each option's stable key (the RadioItem id or value) to a declared id, or 0 to
     * keep it. The library's own first-layout scroll still reads the index ids, so the ids are set after the
     * first layout.
     */
    @JvmStatic
    fun tagRadioOptions(keys: List<Any?>, idOf: (Any?) -> Int) {
        if (!BuildConfig.EXPOSE_TEST_TAGS || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val root = WindowInspector.getGlobalWindowViews().lastOrNull() ?: return
        val group = root.findViewById<RadioGroup>(org.fossify.commons.R.id.dialog_radio_group) ?: return
        val apply = Runnable {
            for (i in 0 until minOf(group.childCount, keys.size)) {
                val id = idOf(keys[i])
                if (id != 0) group.getChildAt(i).id = id
            }
        }
        group.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                group.viewTreeObserver.removeOnGlobalLayoutListener(this)
                group.post(apply)
            }
        })
    }

    private val fragmentCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            val id = FRAGMENTS[f.javaClass] ?: return
            f.activity?.window?.peekDecorView()?.id = id
        }

        // A DialogFragment shows its dialog in onStart: tag the dialog's own window.
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            val id = DIALOG_FRAGMENTS.entries.firstOrNull { it.key.isInstance(f) }?.value ?: return
            (f as DialogFragment).dialog?.window?.peekDecorView()?.id = id
        }
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            (activity as? FragmentActivity)?.supportFragmentManager
                ?.registerFragmentLifecycleCallbacks(fragmentCallbacks, true)
        }

        // After onCreate, so the decor is never forced before the activity sets its content or window features.
        override fun onActivityStarted(activity: Activity) {
            val id = ACTIVITIES[activity.javaClass] ?: return
            activity.window?.peekDecorView()?.id = id
        }

        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
