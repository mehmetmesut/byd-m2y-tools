package com.m2y.eq

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/** Vertical band sliders drawn in one view, so 31 bands stay cheap and fit the car screen width. */
class BandsView(context: Context, private val palette: Palette, private val onChange: (index: Int, db: Float) -> Unit) : View(context) {
    var settings: EqSettings = EqSettings.FLAT
        set(value) {
            if (value.gains.size != field.gains.size) { // another layout: everything is new
                labels = value.centers.map(EqSettings::label)
                shaders = arrayOfNulls(value.gains.size)
            } else {
                EqSettings.changedBands(field.gains, value.gains).forEach { shaders[it] = null } // rebuilt lazily in onDraw
            }
            field = value
            invalidate()
        }

    // Per-frame work is drawing only: labels and gradient shaders are built once per change, not per frame.
    private var labels: List<String> = EqSettings.FLAT.centers.map(EqSettings::label)
    private var shaders: Array<LinearGradient?> = arrayOfNulls(EqSettings.FLAT.gains.size)
    private var shaderWidth = 0
    private var shaderHeight = 0
    private val gradient = IntArray(3)
    private val safeEdges = floatArrayOf(EqSettings.SAFE_DB, -EqSettings.SAFE_DB)
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var moved = false

    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.track; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent; strokeCap = Paint.Cap.ROUND }
    private val zero = Paint().apply { color = palette.zeroLine; strokeWidth = density }
    private val safeLine = Paint().apply { strokeWidth = density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.secondary; textAlign = Paint.Align.CENTER }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.text
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    init {
        contentDescription = "Ekolayzır bantları"
    }

    override fun performClick(): Boolean = super.performClick()

    private val labelH = 22 * density
    private val valueH = 22 * density
    private var dragging = -1

    override fun onDraw(canvas: Canvas) {
        val n = settings.gains.size
        val col = width / n.toFloat()
        val top = valueH + (6 + KNOB_DP) * density
        val bottom = height - labelH - (6 + KNOB_DP) * density
        val zeroY = yFor(0f, top, bottom)
        text.textSize = (if (n > 10) 12 else 14) * density
        value.textSize = (if (n > 10) 12 else 15) * density
        track.strokeWidth = TRACK_DP * density
        fill.strokeWidth = TRACK_DP * density
        val knob = minOf(KNOB_DP * density, col * 0.3f)
        canvas.drawLine(0f, zeroY, width.toFloat(), zeroY, zero)
        if (width != shaderWidth || height != shaderHeight) { shaderWidth = width; shaderHeight = height; shaders = arrayOfNulls(n) }
        for (edge in safeEdges) { // faint marks of the ±6 dB safe range
            val y = yFor(edge, top, bottom)
            safeLine.color = GainColors.at(edge)
            safeLine.alpha = SAFE_LINE_ALPHA
            canvas.drawLine(0f, y, width.toFloat(), y, safeLine)
        }
        settings.gains.forEachIndexed { i, db ->
            val x = col * (i + 0.5f)
            val y = yFor(db, top, bottom)
            canvas.drawLine(x, top, x, bottom, track)
            // the bar runs through every colour it passes on its way from 0 dB to the value
            fill.shader = if (y == zeroY) null else shaders[i] ?: run {
                gradient[0] = GainColors.at(0f); gradient[1] = GainColors.at(db / 2); gradient[2] = GainColors.at(db)
                LinearGradient(x, zeroY, x, y, gradient, null, Shader.TileMode.CLAMP).also { shaders[i] = it }
            }
            fill.color = GainColors.at(db)
            canvas.drawLine(x, zeroY, x, y, fill)
            fill.shader = null
            canvas.drawCircle(x, y, knob, fill)
            canvas.drawText(labels[i], x, height - 6 * density, text)
            if (n <= 10 || i == dragging) canvas.drawText(formatDb(db), x, valueH, value)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val n = settings.gains.size
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = (e.x / (width / n.toFloat())).toInt().coerceIn(0, n - 1)
                downY = e.y
                moved = false
                parent.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (e.actionMasked == MotionEvent.ACTION_UP && !moved) performClick()
                dragging = -1
                invalidate()
                return true
            }
        }
        if (!moved && kotlin.math.abs(e.y - downY) > touchSlop) moved = true
        if (dragging >= 0 && moved) {
            val top = valueH + (6 + KNOB_DP) * density
            val bottom = height - labelH - (6 + KNOB_DP) * density
            val raw = EqSettings.BAND_MAX_DB - (e.y - top) / (bottom - top) * (EqSettings.BAND_MAX_DB - EqSettings.BAND_MIN_DB)
            val db = EqSettings.progressToDb(EqSettings.dbToProgress(raw, EqSettings.BAND_MIN_DB, EqSettings.BAND_MAX_DB), EqSettings.BAND_MIN_DB)
            if (db != settings.gains[dragging]) onChange(dragging, db)
        }
        return true
    }

    private fun yFor(db: Float, top: Float, bottom: Float) =
        top + (EqSettings.BAND_MAX_DB - db) / (EqSettings.BAND_MAX_DB - EqSettings.BAND_MIN_DB) * (bottom - top)

    private companion object {
        const val SAFE_LINE_ALPHA = 90
        const val TRACK_DP = 6f
        const val KNOB_DP = 11f // shrinks to 30% of the column in 31-band mode
        val TR: java.util.Locale = java.util.Locale("tr")
        fun formatDb(db: Float) = if (db == 0f) "0" else String.format(TR, "%+.1f", db)
    }
}
