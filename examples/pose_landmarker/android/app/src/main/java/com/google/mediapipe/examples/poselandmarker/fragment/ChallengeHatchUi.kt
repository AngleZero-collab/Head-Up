package com.google.mediapipe.examples.poselandmarker.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.DialogDragonStoryBinding
import com.google.mediapipe.examples.poselandmarker.databinding.LayoutChallengeHatchBinding
import kotlin.math.sin

/** Presentation-only preview. Never reads or writes rewards, posture data or pet inventory. */
internal class ChallengeHatchUi(private val binding: LayoutChallengeHatchBinding) {
    private data class Egg(
        val name: Int, val requirement: Int, val progressLabel: Int,
        val progress: Int, val color: Int, val rates: List<Int>?,
    )

    private val context = binding.root.context
    private val eggs = listOf(
        Egg(R.string.hatch_white, R.string.hatch_white_requirement, R.string.hatch_white_progress,
            50, Color.rgb(235, 242, 249), listOf(90, 9, 1)),
        Egg(R.string.hatch_silver, R.string.hatch_silver_requirement, R.string.hatch_silver_progress,
            40, Color.rgb(170, 194, 217), listOf(60, 35, 5)),
        Egg(R.string.hatch_gold, R.string.hatch_gold_requirement, R.string.hatch_gold_progress,
            50, Color.rgb(255, 209, 102), listOf(20, 65, 15)),
        Egg(R.string.hatch_platinum, R.string.hatch_platinum_requirement, R.string.hatch_platinum_progress,
            57, Color.rgb(197, 175, 255), null),
        Egg(R.string.hatch_diamond, R.string.hatch_diamond_requirement, R.string.hatch_diamond_progress,
            68, Color.rgb(103, 224, 255), listOf(0, 40, 60)),
    )
    private val chapters = listOf(
        Triple(R.string.hatch_chapter_0_title, R.string.hatch_chapter_0_body, R.string.hatch_chapter_0_action),
        Triple(R.string.hatch_chapter_1_title, R.string.hatch_chapter_1_body, R.string.hatch_chapter_1_action),
        Triple(R.string.hatch_chapter_2_title, R.string.hatch_chapter_2_body, R.string.hatch_chapter_2_action),
        Triple(R.string.hatch_chapter_3_title, R.string.hatch_chapter_3_body, R.string.hatch_chapter_3_action),
        Triple(R.string.hatch_chapter_4_title, R.string.hatch_chapter_4_body, R.string.hatch_chapter_4_action),
    )
    private val choices = mutableListOf<MaterialButton>()
    private var selected = 4
    private var animation: ValueAnimator? = null
    private var storyDialog: AlertDialog? = null

    init {
        eggs.forEachIndexed { index, egg ->
            val choice = MaterialButton(context).apply {
                setText(egg.name)
                isAllCaps = false
                minHeight = dp(56)
                setTextColor(egg.color)
                cornerRadius = dp(16)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(8) }
                setOnClickListener { selectEgg(index) }
            }
            choices += choice
            binding.eggChoices.addView(choice)
        }
        binding.hatchButton.setOnClickListener { hatch() }
        binding.replayButton.setOnClickListener { hatch() }
        binding.storyButton.setOnClickListener { showStory() }
        binding.resultStoryButton.setOnClickListener { showStory() }
        selectEgg(selected)
        // The initial diamond selection should be visible in the horizontal list.
        binding.eggChoices.post {
            (binding.eggChoices.parent as HorizontalScrollView).scrollTo(choices[selected].left, 0)
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun selectEgg(index: Int) {
        if (animation != null) return
        selected = index
        val egg = eggs[index]
        resetStage()
        binding.eggName.setText(egg.name)
        binding.eggRequirement.setText(egg.requirement)
        binding.eggProgress.progress = egg.progress
        binding.eggProgressLabel.setText(egg.progressLabel)
        binding.eggProgress.contentDescription = context.getString(egg.progressLabel)
        binding.eggImage.imageTintList = ColorStateList.valueOf(egg.color)
        binding.eggImage.imageTintMode = PorterDuff.Mode.MULTIPLY
        binding.eggRates.text = egg.rates?.let {
            context.getString(R.string.hatch_rates, it[0], it[1], it[2])
        } ?: context.getString(R.string.hatch_rates_pending)
        choices.forEachIndexed { i, button ->
            button.isSelected = i == index
            button.strokeWidth = dp(if (i == index) 2 else 1)
            button.strokeColor = ColorStateList.valueOf(if (i == index) egg.color else Color.rgb(62, 78, 106))
            button.backgroundTintList = ColorStateList.valueOf(
                if (i == index) Color.rgb(43, 64, 98) else Color.rgb(19, 31, 54),
            )
        }
    }

    private fun resetStage() {
        binding.resultDragonAnimation.stopPlayback()
        binding.hatchResultPanel.visibility = View.GONE
        binding.eggStage.visibility = View.VISIBLE
        binding.eggImage.rotation = 0f
        binding.eggImage.alpha = 1f
        binding.eggCrack.rotation = 0f
        binding.eggCrack.visibility = View.INVISIBLE
        binding.eggGlow.alpha = 1f
        binding.eggGlow.scaleX = 1f
        binding.eggGlow.scaleY = 1f
        binding.hatchButton.visibility = View.VISIBLE
        binding.hatchButton.isEnabled = true
        binding.hatchButton.setText(R.string.hatch_action)
        choices.forEach { it.isEnabled = true }
    }

    private fun hatch() {
        if (animation != null) return
        resetStage()
        binding.hatchButton.isEnabled = false
        binding.hatchButton.setText(R.string.hatch_running)
        choices.forEach { it.isEnabled = false }
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            addUpdateListener {
                val fraction = it.animatedValue as Float
                val rotation = sin(fraction * Math.PI * 12).toFloat() * 7f
                binding.eggImage.rotation = rotation
                binding.eggCrack.rotation = rotation
                binding.eggCrack.visibility = if (fraction > .45f) View.VISIBLE else View.INVISIBLE
                binding.eggGlow.scaleX = 1f + fraction * .2f
                binding.eggGlow.scaleY = binding.eggGlow.scaleX
                binding.eggImage.alpha = if (fraction > .8f) (1f - fraction) * 5f else 1f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    animation = null
                    resetStage()
                    binding.eggStage.visibility = View.GONE
                    binding.hatchButton.visibility = View.GONE
                    binding.hatchResultPanel.visibility = View.VISIBLE
                    this@ChallengeHatchUi.resume()
                    binding.hatchResultPanel.announceForAccessibility(
                        context.getString(R.string.hatch_result) + " " + context.getString(R.string.hatch_dragon),
                    )
                    binding.hatchResultPanel.post {
                        if (binding.root.isAttachedToWindow) binding.hatchResultPanel.requestRectangleOnScreen(
                            android.graphics.Rect(0, 0, binding.hatchResultPanel.width, binding.hatchResultPanel.height), false,
                        )
                    }
                }
            })
        }
        animation?.start()
    }

    private fun showStory() {
        if (storyDialog != null) return
        val story = DialogDragonStoryBinding.inflate(LayoutInflater.from(context))
        ViewCompat.setAccessibilityHeading(story.chapterTitle, true)
        var chapter = 0
        fun renderChapter() {
            val page = chapters[chapter]
            story.chapterCounter.text = context.getString(R.string.hatch_chapter_count, chapter + 1, chapters.size)
            story.chapterTitle.setText(page.first)
            story.chapterBody.setText(page.second)
            story.chapterAction.setText(page.third)
            story.previousButton.isEnabled = chapter > 0
            story.nextButton.setText(if (chapter == chapters.lastIndex) R.string.hatch_finish else R.string.hatch_next)
            story.storyScroll.scrollTo(0, 0)
            story.chapterTitle.announceForAccessibility(context.getString(page.first))
        }
        val dialog = AlertDialog.Builder(context).setView(story.root).create()
        storyDialog = dialog
        dialog.setOnDismissListener {
            story.storyDragonAnimation.stopPlayback()
            storyDialog = null
        }
        story.previousButton.setOnClickListener { if (chapter > 0) { chapter--; renderChapter() } }
        story.nextButton.setOnClickListener {
            if (chapter == chapters.lastIndex) dialog.dismiss() else { chapter++; renderChapter() }
        }
        story.closeButton.setOnClickListener { dialog.dismiss() }
        renderChapter()
        dialog.show()
        if (!story.storyDragonAnimation.play(R.raw.dragon_happy_gold, loop = true)) {
            story.storyDragonAnimation.setImageResource(R.drawable.vision_dragon_sunny_gold_cutout)
        }
        dialog.window?.apply {
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            setLayout(
                (context.resources.displayMetrics.widthPixels * .94f).toInt(),
                (context.resources.displayMetrics.heightPixels * .82f).toInt(),
            )
        }
    }

    fun resume() {
        if (binding.hatchResultPanel.visibility != View.VISIBLE) return
        binding.resultDragonAnimation.visibility = View.VISIBLE
        val playing = binding.resultDragonAnimation.play(R.raw.dragon_happy_gold, loop = true)
        binding.resultDragonAnimation.visibility = if (playing) View.VISIBLE else View.GONE
        binding.resultDragonFallback.visibility = if (playing) View.GONE else View.VISIBLE
    }

    fun pause() {
        binding.resultDragonAnimation.stopPlayback()
        animation?.let {
            it.removeAllListeners()
            it.removeAllUpdateListeners()
            it.cancel()
            animation = null
            resetStage()
        }
        storyDialog?.dismiss()
        storyDialog = null
    }

    fun dispose() = pause()
}
