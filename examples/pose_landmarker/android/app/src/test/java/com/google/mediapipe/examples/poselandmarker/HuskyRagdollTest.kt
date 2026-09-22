package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuskyRagdollTest {
    private val newPets = listOf("playful_husky", "gentle_ragdoll")

    @Test
    fun newPetsHaveIndependentHappyAndAlertAssets() {
        val pets = newPets.map(VirtualPetCatalog::byId)
        assertEquals(newPets, pets.map { it.id })
        val visuals = pets.flatMap { listOf(it.happyImageRes, it.alertImageRes, it.happyAnimationRes, it.alertAnimationRes) }
        assertEquals(8, visuals.toSet().size)
        assertTrue(visuals.all { it != 0 })
        pets.forEach { pet ->
            assertEquals(1, VirtualPetCatalog.all.count { it.id == pet.id })
            assertNotEquals(pet.nameRes, pet.traitRes)
        }
    }

    @Test
    fun originalPetOrderAndDefaultRemainStable() {
        assertEquals(
            listOf("little_blue", "ember_red", "mint_leaf", "violet_star", "sunny_gold", "uplift_fox", "focus_lizard", "cozy_bunny"),
            VirtualPetCatalog.all.take(8).map { it.id },
        )
        assertEquals("little_blue", VirtualPetCatalog.DEFAULT_PET_ID)
    }

    @Test
    fun newPetsPreserveSharedProgressEquipmentAndAlertState() {
        val state = HeadUpUiState(dragonLevel = 12, dragonEnergy = 66, dragonBond = 155, coins = 90,
            isPostureAlertActive = true, equippedShopItems = setOf("focus_goggles", "forest_background"))
        newPets.forEach { id ->
            val next = state.copy(selectedPetId = id)
            assertEquals(id, next.selectedPet.id)
            assertEquals(state, next.copy(selectedPetId = state.selectedPetId))
            assertEquals(PostureZone.DANGER, next.feedbackZone)
            assertEquals(PostureZone.SAFE, next.copy(isPostureAlertActive = false).feedbackZone)
        }
    }
}
