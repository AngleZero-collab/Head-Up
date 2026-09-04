package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.view.View
import androidx.navigation.findNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampusInsightsSmokeTest {
    @Test
    fun campusChallengeAndGuardInsightsInflateOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            // MainActivity completes its splash/auth routing asynchronously.
            Thread.sleep(1_800)
            instrumentation.runOnMainSync {
                activity.findNavController(R.id.fragment_container).navigate(R.id.guard_effectiveness_fragment)
            }
            assertDisplayedEventually { activity.findViewById(R.id.effectiveness_chart) }
            assertDisplayed(activity.findViewById(R.id.range_toggle))
            assertDisplayed(activity.findViewById(R.id.unit_toggle))

            instrumentation.runOnMainSync {
                activity.findNavController(R.id.fragment_container).navigate(R.id.campus_challenge_fragment)
            }
            assertDisplayedEventually { activity.findViewById(R.id.challenge_points_value) }
            assertDisplayed(activity.findViewById(R.id.entity_spinner))
            assertDisplayed(activity.findViewById(R.id.leaderboard_container))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun assertDisplayed(view: View?) {
        assertNotNull(view)
        assertEquals(View.VISIBLE, view!!.visibility)
    }

    private fun assertDisplayedEventually(lookup: () -> View?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(30) {
            instrumentation.waitForIdleSync()
            val view = lookup()
            if (view != null && view.visibility == View.VISIBLE) return
            Thread.sleep(100)
        }
        assertDisplayed(lookup())
    }
}
