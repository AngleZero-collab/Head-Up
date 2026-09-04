package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualPetCatalogTest {
    @Test
    fun catalogIncludesFoxAndLizardWithUniqueIds() {
        val ids = VirtualPetCatalog.all.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue("uplift_fox" in ids)
        assertTrue("focus_lizard" in ids)
    }

    @Test
    fun everyPetHasHappyAndAlertVisuals() {
        VirtualPetCatalog.all.forEach { pet ->
            assertTrue("${pet.id} is missing a happy image", pet.happyImageRes != 0)
            assertTrue("${pet.id} is missing a happy animation", pet.happyAnimationRes != 0)
            assertTrue("${pet.id} is missing an alert image", pet.alertImageRes != 0)
            assertTrue("${pet.id} is missing an alert animation", pet.alertAnimationRes != 0)
        }
    }

    @Test
    fun unknownPetFallsBackToDefault() {
        assertEquals(
            VirtualPetCatalog.DEFAULT_PET_ID,
            VirtualPetCatalog.byId("not-a-real-pet").id,
        )
    }
}
