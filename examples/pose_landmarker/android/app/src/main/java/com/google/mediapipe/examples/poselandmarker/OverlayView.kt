/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var results: PoseLandmarkerResult? = null
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lineGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var imageWidth = 1
    private var imageHeight = 1
    private var postureZone = PostureZone.SAFE
    private val facePath = Path()
    private val facePathIndices = listOf(3, 2, 1, 0, 4, 5, 6)

    init {
        if (!isInEditMode) {
            linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
            linePaint.style = Paint.Style.STROKE
            linePaint.strokeCap = Paint.Cap.ROUND
            linePaint.strokeJoin = Paint.Join.ROUND
            lineGlowPaint.strokeWidth = CONNECTION_GLOW_WIDTH
            lineGlowPaint.style = Paint.Style.STROKE
            lineGlowPaint.strokeCap = Paint.Cap.ROUND
            lineGlowPaint.strokeJoin = Paint.Join.ROUND
            pointPaint.style = Paint.Style.FILL
            applyZoneColor()
        }
    }

    fun clear() {
        results = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isInEditMode) return
        val landmarks = results?.landmarks()?.firstOrNull() ?: return
        applyZoneColor()

        // MediaPipe Pose indices: face 0..10, shoulders 11..12, hips 23..24.
        (0..12).forEach { index ->
            landmarks.getOrNull(index)?.let { point ->
                pointPaint.color = landmarkColor(index)
                canvas.drawCircle(mapX(point.x()), mapY(point.y()), POINT_RADIUS, pointPaint)
            }
        }
        listOf(23, 24).forEach { index ->
            landmarks.getOrNull(index)?.let { point ->
                pointPaint.color = landmarkColor(index)
                canvas.drawCircle(mapX(point.x()), mapY(point.y()), POINT_RADIUS, pointPaint)
            }
        }

        if (HeadUpRepository.arePoseConnectionsEnabled(context)) {
            drawConnection(canvas, landmarks, 11, 12)
            drawConnection(canvas, landmarks, 11, 23)
            drawConnection(canvas, landmarks, 12, 24)
            drawConnection(canvas, landmarks, 23, 24)
            drawConnection(canvas, landmarks, 7, 3)
            drawConnection(canvas, landmarks, 6, 8)
            drawConnection(canvas, landmarks, 9, 10)

            facePath.reset()
            facePathIndices.forEachIndexed { pathIndex, landmarkIndex ->
                landmarks.getOrNull(landmarkIndex)?.let { point ->
                    if (pathIndex == 0) facePath.moveTo(mapX(point.x()), mapY(point.y()))
                    else facePath.lineTo(mapX(point.x()), mapY(point.y()))
                }
            }
            canvas.drawPath(facePath, lineGlowPaint)
            canvas.drawPath(facePath, linePaint)
        }
    }

    private fun drawConnection(
        canvas: Canvas,
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        startIndex: Int,
        endIndex: Int,
    ) {
        val start = landmarks.getOrNull(startIndex) ?: return
        val end = landmarks.getOrNull(endIndex) ?: return
        val startX = mapX(start.x())
        val startY = mapY(start.y())
        val endX = mapX(end.x())
        val endY = mapY(end.y())
        canvas.drawLine(startX, startY, endX, endY, lineGlowPaint)
        canvas.drawLine(startX, startY, endX, endY, linePaint)
    }

    private fun applyZoneColor() {
        val color = when (postureZone) {
            PostureZone.SAFE -> ContextCompat.getColor(context, R.color.headup_safe)
            // Every non-safe state uses the same red warning color as the result panel.
            PostureZone.WARNING -> ContextCompat.getColor(context, R.color.headup_danger)
            PostureZone.DANGER -> ContextCompat.getColor(context, R.color.headup_danger)
        }
        linePaint.color = color
        lineGlowPaint.color = Color.argb(
            CONNECTION_GLOW_ALPHA,
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
        pointPaint.color = color
    }

    private fun landmarkColor(index: Int): Int {
        if (postureZone != PostureZone.SAFE) {
            return ContextCompat.getColor(context, R.color.headup_danger)
        }
        return when (index) {
        1, 2, 3, 4, 5, 6 -> ContextCompat.getColor(context, R.color.headup_primary)
        7, 8 -> ContextCompat.getColor(context, R.color.headup_purple)
        9, 10 -> ContextCompat.getColor(context, R.color.headup_orange)
        else -> linePaint.color
        }
    }

    private fun mapX(normalizedX: Float): Float = normalizedX * imageWidth * scaleFactor + offsetX

    private fun mapY(normalizedY: Float): Float = normalizedY * imageHeight * scaleFactor + offsetY

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE,
        zone: PostureZone = PostureZone.SAFE,
    ) {
        results = poseLandmarkerResults
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        postureZone = zone

        // PreviewView uses FIT_CENTER, so the overlay must use the same letterbox transform.
        scaleFactor = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        offsetX = (width - imageWidth * scaleFactor) / 2f
        offsetY = (height - imageHeight * scaleFactor) / 2f
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 4.5f
        private const val CONNECTION_GLOW_WIDTH = 9f
        private const val CONNECTION_GLOW_ALPHA = 80
        private const val POINT_RADIUS = 7f
    }
}
