package com.custom.dayanddate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateIconGenerator {

    const val ICON_WIDTH = 96
    const val ICON_HEIGHT = 96
    private const val CENTER_X = ICON_WIDTH / 2f

    const val STRESS_TEST_DAY = "Ср"
    const val STRESS_TEST_DATE = "29"

    // Original calibrated defaults, fixed for every day and independent of saved preferences.
    private const val LINE1_SIZE = 46f
    private const val LINE2_SIZE = 46f
    private const val LINE1_Y = 40f
    private const val LINE2_Y = 83f
    private const val TEXT_SCALE_X = 0.97f
    private const val BOLDNESS = 2.5f
    private val russianLocale = Locale.forLanguageTag("ru")

    private const val WEIGHT_MEDIUM = 500
    private const val WEIGHT_BOLD = 700
    private const val BOLD_THRESHOLD = 1.5f

    /**
     * Resolves the Pixel status bar clock typeface:
     * `google-sans-text` -> `google-sans-flex` -> `sans-serif-medium`.
     * Uses the default bold weight (700 / [Typeface.BOLD]) so the stroke builds
     * cleanly on a bold glyph base.
     */
    private fun resolvePixelStatusBarTypeface(): Typeface {
        val useBoldBase = BOLDNESS >= BOLD_THRESHOLD
        val targetWeight = if (useBoldBase) WEIGHT_BOLD else WEIGHT_MEDIUM
        val targetStyle = if (useBoldBase) Typeface.BOLD else Typeface.NORMAL

        val primaryBase = Typeface.create("google-sans-text", targetStyle)
        if (primaryBase != null && primaryBase != Typeface.DEFAULT && primaryBase != Typeface.DEFAULT_BOLD) {
            return Typeface.create(primaryBase, targetWeight, false)
        }
        val primaryNamed = Typeface.create(
            if (useBoldBase) "google-sans-text-bold" else "google-sans-text-medium",
            targetStyle
        )
        if (primaryNamed != null && primaryNamed != Typeface.DEFAULT && primaryNamed != Typeface.DEFAULT_BOLD) {
            return primaryNamed
        }
        val secondaryBase = Typeface.create("google-sans-flex", targetStyle)
        if (secondaryBase != null && secondaryBase != Typeface.DEFAULT && secondaryBase != Typeface.DEFAULT_BOLD) {
            return Typeface.create(secondaryBase, targetWeight, false)
        }
        val secondaryNamed = Typeface.create(
            if (useBoldBase) "google-sans-flex-bold" else "google-sans-flex-medium",
            targetStyle
        )
        if (secondaryNamed != null && secondaryNamed != Typeface.DEFAULT && secondaryNamed != Typeface.DEFAULT_BOLD) {
            return secondaryNamed
        }
        val fallback = Typeface.create("sans-serif-medium", targetStyle) ?: Typeface.DEFAULT_BOLD
        return Typeface.create(fallback, targetWeight, false)
    }

    /**
     * Formats the short Russian day of week (e.g., "Ср"), regardless of device language.
     */
    fun formatDayOfWeek(date: Date = Date()): String {
        val raw = SimpleDateFormat("EEE", russianLocale).format(date)
        return toTitleCase(raw)
    }

    /**
     * Formats the day number of the month with NO leading zero (e.g., "4", "14", or "29").
     */
    fun formatMonthAndDay(date: Date = Date()): String {
        return SimpleDateFormat("d", Locale.US).format(date)
    }

    private fun toTitleCase(input: String): String {
        if (input.isEmpty()) return input
        return input.trimEnd('.').lowercase(russianLocale).replaceFirstChar { it.titlecase(russianLocale) }
    }

    /**
     * Renders the 96x96 ARGB_8888 status bar icon bitmap using the fixed default typography.
     */
    fun generateBitmap(
        context: Context,
        date: Date = Date()
    ): Bitmap {
        val settings = AppPreferences(context).loadSettings()
        return generateBitmapFromSettings(settings, date)
    }

    /**
     * Renders the 96x96 ARGB_8888 status bar icon bitmap from explicit [IconSettings].
     */
    fun generateBitmapFromSettings(
        settings: IconSettings,
        date: Date = Date()
    ): Bitmap {
        val dayText = if (settings.stressTestWidestDate) {
            STRESS_TEST_DAY
        } else {
            formatDayOfWeek(date)
        }

        val dateText = if (settings.stressTestWidestDate) {
            STRESS_TEST_DATE
        } else {
            formatMonthAndDay(date)
        }

        return renderTwoLineBitmap(
            line1 = dayText,
            line2 = dateText
        )
    }

    /**
     * Low-level 96x96 ARGB_8888 canvas renderer with transparent background, solid white
     * center-aligned stacked text, and fixed default stroke boldness.
     */
    fun renderTwoLineBitmap(
        line1: String,
        line2: String
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_WIDTH, ICON_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val typeface = resolvePixelStatusBarTypeface()
        val paintFlags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        val paintStyle = if (BOLDNESS > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL

        val line1Paint = Paint(paintFlags).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
            textSize = LINE1_SIZE
            textScaleX = TEXT_SCALE_X
            style = paintStyle
            strokeWidth = BOLDNESS
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val line2Paint = Paint(paintFlags).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
            textSize = LINE2_SIZE
            textScaleX = TEXT_SCALE_X
            style = paintStyle
            strokeWidth = BOLDNESS
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        canvas.drawText(line1, CENTER_X, LINE1_Y, line1Paint)
        canvas.drawText(line2, CENTER_X, LINE2_Y, line2Paint)

        return bitmap
    }

    /**
     * Converts the rendered 96x96 Bitmap into an [IconCompat] suitable for
     * `NotificationCompat.Builder.setSmallIcon(iconCompat)`.
     */
    fun generateIconCompat(
        context: Context,
        date: Date = Date()
    ): IconCompat {
        val bitmap = generateBitmap(context, date)
        return IconCompat.createWithBitmap(bitmap)
    }
}
