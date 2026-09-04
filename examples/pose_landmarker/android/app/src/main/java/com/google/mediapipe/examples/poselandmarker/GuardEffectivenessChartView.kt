package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

class GuardEffectivenessChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    enum class UnitMode { PERCENT, MINUTES, SCORE }

    var onDaySelected: ((GuardEffectivenessDay) -> Unit)? = null
    private var days: List<GuardEffectivenessDay> = emptyList()
    private var unitMode = UnitMode.PERCENT
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.headup_text_secondary)
        strokeWidth = dp(1f)
        textSize = sp(10f)
    }
    private val green = ContextCompat.getColor(context, R.color.headup_safe)
    private val yellow = ContextCompat.getColor(context, R.color.headup_warning)
    private val red = ContextCompat.getColor(context, R.color.headup_danger)
    private val observation = ContextCompat.getColor(context, R.color.headup_primary)

    fun submitData(newDays: List<GuardEffectivenessDay>, newUnitMode: UnitMode) {
        days = newDays
        unitMode = newUnitMode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val visible = days.takeLast(if (days.size > 14) 14 else days.size)
        if (visible.isEmpty()) {
            axisPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.no_monitoring_data), width / 2f, height / 2f, axisPaint)
            return
        }
        val left = dp(28f)
        val right = width - dp(8f)
        val top = dp(12f)
        val bottom = height - dp(34f)
        axisPaint.style = Paint.Style.STROKE
        canvas.drawLine(left, bottom, right, bottom, axisPaint)
        axisPaint.style = Paint.Style.FILL
        val groupWidth = (right - left) / visible.size
        val maximumMinutes = visible.maxOfOrNull {
            max(it.observation.validSeconds, it.guarding.validSeconds) / 60f
        }?.coerceAtLeast(1f) ?: 1f

        if (unitMode == UnitMode.SCORE) {
            drawScoreSeries(canvas, visible, left, top, bottom, groupWidth, MonitoringMode.OBSERVATION, observation)
            drawScoreSeries(canvas, visible, left, top, bottom, groupWidth, MonitoringMode.GUARDING, green)
        }
        visible.forEachIndexed { index, day ->
            val center = left + groupWidth * (index + 0.5f)
            if (unitMode != UnitMode.SCORE) {
                val barWidth = (groupWidth * 0.24f).coerceIn(dp(4f), dp(13f))
                drawStack(canvas, center - barWidth - dp(1f), barWidth, top, bottom, day.observation, maximumMinutes)
                drawStack(canvas, center + dp(1f), barWidth, top, bottom, day.guarding, maximumMinutes)
                axisPaint.textAlign = Paint.Align.CENTER
                axisPaint.textSize = sp(8f)
                canvas.drawText("O", center - barWidth / 2f - dp(1f), bottom + dp(11f), axisPaint)
                canvas.drawText("G", center + barWidth / 2f + dp(1f), bottom + dp(11f), axisPaint)
            }
            if (visible.size <= 7 || index % 2 == 0 || index == visible.lastIndex) {
                axisPaint.textAlign = Paint.Align.CENTER
                axisPaint.textSize = sp(9f)
                canvas.drawText(day.date.takeLast(5), center, bottom + dp(26f), axisPaint)
            }
        }
    }

    private fun drawStack(
        canvas: Canvas,
        x: Float,
        barWidth: Float,
        top: Float,
        bottom: Float,
        aggregate: ModePostureAggregate,
        maximumMinutes: Float,
    ) {
        val valid = aggregate.validSeconds.toFloat()
        if (valid <= 0f) return
        val available = bottom - top
        val totalHeight = when (unitMode) {
            UnitMode.PERCENT -> available
            UnitMode.MINUTES -> available * ((valid / 60f) / maximumMinutes)
            UnitMode.SCORE -> available
        }
        var cursor = bottom
        listOf(
            aggregate.redSeconds.toFloat() to red,
            aggregate.yellowSeconds.toFloat() to yellow,
            aggregate.greenSeconds.toFloat() to green,
        ).forEach { (seconds, color) ->
            val segment = totalHeight * seconds / valid
            paint.color = color
            canvas.drawRect(RectF(x, cursor - segment, x + barWidth, cursor), paint)
            cursor -= segment
        }
    }

    private fun drawScoreSeries(
        canvas: Canvas,
        visible: List<GuardEffectivenessDay>,
        left: Float,
        top: Float,
        bottom: Float,
        groupWidth: Float,
        mode: MonitoringMode,
        color: Int,
    ) {
        paint.color = color
        var previous: Pair<Float, Float>? = null
        visible.forEachIndexed { index, day ->
            val aggregate = if (mode == MonitoringMode.OBSERVATION) day.observation else day.guarding
            val score = aggregate.postureScore ?: run {
                previous = null
                return@forEachIndexed
            }
            val x = left + groupWidth * (index + 0.5f)
            val y = bottom - ((bottom - top) * (score / 100.0)).toFloat()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2f)
            previous?.let { canvas.drawLine(it.first, it.second, x, y, paint) }
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, dp(4f), paint)
            previous = x to y
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP || days.isEmpty()) return true
        val visible = days.takeLast(if (days.size > 14) 14 else days.size)
        val left = dp(28f)
        val groupWidth = (width - left - dp(8f)) / visible.size
        val index = ((event.x - left) / groupWidth).toInt().coerceIn(0, visible.lastIndex)
        onDaySelected?.invoke(visible[index])
        performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
