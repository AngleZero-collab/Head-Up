package com.google.mediapipe.examples.poselandmarker

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

class HeadUpService : Service(), LifecycleOwner, PoseLandmarkerHelper.LandmarkerListener, SensorEventListener {
    companion object {
        const val ACTION_PAUSE_CAMERA = "com.google.mediapipe.examples.poselandmarker.PAUSE_CAMERA"
        const val ACTION_RESUME_CAMERA = "com.google.mediapipe.examples.poselandmarker.RESUME_CAMERA"
        private const val CHANNEL_ID = "HeadUpServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "HeadUpService"
        private const val WARNING_DELAY_MS = 3_000L
        private const val WARNING_CLEAR_GRACE_MS = 900L
        private const val RAPID_FALL_VIBRATION_MS = 100L
        private const val WARNING_VIBRATION_MS = 260L
        private const val VIBRATION_COOLDOWN_MS = 1_500L
        private const val ALARM_COOLDOWN_MS = 3_000L
        private const val PET_SIZE_DP = 112
        private const val HAPPY_PET_HIDE_DELAY_MS = 1_800L
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private var helperReady = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraOwnershipToken = -1L
    private var isCameraPaused = true

    private var windowManager: WindowManager? = null
    private var warningOverlayView: View? = null
    private var warningAnimator: ValueAnimator? = null
    private var petOverlayView: FrameLayout? = null
    private var petVideoView: VisionDragonVideoView? = null
    private var petImageView: ImageView? = null
    private var petMotionAnimator: ValueAnimator? = null
    private var petLayoutParams: WindowManager.LayoutParams? = null
    private var currentPetMood: PetMood? = null
    private var currentPetDragonId: String? = null
    private var currentPetBackgroundResId = 0
    private var wasShowingBadPet = false
    private var hidePetRunnable: Runnable? = null
    private var sensorManager: SensorManager? = null
    private var gravitySensor: Sensor? = null
    private var lastDeviceTilt = 0
    private var isDeviceFlat = false

    private var badPostureStartTime = 0L
    private var warningActive = false
    private var badPostureFlag = false
    private var warningDelayRunnable: Runnable? = null
    private var warningClearRunnable: Runnable? = null
    private var wasRapidFall = false
    private var lastVibrationTime = 0L
    private var lastProcessedTimestamp = Long.MIN_VALUE
    private var toneGenerator: ToneGenerator? = null
    private var lastAlarmTime = 0L

    private enum class PetMood {
        ANGRY,
        HAPPY,
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        backgroundExecutor = Executors.newSingleThreadExecutor()
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 80)
        createNotificationChannel()
        val state = HeadUpRepository.currentState(this)
        startForegroundCompat(buildNotification(state))
        setupSensors()

        HeadUpRepository.observeState().observe(this) { updatedState ->
            updateNotification(updatedState)
            if (!HeadUpRepository.isPetOverlayEnabled(this)) {
                wasShowingBadPet = false
                hidePetOverlay()
            }
            if (isCameraPaused || HeadUpRepository.isForegroundScanActive(this)) {
                processPostureMetrics(updatedState.metrics)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_CAMERA -> pauseHiddenCamera()
            ACTION_RESUME_CAMERA -> resumeHiddenCamera()
            else -> if (HeadUpRepository.isForegroundScanActive(this)) pauseHiddenCamera() else resumeHiddenCamera()
        }
        return START_STICKY
    }

    private fun pauseHiddenCamera() {
        isCameraPaused = true
        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        CameraOwnership.release(CameraOwnership.Owner.BACKGROUND_SERVICE)
        helperReady = false
        if (this::poseLandmarkerHelper.isInitialized && !backgroundExecutor.isShutdown) {
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
        PostureAnalyzer.resetSmoothing()
        resetDangerTimer()
        wasShowingBadPet = false
        hidePetOverlay()
    }

    private fun resumeHiddenCamera() {
        if (HeadUpRepository.isForegroundScanActive(this) ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) return

        isCameraPaused = false
        cameraOwnershipToken = CameraOwnership.claim(CameraOwnership.Owner.BACKGROUND_SERVICE)
        val token = cameraOwnershipToken
        PostureAnalyzer.resetSmoothing()
        resetDangerTimer()

        backgroundExecutor.execute {
            if (!this::poseLandmarkerHelper.isInitialized) {
                poseLandmarkerHelper = PoseLandmarkerHelper(
                    context = applicationContext,
                    runningMode = RunningMode.LIVE_STREAM,
                    currentModel = HeadUpRepository.getSelectedModel(applicationContext),
                    currentDelegate = HeadUpRepository.getSelectedDelegate(applicationContext),
                    poseLandmarkerHelperListener = this,
                )
            } else if (poseLandmarkerHelper.isClose()) {
                poseLandmarkerHelper.setupPoseLandmarker()
            }
            helperReady = true
            mainHandler.post { startHiddenCamera(token) }
        }
    }

    private fun startHiddenCamera(token: Long) {
        if (!canOwnBackgroundCamera(token) || !helperReady) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                val provider = future.get()
                if (!canOwnBackgroundCamera(token)) {
                    return@addListener
                }
                cameraProvider = provider
                imageAnalyzer?.clearAnalyzer()
                provider.unbindAll()

                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(backgroundExecutor) { imageProxy ->
                            try {
                                if (canOwnBackgroundCamera(token) && helperReady && !poseLandmarkerHelper.isClose()) {
                                    poseLandmarkerHelper.detectLiveStream(imageProxy, true)
                                }
                            } catch (error: Throwable) {
                                Log.e(TAG, "Background pose frame failed", error)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        imageAnalyzer,
                    )
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to bind background camera", error)
                    imageAnalyzer?.clearAnalyzer()
                    provider.unbindAll()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun canOwnBackgroundCamera(token: Long): Boolean =
        !isCameraPaused &&
            !HeadUpRepository.isForegroundScanActive(this) &&
            CameraOwnership.isCurrent(CameraOwnership.Owner.BACKGROUND_SERVICE, token)

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        if (!canOwnBackgroundCamera(cameraOwnershipToken)) return
        val landmarks = resultBundle.results.firstOrNull()?.landmarks()?.firstOrNull() ?: return
        val metrics = PostureAnalyzer.analyzeMediaPipe(
            landmarks = landmarks,
            deviceTilt = lastDeviceTilt,
            isFlat = isDeviceFlat,
            calibration = HeadUpRepository.getCalibration(this),
            inputImageWidth = resultBundle.inputImageWidth,
            inputImageHeight = resultBundle.inputImageHeight,
        ) ?: return
        processPostureMetrics(metrics)
        HeadUpRepository.recordMetrics(this, metrics, source = "background")
    }

    private fun processPostureMetrics(metrics: PostureMetrics) {
        if (metrics.timestampMs == lastProcessedTimestamp) return
        lastProcessedTimestamp = metrics.timestampMs
        mainHandler.post {
            if (metrics.zone == PostureZone.DANGER) {
                cancelWarningClearGrace()
                armWarningOverlayDebounce()
            } else {
                clearDangerWithGraceIfNeeded()
            }

            updatePetOverlay(metrics)

            if (metrics.isRapidFall && !wasRapidFall) {
                vibrate(RAPID_FALL_VIBRATION_MS)
            }
            wasRapidFall = metrics.isRapidFall
        }
    }

    private fun armWarningOverlayDebounce() {
        badPostureFlag = true
        if (badPostureStartTime == 0L) badPostureStartTime = SystemClock.elapsedRealtime()
        if (warningActive) {
            showWarningOverlay()
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - badPostureStartTime
        if (elapsed >= WARNING_DELAY_MS) {
            activateWarningOverlay()
            return
        }
        if (warningDelayRunnable != null) return

        warningDelayRunnable = Runnable {
            warningDelayRunnable = null
            val elapsed = SystemClock.elapsedRealtime() - badPostureStartTime
            if (badPostureFlag && elapsed >= WARNING_DELAY_MS && !warningActive) activateWarningOverlay()
        }
        mainHandler.postDelayed(warningDelayRunnable!!, WARNING_DELAY_MS - elapsed)
    }

    private fun activateWarningOverlay() {
        if (showWarningOverlay()) {
            warningActive = true
            vibrate(WARNING_VIBRATION_MS)
            playAlarmIfNeeded()
        }
    }

    private fun clearDangerWithGraceIfNeeded() {
        badPostureFlag = false
        warningDelayRunnable?.let { mainHandler.removeCallbacks(it) }
        warningDelayRunnable = null
        badPostureStartTime = 0L
        if (!warningActive) {
            wasRapidFall = false
            hideWarningOverlay()
            return
        }
        if (warningClearRunnable != null) return
        warningClearRunnable = Runnable {
            warningClearRunnable = null
            resetDangerTimer()
        }
        mainHandler.postDelayed(warningClearRunnable!!, WARNING_CLEAR_GRACE_MS)
    }

    private fun cancelWarningClearGrace() {
        warningClearRunnable?.let { mainHandler.removeCallbacks(it) }
        warningClearRunnable = null
    }

    private fun resetDangerTimer() {
        badPostureFlag = false
        warningDelayRunnable?.let { mainHandler.removeCallbacks(it) }
        warningDelayRunnable = null
        cancelWarningClearGrace()
        badPostureStartTime = 0L
        warningActive = false
        wasRapidFall = false
        hideWarningOverlay()
    }

    private fun ensureWarningOverlay(): View? {
        if (!canUseApplicationOverlay()) {
            warningOverlayView = null
            return null
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        warningOverlayView?.let { overlay ->
            if (overlay.parent != null) return overlay
            warningAnimator?.cancel()
            warningAnimator = null
            warningOverlayView = null
        }
        val overlay = LayoutInflater.from(this).inflate(R.layout.view_warning_frame, null, false)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            windowManager?.addView(overlay, params)
            warningOverlayView = overlay
        } catch (error: Exception) {
            Log.e(TAG, "Unable to attach warning overlay", error)
            warningOverlayView = null
        }
        return warningOverlayView
    }

    private fun showWarningOverlay(): Boolean {
        val overlay = ensureWarningOverlay() ?: return false
        overlay.visibility = View.VISIBLE
        overlay.alpha = overlay.alpha.coerceAtLeast(0.72f)
        overlay.bringToFront()
        if (warningAnimator?.isRunning == true) return true
        warningAnimator = ValueAnimator.ofFloat(0.5f, 1f).apply {
            duration = 700L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { overlay.alpha = it.animatedValue as Float }
            start()
        }
        return true
    }

    private fun hideWarningOverlay() {
        warningAnimator?.cancel()
        warningAnimator = null
        warningOverlayView?.let { overlay ->
            try {
                if (overlay.parent != null) windowManager?.removeView(overlay)
            } catch (error: Exception) {
                Log.w(TAG, "Unable to remove warning overlay", error)
            }
        }
        warningOverlayView = null
    }

    private fun updatePetOverlay(metrics: PostureMetrics) {
        if (!HeadUpRepository.isPetOverlayEnabled(this) || !canUseApplicationOverlay()) {
            wasShowingBadPet = false
            hidePetOverlay()
            return
        }

        when (metrics.zone) {
            PostureZone.DANGER -> {
                wasShowingBadPet = true
                showPetOverlay(PetMood.ANGRY)
            }

            PostureZone.SAFE -> {
                if (wasShowingBadPet) {
                    wasShowingBadPet = false
                    showHappyPetThenHide()
                } else if (currentPetMood != PetMood.HAPPY) {
                    hidePetOverlay()
                }
            }

            PostureZone.WARNING -> {
                if (!wasShowingBadPet) hidePetOverlay()
            }
        }
    }

    private fun showHappyPetThenHide() {
        showPetOverlay(PetMood.HAPPY)
        hidePetRunnable?.let { mainHandler.removeCallbacks(it) }
        hidePetRunnable = Runnable {
            hidePetRunnable = null
            hidePetOverlay()
        }
        mainHandler.postDelayed(hidePetRunnable!!, HAPPY_PET_HIDE_DELAY_MS)
    }

    private fun showPetOverlay(mood: PetMood) {
        hidePetRunnable?.let { mainHandler.removeCallbacks(it) }
        hidePetRunnable = null
        if (!canUseApplicationOverlay()) return
        windowManager = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlay = petOverlayView ?: createPetOverlayView()
        if (petOverlayView == null) {
            val sizePx = dpToPx(PET_SIZE_DP)
            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = resources.displayMetrics.widthPixels - sizePx - dpToPx(18)
                y = dpToPx(140)
            }
            overlay.setOnTouchListener(createPetDragListener(params))

            try {
                windowManager?.addView(overlay, params)
                petOverlayView = overlay
                petLayoutParams = params
            } catch (error: Exception) {
                Log.e(TAG, "Unable to attach Vision Dragon overlay", error)
                return
            }
        }

        val state = HeadUpRepository.currentState(this)
        val selectedDragon = state.selectedDragon
        val backgroundRes = petOrbBackground(mood, state)
        val dragonChanged = currentPetDragonId != selectedDragon.id
        val backgroundChanged = currentPetBackgroundResId != backgroundRes
        if (currentPetMood != mood || dragonChanged || backgroundChanged) {
            currentPetMood = mood
            currentPetDragonId = selectedDragon.id
            currentPetBackgroundResId = backgroundRes
            overlay.background = ContextCompat.getDrawable(this, backgroundRes)
            petImageView?.setImageResource(selectedDragon.imageRes)
            petImageView?.visibility = View.GONE
            petVideoView?.visibility = View.VISIBLE
            petVideoView?.play(
                if (mood == PetMood.ANGRY) R.raw.angry_dragon else R.raw.happy_dragon,
                loop = true,
            )
            animatePetOverlay(mood)
        }
    }

    private fun createPetOverlayView(): FrameLayout {
        val paddingPx = dpToPx(7)
        return CircleClipFrameLayout(this).apply {
            contentDescription = getString(R.string.vision_dragon_animation)
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            alpha = 0.98f
            elevation = dpToPx(12).toFloat()
            petVideoView = VisionDragonVideoView(this@HeadUpService).also { video ->
                video.isClickable = false
                video.isFocusable = false
                video.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                )
                addView(video)
            }
            petImageView = ImageView(this@HeadUpService).also { image ->
                image.isClickable = false
                image.isFocusable = false
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setImageResource(HeadUpRepository.currentState(this@HeadUpService).selectedDragon.imageRes)
                image.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                )
                addView(image)
            }
        }
    }

    private fun animatePetOverlay(mood: PetMood) {
        petMotionAnimator?.cancel()
        petMotionAnimator = null
        val target = petOverlayView ?: return
        target.animate().cancel()
        target.alpha = 1f
        target.rotation = 0f
        target.scaleX = 1f
        target.scaleY = 1f
        target.translationY = 0f

        if (mood == PetMood.ANGRY) {
            petMotionAnimator = ValueAnimator.ofFloat(-7f, 7f).apply {
                duration = 95L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = 9
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    petOverlayView?.rotation = animator.animatedValue as Float
                }
                start()
            }
        } else {
            target.scaleX = 0.82f
            target.scaleY = 0.82f
            target.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .translationY(-8f)
                .setDuration(220L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    petOverlayView?.animate()
                        ?.scaleX(1f)
                        ?.scaleY(1f)
                        ?.translationY(0f)
                        ?.setDuration(180L)
                        ?.start()
                }
                .start()
        }
    }

    private fun hidePetOverlay() {
        hidePetRunnable?.let { mainHandler.removeCallbacks(it) }
        hidePetRunnable = null
        petMotionAnimator?.cancel()
        petMotionAnimator = null
        petVideoView?.stopPlayback()
        petVideoView?.animate()?.cancel()
        petImageView?.animate()?.cancel()
        petOverlayView?.let { pet ->
            try {
                windowManager?.removeView(pet)
            } catch (error: Exception) {
                Log.w(TAG, "Unable to remove Vision Dragon overlay", error)
            }
        }
        petOverlayView = null
        petVideoView = null
        petImageView = null
        petLayoutParams = null
        currentPetMood = null
        currentPetDragonId = null
        currentPetBackgroundResId = 0
    }

    private fun createPetDragListener(params: WindowManager.LayoutParams): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    view.animate().cancel()
                    view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).roundToInt()
                    params.y = initialY + (event.rawY - initialTouchY).roundToInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
                    if (abs(event.rawX - initialTouchX) < dpToPx(8) &&
                        abs(event.rawY - initialTouchY) < dpToPx(8)
                    ) {
                        HeadUpRepository.recordEyeRest(this)
                        Toast.makeText(this, R.string.eye_rest_recorded, Toast.LENGTH_SHORT).show()
                    }
                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
                    true
                }

                else -> false
            }
        }
    }

    private fun petOrbBackground(mood: PetMood, state: HeadUpUiState): Int = when {
        mood == PetMood.ANGRY -> R.drawable.bg_headup_dragon_orb_danger
        "sunrise_background" in state.equippedShopItems -> R.drawable.bg_headup_dragon_orb_sunrise
        "forest_background" in state.equippedShopItems -> R.drawable.bg_headup_dragon_orb_forest
        "ocean_background" in state.equippedShopItems -> R.drawable.bg_headup_dragon_orb_ocean
        else -> R.drawable.bg_headup_dragon_orb
    }

    private fun canUseApplicationOverlay(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(this)

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    private fun playAlarmIfNeeded() {
        if (!HeadUpRepository.isAlarmEnabled(this)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAlarmTime < ALARM_COOLDOWN_MS) return
        lastAlarmTime = now
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
    }

    private fun vibrate(durationMs: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastVibrationTime < VIBRATION_COOLDOWN_MS) return
        lastVibrationTime = now
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gravitySensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GRAVITY || event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val y = event.values[1]
            val z = event.values[2]
            lastDeviceTilt = Math.toDegrees(Math.atan2(y.toDouble(), z.toDouble())).toInt()
            isDeviceFlat = kotlin.math.abs(z) > 8.5f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: HeadUpUiState) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(state))
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification permission denied", error)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(state: HeadUpUiState): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_guard_title))
            .setContentText(getString(R.string.notification_guard_text, state.metrics.angleDegrees, localizedStatus(state.metrics.zone)))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun localizedStatus(zone: PostureZone): String = when (zone) {
        PostureZone.SAFE -> getString(R.string.posture_status_safe)
        PostureZone.WARNING -> getString(R.string.posture_status_warning)
        PostureZone.DANGER -> getString(R.string.posture_status_danger)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "MediaPipe error: $error")
    }

    override fun onDestroy() {
        isCameraPaused = true
        toneGenerator?.release()
        toneGenerator = null
        sensorManager?.unregisterListener(this)
        resetDangerTimer()
        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        CameraOwnership.release(CameraOwnership.Owner.BACKGROUND_SERVICE)
        if (this::poseLandmarkerHelper.isInitialized) poseLandmarkerHelper.clearPoseLandmarker()
        backgroundExecutor.shutdown()
        try {
            backgroundExecutor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        warningOverlayView?.let { overlay ->
            try {
                windowManager?.removeView(overlay)
            } catch (_: Exception) {
                Unit
            }
        }
        warningOverlayView = null
        wasShowingBadPet = false
        hidePetOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
