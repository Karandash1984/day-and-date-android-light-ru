package com.custom.dayanddate

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

class MainViewModel(
    private val appContext: Context,
    private val preferences: AppPreferences = AppPreferences(appContext)
) : ViewModel() {

    private val _settings = MutableStateFlow(preferences.loadSettings())
    val settings: StateFlow<IconSettings> = _settings.asStateFlow()

    private val _previewBitmap = MutableStateFlow(
        DateIconGenerator.generateBitmapFromSettings(_settings.value)
    )
    val previewBitmap: StateFlow<Bitmap> = _previewBitmap.asStateFlow()

    fun refreshState(date: Date = Date()) {
        val loaded = preferences.loadSettings()
        _settings.value = loaded
        _previewBitmap.value = DateIconGenerator.generateBitmapFromSettings(loaded, date)
    }

    fun setServiceEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
        refreshState()
        if (enabled) {
            DateNotificationService.startOrRefresh(appContext)
        } else {
            DateNotificationService.stop(appContext)
        }
    }

    fun setStressTestWidestDate(enabled: Boolean) {
        preferences.setStressTestWidestDate(enabled)
        refreshState()
        notifyServiceIfRunning()
    }

    fun setPriorityMode(mode: PriorityMode) {
        preferences.setPriorityMode(mode)
        refreshState()
        notifyServiceIfRunning()
    }

    private fun notifyServiceIfRunning() {
        if (_settings.value.isEnabled) {
            DateNotificationService.startOrRefresh(appContext)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(context.applicationContext) as T
        }
    }
}
