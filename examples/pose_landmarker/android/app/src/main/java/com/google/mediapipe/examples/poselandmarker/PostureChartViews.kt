package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class PosturePieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.headup_card_stroke)
    }
    private var safeSeconds = 0L
    private var warningSeconds = 0L
    private var dangerSeconds = 0L

    fun setData(safe: Long, warning: Long, danger: Long) {
        safeSeconds = safe
        warningSeconds = warning
        dangerSeconds = danger
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isInEditMode) return
        val stroke = 18f * resources.displayMetrics.density
        val size = minOf(width, height).toFloat() - stroke * 1.5f
        val oval = RectF(
            (width - size) / 2f,
            (height - size) / 2f,
            (width + size) / 2f,
            (height + size) / 2f,
        )
        canvas.drawOval(oval, trackPaint)
        val total = safeSeconds + warningSeconds + dangerSeconds
        if (total <= 0L) return

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.BUTT
        var start = -90f
        listOf(
            safeSeconds to R.color.headup_safe,
            warningSeconds to R.color.headup_warning,
            dangerSeconds to R.color.headup_danger,
        ).forEach { (value, colorRes) ->
            val sweep = value.toFloat() / total.toFloat() * 360f
            paint.color = ContextCompat.getColor(context, colorRes)
            canvas.drawArc(oval, start, sweep, false, paint)
            start += sweep
        }
    }
}

class PostureLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.headup_danger)
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.headup_danger)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.headup_card_stroke)
    }
    private var values: List<Int> = emptyList()

    fun setData(dangerEvents: List<Int>) {
        values = dangerEvents
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isInEditMode) return
        val padding = 18f * resources.displayMetrics.density
        val chartWidth = (width - padding * 2f).coerceAtLeast(1f)
        val chartHeight = (height - padding * 2f).coerceAtLeast(1f)
        canvas.drawLine(padding, height - padding, width - padding, height - padding, axisPaint)
        if (values.isEmpty()) return

        val maxValue = max(1, values.maxOrNull() ?: 1)
        val stepX = if (values.size == 1) 0f else chartWidth / (values.size - 1)
        var previousX = padding
        var previousY = height - padding - values.first().toFloat() / maxValue * chartHeight
        values.forEachIndexed { index, value ->
            val x = padding + index * stepX
            val y = height - padding - value.toFloat() / maxValue * chartHeight
            if (index > 0) canvas.drawLine(previousX, previousY, x, y, linePaint)
            canvas.drawCircle(x, y, 4.5f * resources.displayMetrics.density, pointPaint)
            previousX = x
            previousY = y
        }
    }
}

class HealthTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.headup_card_stroke)
    }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val eventLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.headup_danger)
    }
    private val eventPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.headup_danger)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = ContextCompat.getColor(context, R.color.headup_card_stroke)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.headup_text_secondary)
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
    }
    private val dayFormat = SimpleDateFormat("E", Locale.getDefault())
    private var summaries: List<DailyHealthTrendSummary> = emptyList()

    fun setData(data: List<DailyHealthTrendSummary>) {
        summaries = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isInEditMode) return
        val data = summaries
        if (data.isEmpty()) return

        val left = 12f * density
        val right = width - 12f * density
        val top = 16f * density
        val bottom = height - 28f * density
        val chartWidth = (right - left).coerceAtLeast(1f)
        val chartHeight = (bottom - top).coerceAtLeast(1f)

        repeat(3) { index ->
            val y = top + chartHeight * index / 2f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        val stepX = chartWidth / data.size
        val barWidth = min(28f * density, stepX * 0.48f)
        val radius = 8f * density
        val safeColor = ContextCompat.getColor(context, R.color.headup_safe)
        val warningColor = ContextCompat.getColor(context, R.color.headup_warning)
        val dangerColor = ContextCompat.getColor(context, R.color.headup_danger)
        val maxEvents = max(1, data.maxOfOrNull { it.dangerEvents } ?: 1)
        var previousEventX: Float? = null
        var previousEventY: Float? = null

        data.forEachIndexed { index, summary ->
            val centerX = left + stepX * index + stepX / 2f
            val barLeft = centerX - barWidth / 2f
            val barRight = centerX + barWidth / 2f
            val track = RectF(barLeft, top, barRight, bottom)
            canvas.drawRoundRect(track, radius, radius, trackPaint)

            var segmentBottom = bottom
            val total = summary.totalSeconds.toFloat().coerceAtLeast(1f)
            listOf(
                summary.safeSeconds to safeColor,
                summary.warningSeconds to warningColor,
                summary.dangerSeconds to dangerColor,
            ).forEach { (seconds, color) ->
                val height = chartHeight * seconds.toFloat() / total
                if (height > 0f) {
                    segmentPaint.color = color
                    canvas.drawRoundRect(RectF(barLeft, segmentBottom - height, barRight, segmentBottom), radius, radius, segmentPaint)
                    segmentBottom -= height
                }
            }

            val eventY = bottom - chartHeight * summary.dangerEvents.toFloat() / maxEvents.toFloat()
            previousEventX?.let { px ->
                canvas.drawLine(px, previousEventY ?: eventY, centerX, eventY, eventLinePaint)
            }
            canvas.drawCircle(centerX, eventY, 4f * density, eventPointPaint)
            previousEventX = centerX
            previousEventY = eventY

            canvas.drawText(dayFormat.format(Date(summary.dayStartMs)), centerX, height - 8f * density, labelPaint)
        }
    }
}
