package com.google.mediapipe.examples.poselandmarker

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CozyBunnyAssetsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fallbackImagesHaveTransparentMarginsAndVisibleContent() {
        val bunny = VirtualPetCatalog.byId("cozy_bunny")
        listOf(bunny.happyImageRes, bunny.alertImageRes).forEach { resource ->
            val bitmap = BitmapFactory.decodeResource(context.resources, resource)
            try {
                assertTrue(bitmap.hasAlpha())
                assertEquals(bitmap.width, bitmap.height)
                assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)))
                assertTrue(Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 200)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun happyAndAlertAssetsDecodeAsLoopingAnimations() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val bunny = VirtualPetCatalog.byId("cozy_bunny")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = VisionDragonVideoView(context)
            try {
                listOf(bunny.happyAnimationRes, bunny.alertAnimationRes).forEach { resource ->
                    assertTrue(view.play(resource, loop = true))
                    val animation = view.drawable as AnimatedImageDrawable
                    assertEquals(AnimatedImageDrawable.REPEAT_INFINITE, animation.repeatCount)
                    assertTrue(view.play(resource, loop = true))
                    view.stopPlayback()
                    assertTrue(view.play(resource, loop = true))
                }
            } finally {
                view.stopPlayback()
            }
        }
    }

    @Test
    fun bunnyNamesAreLocalized() {
        listOf(Locale.ENGLISH to "Cozy Bunny", Locale.TRADITIONAL_CHINESE to "暖心兔").forEach { (locale, name) ->
            val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
            val localizedContext = context.createConfigurationContext(configuration)
            assertEquals(name, localizedContext.getString(R.string.pet_cozy_bunny))
        }
    }
}
