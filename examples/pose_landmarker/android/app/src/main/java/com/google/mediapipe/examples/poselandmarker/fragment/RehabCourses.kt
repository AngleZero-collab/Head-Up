package com.google.mediapipe.examples.poselandmarker.fragment

import com.google.mediapipe.examples.poselandmarker.R

/** Read-only education content. No patient state or exercise completion data. */
internal object RehabCourses {
    data class Course(val category: Int, val title: Int, val summary: Int, val steps: IntArray)
    val all = listOf(
        Course(0, R.string.rehab_posture, R.string.rehab_posture_summary, intArrayOf(R.string.rehab_posture_step_0, R.string.rehab_posture_step_1, R.string.rehab_posture_step_2)),
        Course(0, R.string.rehab_shoulders, R.string.rehab_shoulders_summary, intArrayOf(R.string.rehab_shoulders_step_0, R.string.rehab_shoulders_step_1, R.string.rehab_shoulders_step_2)),
        Course(0, R.string.rehab_turn, R.string.rehab_turn_summary, intArrayOf(R.string.rehab_turn_step_0, R.string.rehab_turn_step_1, R.string.rehab_turn_step_2)),
        Course(1, R.string.rehab_distance, R.string.rehab_distance_summary, intArrayOf(R.string.rehab_distance_step_0, R.string.rehab_distance_step_1, R.string.rehab_distance_step_2)),
        Course(1, R.string.rehab_blink, R.string.rehab_blink_summary, intArrayOf(R.string.rehab_blink_step_0, R.string.rehab_blink_step_1, R.string.rehab_blink_step_2)),
        Course(1, R.string.rehab_screen, R.string.rehab_screen_summary, intArrayOf(R.string.rehab_screen_step_0, R.string.rehab_screen_step_1, R.string.rehab_screen_step_2)),
        Course(2, R.string.rehab_protection, R.string.rehab_protection_summary, intArrayOf(R.string.rehab_protection_step_0, R.string.rehab_protection_step_1, R.string.rehab_protection_step_2)),
        Course(2, R.string.rehab_daily, R.string.rehab_daily_summary, intArrayOf(R.string.rehab_daily_step_0, R.string.rehab_daily_step_1, R.string.rehab_daily_step_2)),
        Course(1, R.string.guardian_vision_routine, R.string.guardian_routine_note, intArrayOf(R.string.guardian_eye_step_0, R.string.guardian_eye_step_1, R.string.guardian_eye_step_2)),
        Course(0, R.string.guardian_spine_routine, R.string.guardian_routine_note, intArrayOf(R.string.guardian_neck_step_0, R.string.guardian_neck_step_1, R.string.guardian_neck_step_2)),
    )
}
