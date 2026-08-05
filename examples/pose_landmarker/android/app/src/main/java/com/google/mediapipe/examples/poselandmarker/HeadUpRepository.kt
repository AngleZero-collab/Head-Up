package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToLong

object HeadUpRepository {
    private const val TAG = "HeadUpRepository"
    private const val PREFS_NAME = "headup_secure_prefs"
    private const val KEY_GOOD_SECONDS = "good_seconds"
    private const val KEY_WARNING_SECONDS = "warning_seconds"
    private const val KEY_DANGER_SECONDS = "danger_seconds"
    private const val KEY_EYE_REST_COUNT = "eye_rest_count"
    private const val KEY_CONSECUTIVE_DAYS = "consecutive_days"
    private const val KEY_DRAGON_ENERGY = "dragon_energy"
    private const val KEY_DRAGON_LEVEL = "dragon_level"
    private const val KEY_COINS = "coins"
    private const val KEY_LAST_UPDATED = "last_updated"
    private const val KEY_STATE_DAY = "state_day"
    private const val KEY_CALIBRATION_ANGLE = "calibration_angle"
    private const val KEY_CALIBRATION_RATIO = "calibration_ratio"
    private const val KEY_CALIBRATION_SHOULDER = "calibration_shoulder"
    private const val KEY_CALIBRATION_EYE_DISTANCE = "calibration_eye_distance"
    private const val KEY_CALIBRATION_DISTANCE_K = "calibration_distance_k"
    private const val KEY_CALIBRATION_TIME = "calibration_time"
    private const val KEY_FOREGROUND_SCAN_ACTIVE = "foreground_scan_active"
    private const val KEY_OWNED_ITEMS = "owned_items"
    private const val KEY_CLAIMED_TASKS = "claimed_tasks"
    private const val KEY_ALARM_ENABLED = "alarm_enabled"
    private const val KEY_BACKGROUND_GUARD_ENABLED = "background_guard_enabled"
    private const val KEY_CALIBRATION_REQUESTED = "calibration_requested"
    private const val KEY_SELECTED_MODEL = "selected_model"
    private const val KEY_SELECTED_DELEGATE = "selected_delegate"
    private const val RECORD_INTERVAL_MS = 1_000L
    private const val MAX_RECORD_INTERVAL_MS = 2_000L
    private const val HISTORY_RETENTION_DAYS = 90

    private val stateLiveData = MutableLiveData(HeadUpUiState())
    private val dashboardLiveData = MutableLiveData(PostureDashboard())
    private val databaseExecutor = Executors.newSingleThreadExecutor()
    private var lastDashboardRefreshMs = 0L
    private var sharedPrefs: SharedPreferences? = null

    fun observeState(): LiveData<HeadUpUiState> = stateLiveData

    fun observeDashboard(): LiveData<PostureDashboard> = dashboardLiveData

    @Synchronized
    fun currentState(context: Context): HeadUpUiState =
        loadState(context).also { stateLiveData.postValue(it) }

    fun setCalibration(context: Context, profile: CalibrationProfile) {
        getPrefs(context).edit {
            putFloat(KEY_CALIBRATION_ANGLE, profile.angleDegrees)
            putFloat(KEY_CALIBRATION_RATIO, profile.postureRatio)
            putFloat(KEY_CALIBRATION_SHOULDER, profile.shoulderWidth)
            profile.eyeDistancePixels?.let { putFloat(KEY_CALIBRATION_EYE_DISTANCE, it) }
                ?: remove(KEY_CALIBRATION_EYE_DISTANCE)
            profile.distanceConstantK?.let { putFloat(KEY_CALIBRATION_DISTANCE_K, it) }
                ?: remove(KEY_CALIBRATION_DISTANCE_K)
            putLong(KEY_CALIBRATION_TIME, profile.calibratedAtMs)
        }
        PostureAnalyzer.resetSmoothing()
        currentState(context)
    }

    fun getCalibration(context: Context): CalibrationProfile? {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_CALIBRATION_ANGLE)) return null
        return CalibrationProfile(
            angleDegrees = prefs.getFloat(KEY_CALIBRATION_ANGLE, 0f),
            postureRatio = prefs.getFloat(KEY_CALIBRATION_RATIO, 0f),
            shoulderWidth = prefs.getFloat(KEY_CALIBRATION_SHOULDER, 0f),
            eyeDistancePixels = prefs.getFloat(KEY_CALIBRATION_EYE_DISTANCE, 0f)
                .takeIf { it > 0f },
            distanceConstantK = prefs.getFloat(KEY_CALIBRATION_DISTANCE_K, 0f)
                .takeIf { it > 0f },
            calibratedAtMs = prefs.getLong(KEY_CALIBRATION_TIME, 0L),
        )
    }

    fun clearCalibration(context: Context) {
        getPrefs(context).edit {
            remove(KEY_CALIBRATION_ANGLE)
            remove(KEY_CALIBRATION_RATIO)
            remove(KEY_CALIBRATION_SHOULDER)
            remove(KEY_CALIBRATION_EYE_DISTANCE)
            remove(KEY_CALIBRATION_DISTANCE_K)
            remove(KEY_CALIBRATION_TIME)
        }
        PostureAnalyzer.resetSmoothing()
        currentState(context)
    }

    fun requestCalibration(context: Context) {
        getPrefs(context).edit { putBoolean(KEY_CALIBRATION_REQUESTED, true) }
    }

    fun consumeCalibrationRequest(context: Context): Boolean {
        val prefs = getPrefs(context)
        val requested = prefs.getBoolean(KEY_CALIBRATION_REQUESTED, false)
        if (requested) prefs.edit { putBoolean(KEY_CALIBRATION_REQUESTED, false) }
        return requested
    }

    @Synchronized
    fun recordMetrics(
        context: Context,
        metrics: PostureMetrics,
        source: String,
    ): HeadUpUiState {
        val appContext = context.applicationContext
        val previous = loadState(appContext)
        val now = metrics.timestampMs
        val elapsedMs = (now - previous.lastUpdatedMs).coerceIn(0L, MAX_RECORD_INTERVAL_MS)
        if (previous.lastUpdatedMs > 0L && elapsedMs < RECORD_INTERVAL_MS) {
            return previous.copy(metrics = metrics).also { stateLiveData.postValue(it) }
        }

        val elapsedSeconds = (elapsedMs / 1_000f).roundToLong()
        val next = previous.copy(
            metrics = metrics,
            goodPostureSecondsToday = previous.goodPostureSecondsToday +
                if (metrics.zone == PostureZone.SAFE) elapsedSeconds else 0L,
            warningSecondsToday = previous.warningSecondsToday +
                if (metrics.zone == PostureZone.WARNING) elapsedSeconds else 0L,
            dangerSecondsToday = previous.dangerSecondsToday +
                if (metrics.zone == PostureZone.DANGER) elapsedSeconds else 0L,
            dragonEnergy = (previous.dragonEnergy + metrics.energyDelta(elapsedSeconds)).coerceIn(0, 100),
            dragonLevel = previous.dragonLevel +
                if (previous.dragonEnergy < 100 && previous.dragonEnergy + metrics.energyDelta(elapsedSeconds) >= 100) 1 else 0,
            lastUpdatedMs = now,
        )
        saveState(appContext, next)
        stateLiveData.postValue(next)

        if (elapsedMs > 0L) {
            val record = PostureRecordEntity(
                userId = HeadUpAuthStore.currentUserId(appContext),
                timestampMs = now,
                durationMs = elapsedMs,
                angleDegrees = metrics.angleDegrees,
                rawAngleDegrees = metrics.rawAngleDegrees,
                neckFlexionDegrees = metrics.neckFlexionDegrees,
                shoulderBalanceDegrees = metrics.shoulderBalanceDegrees,
                screenDistanceCm = metrics.screenDistanceCm,
                landmarkConfidence = metrics.landmarkConfidence,
                zone = metrics.zone.name,
                source = source,
                isRapidFall = metrics.isRapidFall,
                isSynced = false,
            )
            executeDatabaseTask {
                val dao = PostureDatabase.getInstance(appContext).postureRecordDao()
                dao.insert(record)
                if (now - lastDashboardRefreshMs >= 5_000L) {
                    lastDashboardRefreshMs = now
                    refreshDashboardInternal(appContext, dao)
                }
            }
        }
        return next
    }

    fun recordEyeRest(context: Context) {
        val state = loadState(context)
        val next = state.copy(eyeRestCountToday = state.eyeRestCountToday + 1)
        saveState(context, next)
        stateLiveData.postValue(next)
    }

    fun claimTask(context: Context, taskId: String): Boolean {
        val state = loadState(context)
        val task = state.tasks.firstOrNull { it.id == taskId } ?: return false
        if (!task.isComplete || task.claimed) return false
        val next = state.copy(
            coins = state.coins + task.reward,
            claimedTasks = state.claimedTasks + taskId,
        )
        saveState(context, next)
        stateLiveData.postValue(next)
        return true
    }

    fun purchaseItem(context: Context, itemId: String): Boolean {
        val state = loadState(context)
        val item = state.shopItems.firstOrNull { it.id == itemId } ?: return false
        if (item.isOwned || state.coins < item.cost) return false
        val next = state.copy(
            coins = state.coins - item.cost,
            ownedShopItems = state.ownedShopItems + itemId,
        )
        saveState(context, next)
        stateLiveData.postValue(next)
        return true
    }

    fun refreshDashboard(context: Context) {
        val appContext = context.applicationContext
        executeDatabaseTask {
            refreshDashboardInternal(appContext, PostureDatabase.getInstance(appContext).postureRecordDao())
        }
    }

    fun resetAllData(context: Context) {
        val appContext = context.applicationContext
        getPrefs(appContext).edit { clear() }
        stateLiveData.postValue(HeadUpUiState())
        dashboardLiveData.postValue(PostureDashboard())
        executeDatabaseTask {
            PostureDatabase.getInstance(appContext).postureRecordDao().deleteAll()
        }
        PostureAnalyzer.resetSmoothing()
    }

    fun setForegroundScanActive(context: Context, active: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_FOREGROUND_SCAN_ACTIVE, active) }
    }

    fun isForegroundScanActive(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_FOREGROUND_SCAN_ACTIVE, false)

    fun isAlarmEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_ALARM_ENABLED, false)

    fun setAlarmEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_ALARM_ENABLED, enabled) }
        currentState(context)
    }

    fun isBackgroundGuardEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_BACKGROUND_GUARD_ENABLED, true)

    fun setBackgroundGuardEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_BACKGROUND_GUARD_ENABLED, enabled) }
        currentState(context)
    }

    fun getSelectedModel(context: Context): Int =
        getPrefs(context).getInt(KEY_SELECTED_MODEL, PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL)

    fun setSelectedModel(context: Context, model: Int) {
        getPrefs(context).edit { putInt(KEY_SELECTED_MODEL, model) }
    }

    fun getSelectedDelegate(context: Context): Int =
        getPrefs(context).getInt(KEY_SELECTED_DELEGATE, PoseLandmarkerHelper.DELEGATE_CPU)

    fun setSelectedDelegate(context: Context, delegate: Int) {
        getPrefs(context).edit { putInt(KEY_SELECTED_DELEGATE, delegate) }
    }

    fun getAllRecordsAsCsv(context: Context, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        executeDatabaseTask {
            val records = PostureDatabase.getInstance(appContext).postureRecordDao()
                .recordsBetween(0, System.currentTimeMillis())
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val csv = buildString {
                append("time,user_id,angle,raw_angle,neck_flexion,shoulder_balance,screen_distance_cm,zone,source,rapid_fall,synced\n")
                records.forEach { record ->
                    append(dateFormat.format(Date(record.timestampMs)))
                    append(",${record.userId},${record.angleDegrees},${record.rawAngleDegrees}")
                    append(",${record.neckFlexionDegrees},${record.shoulderBalanceDegrees}")
                    append(",${record.screenDistanceCm ?: ""},${record.zone},${record.source}")
                    append(",${record.isRapidFall},${record.isSynced}\n")
                }
            }
            callback(csv)
        }
    }

    fun getRecordStats(context: Context, callback: (count: Int, unsynced: Int, sizeKb: Long) -> Unit) {
        val appContext = context.applicationContext
        executeDatabaseTask {
            val dao = PostureDatabase.getInstance(appContext).postureRecordDao()
            val count = dao.recordsBetween(0, System.currentTimeMillis()).size
            val unsynced = dao.unsyncedCount()
            val sizeKb = PostureDatabase.databaseFile(appContext).length() / 1024L
            callback(count, unsynced, sizeKb)
        }
    }

    private fun refreshDashboardInternal(context: Context, dao: PostureRecordDao) {
        val todayStart = startOfDay(System.currentTimeMillis())
        val firstDay = addDays(todayStart, -6)
        val records = dao.recordsBetween(firstDay, addDays(todayStart, 1))
        val summaries = (0..6).map { index ->
            val dayStart = addDays(firstDay, index)
            val dayEnd = addDays(dayStart, 1)
            summarizeDay(dayStart, records.filter { it.timestampMs in dayStart until dayEnd })
        }

        dashboardLiveData.postValue(
            PostureDashboard(
                today = summaries.lastOrNull() ?: DailyPostureSummary(todayStart, 0L, 0L, 0L, 0),
                week = summaries,
                insights = generateInsights(summaries),
            ),
        )
        dao.deleteOlderThan(addDays(todayStart, -HISTORY_RETENTION_DAYS))

        var trackedDays = 0
        for (summary in summaries.asReversed()) {
            if (summary.safeSeconds + summary.warningSeconds + summary.dangerSeconds <= 0L) break
            trackedDays++
        }
        val state = loadState(context)
        if (state.consecutiveDays != trackedDays) {
            val next = state.copy(consecutiveDays = trackedDays)
            saveState(context, next)
            stateLiveData.postValue(next)
        }
    }

    private fun generateInsights(summaries: List<DailyPostureSummary>): List<PostureInsight> {
        val today = summaries.lastOrNull() ?: return emptyList()
        val totalSeconds = today.safeSeconds + today.warningSeconds + today.dangerSeconds
        if (totalSeconds < 300L) {
            return listOf(PostureInsight("資料累積中", "累積更多偵測時間後，家長報表會顯示更穩定的趨勢。"))
        }

        val safePercent = (today.safeSeconds * 100L / totalSeconds).toInt()
        val insights = mutableListOf<PostureInsight>()
        when {
            safePercent >= 90 -> insights += PostureInsight("姿勢表現優秀", "今日正確坐姿比例達 $safePercent%。", InsightLevel.SUCCESS)
            safePercent >= 70 -> insights += PostureInsight("姿勢穩定", "今日正確坐姿比例達 $safePercent%，仍有進步空間。")
            else -> insights += PostureInsight("需要更多提醒", "今日姿勢不良時間偏高，建議重新校準並調整桌椅。", InsightLevel.WARNING)
        }
        if (today.dangerEvents > 10) {
            insights += PostureInsight("低頭次數偏多", "今日已出現 ${today.dangerEvents} 次姿勢不良事件。", InsightLevel.WARNING)
        }
        return insights
    }

    private fun summarizeDay(dayStart: Long, records: List<PostureRecordEntity>): DailyPostureSummary {
        var previousDanger = false
        var dangerEvents = 0
        records.forEach { record ->
            val danger = record.zone == PostureZone.DANGER.name
            if (danger && !previousDanger) dangerEvents++
            previousDanger = danger
        }
        fun secondsFor(zone: PostureZone): Long = records
            .filter { it.zone == zone.name }
            .sumOf { it.durationMs } / 1_000L
        return DailyPostureSummary(
            dayStartMs = dayStart,
            safeSeconds = secondsFor(PostureZone.SAFE),
            warningSeconds = secondsFor(PostureZone.WARNING),
            dangerSeconds = secondsFor(PostureZone.DANGER),
            dangerEvents = dangerEvents,
        )
    }

    private fun PostureMetrics.energyDelta(elapsedSeconds: Long): Int = when (zone) {
        PostureZone.SAFE -> elapsedSeconds.toInt()
        PostureZone.WARNING -> 0
        PostureZone.DANGER -> -elapsedSeconds.toInt()
    }

    private fun loadState(context: Context): HeadUpUiState {
        val prefs = getPrefs(context)
        val today = startOfDay(System.currentTimeMillis())
        val storedDay = prefs.getLong(KEY_STATE_DAY, today)
        val isToday = storedDay == today
        return HeadUpUiState(
            metrics = stateLiveData.value?.metrics ?: PostureAnalyzer.defaultMetrics(),
            goodPostureSecondsToday = if (isToday) prefs.getLong(KEY_GOOD_SECONDS, 0L) else 0L,
            warningSecondsToday = if (isToday) prefs.getLong(KEY_WARNING_SECONDS, 0L) else 0L,
            dangerSecondsToday = if (isToday) prefs.getLong(KEY_DANGER_SECONDS, 0L) else 0L,
            eyeRestCountToday = if (isToday) prefs.getInt(KEY_EYE_REST_COUNT, 0) else 0,
            consecutiveDays = prefs.getInt(KEY_CONSECUTIVE_DAYS, 0),
            dragonEnergy = prefs.getInt(KEY_DRAGON_ENERGY, 50),
            dragonLevel = prefs.getInt(KEY_DRAGON_LEVEL, 1),
            coins = prefs.getInt(KEY_COINS, 0),
            lastUpdatedMs = if (isToday) prefs.getLong(KEY_LAST_UPDATED, 0L) else 0L,
            calibrationProfile = getCalibration(context),
            ownedShopItems = prefs.getStringSet(KEY_OWNED_ITEMS, emptySet())?.toSet().orEmpty(),
            claimedTasks = if (isToday) prefs.getStringSet(KEY_CLAIMED_TASKS, emptySet())?.toSet().orEmpty() else emptySet(),
            isAlarmEnabled = prefs.getBoolean(KEY_ALARM_ENABLED, false),
        )
    }

    private fun saveState(context: Context, state: HeadUpUiState) {
        getPrefs(context).edit {
            putLong(KEY_STATE_DAY, startOfDay(System.currentTimeMillis()))
            putLong(KEY_GOOD_SECONDS, state.goodPostureSecondsToday)
            putLong(KEY_WARNING_SECONDS, state.warningSecondsToday)
            putLong(KEY_DANGER_SECONDS, state.dangerSecondsToday)
            putInt(KEY_EYE_REST_COUNT, state.eyeRestCountToday)
            putInt(KEY_CONSECUTIVE_DAYS, state.consecutiveDays)
            putInt(KEY_DRAGON_ENERGY, state.dragonEnergy)
            putInt(KEY_DRAGON_LEVEL, state.dragonLevel)
            putInt(KEY_COINS, state.coins)
            putLong(KEY_LAST_UPDATED, state.lastUpdatedMs)
            putStringSet(KEY_OWNED_ITEMS, state.ownedShopItems)
            putStringSet(KEY_CLAIMED_TASKS, state.claimedTasks)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences =
        sharedPrefs ?: synchronized(this) {
            sharedPrefs ?: createEncryptedPrefs(context.applicationContext).also { sharedPrefs = it }
        }

    private fun createEncryptedPrefs(context: Context): SharedPreferences =
        HeadUpPrefs.encryptedOrPrivate(context.applicationContext, PREFS_NAME)

    private fun executeDatabaseTask(task: () -> Unit) {
        databaseExecutor.execute {
            try {
                task()
            } catch (error: Exception) {
                Log.e(TAG, "Posture database task failed.", error)
            }
        }
    }

    private fun startOfDay(timeMs: Long): Long = Calendar.getInstance().run {
        timeInMillis = timeMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    private fun addDays(timeMs: Long, days: Int): Long = Calendar.getInstance().run {
        timeInMillis = timeMs
        add(Calendar.DAY_OF_YEAR, days)
        timeInMillis
    }
}
