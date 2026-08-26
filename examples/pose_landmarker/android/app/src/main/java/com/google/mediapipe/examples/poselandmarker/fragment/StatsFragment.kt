package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.mediapipe.examples.poselandmarker.FamilyAccountResponse
import com.google.mediapipe.examples.poselandmarker.FamilyCreateRequest
import com.google.mediapipe.examples.poselandmarker.FamilyDashboardResponse
import com.google.mediapipe.examples.poselandmarker.FamilyJoinRequest
import com.google.mediapipe.examples.poselandmarker.FamilyLeaderboardEntryResponse
import com.google.mediapipe.examples.poselandmarker.FamilyLeaderboardResponse
import com.google.mediapipe.examples.poselandmarker.HeadUpApiClient
import com.google.mediapipe.examples.poselandmarker.HeadUpAuthStore
import com.google.mediapipe.examples.poselandmarker.HeadUpRepository
import com.google.mediapipe.examples.poselandmarker.HeadUpTask
import com.google.mediapipe.examples.poselandmarker.HeadUpUiState
import com.google.mediapipe.examples.poselandmarker.InsightLevel
import com.google.mediapipe.examples.poselandmarker.PostureDashboard
import com.google.mediapipe.examples.poselandmarker.PostureInsight
import com.google.mediapipe.examples.poselandmarker.PostureZone
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentStatsBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemDailyTaskBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemStatCardBinding
import kotlinx.coroutines.launch

class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private var latestState = HeadUpUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAccountControls()
        renderLocalAccount()
        render(HeadUpRepository.currentState(requireContext()))
        HeadUpRepository.observeState().observe(viewLifecycleOwner) { render(it) }
        HeadUpRepository.observeDashboard().observe(viewLifecycleOwner) { renderDashboard(it) }
        HeadUpRepository.refreshDashboard(requireContext())
        refreshFamilyAccount()
    }

    private fun setupAccountControls() {
        binding.familyCreateButton.setOnClickListener { showCreateFamilyDialog() }
        binding.familyJoinButton.setOnClickListener { showJoinFamilyDialog() }
        binding.familyRefreshButton.setOnClickListener { refreshFamilyAccount(showToast = true) }
    }

    private fun renderLocalAccount() {
        val context = requireContext()
        val label = HeadUpAuthStore.userLabel(context)
        val plan = HeadUpAuthStore.subscriptionTier(context)
        val role = HeadUpAuthStore.role(context)
        val signedIn = HeadUpAuthStore.isSignedIn(context)
        binding.accountPlanTitle.text = getString(R.string.account_management)
        binding.accountPlanDetail.text = if (signedIn) {
            getString(R.string.account_personal_format, label, planLabel(plan), roleLabel(role))
        } else {
            getString(R.string.account_quick_use_format, label)
        }
        binding.familyCreateButton.visibility = if (signedIn && plan != "family" && role != "guest") View.VISIBLE else View.GONE
        binding.familyJoinButton.visibility = if (signedIn && plan != "family" && role != "guest") View.VISIBLE else View.GONE
        binding.familyRefreshButton.isEnabled = signedIn
        binding.familyManagementSection.visibility = View.GONE
    }

    private fun refreshFamilyAccount(showToast: Boolean = false) {
        val appContext = requireContext().applicationContext
        if (!HeadUpAuthStore.isSignedIn(appContext)) {
            renderLocalAccount()
            return
        }
        binding.accountPlanDetail.text = getString(R.string.family_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val service = HeadUpApiClient.authenticatedService(appContext)
                val account = service.familyAccount()
                HeadUpAuthStore.updateAccountMetadata(
                    appContext,
                    account.currentUser.id,
                    account.currentUser.subscriptionTier,
                    account.currentUser.role,
                    account.currentUser.displayName,
                    account.currentUser.familyId,
                )
                val dashboard = service.familyDashboard()
                val leaderboard = service.familyLeaderboard()
                renderFamilyAccount(account, dashboard, leaderboard)
                if (showToast) {
                    Toast.makeText(requireContext(), R.string.family_refreshed, Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                renderLocalAccount()
                if (showToast) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.family_unavailable, error.message ?: ""),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun renderFamilyAccount(
        account: FamilyAccountResponse,
        dashboard: FamilyDashboardResponse,
        leaderboard: FamilyLeaderboardResponse,
    ) {
        val userName = account.currentUser.displayName?.takeIf { it.isNotBlank() }
            ?: account.currentUser.email
        val family = account.family
        binding.accountPlanDetail.text = if (family == null) {
            getString(
                R.string.account_personal_format,
                userName,
                planLabel(account.plan),
                roleLabel(account.role),
            )
        } else {
            getString(
                R.string.account_family_format,
                userName,
                roleLabel(account.role),
                family.name,
                family.inviteCode,
            )
        }
        binding.familyCreateButton.visibility = if (family == null && account.role != "guest") View.VISIBLE else View.GONE
        binding.familyJoinButton.visibility = if (family == null && account.role != "guest") View.VISIBLE else View.GONE
        binding.familyRefreshButton.isEnabled = true

        if (family == null) {
            binding.familyManagementSection.visibility = View.GONE
            return
        }

        binding.familyManagementSection.visibility = View.VISIBLE
        binding.familyManagementTitle.text = if (account.isFamilyManager) {
            getString(R.string.family_dashboard)
        } else {
            getString(R.string.family_leaderboard)
        }
        binding.familyOverviewText.text = if (account.isFamilyManager) {
            getString(
                R.string.family_overview_format,
                family.name,
                dashboard.memberCount,
                dashboard.totalSlouchCount,
                (dashboard.averageAiInterceptRate * 100).toInt(),
                dashboard.totalPetExp,
                family.inviteCode,
            )
        } else {
            getString(R.string.family_member_privacy_note, family.name)
        }
        renderLeaderboard(leaderboard.leaderboard)
    }

    private fun renderLeaderboard(entries: List<FamilyLeaderboardEntryResponse>) {
        binding.familyLeaderboardContainer.removeAllViews()
        if (entries.isEmpty()) {
            binding.familyLeaderboardContainer.addView(accountTextRow(getString(R.string.family_no_rank_data)))
            return
        }
        entries.forEach { entry ->
            binding.familyLeaderboardContainer.addView(
                accountTextRow(
                    getString(
                        R.string.family_rank_format,
                        entry.rank,
                        entry.displayName,
                        entry.goodPostureScore,
                        entry.slouchCount,
                    ),
                ),
            )
        }
    }

    private fun accountTextRow(text: String): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(requireContext(), R.color.headup_text_primary))
            textSize = 14f
            setPadding(0, 8, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun showCreateFamilyDialog() {
        if (!HeadUpAuthStore.isSignedIn(requireContext())) {
            Toast.makeText(requireContext(), R.string.family_requires_login, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.register_family_name)
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.family_create)
            .setView(input)
            .setPositiveButton(R.string.family_create, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), R.string.family_name_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                runFamilyAction {
                    HeadUpApiClient.authenticatedService(requireContext()).createFamily(FamilyCreateRequest(name))
                    Toast.makeText(requireContext(), R.string.family_create_success, Toast.LENGTH_SHORT).show()
                    refreshFamilyAccount()
                }
            }
        }
        dialog.show()
    }

    private fun showJoinFamilyDialog() {
        if (!HeadUpAuthStore.isSignedIn(requireContext())) {
            Toast.makeText(requireContext(), R.string.family_requires_login, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.register_invite_code)
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.family_join)
            .setView(input)
            .setPositiveButton(R.string.family_join, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val inviteCode = input.text.toString().trim()
                if (inviteCode.isBlank()) {
                    Toast.makeText(requireContext(), R.string.register_invite_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                runFamilyAction {
                    HeadUpApiClient.authenticatedService(requireContext()).joinFamily(
                        FamilyJoinRequest(
                            inviteCode = inviteCode,
                            displayName = HeadUpAuthStore.displayName(requireContext()),
                        ),
                    )
                    Toast.makeText(requireContext(), R.string.family_join_success, Toast.LENGTH_SHORT).show()
                    refreshFamilyAccount()
                }
            }
        }
        dialog.show()
    }

    private fun runFamilyAction(action: suspend () -> Unit) {
        setFamilyButtonsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                action()
            } catch (error: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.family_unavailable, error.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                setFamilyButtonsEnabled(true)
            }
        }
    }

    private fun setFamilyButtonsEnabled(enabled: Boolean) {
        _binding?.familyCreateButton?.isEnabled = enabled
        _binding?.familyJoinButton?.isEnabled = enabled
        _binding?.familyRefreshButton?.isEnabled = enabled
    }

    private fun planLabel(plan: String): String = when (plan) {
        "family" -> getString(R.string.plan_family_title)
        "guest" -> getString(R.string.plan_guest_title)
        "admin" -> getString(R.string.plan_admin_title)
        else -> getString(R.string.plan_individual_title)
    }

    private fun roleLabel(role: String): String = when (role) {
        "family_manager", "admin" -> getString(R.string.family_manager_role)
        "family_member" -> getString(R.string.family_member_role)
        "guest" -> getString(R.string.guest_role)
        else -> getString(R.string.personal_role)
    }

    private fun render(state: HeadUpUiState) {
        latestState = state
        val zoneColor = ContextCompat.getColor(requireContext(), state.metrics.zone.colorRes())
        binding.currentAngleValue.text = "${state.metrics.angleDegrees}\u00B0"
        binding.currentAngleValue.setTextColor(zoneColor)
        binding.currentStatusText.text = getString(
            when (state.metrics.zone) {
                PostureZone.SAFE -> R.string.posture_status_safe
                PostureZone.WARNING -> R.string.posture_status_warning
                PostureZone.DANGER -> R.string.posture_status_danger
            },
        )
        binding.currentStatusText.setTextColor(zoneColor)

        val rows = listOf(binding.taskRowOne, binding.taskRowTwo, binding.taskRowThree)
        val titles = listOf(R.string.task_good_posture, R.string.task_eye_rest, R.string.task_posture_challenge)
        state.tasks.zip(rows).forEachIndexed { index, (task, row) ->
            bindTask(row, task, getString(titles[index]))
            row.root.setOnClickListener { handleTaskClick(task) }
        }
    }

    private fun renderDashboard(dashboard: PostureDashboard) {
        val today = dashboard.todayHealth
        val total = today.totalSeconds
        val safePercent = today.safePercent
        val badPercent = today.riskPercent

        bindStat(binding.cumulativeCard, "T", getString(R.string.tracked_today), formatDuration(total))
        bindStat(binding.protectCard, "G", getString(R.string.good_posture_rate), "$safePercent%")
        bindStat(binding.goodPostureCard, "R", getString(R.string.risk_time_today), formatDuration(today.riskSeconds))
        bindStat(binding.streakCard, "E", getString(R.string.posture_event_count), getString(R.string.events_format, today.dangerEvents))

        binding.reportSummaryText.text = getString(
            R.string.health_report_summary_format,
            safePercent,
            today.dangerEvents,
            formatDuration(today.riskSeconds),
        )
        binding.healthTrendChart.setData(dashboard.weekHealth)
        binding.posturePieChart.setData(today.safeSeconds, today.warningSeconds, today.dangerSeconds)
        binding.safePercentText.text = getString(R.string.safe_posture_percent, safePercent)
        binding.badPercentText.text = getString(R.string.bad_posture_percent, badPercent)
        binding.trackedTimeText.text = getString(R.string.tracked_time, formatDuration(total))
        binding.averageAngleValue.text = getString(R.string.angle_degrees_format, today.averageAngleDegrees)
        binding.peakAngleValue.text = getString(R.string.angle_degrees_format, today.peakAngleDegrees)
        binding.closeScreenValue.text = formatDuration(today.closeScreenSeconds)
        binding.rapidFallValue.text = getString(R.string.events_format, today.rapidFallEvents)
        binding.secondaryMarkerText.text = getString(
            R.string.health_secondary_markers_format,
            today.shoulderImbalanceEvents,
            formatDistance(today.averageScreenDistanceCm),
            formatDuration(today.veryCloseScreenSeconds),
        )

        renderInsights(dashboard.insights)
    }

    private fun renderInsights(insights: List<PostureInsight>) {
        binding.insightsContainer.removeAllViews()
        if (insights.isEmpty()) {
            binding.insightsTitle.visibility = View.GONE
            return
        }

        binding.insightsTitle.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(requireContext())
        insights.forEach { insight ->
            val itemView = inflater.inflate(R.layout.item_posture_insight, binding.insightsContainer, false)
            val titleView = itemView.findViewById<android.widget.TextView>(R.id.insight_title)
            val descView = itemView.findViewById<android.widget.TextView>(R.id.insight_description)
            val iconView = itemView.findViewById<android.widget.ImageView>(R.id.insight_icon)

            titleView.text = insight.title
            descView.text = insight.description

            val colorRes = when (insight.level) {
                InsightLevel.SUCCESS -> R.color.headup_safe
                InsightLevel.WARNING -> R.color.headup_danger
                InsightLevel.INFO -> R.color.headup_primary
            }
            iconView.setColorFilter(ContextCompat.getColor(requireContext(), colorRes))
            binding.insightsContainer.addView(itemView)
        }
    }

    private fun handleTaskClick(task: HeadUpTask) {
        if (task.isComplete) {
            val claimed = HeadUpRepository.claimTask(requireContext(), task.id)
            Toast.makeText(
                requireContext(),
                if (claimed) R.string.reward_claimed else R.string.reward_already_claimed,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        when (task.id) {
            "eye_rest" -> showEyeRestDialog()
            "posture_challenge" -> findNavController().navigate(R.id.challenge_fragment)
            else -> findNavController().navigate(R.id.camera_fragment)
        }
    }

    private fun showEyeRestDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.eye_rest_title)
            .setMessage(R.string.eye_rest_message)
            .setPositiveButton(R.string.eye_rest_complete) { _, _ ->
                HeadUpRepository.recordEyeRest(requireContext())
                Toast.makeText(requireContext(), R.string.eye_rest_recorded, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun bindStat(card: ItemStatCardBinding, icon: String, label: String, value: String) {
        card.statIcon.text = icon
        card.statLabel.text = label
        card.statValue.text = value
    }

    private fun formatDistance(distanceCm: Int?): String =
        distanceCm?.let { getString(R.string.centimeters_format, it) } ?: getString(R.string.no_data_placeholder)

    private fun bindTask(row: ItemDailyTaskBinding, task: HeadUpTask, title: String) {
        row.taskTitle.text = title
        row.taskDetail.text = when (task.id) {
            "good_posture" -> getString(R.string.minutes_progress_format, task.progress, task.max)
            "eye_rest" -> getString(R.string.times_format, task.progress, task.max)
            else -> getString(R.string.challenge_progress_format, task.progress, task.max)
        }
        row.taskReward.text = if (task.claimed) getString(R.string.claimed) else "+${task.reward}"
        row.taskCheck.alpha = if (task.isComplete) 1f else 0.35f
        row.root.alpha = if (task.claimed) 0.65f else 1f
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3_600L
        val minutes = (seconds % 3_600L) / 60L
        return if (hours > 0L) getString(R.string.hours_minutes_format, hours, minutes)
        else getString(R.string.minutes_only_format, minutes)
    }

    private fun PostureZone.colorRes(): Int = when (this) {
        PostureZone.SAFE -> R.color.headup_safe
        PostureZone.WARNING -> R.color.headup_warning
        PostureZone.DANGER -> R.color.headup_danger
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
