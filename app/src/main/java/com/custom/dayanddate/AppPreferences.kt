package com.custom.dayanddate

import android.content.Context
import android.content.SharedPreferences

enum class PriorityMode(val label: String, val description: String) {
    FORCED_HIGH(
        label = "Forced high priority",
        description = "Pinned immediately to the right of the status bar clock (sortKey 0000, IMPORTANCE_HIGH, PRIORITY_MAX, silent channel)."
    ),
    FORCED_SILENT(
        label = "Forced silent priority",
        description = "Sinks the notification card to the bottom Silent section of the notification shade (IMPORTANCE_LOW)."
    ),
    DYNAMIC(
        label = "Dynamic",
        description = "Forced high priority while shade is closed; automatically demotes/hides when SystemUI shade opens."
    )
}

data class IconSettings(
    val isEnabled: Boolean = AppPreferences.DEFAULT_ENABLED,
    val stressTestWidestDate: Boolean = false,
    val priorityMode: PriorityMode = PriorityMode.FORCED_HIGH
)

class AppPreferences(context: Context) {
    val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Removed typography, tap-action and launcher-icon preferences are deliberately ignored,
    // including values saved by earlier versions of the app.
    fun loadSettings(): IconSettings {
        val priorityName = prefs.getString(KEY_PRIORITY_MODE, PriorityMode.FORCED_HIGH.name)
            ?: PriorityMode.FORCED_HIGH.name
        return IconSettings(
            isEnabled = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
            stressTestWidestDate = prefs.getBoolean(KEY_STRESS_TEST_WIDEST, false),
            priorityMode = runCatching { PriorityMode.valueOf(priorityName) }
                .getOrDefault(PriorityMode.FORCED_HIGH)
        )
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setStressTestWidestDate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STRESS_TEST_WIDEST, enabled).apply()
    }

    fun setPriorityMode(mode: PriorityMode) {
        prefs.edit().putString(KEY_PRIORITY_MODE, mode.name).apply()
    }

    companion object {
        const val PREFS_NAME = "day_and_date_prefs"
        const val KEY_ENABLED = "pref_service_enabled"
        const val KEY_STRESS_TEST_WIDEST = "pref_stress_test_widest"
        const val KEY_PRIORITY_MODE = "pref_priority_mode"
        const val DEFAULT_ENABLED = false
    }
}
