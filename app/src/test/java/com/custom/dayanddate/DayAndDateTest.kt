package com.custom.dayanddate

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import org.robolectric.Shadows.shadowOf
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class DayAndDateTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        appPreferences = AppPreferences(context)
        DateNotificationService.isShadeOpen = false
    }

    @Test
    fun dateIconGenerator_renders96x96Argb8888AndFormatsDayNumberOnly() {
        val calSingle = Calendar.getInstance(TimeZone.getDefault(), Locale.US).apply {
            set(2026, Calendar.OCTOBER, 4, 12, 0, 0)
        }
        assertEquals("Вс", DateIconGenerator.formatDayOfWeek(calSingle.time))
        assertEquals("4", DateIconGenerator.formatMonthAndDay(calSingle.time))

        val calDouble = Calendar.getInstance(TimeZone.getDefault(), Locale.US).apply {
            set(2026, Calendar.OCTOBER, 14, 12, 0, 0)
        }
        assertEquals("Ср", DateIconGenerator.formatDayOfWeek(calDouble.time))
        assertEquals("14", DateIconGenerator.formatMonthAndDay(calDouble.time))

        assertEquals("Ср", DateIconGenerator.STRESS_TEST_DAY)
        assertEquals("29", DateIconGenerator.STRESS_TEST_DATE)

        val bitmap = DateIconGenerator.generateBitmap(context, calDouble.time)
        assertEquals(96, bitmap.width)
        assertEquals(96, bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)

        val iconCompat = DateIconGenerator.generateIconCompat(context, calDouble.time)
        assertNotNull(iconCompat)
    }

    @Test
    fun weekdays_areRussianForEveryDayEvenOnEnglishDevices() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 12, 12, 0, 0) }
            for (day in listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")) {
                assertEquals(day, DateIconGenerator.formatDayOfWeek(cal.time))
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun preferences_ignoreRemovedSettingsAndKeepSupportedOptions() {
        val date = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 14, 12, 0, 0) }.time
        val baseline = DateIconGenerator.generateBitmap(context, date)
        appPreferences.prefs.edit()
            .putBoolean("prefs_calibrated_v2", true)
            .putFloat("pref_line1_size", 20f)
            .putFloat("pref_line2_size", 18f)
            .putFloat("pref_line1_y", 20f)
            .putFloat("pref_line2_y", 56f)
            .putFloat("pref_text_scale_x", 0.7f)
            .putFloat("pref_boldness", 0f)
            .putString("pref_tap_action", "DO_NOTHING")
            .putInt("pref_launcher_icon", 3)
            .commit()
        assertEquals(IconSettings(), AppPreferences(context).loadSettings())
        assertTrue(baseline.sameAs(DateIconGenerator.generateBitmap(context, date)))

        val viewModel = MainViewModel(context, appPreferences)
        viewModel.setStressTestWidestDate(true)
        viewModel.setPriorityMode(PriorityMode.FORCED_SILENT)
        assertTrue(viewModel.settings.value.stressTestWidestDate)
        assertEquals(PriorityMode.FORCED_SILENT, AppPreferences(context).loadSettings().priorityMode)
        appPreferences.setEnabled(true)
        viewModel.refreshState(date)
        assertTrue(viewModel.settings.value.isEnabled)
    }

    @Test
    fun notificationTap_alwaysTargetsGoogleCalendarEvenWithLegacyPreferences() {
        appPreferences.prefs.edit().putString("pref_tap_action", "OPEN_APP").commit()
        val notification = DateNotificationService.buildStatusNotification(context, appPreferences.loadSettings())
        assertNotNull(notification.contentIntent)
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals("com.google.android.calendar", intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(), intent.data)
    }

    @Test
    fun launcher_hasOnlyMainActivityWithDefaultIcon() {
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)
        val activities = context.packageManager.queryIntentActivities(launcherIntent, 0)
        assertEquals(1, activities.size)
        assertEquals(MainActivity::class.java.name, activities.single().activityInfo.name)
        assertEquals(R.mipmap.ic_launcher_1, context.applicationInfo.icon)
    }

    @Test
    fun notificationService_usesChannelForcedMaxV3AndFutureWhenInHighPriority() {
        val manager = context.getSystemService(NotificationManager::class.java)
        DateNotificationService.ensureNotificationChannels(context, manager)

        assertNull(manager.getNotificationChannel("channel_forced_high"))
        val highChannel = manager.getNotificationChannel("channel_forced_max_v3")
        val silentChannel = manager.getNotificationChannel(DateNotificationService.CHANNEL_FORCED_SILENT)
        assertNotNull(highChannel)
        assertNotNull(silentChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, highChannel.importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW, silentChannel.importance)
        assertNull(highChannel.sound)
        assertFalse(highChannel.shouldVibrate())
        assertFalse(highChannel.canShowBadge())
        assertTrue(highChannel.canBypassDnd())
        assertTrue(silentChannel.canBypassDnd())
        assertEquals(Notification.VISIBILITY_SECRET, highChannel.lockscreenVisibility)
        assertEquals(Notification.VISIBILITY_SECRET, silentChannel.lockscreenVisibility)

        val fixedNow = 1_700_000_000_000L
        val highSettings = IconSettings(
            isEnabled = true,
            priorityMode = PriorityMode.FORCED_HIGH
        )
        val highNotification = DateNotificationService.buildStatusNotification(
            context = context,
            settings = highSettings,
            shadeOpen = false,
            nowMillis = fixedNow
        )
        assertEquals("channel_forced_max_v3", highNotification.channelId)
        assertEquals("0000", highNotification.sortKey)
        assertEquals(NotificationCompat.PRIORITY_MAX, highNotification.priority)
        assertEquals(fixedNow + 31_536_000_000L, highNotification.`when`)
        assertFalse(highNotification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true))
        assertEquals(Notification.VISIBILITY_SECRET, highNotification.visibility)
        assertEquals("Day & date icon", highNotification.extras.getString(Notification.EXTRA_TITLE))
        assertNull(highNotification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertNotNull(highNotification.contentIntent)

        val dynamicSettings = IconSettings(
            isEnabled = true,
            priorityMode = PriorityMode.DYNAMIC
        )
        val closedShadeNotif = DateNotificationService.buildStatusNotification(
            context = context,
            settings = dynamicSettings,
            shadeOpen = false,
            nowMillis = fixedNow
        )
        assertEquals("channel_forced_max_v3", closedShadeNotif.channelId)
        assertEquals("0000", closedShadeNotif.sortKey)
        assertEquals(NotificationCompat.PRIORITY_MAX, closedShadeNotif.priority)
        assertEquals(fixedNow + 31_536_000_000L, closedShadeNotif.`when`)
        assertNotNull(closedShadeNotif.contentIntent)

        val openShadeNotif = DateNotificationService.buildStatusNotification(
            context = context,
            settings = dynamicSettings,
            shadeOpen = true,
            nowMillis = fixedNow
        )
        assertEquals(DateNotificationService.CHANNEL_FORCED_SILENT, openShadeNotif.channelId)
        assertEquals("zzz", openShadeNotif.sortKey)
        assertEquals(NotificationCompat.PRIORITY_LOW, openShadeNotif.priority)
    }

    @Test
    fun midnightCalculation_is000001() {
        val triggerMillis = DateNotificationService.computeNextMidnightTriggerMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(1, cal.get(Calendar.SECOND))
    }
}
