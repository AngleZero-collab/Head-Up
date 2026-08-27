package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.annotation.RawRes

class VisionDragonVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSurface: Surface? = null
    private var currentVideoResId = 0
    private var currentLoop = true
    private var shouldPlay = false

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun play(@RawRes videoResId: Int, loop: Boolean = true) {
        if (currentVideoResId == videoResId && currentLoop == loop && mediaPlayer?.isPlaying == true) return
        currentVideoResId = videoResId
        currentLoop = loop
        shouldPlay = true
        if (isAvailable) preparePlayer(surfaceTexture)
    }

    fun stopPlayback() {
        shouldPlay = false
        currentVideoResId = 0
        releasePlayer()
    }

    fun releasePlayer() {
        mediaPlayer?.runCatching {
            stop()
            reset()
            release()
        }
        mediaPlayer = null
        mediaSurface?.release()
        mediaSurface = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (shouldPlay && currentVideoResId != 0) preparePlayer(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer()
        return true
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
        stopPlayback()
        super.onDetachedFromWindow()
    }

    private fun resumeIfNeeded() {
        if (shouldPlay && currentVideoResId != 0 && isAvailable && mediaPlayer?.isPlaying != true) {
            preparePlayer(surfaceTexture)
        }
    }

    private fun preparePlayer(surfaceTexture: SurfaceTexture?) {
        if (surfaceTexture == null || currentVideoResId == 0) return
        releasePlayer()
        val surface = Surface(surfaceTexture)
        mediaSurface = surface
        val asset = resources.openRawResourceFd(currentVideoResId) ?: return
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            player.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            player.setSurface(surface)
            player.isLooping = currentLoop
            player.setVolume(0f, 0f)
            player.setOnPreparedListener { prepared ->
                if (mediaPlayer === prepared && shouldPlay) prepared.start()
            }
            player.setOnCompletionListener { completed ->
                if (!currentLoop && mediaPlayer === completed) completed.seekTo(0)
            }
            player.setOnErrorListener { _, _, _ ->
                releasePlayer()
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
        } finally {
            asset.close()
        }
    }
}
