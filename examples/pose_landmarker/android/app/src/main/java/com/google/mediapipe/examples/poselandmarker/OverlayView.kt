package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/** A quiet status surround; raw face and body landmarks are intentionally hidden. */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var zone: PostureZone? = null
    private var pulse = 0f
    private val breathing = android.animation.ValueAnimator.ofFloat(0.35f, 1f).apply {
        duration = 1800L
        repeatMode = android.animation.ValueAnimator.REVERSE
        repeatCount = android.animation.ValueAnimator.INFINITE
        addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (windowVisibility == VISIBLE) breathing.start()
    }

    override fun onDetachedFromWindow() {
        breathing.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // The View constructor can invoke this before property initialization.
        if (!isAttachedToWindow) return
        if (visibility == VISIBLE) breathing.start() else breathing.cancel()
    }

    fun clear() {
        zone = null
        contentDescription = context.getString(R.string.no_face_detected_warning)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val color = ContextCompat.getColor(context, when (zone) {
            PostureZone.SAFE -> R.color.headup_safe
            PostureZone.WARNING -> R.color.headup_warning
            PostureZone.DANGER -> R.color.headup_danger
            null -> R.color.headup_text_secondary
        })
        val rect = RectF(7*d, 7*d, width-7*d, height-7*d)
        paint.style = Paint.Style.STROKE
        paint.color = color
        // Layered translucent strokes provide a soft halo without blurring the camera.
        for (layer in 6 downTo 1) {
            paint.strokeWidth = layer * 2*d
            paint.alpha = if (zone == null) 5 else 8
            canvas.drawRoundRect(rect, 23*d, 23*d, paint)
        }
        paint.strokeWidth = 1.2f*d
        paint.alpha = 190
        canvas.drawRoundRect(rect, 23*d, 23*d, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 210
        paint.color = android.graphics.Color.rgb(15, 23, 42)
        canvas.drawRoundRect(RectF(18*d,18*d,116*d,46*d),14*d,14*d,paint)
        paint.color = color
        paint.alpha = if (zone == null) 100 else (100+155*pulse).toInt()
        canvas.drawCircle(31*d,32*d,3*d,paint)
        paint.alpha = 255
        paint.textSize = 11*d
        canvas.drawText(context.getString(if (zone == null) R.string.scan_ai_waiting else R.string.scan_ai_active),
            41*d,36*d,paint)
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE,
        zone: PostureZone = PostureZone.SAFE,
    ) {
        this.zone = zone.takeIf { poseLandmarkerResults.landmarks().isNotEmpty() }
        contentDescription = context.getString(when (this.zone) {
            PostureZone.SAFE -> R.string.posture_status_safe
            PostureZone.WARNING -> R.string.posture_status_warning
            PostureZone.DANGER -> R.string.posture_status_danger
            null -> R.string.no_face_detected_warning
        })
        invalidate()
    }
}
