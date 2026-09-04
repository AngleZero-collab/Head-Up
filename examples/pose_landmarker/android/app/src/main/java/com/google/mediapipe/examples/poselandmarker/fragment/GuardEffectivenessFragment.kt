package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.mediapipe.examples.poselandmarker.GuardEffectivenessChartView
import com.google.mediapipe.examples.poselandmarker.GuardEffectivenessDay
import com.google.mediapipe.examples.poselandmarker.GuardEffectivenessReport
import com.google.mediapipe.examples.poselandmarker.MonitoringInsightsRepository
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentGuardEffectivenessBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GuardEffectivenessFragment : Fragment() {
    private var _binding: FragmentGuardEffectivenessBinding? = null
    private val binding get() = _binding!!
    private var currentReport: GuardEffectivenessReport? = null
    private var unitMode = GuardEffectivenessChartView.UnitMode.PERCENT

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentGuardEffectivenessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.rangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.range_7_days -> loadDays(7)
                R.id.range_30_days -> loadDays(30)
                R.id.range_custom -> showDateRangePicker()
            }
        }
        binding.unitToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            unitMode = when (checkedId) {
                R.id.unit_minutes -> GuardEffectivenessChartView.UnitMode.MINUTES
                R.id.unit_score -> GuardEffectivenessChartView.UnitMode.SCORE
                else -> GuardEffectivenessChartView.UnitMode.PERCENT
            }
            currentReport?.let(::renderChart)
        }
        binding.effectivenessChart.onDaySelected = ::renderDayDetail
        loadDays(7)
    }

    private fun loadDays(days: Int) {
        val (from, to) = MonitoringInsightsRepository.dateRange(days)
        loadRange(from, to)
    }

    private fun loadRange(fromDate: String, toDate: String) {
        binding.dateRangeText.text = getString(R.string.date_range_format, fromDate, toDate)
        MonitoringInsightsRepository.load(requireContext(), fromDate, toDate) { report ->
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                currentReport = report
                render(report)
            }
        }
    }

    private fun render(report: GuardEffectivenessReport) {
        val comparison = report.comparison
        binding.reductionValue.text = comparison.badPostureReductionPercent?.let(::percent) ?: "--"
        binding.correctionValue.text = report.reminderCorrectionRate?.let { percent(it * 100.0) } ?: "--"
        binding.comparisonStatus.text = if (comparison.hasEnoughData) {
            val greenChange = comparison.greenImprovementPercentagePoints?.let(::signedPercentagePoints) ?: "--"
            getString(R.string.guard_comparison_ready, greenChange)
        } else {
            getString(
                R.string.guard_comparison_insufficient,
                comparison.observation.validSeconds / 60,
                comparison.guarding.validSeconds / 60,
            )
        }
        val recovery = report.averageRecoverySeconds?.let { getString(R.string.seconds_value, it) } ?: "--"
        val observationStreak = report.observationAverageGreenStreakSeconds?.let { getString(R.string.seconds_value, it) } ?: "--"
        val guardingStreak = report.guardingAverageGreenStreakSeconds?.let { getString(R.string.seconds_value, it) } ?: "--"
        binding.recoveryText.text = getString(
            R.string.guard_recovery_summary,
            recovery,
            observationStreak,
            guardingStreak,
        )
        renderChart(report)
    }

    private fun renderChart(report: GuardEffectivenessReport) {
        binding.effectivenessChart.submitData(report.days, unitMode)
        binding.effectivenessChart.contentDescription = getString(
            R.string.guard_chart_accessibility,
            report.fromDate,
            report.toDate,
            report.comparison.observation.postureScore?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "--",
            report.comparison.guarding.postureScore?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "--",
        )
    }

    private fun renderDayDetail(day: GuardEffectivenessDay) {
        binding.dayDetailText.text = getString(
            R.string.guard_day_detail,
            day.date,
            day.observation.validSeconds / 60,
            day.observation.postureScore?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "--",
            day.guarding.validSeconds / 60,
            day.guarding.postureScore?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "--",
            day.guarding.reminderCount,
        )
    }

    private fun showDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.choose_date_range)
            .build()
        picker.addOnPositiveButtonClickListener { range ->
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            loadRange(formatter.format(Date(range.first)), formatter.format(Date(range.second)))
        }
        picker.addOnDismissListener {
            if (currentReport == null) binding.rangeToggle.check(R.id.range_7_days)
        }
        picker.show(parentFragmentManager, "guard-date-range")
    }

    private fun percent(value: Double): String = String.format(Locale.getDefault(), "%.1f%%", value)

    private fun signedPercentagePoints(value: Double): String =
        String.format(Locale.getDefault(), "%+.1f pp", value)

    override fun onDestroyView() {
        binding.effectivenessChart.onDaySelected = null
        _binding = null
        super.onDestroyView()
    }
}
