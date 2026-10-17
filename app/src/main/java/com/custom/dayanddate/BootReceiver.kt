package com.custom.dayanddate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val settings = AppPreferences(context).loadSettings()
        if (!settings.isEnabled) return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            DateNotificationService.ACTION_MIDNIGHT_ROLLOVER -> {
                DateNotificationService.startOrRefresh(context)
                DateNotificationService.scheduleNextMidnightRefresh(context)
            }
        }
    }
}
