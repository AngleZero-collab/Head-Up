package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.mediapipe.examples.poselandmarker.CampusChallengeRepository
import com.google.mediapipe.examples.poselandmarker.CampusChallengeSummary
import com.google.mediapipe.examples.poselandmarker.CachedLeaderboardResult
import com.google.mediapipe.examples.poselandmarker.EducationProfileEntity
import com.google.mediapipe.examples.poselandmarker.HeadUpAuthStore
import com.google.mediapipe.examples.poselandmarker.LeaderboardEntityType
import com.google.mediapipe.examples.poselandmarker.LeaderboardPeriod
import com.google.mediapipe.examples.poselandmarker.LeaderboardScopeType
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.SchoolEntity
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCampusChallengeBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemCampusLeaderboardBinding
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class CampusChallengeFragment : Fragment() {
    private var _binding: FragmentCampusChallengeBinding? = null
    private val binding get() = _binding!!
    private var profile: EducationProfileEntity? = null
    private var selectedSchool: SchoolEntity? = null
    private var controlsReady = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentCampusChallengeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.editProfileButton.setOnClickListener { showProfileDialog() }
        setupSpinner(binding.entitySpinner, resources.getStringArray(R.array.leaderboard_entities).toList())
        setupSpinner(binding.scopeSpinner, resources.getStringArray(R.array.leaderboard_scopes).toList())
        setupSpinner(binding.periodSpinner, resources.getStringArray(R.array.leaderboard_periods).toList())
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (controlsReady) refreshLeaderboard()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.entitySpinner.onItemSelectedListener = listener
        binding.scopeSpinner.onItemSelectedListener = listener
        binding.periodSpinner.onItemSelectedListener = listener
        controlsReady = true
        loadProfileAndData()
    }

    private fun setupSpinner(spinner: Spinner, labels: List<String>) {
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
    }

    private fun loadProfileAndData() {
        viewLifecycleOwner.lifecycleScope.launch {
            profile = CampusChallengeRepository.educationProfile(requireContext())
            selectedSchool = CampusChallengeRepository.school(requireContext(), profile?.schoolId)
            renderProfile()
            refreshLeaderboard()
        }
    }

    private fun refreshLeaderboard() {
        val period = LeaderboardPeriod.entries[binding.periodSpinner.selectedItemPosition.coerceAtLeast(0)]
        val entity = LeaderboardEntityType.entries[binding.entitySpinner.selectedItemPosition.coerceAtLeast(0)]
        val scope = LeaderboardScopeType.entries[binding.scopeSpinner.selectedItemPosition.coerceAtLeast(0)]
        binding.leaderboardStatus.setText(R.string.leaderboard_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val summary = CampusChallengeRepository.challengeSummary(requireContext(), period)
            renderSummary(summary)
            val result = CampusChallengeRepository.leaderboard(requireContext(), entity, scope, period, profile)
            renderLeaderboard(result)
        }
    }

    private fun renderSummary(summary: CampusChallengeSummary) {
        binding.challengePointsValue.text = getString(R.string.challenge_points_format, summary.challengePoints)
        binding.postureScoreValue.text = getString(
            R.string.posture_score_format,
            summary.postureScore?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "--",
        )
        binding.qualificationText.text = if (summary.qualified) {
            getString(R.string.leaderboard_qualified, summary.validDays, summary.validMinutes)
        } else {
            getString(R.string.leaderboard_not_qualified, summary.validDays, summary.validMinutes)
        }
    }

    private fun renderProfile() {
        val current = profile
        if (current == null) {
            binding.profileTitle.setText(R.string.education_profile_required)
            binding.profileDetail.setText(R.string.education_profile_required_detail)
            return
        }
        binding.profileTitle.text = current.publicAlias
        val schoolName = selectedSchool?.localizedName ?: getString(R.string.school_not_selected)
        binding.profileDetail.text = getString(
            R.string.education_profile_format,
            schoolName,
            current.gradeCode ?: "--",
            if (current.leaderboardOptIn) getString(R.string.opted_in) else getString(R.string.opted_out),
        )
    }

    private fun renderLeaderboard(result: CachedLeaderboardResult) {
        binding.leaderboardContainer.removeAllViews()
        val response = result.response
        if (response == null) {
            binding.leaderboardStatus.setText(R.string.leaderboard_unavailable)
            return
        }
        binding.leaderboardStatus.text = if (result.fromCache) {
            getString(
                R.string.leaderboard_cached,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.cachedAtMs ?: 0L)),
            )
        } else {
            getString(R.string.leaderboard_live, response.minimumDays, response.minimumMinutes)
        }
        val visibleEntries = (response.entries + response.currentUserWindow)
            .distinctBy { it.rank }
            .sortedBy { it.rank }
        if (visibleEntries.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                setText(R.string.leaderboard_empty)
                setTextColor(resources.getColor(R.color.headup_text_secondary, context.theme))
                setPadding(12, 20, 12, 20)
            }
            binding.leaderboardContainer.addView(empty)
            return
        }
        visibleEntries.forEach { entry ->
            val row = ItemCampusLeaderboardBinding.inflate(layoutInflater, binding.leaderboardContainer, false)
            row.rankText.text = entry.rank.toString()
            row.aliasText.text = if (entry.isCurrentUser) {
                getString(R.string.current_user_alias_format, entry.publicAlias)
            } else entry.publicAlias
            row.detailText.text = entry.participantCount?.let {
                getString(R.string.leaderboard_school_row_detail, it, entry.validDays, entry.validMinutes)
            } ?: getString(R.string.leaderboard_row_detail, entry.validDays, entry.validMinutes)
            row.scoreText.text = String.format(Locale.getDefault(), "%.1f", entry.postureScore)
            binding.leaderboardContainer.addView(row.root)
        }
    }

    private fun showProfileDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_education_profile, null)
        val alias = content.findViewById<EditText>(R.id.public_alias_input)
        val stage = content.findViewById<Spinner>(R.id.education_stage_spinner)
        val grade = content.findViewById<EditText>(R.id.grade_input)
        val schoolQuery = content.findViewById<EditText>(R.id.school_query_input)
        val schoolStatus = content.findViewById<TextView>(R.id.school_selection_status)
        val schoolResults = content.findViewById<RadioGroup>(R.id.school_results_group)
        val optIn = content.findViewById<MaterialSwitch>(R.id.leaderboard_opt_in_switch)
        val consent = content.findViewById<MaterialSwitch>(R.id.parent_consent_switch)
        setupSpinner(stage, resources.getStringArray(R.array.education_stages).toList())
        val existing = profile
        alias.setText(existing?.publicAlias ?: HeadUpAuthStore.userLabel(requireContext()).take(20))
        stage.setSelection(STAGES.indexOf(existing?.educationStage).takeIf { it >= 0 } ?: 0)
        grade.setText(existing?.gradeCode.orEmpty())
        optIn.isChecked = existing?.leaderboardOptIn == true
        consent.isChecked = existing?.parentConsentStatus == "GRANTED"
        schoolStatus.text = selectedSchool?.let { getString(R.string.selected_school_format, it.localizedName) }
            ?: getString(R.string.no_school_selected)
        content.findViewById<View>(R.id.school_search_button).setOnClickListener {
            val query = schoolQuery.text.toString().trim()
            if (query.length < 2) {
                schoolQuery.error = getString(R.string.school_query_too_short)
                return@setOnClickListener
            }
            schoolStatus.setText(R.string.school_searching)
            viewLifecycleOwner.lifecycleScope.launch {
                val schools = CampusChallengeRepository.searchSchools(
                    requireContext(),
                    STAGES[stage.selectedItemPosition],
                    query,
                )
                schoolResults.removeAllViews()
                if (schools.isEmpty()) schoolStatus.setText(R.string.school_no_results)
                schools.forEach { school ->
                    val option = RadioButton(requireContext()).apply {
                        text = getString(
                            R.string.school_result_format,
                            school.localizedName,
                            school.region,
                            school.officialSchoolCode,
                        )
                        tag = school
                        minHeight = resources.getDimensionPixelSize(R.dimen.school_result_min_height)
                    }
                    schoolResults.addView(option)
                }
                schoolResults.setOnCheckedChangeListener { group, checkedId ->
                    selectedSchool = group.findViewById<RadioButton>(checkedId)?.tag as? SchoolEntity
                    schoolStatus.text = selectedSchool?.let { getString(R.string.selected_school_format, it.localizedName) }
                }
            }
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.education_profile_title)
            .setView(content)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val publicAlias = alias.text.toString().trim()
                if (publicAlias.length !in 2..20) {
                    alias.error = getString(R.string.alias_validation)
                    return@setOnClickListener
                }
                if (optIn.isChecked && selectedSchool == null) {
                    schoolStatus.setText(R.string.school_required_for_ranking)
                    return@setOnClickListener
                }
                if (optIn.isChecked && !consent.isChecked) {
                    Toast.makeText(requireContext(), R.string.parent_consent_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val next = EducationProfileEntity(
                    userId = HeadUpAuthStore.currentUserId(requireContext()),
                    countryCode = "TW",
                    schoolId = selectedSchool?.schoolId,
                    gradeCode = grade.text.toString().trim().ifBlank { null },
                    educationStage = STAGES[stage.selectedItemPosition],
                    publicAlias = publicAlias,
                    leaderboardOptIn = optIn.isChecked,
                    parentConsentStatus = if (consent.isChecked) "GRANTED" else "PENDING",
                    updatedAtMs = System.currentTimeMillis(),
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = CampusChallengeRepository.saveEducationProfile(requireContext(), next)
                    profile = next.copy(isSynced = result.isSuccess && HeadUpAuthStore.isSignedIn(requireContext()))
                    renderProfile()
                    refreshLeaderboard()
                    Toast.makeText(
                        requireContext(),
                        if (result.isSuccess) R.string.education_profile_saved else R.string.education_profile_saved_offline,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private val STAGES = listOf("ELEMENTARY", "JUNIOR_HIGH", "SENIOR_HIGH", "UNIVERSITY")
    }
}
