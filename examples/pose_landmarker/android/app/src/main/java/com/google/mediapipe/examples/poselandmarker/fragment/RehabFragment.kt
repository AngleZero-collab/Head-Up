package com.google.mediapipe.examples.poselandmarker.fragment

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.HeadUpRepository
import com.google.mediapipe.examples.poselandmarker.HeadUpUiState
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentRehabBinding

/** Session-only reading UI: it never writes exercise, health, or reward records. */
class RehabFragment : Fragment() {
    private var binding: FragmentRehabBinding? = null
    private var category = 0
    private var courseIndex = -1
    private var step = 0
    private var completed = false
    private var guardianMetric: TextView? = null
    private var reminderDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val view = FragmentRehabBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            category = it.getInt("category").coerceIn(0, 2)
            courseIndex = it.getInt("course", -1).takeIf { n -> n in RehabCourses.all.indices } ?: -1
            step = it.getInt("step").coerceIn(0, 2)
            completed = it.getBoolean("completed")
        }
        binding!!.rehabBack.setOnClickListener { back() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        render()
        HeadUpRepository.observeState().observe(viewLifecycleOwner) { updateGuardianMetric(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("category", category)
        outState.putInt("course", courseIndex)
        outState.putInt("step", step)
        outState.putBoolean("completed", completed)
        super.onSaveInstanceState(outState)
    }

    private fun back() {
        if (courseIndex >= 0) {
            courseIndex = -1
            completed = false
            render()
        } else findNavController().navigateUp()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)

    private fun label(parent: LinearLayout, text: CharSequence, size: Float = 16f, heading: Boolean = false): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = size
            setTextColor(color(if (heading) R.color.headup_text_primary else R.color.headup_text_secondary))
            setLineSpacing(dp(3).toFloat(), 1f)
            if (heading) {
                setTypeface(typeface, Typeface.BOLD)
                ViewCompat.setAccessibilityHeading(this, true)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            parent.addView(this)
        }
    }

    private fun label(parent: LinearLayout, id: Int, size: Float = 16f, heading: Boolean = false) =
        label(parent, getString(id), size, heading)

    private fun button(parent: LinearLayout, title: Int, action: () -> Unit): MaterialButton =
        MaterialButton(requireContext()).apply {
            setText(title)
            isAllCaps = false
            minHeight = dp(48)
            setTextColor(color(R.color.headup_bg_top))
            backgroundTintList = android.content.res.ColorStateList.valueOf(color(R.color.headup_primary))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            setOnClickListener { action() }
            parent.addView(this)
        }

    private fun card(parent: LinearLayout): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(16))
        setBackgroundResource(R.drawable.bg_headup_card_strong)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        parent.addView(this)
    }

    private fun illustration(parent: LinearLayout) {
        parent.addView(ImageView(requireContext()).apply {
            setImageResource(when (category) {
                0 -> R.drawable.ic_rehab_shoulder
                1 -> R.drawable.ic_rehab_eye
                else -> R.drawable.ic_rehab_cdr
            })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(80)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        })
    }

    private fun source(parent: LinearLayout) {
        val title = when (category) {
            0 -> R.string.rehab_source_neck
            1 -> R.string.rehab_source_eye
            else -> R.string.rehab_source_cdr
        }
        label(parent, title, 12f)
        val urls = when (category) {
            0 -> listOf("https://www.nhs.uk/live-well/exercise/sitting-exercises/",
                "https://www.fhft.nhs.uk/patients-and-visitors/patient-information-library/head-neck-and-shoulder-stretches")
            1 -> listOf("https://aao.org/eye-health/tips-prevention/computer-usage")
            else -> listOf("https://wexnermedical.osu.edu/neurological-institute/clinical-centers/ohio-state-spine-care/spine-rehabilitation-guidelines")
        }
        urls.forEachIndexed { index, url ->
            button(parent, R.string.rehab_sources) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try { startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
                    // The source remains readable even when the device has no browser.
                    label(parent, url, 12f)
                }
            }.apply { if (urls.size > 1) text = getString(R.string.rehab_sources) + " ${index + 1}" }
        }
    }

    private fun render() {
        val ui = binding ?: return
        val parent = ui.rehabContent
        guardianMetric = null
        parent.removeAllViews()
        label(parent, R.string.rehab_title, 28f, true)
        label(parent, R.string.rehab_preview, 12f)
        if (courseIndex < 0) renderList(parent) else renderCourse(parent)
        ui.rehabScroll.scrollTo(0, 0)
    }

    private fun renderList(parent: LinearLayout) {
        label(parent, R.string.rehab_intro)
        // Vertical choices stay readable at large font sizes and narrow screen widths.
        listOf(R.string.guardian_spine, R.string.guardian_vision, R.string.rehab_cdr).forEachIndexed { i, title ->
            button(parent, title) { category = i; render() }.apply {
                isSelected = category == i
                strokeWidth = if (isSelected) dp(2) else 0
                strokeColor = android.content.res.ColorStateList.valueOf(color(R.color.headup_text_primary))
                alpha = if (isSelected) 1f else .75f
            }
        }
        label(parent, when (category) {
            0 -> R.string.rehab_neck_caution
            1 -> R.string.rehab_eye_caution
            else -> R.string.rehab_cdr_intro
        })
        if (category == 2) {
            label(parent, R.string.rehab_cdr_caution)
            label(parent, R.string.rehab_cdr_review, 13f)
        } else {
            renderGuardian(parent)
        }
        RehabCourses.all.forEachIndexed { index, course ->
            if (course.category == category) {
                val item = card(parent)
                illustration(item)
                label(item, course.title, 21f, true)
                label(item, course.summary)
                button(item, R.string.rehab_read) {
                    courseIndex = index; step = 0; completed = false; render()
                }
            }
        }
        if (category == 2) {
            val pending = card(parent)
            label(pending, R.string.rehab_pending_title, 21f, true)
            label(pending, R.string.rehab_pending_note)
            listOf(R.string.rehab_rom, R.string.rehab_scapula, R.string.rehab_function).forEach {
                label(pending, it, 18f, true)
                label(pending, R.string.rehab_pending, 14f)
            }
        }
        source(parent)
    }

    private fun renderCourse(parent: LinearLayout) {
        val course = RehabCourses.all[courseIndex]
        val item = card(parent)
        label(item, course.title, 24f, true)
        illustration(item)
        if (completed) {
            label(item, R.string.rehab_done, 22f, true)
            label(item, R.string.rehab_done_note)
            button(item, R.string.rehab_list) { back() }
            return
        }
        label(item, if (category == 2) R.string.rehab_cdr_review else if (category == 0)
            R.string.rehab_audience else R.string.rehab_eye_caution, 14f)
        label(item, getString(R.string.rehab_step, step + 1, course.steps.size), 14f)
        label(item, course.steps[step], 19f)
        if (category != 1) label(item, if (category == 0) R.string.rehab_neck_caution else R.string.rehab_cdr_caution, 14f)
        button(item, R.string.rehab_previous) { if (step > 0) { step--; render() } }.isEnabled = step > 0
        button(item, if (step == course.steps.lastIndex) R.string.rehab_finish else R.string.rehab_next) {
            if (step == course.steps.lastIndex) completed = true else step++
            render()
        }
        source(parent)
    }

    private fun renderGuardian(parent: LinearLayout) {
        val panel = card(parent)
        illustration(panel)
        label(panel, if (category == 1) R.string.guardian_vision else R.string.guardian_spine, 23f, true)
        label(panel, R.string.guardian_estimate_note, 13f)
        guardianMetric = label(panel, R.string.guardian_no_reading, 20f, true)
        updateGuardianMetric(HeadUpRepository.currentState(requireContext()))
        label(panel, if (category == 1) R.string.guardian_vision_pending else R.string.guardian_spine_pending, 14f)
        label(panel, R.string.guardian_intervention, 18f, true)
        label(panel, if (category == 1) R.string.guardian_vision_reminder else R.string.guardian_spine_reminder)
        label(panel, R.string.guardian_preview_note, 13f)
        button(panel, R.string.guardian_preview) {
            reminderDialog?.dismiss()
            reminderDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(if (category == 1) R.string.guardian_vision else R.string.guardian_spine)
                .setMessage(if (category == 1) R.string.guardian_vision_reminder else R.string.guardian_spine_reminder)
                .setPositiveButton(android.R.string.ok, null).show()
        }
        label(panel, R.string.guardian_routine_note, 14f)
        button(panel, R.string.guardian_start) {
            courseIndex = RehabCourses.all.indexOfFirst {
                it.title == if (category == 1) R.string.guardian_vision_routine else R.string.guardian_spine_routine
            }
            step = 0; completed = false; render()
        }
    }

    private fun updateGuardianMetric(state: HeadUpUiState) {
        val target = guardianMetric ?: return
        val value = if (category == 1) state.metrics.screenDistanceCm else state.metrics.headPitchDegrees
        target.text = if (state.lastUpdatedMs <= 0 || value == null) getString(R.string.guardian_no_reading)
        else getString(if (category == 1) R.string.guardian_distance else R.string.guardian_pitch, value)
    }

    override fun onDestroyView() {
        reminderDialog?.dismiss()
        reminderDialog = null
        guardianMetric = null
        binding = null
        super.onDestroyView()
    }
}
