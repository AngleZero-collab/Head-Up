package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CozyBunnyTest {
    @Test
    fun bunnyIsSelectableAndHasDedicatedVisuals() {
        val bunny = VirtualPetCatalog.byId("cozy_bunny")

        assertEquals(1, VirtualPetCatalog.all.count { it.id == "cozy_bunny" })
        assertEquals("cozy_bunny", bunny.id)
        assertEquals(R.string.pet_cozy_bunny, bunny.nameRes)
        assertEquals(R.string.pet_trait_cozy_bunny, bunny.traitRes)
        assertEquals(R.drawable.virtual_pet_bunny_happy, bunny.happyImageRes)
        assertEquals(R.drawable.virtual_pet_bunny_alert, bunny.alertImageRes)
        assertEquals(R.raw.pet_bunny_happy, bunny.happyAnimationRes)
        assertEquals(R.raw.pet_bunny_alert, bunny.alertAnimationRes)
        assertNotEquals(bunny.happyAnimationRes, bunny.alertAnimationRes)
    }

    @Test
    fun selectingBunnyPreservesProgressAndUnifiedAlertState() {
        val original = HeadUpUiState(
            dragonLevel = 12,
            dragonEnergy = 60,
            dragonBond = 150,
            coins = 80,
            isPostureAlertActive = true,
            equippedShopItems = setOf("moon_cape", "forest_background"),
        )
        val selected = original.copy(selectedPetId = "cozy_bunny")

        assertEquals("cozy_bunny", selected.selectedPet.id)
        assertEquals(original.dragonLevel, selected.dragonLevel)
        assertEquals(original.dragonEnergy, selected.dragonEnergy)
        assertEquals(original.dragonBond, selected.dragonBond)
        assertEquals(original.coins, selected.coins)
        assertEquals(original.equippedShopItems, selected.equippedShopItems)
        assertEquals(PostureZone.DANGER, selected.feedbackZone)
    }
}
