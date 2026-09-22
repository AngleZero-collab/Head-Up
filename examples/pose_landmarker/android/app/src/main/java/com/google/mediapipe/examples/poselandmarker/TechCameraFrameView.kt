package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class TechCameraFrameView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = withAlpha(ContextCompat.getColor(context, R.color.headup_primary), 150)
    }
    private val cornerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = withAlpha(ContextCompat.getColor(context, R.color.headup_primary), 34)
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.headup_primary)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.7f * density
        color = withAlpha(ContextCompat.getColor(context, R.color.headup_primary), 22)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.headup_safe)
    }
    private val framePath = Path()
    private val frameRect = RectF()
    private var postureZone: PostureZone? = null

    fun setZone(zone: PostureZone?) {
        if (postureZone == zone) return
        postureZone = zone
        val color = ContextCompat.getColor(
            context,
            when (zone) {
                PostureZone.SAFE -> R.color.headup_safe
                PostureZone.WARNING -> R.color.headup_warning
                PostureZone.DANGER -> R.color.headup_danger
                null -> R.color.headup_primary
            },
        )
        borderPaint.color = withAlpha(color, 190)
        cornerGlowPaint.color = withAlpha(color, 48)
        cornerPaint.color = color
        accentPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 3f * density
        val radius = 22f * density
        frameRect.set(inset, inset, width - inset, height - inset)

        canvas.save()
        framePath.reset()
        framePath.addRoundRect(frameRect, radius, radius, Path.Direction.CW)
        canvas.clipPath(framePath)
        drawGrid(canvas)
        canvas.restore()

        canvas.drawRoundRect(frameRect, radius, radius, borderPaint)
        drawCorners(canvas)
        drawStatusAccents(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        val spacing = 36f * density
        var x = spacing
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += spacing
        }
        var y = spacing
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += spacing
        }
    }

    private fun drawCorners(canvas: Canvas) {
        val edge = 7f * density
        val length = 34f * density
        val right = width - edge
        val bottom = height - edge
        val paths = listOf(
            cornerPath(edge, edge, edge + length, edge, edge, edge + length),
            cornerPath(right, edge, right - length, edge, right, edge + length),
            cornerPath(edge, bottom, edge + length, bottom, edge, bottom - length),
            cornerPath(right, bottom, right - length, bottom, right, bottom - length),
        )
        paths.forEach { path ->
            canvas.drawPath(path, cornerGlowPaint)
            canvas.drawPath(path, cornerPaint)
        }
    }

    private fun cornerPath(
        originX: Float,
        originY: Float,
        horizontalX: Float,
        horizontalY: Float,
        verticalX: Float,
        verticalY: Float,
    ): Path = Path().apply {
        moveTo(horizontalX, horizontalY)
        lineTo(originX, originY)
        lineTo(verticalX, verticalY)
    }

    private fun drawStatusAccents(canvas: Canvas) {
        val centerX = width / 2f
        val top = 7f * density
        val segmentWidth = 12f * density
        val gap = 5f * density
        for (index in -1..1) {
            val left = centerX + index * (segmentWidth + gap) - segmentWidth / 2f
            accentPaint.alpha = if (index == 0) 235 else 105
            canvas.drawRoundRect(
                left,
                top,
                left + segmentWidth,
                top + 2.5f * density,
                2f * density,
                2f * density,
                accentPaint,
            )
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
