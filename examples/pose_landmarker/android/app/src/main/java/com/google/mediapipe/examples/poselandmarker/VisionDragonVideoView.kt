package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RawRes
import androidx.appcompat.widget.AppCompatImageView

class VisionDragonVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var currentAnimationResId = 0
    private var currentLoop = true
    private var shouldPlay = false

    init {
        scaleType = ScaleType.FIT_CENTER
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    fun play(@RawRes animationResId: Int, loop: Boolean = true): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (currentAnimationResId == animationResId && currentLoop == loop) {
            shouldPlay = true
            resumeIfNeeded()
            return drawable is AnimatedImageDrawable
        }
        currentAnimationResId = animationResId
        currentLoop = loop
        shouldPlay = true
        return decodeAnimation(animationResId)
    }

    fun stopPlayback() {
        shouldPlay = false
        currentAnimationResId = 0
        releasePlayer()
    }

    fun releasePlayer() {
        (drawable as? AnimatedImageDrawable)?.stop()
        setImageDrawable(null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resumeIfNeeded()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) resumeIfNeeded()
    }

    override fun onDetachedFromWindow() {
        (drawable as? AnimatedImageDrawable)?.stop()
        super.onDetachedFromWindow()
    }

    private fun resumeIfNeeded() {
        if (!shouldPlay || visibility != VISIBLE || !isAttachedToWindow) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (drawable as? AnimatedImageDrawable)?.start()
        }
    }

    private fun decodeAnimation(@RawRes animationResId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        releasePlayer()
        return runCatching {
            val source = ImageDecoder.createSource(resources, animationResId)
            val decoded = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val animated = decoded as? AnimatedImageDrawable ?: run {
                currentAnimationResId = 0
                return@runCatching false
            }
            animated.repeatCount = if (currentLoop) {
                AnimatedImageDrawable.REPEAT_INFINITE
            } else {
                0
            }
            setImageDrawable(animated)
            resumeIfNeeded()
            true
        }.getOrElse {
            currentAnimationResId = 0
            setImageDrawable(null)
            false
        }
    }
}
