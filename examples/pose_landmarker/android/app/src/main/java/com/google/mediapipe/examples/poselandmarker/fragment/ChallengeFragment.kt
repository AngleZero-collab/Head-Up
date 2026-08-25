package com.google.mediapipe.examples.poselandmarker.fragment

import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.poselandmarker.DragonInteraction
import com.google.mediapipe.examples.poselandmarker.HeadUpRepository
import com.google.mediapipe.examples.poselandmarker.HeadUpUiState
import com.google.mediapipe.examples.poselandmarker.PostureZone
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.ShopItem
import com.google.mediapipe.examples.poselandmarker.VisionDragonCatalog
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentChallengeBinding

class ChallengeFragment : Fragment() {
    private var _binding: FragmentChallengeBinding? = null
    private val binding get() = _binding!!
    private var mediaPlayer: MediaPlayer? = null
    private var currentSurface: Surface? = null
    private var currentVideoResId = 0
    private var latestState = HeadUpUiState()

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
        binding.dragonVideoView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                currentSurface?.release()
                currentSurface = Surface(texture)
                updateDragonVideo(currentVideoResId.takeIf { it != 0 } ?: R.raw.happy_dragon)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseMediaPlayer()
                currentSurface?.release()
                currentSurface = null
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        binding.dragonVideoView.setOnClickListener {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) player.pause() else player.start()
                } catch (error: IllegalStateException) {
                    Log.w("ChallengeFragment", "Dragon player was stale after surface change", error)
                    updateDragonVideo(currentVideoResId.takeIf { it != 0 } ?: R.raw.happy_dragon, force = true)
                }
            }
        }
        binding.claimMaintainButton.setOnClickListener { claimGoodPostureReward() }
        binding.recordEyeRestButton.setOnClickListener { showEyeRestDialog() }
        binding.feedDragonButton.setOnClickListener { handleDragonInteraction(DragonInteraction.FEED) }
        binding.playDragonButton.setOnClickListener { handleDragonInteraction(DragonInteraction.PLAY) }
        binding.restDragonButton.setOnClickListener { handleDragonInteraction(DragonInteraction.REST) }
        binding.equipDragonButton.setOnClickListener { showEquipmentDialog() }

        render(HeadUpRepository.currentState(requireContext()))
        HeadUpRepository.observeState().observe(viewLifecycleOwner) { render(it) }
    }

    override fun onResume() {
        super.onResume()
        if (currentSurface != null && currentVideoResId != 0) updateDragonVideo(currentVideoResId)
    }

    override fun onPause() {
        releaseMediaPlayer()
        super.onPause()
    }

    private fun setupDragonOrb() {
        binding.dragonVideoView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        binding.dragonVideoView.clipToOutline = true
    }

    private fun render(state: HeadUpUiState) {
        if (_binding == null) return
        latestState = state
        val zoneColor = ContextCompat.getColor(requireContext(), state.metrics.zone.colorRes())
        val selectedDragon = state.selectedDragon
        binding.dragonLevelBadge.text = "Lv.${state.dragonLevel}"
        binding.dragonNameText.text = getString(selectedDragon.nameRes)
        binding.dragonTraitText.text = getString(selectedDragon.traitRes)
        binding.dragonTypeBadge.text = selectedDragon.icon
        binding.dragonTypeBadge.setTextColor(ContextCompat.getColor(requireContext(), selectedDragon.accentColorRes))
        binding.dragonGearBadge.text = if (state.equippedShopItems.isEmpty()) {
            getString(R.string.no_gear_short)
        } else {
            getString(R.string.gear_count_short, state.equippedShopItems.size)
        }
        binding.dragonEnergyProgress.progress = state.dragonEnergy
        binding.dragonEnergyText.text = getString(
            if (state.metrics.isGoodPosture) R.string.dragon_energy_good else R.string.dragon_energy_rest,
            state.dragonEnergy,
        )
        binding.dragonBondText.text = getString(R.string.dragon_bond_format, state.dragonBond)
        binding.dragonMoodText.text = getString(
            when (state.metrics.zone) {
                PostureZone.SAFE -> R.string.dragon_mood_safe_format
                PostureZone.WARNING -> R.string.dragon_mood_warning_format
                PostureZone.DANGER -> R.string.dragon_mood_danger_format
            },
            getString(selectedDragon.nameRes),
        )
        binding.dragonMoodText.setTextColor(zoneColor)

        if (state.metrics.zone == PostureZone.DANGER) {
            binding.dragonOrb.setBackgroundResource(R.drawable.bg_headup_dragon_orb_danger)
        } else {
            binding.dragonOrb.setBackgroundResource(R.drawable.bg_headup_dragon_orb)
        }
        renderDragonSelector(state)

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
        updateDragonVideo(videoFor(state))
    }

    private fun renderDragonSelector(state: HeadUpUiState) {
        val container = binding.dragonSelectorContainer
        container.removeAllViews()
        VisionDragonCatalog.all.forEach { dragon ->
            val chip = TextView(requireContext()).apply {
                text = getString(R.string.dragon_selector_format, dragon.icon, getString(dragon.nameRes))
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (dragon.id == state.selectedDragonId) R.color.headup_text_primary else R.color.headup_text_secondary,
                    ),
                )
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                minWidth = 112.dp()
                setPadding(12.dp(), 0, 12.dp(), 0)
                setBackgroundResource(if (dragon.id == state.selectedDragonId) R.drawable.bg_headup_nav_item_selected else R.drawable.bg_headup_icon_button)
                setOnClickListener {
                    HeadUpRepository.selectDragon(requireContext(), dragon.id)
                    Toast.makeText(requireContext(), getString(R.string.dragon_selected, getString(dragon.nameRes)), Toast.LENGTH_SHORT).show()
                }
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 44.dp()).apply {
                marginEnd = 8.dp()
            }
            container.addView(chip, params)
        }
    }

    private fun handleDragonInteraction(interaction: DragonInteraction) {
        HeadUpRepository.interactWithDragon(requireContext(), interaction)
        val message = when (interaction) {
            DragonInteraction.FEED -> R.string.dragon_fed
            DragonInteraction.PLAY -> R.string.dragon_played
            DragonInteraction.REST -> R.string.dragon_rested
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

    private fun updateDragonVideo(videoResId: Int, force: Boolean = false) {
        if (!force && currentVideoResId == videoResId && mediaPlayer != null) {
            ensureDragonVideoPlaying(videoResId)
            return
        }
        val surface = currentSurface ?: run {
            currentVideoResId = videoResId
            return
        }
        currentVideoResId = videoResId
        releaseMediaPlayer()
        try {
            resources.openRawResourceFd(videoResId).use { descriptor ->
                mediaPlayer = MediaPlayer().apply {
                    setSurface(surface)
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    isLooping = true
                    setVolume(0f, 0f)
                    setOnPreparedListener { it.start() }
                    setOnErrorListener { _, what, extra ->
                        Log.w("ChallengeFragment", "Dragon video failed: $what/$extra")
                        true
                    }
                    prepareAsync()
                }
            }
        } catch (error: Exception) {
            Log.e("ChallengeFragment", "Unable to play dragon animation", error)
        }
    }

    private fun videoFor(state: HeadUpUiState): Int =
        if (state.metrics.zone == PostureZone.DANGER) R.raw.angry_dragon
        else if (state.selectedDragonId == VisionDragonCatalog.DEFAULT_DRAGON_ID) R.raw.happy_dragon
        else R.raw.blue_dragon

    private fun ShopItem.title(): String = getString(
        when (id) {
            "starlight_armor" -> R.string.shop_starlight_armor
            "focus_goggles" -> R.string.shop_focus_goggles
            "moon_cape" -> R.string.shop_moon_cape
            "ocean_background" -> R.string.shop_ocean_background
            "eye_time_ticket" -> R.string.shop_eye_time_ticket
            "focus_badge" -> R.string.shop_focus_badge
            else -> R.string.shop_unknown_item
        },
    )

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun ensureDragonVideoPlaying(videoResId: Int) {
        val player = mediaPlayer ?: return updateDragonVideo(videoResId, force = true)
        try {
            if (!player.isPlaying) player.start()
        } catch (error: IllegalStateException) {
            Log.w("ChallengeFragment", "Dragon player stopped unexpectedly; rebuilding", error)
            updateDragonVideo(videoResId, force = true)
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
            Unit
        }
        mediaPlayer = null
    }

    private fun PostureZone.colorRes(): Int = when (this) {
        PostureZone.SAFE -> R.color.headup_safe
        PostureZone.WARNING -> R.color.headup_warning
        PostureZone.DANGER -> R.color.headup_danger
    }

    override fun onDestroyView() {
        releaseMediaPlayer()
        _binding = null
        super.onDestroyView()
    }
}
