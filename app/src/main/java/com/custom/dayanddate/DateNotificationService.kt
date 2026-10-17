package com.custom.dayanddate

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class DateNotificationService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var appPreferences: AppPreferences
    private lateinit var notificationManager: NotificationManager
    private var receiverRegistered = false

    private val timeAndDateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                ACTION_MIDNIGHT_ROLLOVER -> {
                    updateNotification()
                    scheduleNextMidnightRefresh(this@DateNotificationService)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appPreferences = AppPreferences(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        ensureNotificationChannels(this, notificationManager)
        appPreferences.prefs.registerOnSharedPreferenceChangeListener(this)
        registerTimeAndDateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cancelMidnightRefresh(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHADE_STATE_CHANGED -> {
                isShadeOpen = intent.getBooleanExtra(EXTRA_SHADE_OPEN, false)
                updateNotification()
                return START_STICKY
            }
        }

        val notification = buildStatusNotification(this, appPreferences.loadSettings(), isShadeOpen)
        startForegroundCompat(notification)
        scheduleNextMidnightRefresh(this)
        return START_STICKY
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val settings = appPreferences.loadSettings()
        if (!settings.isEnabled) {
            cancelMidnightRefresh(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        updateNotification()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateNotification() {
        val settings = appPreferences.loadSettings()
        if (!settings.isEnabled) return
        ensureNotificationChannels(this, notificationManager)
        val notification = buildStatusNotification(this, settings, isShadeOpen)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun registerTimeAndDateReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(ACTION_MIDNIGHT_ROLLOVER)
        }
        ContextCompat.registerReceiver(
            this,
            timeAndDateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(timeAndDateReceiver) }
            receiverRegistered = false
        }
        appPreferences.prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val LEGACY_CHANNEL_FORCED_HIGH = "channel_forced_high"
        const val CHANNEL_FORCED_HIGH = "channel_forced_max_v3"
        const val CHANNEL_FORCED_SILENT = "channel_forced_silent"

        const val FUTURE_WHEN_OFFSET_MS = 31_536_000_000L

        const val ACTION_START_OR_REFRESH = "com.custom.dayanddate.ACTION_START_OR_REFRESH"
        const val ACTION_STOP = "com.custom.dayanddate.ACTION_STOP"
        const val ACTION_SHADE_STATE_CHANGED = "com.custom.dayanddate.ACTION_SHADE_STATE_CHANGED"
        const val ACTION_MIDNIGHT_ROLLOVER = "com.custom.dayanddate.ACTION_MIDNIGHT_ROLLOVER"
        const val EXTRA_SHADE_OPEN = "extra_shade_open"

        private const val MIDNIGHT_ALARM_REQUEST_CODE = 2001
        private const val MIDNIGHT_WINDOW_MS = 5_000L

        @Volatile
        var isShadeOpen: Boolean = false

        fun ensureNotificationChannels(context: Context, manager: NotificationManager) {
            runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_FORCED_HIGH) }

            val highChannel = NotificationChannel(
                CHANNEL_FORCED_HIGH,
                context.getString(R.string.channel_high_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_high_desc)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            val silentChannel = NotificationChannel(
                CHANNEL_FORCED_SILENT,
                context.getString(R.string.channel_silent_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_silent_desc)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            manager.createNotificationChannels(listOf(highChannel, silentChannel))
        }

        fun resolveEffectiveHighPriority(mode: PriorityMode, shadeOpen: Boolean): Boolean {
            return when (mode) {
                PriorityMode.FORCED_HIGH -> true
                PriorityMode.FORCED_SILENT -> false
                PriorityMode.DYNAMIC -> !shadeOpen
            }
        }

        fun buildStatusNotification(
            context: Context,
            settings: IconSettings,
            shadeOpen: Boolean = isShadeOpen,
            nowMillis: Long = System.currentTimeMillis()
        ): Notification {
            val useHighPriority = resolveEffectiveHighPriority(settings.priorityMode, shadeOpen)
            val channelId = if (useHighPriority) CHANNEL_FORCED_HIGH else CHANNEL_FORCED_SILENT
            val iconCompat = DateIconGenerator.generateIconCompat(context)

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(iconCompat)
                .setContentTitle("Day & date icon")
                .setContentText(null)
                .setSubText(null)
                .setShowWhen(false)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

            if (useHighPriority) {
                // Do NOT call setSilent(true) in high priority mode; silence is enforced via
                // channel_forced_max_v3 so SystemUI NotificationRankingManager keeps maximum rank.
                builder.setPriority(NotificationCompat.PRIORITY_MAX)
                builder.setSortKey("0000")
                builder.setWhen(nowMillis + FUTURE_WHEN_OFFSET_MS)
            } else {
                builder.setSilent(true)
                builder.setPriority(NotificationCompat.PRIORITY_LOW)
                builder.setSortKey("zzz")
            }

            builder.setContentIntent(createTapPendingIntent(context))
            return builder.build()
        }

        fun createTapPendingIntent(context: Context): PendingIntent {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val calendarPackage = "com.google.android.calendar"
            val calendarIntent = context.packageManager.getLaunchIntentForPackage(calendarPackage)
                ?: Intent(Intent.ACTION_VIEW).apply {
                    data = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                    // Never route to app settings or a different calendar application.
                    setPackage(calendarPackage)
                }
            calendarIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(context, 101, calendarIntent, flags)
        }

        /**
         * Computes the next 00:00:01 local timestamp for daily midnight rollover.
         */
        fun computeNextMidnightTriggerMillis(nowMillis: Long = System.currentTimeMillis()): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis
        }

        fun scheduleNextMidnightRefresh(context: Context, nowMillis: Long = System.currentTimeMillis()) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAtMillis = computeNextMidnightTriggerMillis(nowMillis)
            val intent = Intent(context, BootReceiver::class.java).apply {
                action = ACTION_MIDNIGHT_ROLLOVER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                MIDNIGHT_WINDOW_MS,
                pendingIntent
            )
        }

        fun cancelMidnightRefresh(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, BootReceiver::class.java).apply {
                action = ACTION_MIDNIGHT_ROLLOVER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun startOrRefresh(context: Context) {
            val intent = Intent(context, DateNotificationService::class.java).apply {
                action = ACTION_START_OR_REFRESH
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DateNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }

        fun onShadeVisibilityChanged(context: Context, shadeOpen: Boolean) {
            if (isShadeOpen == shadeOpen) return
            isShadeOpen = shadeOpen
            val settings = AppPreferences(context).loadSettings()
            if (!settings.isEnabled || settings.priorityMode != PriorityMode.DYNAMIC) return

            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureNotificationChannels(context, manager)
            val notification = buildStatusNotification(context, settings, shadeOpen)
            manager.notify(NOTIFICATION_ID, notification)
        }

        fun onExternalNotificationPosted(context: Context) {
            if (isShadeOpen) return
            val settings = AppPreferences(context).loadSettings()
            if (!settings.isEnabled) return
            if (!resolveEffectiveHighPriority(settings.priorityMode, isShadeOpen)) return

            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureNotificationChannels(context, manager)
            val notification = buildStatusNotification(context, settings, isShadeOpen)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
