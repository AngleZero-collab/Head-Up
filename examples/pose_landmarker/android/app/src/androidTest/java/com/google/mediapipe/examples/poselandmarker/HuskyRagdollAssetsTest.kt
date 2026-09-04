package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HuskyRagdollAssetsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val pets = listOf("playful_husky", "gentle_ragdoll").map(VirtualPetCatalog::byId)

    @Test
    fun fallbackImagesAreVisibleTransparentAndCircleSafe() {
        pets.forEach { pet ->
            listOf(pet.happyImageRes, pet.alertImageRes).forEach { resource ->
                val bitmap = BitmapFactory.decodeResource(context.resources, resource)
                try {
                    assertTrue(bitmap.hasAlpha())
                    assertEquals(1024, bitmap.width)
                    assertEquals(bitmap.width, bitmap.height)
                    var visible = 0
                    for (y in 0 until bitmap.height step 4) {
                        for (x in 0 until bitmap.width step 4) {
                            if (Color.alpha(bitmap.getPixel(x, y)) > 12) {
                                assertTrue("${pet.id} exceeds circular frame", Math.hypot(x - 512.0, y - 512.0) < 440)
                                visible++
                            }
                        }
                    }
                    assertTrue("${pet.id} is blank", visible > 6000)
                    assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun animationsDecodeSwitchAndRestartWithoutBlankDrawable() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        instrumentation.runOnMainSync {
            val view = VisionDragonVideoView(context)
            try {
                repeat(2) {
                    pets.flatMap { listOf(it.happyAnimationRes, it.alertAnimationRes, it.happyAnimationRes) }.forEach { resource ->
                        assertTrue(view.play(resource, loop = true))
                        val drawable = view.drawable as AnimatedImageDrawable
                        assertEquals(AnimatedImageDrawable.REPEAT_INFINITE, drawable.repeatCount)
                        drawable.start()
                        assertTrue(drawable.isRunning)
                        view.stopPlayback()
                        assertTrue(view.play(resource, loop = true))
                        assertTrue(view.drawable is AnimatedImageDrawable)
                    }
                }
            } finally {
                view.stopPlayback()
            }
        }
    }

    @Test
    fun petNamesAndDescriptionsSupportEnglishAndTraditionalChinese() {
        val locales = listOf(
            Locale.ENGLISH to listOf("Playful Husky", "Gentle Ragdoll"),
            Locale.TRADITIONAL_CHINESE to listOf("活力哈士奇", "柔柔布偶貓"),
        )
        locales.forEach { (locale, names) ->
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
            pets.forEachIndexed { index, pet ->
                assertEquals(names[index], localized.getString(pet.nameRes))
                assertTrue(localized.getString(pet.traitRes).isNotBlank())
            }
        }
    }

    @Test
    fun selectionPersistsWithInteractionsAndEquipmentWithoutTouchingUserProgress() {
        // Isolate the repository cache to a disposable preferences file, never the user's profile.
        val field = HeadUpRepository::class.java.getDeclaredField("sharedPrefs").apply { isAccessible = true }
        val previousPrefs = field.get(HeadUpRepository)
        val prefs = context.getSharedPreferences("husky_ragdoll_instrumentation", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        field.set(HeadUpRepository, prefs)
        try {
            HeadUpRepository.currentState(context)
            prefs.edit().putInt("dragon_energy", 60).putInt("dragon_level", 8).putInt("dragon_bond", 110)
                .putInt("coins", 1000).putStringSet("owned_items", setOf("moon_cape", "forest_background"))
                .putStringSet("equipped_items", setOf("moon_cape", "forest_background")).commit()
            pets.forEach { pet ->
                val before = HeadUpRepository.currentState(context)
                assertTrue(HeadUpRepository.selectPet(context, pet.id))
                val selected = HeadUpRepository.currentState(context)
                assertEquals(pet.id, selected.selectedPet.id)
                assertEquals(before.dragonLevel, selected.dragonLevel)
                assertEquals(before.dragonEnergy, selected.dragonEnergy)
                assertEquals(before.dragonBond, selected.dragonBond)
                assertEquals(before.equippedShopItems, selected.equippedShopItems)
                val fed = HeadUpRepository.interactWithPet(context, PetInteraction.FEED)
                assertTrue(fed.dragonEnergy > selected.dragonEnergy)
                val played = HeadUpRepository.interactWithPet(context, PetInteraction.PLAY)
                assertTrue(played.dragonBond > fed.dragonBond)
                val rested = HeadUpRepository.interactWithPet(context, PetInteraction.REST)
                assertTrue(rested.dragonEnergy >= played.dragonEnergy)
                assertEquals(pet.id, HeadUpRepository.currentState(context).selectedPet.id)
                assertNotEquals(pet.happyImageRes, pet.alertImageRes)
            }
        } finally {
            prefs.edit().clear().commit()
            field.set(HeadUpRepository, previousPrefs)
            HeadUpRepository.currentState(context)
        }
    }
}
