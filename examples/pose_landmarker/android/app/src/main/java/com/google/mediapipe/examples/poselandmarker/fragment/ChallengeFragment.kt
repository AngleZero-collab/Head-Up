package com.google.mediapipe.examples.poselandmarker.fragment

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.appcompat.app.AlertDialog
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.poselandmarker.HeadUpRepository
import com.google.mediapipe.examples.poselandmarker.HeadUpUiState
import com.google.mediapipe.examples.poselandmarker.PetInteraction
import com.google.mediapipe.examples.poselandmarker.PostureZone
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.ShopItem
import com.google.mediapipe.examples.poselandmarker.VirtualPetCatalog
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentChallengeBinding

class ChallengeFragment : Fragment() {
    private var _binding: FragmentChallengeBinding? = null
    private val binding get() = _binding!!
    private var latestState = HeadUpUiState()
    private var currentDragonImageResId = 0
    private var currentDragonVideoResId = 0
    private var currentDragonOrbBackgroundResId = 0
    private var renderedSelectorDragonId: String? = null
    private var dragonBreathingAnimator: ValueAnimator? = null
    private var dragonBreathingTarget: View? = null
    private var isDragonInDangerPose = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentChallengeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDragonOrb()
        binding.claimMaintainButton.setOnClickListener { claimGoodPostureReward() }
        binding.recordEyeRestButton.setOnClickListener { showEyeRestDialog() }
        binding.feedDragonButton.setOnClickListener { handlePetInteraction(PetInteraction.FEED) }
        binding.playDragonButton.setOnClickListener { handlePetInteraction(PetInteraction.PLAY) }
        binding.restDragonButton.setOnClickListener { handlePetInteraction(PetInteraction.REST) }
        binding.equipDragonButton.setOnClickListener { showEquipmentDialog() }

        render(HeadUpRepository.currentState(requireContext()))
        HeadUpRepository.observeState().observe(viewLifecycleOwner) { render(it) }
    }

    override fun onResume() {
        super.onResume()
        renderDragonVisuals(latestState)
    }

    override fun onPause() {
        stopDragonAnimation()
        binding.dragonVideoView.stopPlayback()
        super.onPause()
    }

    private fun setupDragonOrb() {
        binding.dragonOrb.isClickable = true
        binding.dragonOrb.isFocusable = true
        binding.dragonOrb.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().cancel()
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90L).start()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140L).setInterpolator(OvershootInterpolator(1.6f)).start()
                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                    true
                }

                else -> false
            }
        }
        binding.dragonOrb.setOnClickListener { handlePetInteraction(PetInteraction.PLAY) }
    }

    private fun render(state: HeadUpUiState) {
        if (_binding == null) return
        latestState = state
        val feedbackZone = state.feedbackZone
        val zoneColor = ContextCompat.getColor(requireContext(), feedbackZone.colorRes())
        val selectedPet = state.selectedPet
        binding.dragonLevelBadge.text = "Lv.${state.dragonLevel}"
        binding.dragonNameText.text = getString(selectedPet.nameRes)
        binding.dragonTraitText.text = getString(selectedPet.traitRes)
        binding.dragonEnergyProgress.progress = state.dragonEnergy
        binding.dragonEnergyText.text = getString(
            if (state.metrics.isGoodPosture) R.string.dragon_energy_good else R.string.dragon_energy_rest,
            state.dragonEnergy,
        )
        binding.dragonBondText.text = getString(R.string.dragon_bond_format, state.dragonBond)
        binding.dragonMoodText.text = getString(
            when (feedbackZone) {
                PostureZone.SAFE -> R.string.dragon_mood_safe_format
                PostureZone.WARNING -> R.string.dragon_mood_warning_format
                PostureZone.DANGER -> R.string.dragon_mood_danger_format
            },
            getString(selectedPet.nameRes),
        )
        binding.dragonMoodText.setTextColor(zoneColor)

        val orbBackground = dragonOrbBackground(state)
        if (currentDragonOrbBackgroundResId != orbBackground) {
            currentDragonOrbBackgroundResId = orbBackground
            binding.dragonOrb.setBackgroundResource(orbBackground)
        }
        renderDragonVisuals(state)
        if (renderedSelectorDragonId != state.selectedPetId) {
            renderedSelectorDragonId = state.selectedPetId
            renderDragonSelector(state)
        }

        binding.maintainTaskDetail.text = getString(
            R.string.minutes_progress_format,
            state.goodPostureMinutesToday.coerceAtMost(30),
            30,
        )
        binding.protectTaskProgress.progress = state.eyeRestCountToday.coerceAtMost(3)
        binding.protectTaskDetail.text = getString(R.string.times_format, state.eyeRestCountToday.coerceAtMost(3), 3)

        val goodTask = state.tasks.first { it.id == "good_posture" }
        binding.claimMaintainButton.isEnabled = goodTask.isComplete && !goodTask.claimed
        binding.claimMaintainButton.setText(if (goodTask.claimed) R.string.claimed else R.string.claim_reward)
    }

    private fun renderDragonSelector(state: HeadUpUiState) {
        val container = binding.dragonSelectorContainer
        container.removeAllViews()
        var selectedChip: View? = null
        VirtualPetCatalog.all.forEach { pet ->
            val chip = TextView(requireContext()).apply {
                text = getString(R.string.dragon_selector_format, pet.icon, getString(pet.nameRes))
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (pet.id == state.selectedPetId) R.color.headup_text_primary else R.color.headup_text_secondary,
                    ),
                )
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                minWidth = 112.dp()
                setPadding(12.dp(), 0, 12.dp(), 0)
                setBackgroundResource(if (pet.id == state.selectedPetId) R.drawable.bg_headup_nav_item_selected else R.drawable.bg_headup_icon_button)
                setOnClickListener {
                    HeadUpRepository.selectPet(requireContext(), pet.id)
                    Toast.makeText(requireContext(), getString(R.string.pet_selected, getString(pet.nameRes)), Toast.LENGTH_SHORT).show()
                }
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 44.dp()).apply {
                marginEnd = 8.dp()
            }
            container.addView(chip, params)
            if (pet.id == state.selectedPetId) selectedChip = chip
        }
        binding.dragonSelectorScroll.post {
            val target = selectedChip ?: return@post
            binding.dragonSelectorScroll.smoothScrollTo((target.left - 12.dp()).coerceAtLeast(0), 0)
        }
    }

    private fun handlePetInteraction(interaction: PetInteraction) {
        HeadUpRepository.interactWithPet(requireContext(), interaction)
        animatePetInteraction(interaction)
        val petName = getString(latestState.selectedPet.nameRes)
        val message = when (interaction) {
            PetInteraction.FEED -> getString(R.string.pet_fed_format, petName)
            PetInteraction.PLAY -> getString(R.string.pet_played_format, petName)
            PetInteraction.REST -> getString(R.string.pet_rested_format, petName)
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showEquipmentDialog() {
        val equipment = latestState.shopItems.filter { it.isOwned && it.isEquippable }
        if (equipment.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_equipment_owned, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = equipment.map { item ->
            val suffix = if (item.isEquipped) getString(R.string.shop_equipped) else getString(R.string.shop_equip)
            "${item.title()} - $suffix"
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.equip_dragon)
            .setItems(labels) { _, index ->
                val item = equipment[index]
                HeadUpRepository.equipItem(requireContext(), item.id)
                Toast.makeText(requireContext(), getString(R.string.shop_equipment_updated, item.title()), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun claimGoodPostureReward() {
        val claimed = HeadUpRepository.claimTask(requireContext(), "good_posture")
        Toast.makeText(
            requireContext(),
            if (claimed) R.string.reward_claimed else R.string.task_not_complete,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun showEyeRestDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.eye_rest_title)
            .setMessage(R.string.eye_rest_message)
            .setPositiveButton(R.string.eye_rest_complete) { _, _ ->
                HeadUpRepository.recordEyeRest(requireContext())
                Toast.makeText(requireContext(), R.string.eye_rest_recorded, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderDragonVisuals(state: HeadUpUiState) {
        val selectedPet = state.selectedPet
        when {
            state.isPostureAlertActive -> showAnimatedDragon(
                selectedPet.alertAnimationRes,
                selectedPet.alertImageRes,
            )
            else -> showAnimatedDragon(selectedPet.happyAnimationRes, selectedPet.happyImageRes)
        }

        binding.dragonCapeOverlay.visibility =
            if ("moon_cape" in state.equippedShopItems) View.VISIBLE else View.GONE
        binding.dragonGogglesOverlay.visibility =
            if ("focus_goggles" in state.equippedShopItems) View.VISIBLE else View.GONE
        binding.dragonArmorOverlay.visibility =
            if ("starlight_armor" in state.equippedShopItems) View.VISIBLE else View.GONE
        binding.dragonBadgeOverlay.visibility =
            if ("focus_badge" in state.equippedShopItems) View.VISIBLE else View.GONE

        val isDanger = state.isPostureAlertActive
        if (isDanger && !isDragonInDangerPose) {
            isDragonInDangerPose = true
            animateDragonDanger()
        } else if (!isDanger && isDragonInDangerPose) {
            isDragonInDangerPose = false
            startDragonBreathing()
        } else if (!isDanger) {
            startDragonBreathing()
        }
    }

    private fun showStaticDragon(imageRes: Int) {
        if (currentDragonVideoResId != 0) binding.dragonVideoView.stopPlayback()
        currentDragonVideoResId = 0
        binding.dragonVideoView.visibility = View.GONE
        binding.dragonImageView.visibility = View.VISIBLE
        if (currentDragonImageResId != imageRes) {
            currentDragonImageResId = imageRes
            binding.dragonImageView.setImageResource(imageRes)
            animateDragonSwap()
        }
    }

    private fun showAnimatedDragon(@RawRes videoRes: Int, fallbackImageRes: Int) {
        if (currentDragonImageResId != fallbackImageRes) {
            currentDragonImageResId = fallbackImageRes
            binding.dragonImageView.setImageResource(fallbackImageRes)
        }
        binding.dragonVideoView.visibility = View.VISIBLE
        val animationReady: Boolean
        if (currentDragonVideoResId != videoRes) {
            currentDragonVideoResId = videoRes
            animationReady = binding.dragonVideoView.play(videoRes, loop = true)
            if (animationReady) animateDragonSwap()
        } else {
            animationReady = binding.dragonVideoView.play(videoRes, loop = true)
        }
        binding.dragonVideoView.visibility = if (animationReady) View.VISIBLE else View.GONE
        binding.dragonImageView.visibility = if (animationReady) View.GONE else View.VISIBLE
        if (!animationReady) {
            currentDragonVideoResId = 0
            startDragonBreathing()
        }
    }

    private fun dragonVisualTarget(): View =
        if (binding.dragonVideoView.visibility == View.VISIBLE) binding.dragonVideoView else binding.dragonImageView

    private fun animateDragonSwap() {
        val target = dragonVisualTarget()
        target.animate().cancel()
        target.alpha = 0f
        target.scaleX = 0.88f
        target.scaleY = 0.88f
        target.rotation = 0f
        target.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260L)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction { if (!isDragonInDangerPose) startDragonBreathing() }
            .start()
    }

    private fun animatePetInteraction(interaction: PetInteraction) {
        stopDragonAnimation()
        val target = dragonVisualTarget()
        target.animate().cancel()
        target.rotation = 0f
        target.alpha = 1f
        when (interaction) {
            PetInteraction.FEED -> {
                dragonVisualTarget().animate()
                .scaleX(1.12f)
                .scaleY(1.12f)
                .translationY(-8f)
                .setDuration(140L)
                .setInterpolator(OvershootInterpolator(1.8f))
                .withEndAction { settleDragonImage() }
                .start()
            }

            PetInteraction.PLAY -> {
                val playingTarget = dragonVisualTarget()
                ValueAnimator.ofFloat(0f, -10f, 10f, -7f, 7f, 0f).apply {
                    duration = 520L
                    addUpdateListener { animator ->
                        if (_binding == null) return@addUpdateListener
                        playingTarget.rotation = animator.animatedValue as Float
                    }
                    doOnEnd { settleDragonImage() }
                    start()
                }
            }

            PetInteraction.REST -> target.animate()
                .alpha(0.72f)
                .translationY(12f)
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(220L)
                .withEndAction { settleDragonImage() }
                .start()
        }
    }

    private fun animateDragonDanger() {
        stopDragonAnimation()
        val target = dragonVisualTarget()
        target.alpha = 1f
        target.scaleX = 1f
        target.scaleY = 1f
        target.translationY = 0f
        target.rotation = 0f
        ValueAnimator.ofFloat(-5f, 5f).apply {
            duration = 90L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = 7
            addUpdateListener { animator ->
                if (_binding == null) return@addUpdateListener
                target.rotation = animator.animatedValue as Float
            }
            doOnEnd {
                target.rotation = 0f
                if (_binding != null && !isDragonInDangerPose) startDragonBreathing()
            }
            start()
        }
    }

    private fun settleDragonImage() {
        if (_binding == null) return
        dragonVisualTarget().animate()
            .alpha(1f)
            .translationY(0f)
            .rotation(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .withEndAction { if (!isDragonInDangerPose) startDragonBreathing() }
            .start()
    }

    private fun startDragonBreathing() {
        if (_binding == null || isDragonInDangerPose) return
        val target = dragonVisualTarget()
        if (dragonBreathingAnimator?.isRunning == true && dragonBreathingTarget == target) return
        dragonBreathingAnimator?.cancel()
        dragonBreathingTarget?.scaleX = 1f
        dragonBreathingTarget?.scaleY = 1f
        dragonBreathingTarget = target
        dragonBreathingAnimator = ValueAnimator.ofFloat(0.97f, 1.03f).apply {
            duration = 1_300L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                if (_binding == null) return@addUpdateListener
                val scale = animator.animatedValue as Float
                target.scaleX = scale
                target.scaleY = scale
            }
            start()
        }
    }

    private fun stopDragonAnimation() {
        dragonBreathingAnimator?.cancel()
        dragonBreathingAnimator = null
        dragonBreathingTarget?.scaleX = 1f
        dragonBreathingTarget?.scaleY = 1f
        dragonBreathingTarget = null
        _binding?.dragonImageView?.animate()?.cancel()
        _binding?.dragonVideoView?.animate()?.cancel()
    }

    private fun ShopItem.title(): String = getString(
        when (id) {
            "starlight_armor" -> R.string.shop_starlight_armor
            "focus_goggles" -> R.string.shop_focus_goggles
            "moon_cape" -> R.string.shop_moon_cape
            "ocean_background" -> R.string.shop_ocean_background
            "sunrise_background" -> R.string.shop_sunrise_background
            "forest_background" -> R.string.shop_forest_background
            "eye_time_ticket" -> R.string.shop_eye_time_ticket
            "focus_badge" -> R.string.shop_focus_badge
            "voucher_711" -> R.string.shop_voucher_711
            "voucher_familymart" -> R.string.shop_voucher_familymart
            "voucher_pxmart" -> R.string.shop_voucher_pxmart
            else -> R.string.shop_unknown_item
        },
    )

    private fun dragonOrbBackground(state: HeadUpUiState): Int = when {
        state.isPostureAlertActive -> R.drawable.bg_headup_dragon_orb_danger
        "sunrise_background" in state.equippedShopItems -> R.drawable.bg_headup_dragon_orb_sunrise
        "forest_background" in state.equippedShopItems -> R.drawable.bg_headup_dragon_orb_forest
        "ocean_background" in state.equippedShopItems -> R.drawable.bg_headup_dragon_orb_ocean
        else -> R.drawable.bg_headup_dragon_orb
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun PostureZone.colorRes(): Int = when (this) {
        PostureZone.SAFE -> R.color.headup_safe
        PostureZone.WARNING -> R.color.headup_warning
        PostureZone.DANGER -> R.color.headup_danger
    }

    override fun onDestroyView() {
        stopDragonAnimation()
        binding.dragonVideoView.stopPlayback()
        _binding = null
        super.onDestroyView()
    }
}
