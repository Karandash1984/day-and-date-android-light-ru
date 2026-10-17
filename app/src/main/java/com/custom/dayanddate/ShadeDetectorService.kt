package com.custom.dayanddate

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class ShadeDetectorService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val settings = AppPreferences(this).loadSettings()
        if (!settings.isEnabled) {
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val eventPkg = event.packageName?.toString().orEmpty()
            if (eventPkg != packageName && !DateNotificationService.isShadeOpen) {
                DateNotificationService.onExternalNotificationPosted(this)
            }
            return
        }

        if (settings.priorityMode != PriorityMode.DYNAMIC) {
            return
        }

        val detectedShadeOpen = detectShadeOpen(event)
        if (detectedShadeOpen != null) {
            DateNotificationService.onShadeVisibilityChanged(this, detectedShadeOpen)
        }
    }

    private fun detectShadeOpen(event: AccessibilityEvent): Boolean? {
        val pkg = event.packageName?.toString().orEmpty()
        val cls = event.className?.toString().orEmpty()
        val text = event.text.joinToString(" ")

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg == SYSTEM_UI_PACKAGE) {
                val shadeKeywords = listOf(
                    "NotificationShade",
                    "StatusBar",
                    "QuickSettings",
                    "Notification panel",
                    "Notification shade",
                    "FrameLayout"
                )
                if (shadeKeywords.any { cls.contains(it, ignoreCase = true) || text.contains(it, ignoreCase = true) }) {
                    return true
                }
            } else if (pkg.isNotEmpty() && pkg != packageName) {
                return false
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val windowList = runCatching { windows }.getOrNull()
            if (!windowList.isNullOrEmpty()) {
                val shadeWindowFocused = windowList.any { window ->
                    window.type == AccessibilityWindowInfo.TYPE_SYSTEM &&
                        (window.isFocused || window.isActive) &&
                        window.title?.let { title ->
                            title.contains("Notification", ignoreCase = true) ||
                                title.contains("Quick Settings", ignoreCase = true) ||
                                title.contains("Status", ignoreCase = true)
                        } == true
                }
                return shadeWindowFocused
            }
        }

        return null
    }

    override fun onInterrupt() {
        DateNotificationService.onShadeVisibilityChanged(this, false)
    }

    override fun onDestroy() {
        DateNotificationService.onShadeVisibilityChanged(this, false)
        super.onDestroy()
    }

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, ShadeDetectorService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expectedComponent }
        }
    }
}
