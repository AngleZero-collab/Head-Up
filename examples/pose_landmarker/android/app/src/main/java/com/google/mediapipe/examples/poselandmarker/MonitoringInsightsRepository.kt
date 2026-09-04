package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class GuardEffectivenessDay(
    val date: String,
    val observation: ModePostureAggregate,
    val guarding: ModePostureAggregate,
)

data class GuardEffectivenessReport(
    val fromDate: String,
    val toDate: String,
    val days: List<GuardEffectivenessDay>,
    val comparison: GuardEffectivenessComparison,
    val reminderCorrectionRate: Double?,
    val averageRecoverySeconds: Double?,
    val observationAverageGreenStreakSeconds: Double?,
    val guardingAverageGreenStreakSeconds: Double?,
)

object MonitoringInsightsRepository {
    private val executor = Executors.newSingleThreadExecutor()

    fun dateRange(days: Int, nowMs: Long = System.currentTimeMillis()): Pair<String, String> {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        val to = DATE_FORMAT.get()!!.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -(days.coerceAtLeast(1) - 1))
        return DATE_FORMAT.get()!!.format(calendar.time) to to
    }

    fun load(
        context: Context,
        fromDate: String,
        toDate: String,
        callback: (GuardEffectivenessReport) -> Unit,
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val rows = PostureDatabase.getInstance(appContext).monitoringDao()
                .aggregatesBetween(HeadUpAuthStore.currentUserId(appContext), fromDate, toDate)
            callback(buildReport(fromDate, toDate, rows))
        }
    }

    fun buildReport(
        fromDate: String,
        toDate: String,
        rows: List<DailyPostureAggregateEntity>,
    ): GuardEffectivenessReport {
        val byDate = rows.groupBy { it.recordDate }
        val days = enumerateDates(fromDate, toDate).map { date ->
            val current = byDate[date].orEmpty()
            GuardEffectivenessDay(
                date = date,
                observation = current.toModeAggregate(MonitoringMode.OBSERVATION),
                guarding = current.toModeAggregate(MonitoringMode.GUARDING),
            )
        }
        val observation = rows.toModeAggregate(MonitoringMode.OBSERVATION)
        val guarding = rows.toModeAggregate(MonitoringMode.GUARDING)
        return GuardEffectivenessReport(
            fromDate = fromDate,
            toDate = toDate,
            days = days,
            comparison = GuardEffectivenessCalculator.compare(observation, guarding),
            reminderCorrectionRate = guarding.reminderCount.takeIf { it > 0 }
                ?.let { guarding.successfulCorrections.toDouble() / it },
            averageRecoverySeconds = guarding.successfulCorrections.takeIf { it > 0 }
                ?.let { guarding.recoverySecondsTotal.toDouble() / it },
            observationAverageGreenStreakSeconds = observation.greenStreakCount.takeIf { it > 0 }
                ?.let { observation.greenStreakTotalSeconds.toDouble() / it },
            guardingAverageGreenStreakSeconds = guarding.greenStreakCount.takeIf { it > 0 }
                ?.let { guarding.greenStreakTotalSeconds.toDouble() / it },
        )
    }

    private fun List<DailyPostureAggregateEntity>.toModeAggregate(mode: MonitoringMode): ModePostureAggregate {
        val matching = filter { it.mode == mode.name }
        return ModePostureAggregate(
            mode = mode,
            greenSeconds = matching.sumOf { it.greenSeconds },
            yellowSeconds = matching.sumOf { it.yellowSeconds },
            redSeconds = matching.sumOf { it.redSeconds },
            unknownSeconds = matching.sumOf { it.unknownSeconds },
            longestGreenStreakSeconds = matching.maxOfOrNull { it.longestGreenStreakSeconds } ?: 0L,
            greenStreakCount = matching.sumOf { it.greenStreakCount },
            greenStreakTotalSeconds = matching.sumOf { it.greenStreakTotalSeconds },
            reminderCount = matching.sumOf { it.reminderCount },
            successfulCorrections = matching.sumOf { it.successfulCorrections },
            recoverySecondsTotal = matching.sumOf { it.recoverySecondsTotal },
        )
    }

    private fun enumerateDates(fromDate: String, toDate: String): List<String> {
        val from = DATE_FORMAT.get()!!.parse(fromDate) ?: return emptyList()
        val to = DATE_FORMAT.get()!!.parse(toDate) ?: return emptyList()
        val calendar = Calendar.getInstance().apply { time = from }
        val result = mutableListOf<String>()
        while (!calendar.time.after(to) && result.size < 366) {
            result += DATE_FORMAT.get()!!.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }
    }
}
