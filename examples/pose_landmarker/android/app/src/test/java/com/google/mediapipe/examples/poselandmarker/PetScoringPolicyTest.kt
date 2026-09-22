package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.assertEquals
import org.junit.Test

class PetScoringPolicyTest {
    @Test
    fun dangerDeductsTwoEnergyPointsPerSecond() {
        assertEquals(-6, PetScoringPolicy.energyDelta(PostureZone.DANGER, 3))
    }

    @Test
    fun safeRecoversOnePointAndWarningDoesNotChangeEnergy() {
        assertEquals(3, PetScoringPolicy.energyDelta(PostureZone.SAFE, 3))
        assertEquals(0, PetScoringPolicy.energyDelta(PostureZone.WARNING, 3))
    }
}
