package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.poselandmarker.HeadUpRepository
import com.google.mediapipe.examples.poselandmarker.HeadUpUiState
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.ShopItem
import com.google.mediapipe.examples.poselandmarker.ShopItemCategory
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentShopBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemShopItemBinding

class ShopFragment : Fragment() {
    private var _binding: FragmentShopBinding? = null
    private val binding get() = _binding!!
    private var latestState = HeadUpUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentShopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render(HeadUpRepository.currentState(requireContext()))
        HeadUpRepository.observeState().observe(viewLifecycleOwner) { render(it) }
    }

    private fun render(state: HeadUpUiState) {
        latestState = state
        binding.shopCoinValue.text = "%,d".format(state.coins)
        binding.shopEquipmentContainer.removeAllViews()
        binding.shopRewardContainer.removeAllViews()
        state.shopItems.forEach { item ->
            val container = if (item.category == ShopItemCategory.CONSUMABLE || item.category == ShopItemCategory.VOUCHER) {
                binding.shopRewardContainer
            } else {
                binding.shopEquipmentContainer
            }
            val row = ItemShopItemBinding.inflate(LayoutInflater.from(requireContext()), container, false)
            bindItem(row, item, item.title(), item.detail(), item.icon(), state.coins)
            container.addView(row.root, rowLayoutParams())
        }
    }

    private fun bindItem(
        row: ItemShopItemBinding,
        item: ShopItem,
        title: String,
        detail: String,
        icon: String,
        coins: Int,
    ) {
        row.shopItemIcon.text = icon
        row.shopItemTitle.text = title
        row.shopItemDetail.text = detail
        row.shopItemCost.text = when {
            !item.isOwned -> getString(R.string.shop_cost_format, item.cost)
            item.isEquipped -> getString(R.string.shop_equipped)
            item.isEquippable -> getString(R.string.shop_equip)
            item.category == ShopItemCategory.CONSUMABLE -> getString(R.string.shop_use)
            item.category == ShopItemCategory.VOUCHER -> getString(R.string.shop_redeem_voucher)
            else -> getString(R.string.shop_cost_format, item.cost)
        }
        row.shopItemCost.isEnabled = true
        row.root.alpha = if (item.isOwned) 0.86f else 1f
        row.shopItemCost.setOnClickListener {
            val purchased = !item.isOwned && HeadUpRepository.purchaseItem(requireContext(), item.id)
            val equipped = item.isOwned && item.isEquippable && HeadUpRepository.equipItem(requireContext(), item.id)
            val used = item.isOwned && item.category == ShopItemCategory.CONSUMABLE && HeadUpRepository.useShopItem(requireContext(), item.id)
            val voucherRedeemed = item.isOwned && item.category == ShopItemCategory.VOUCHER &&
                HeadUpRepository.useShopItem(requireContext(), item.id)
            if (voucherRedeemed) {
                showVoucherDialog(title, item.id)
                return@setOnClickListener
            }
            val message = when {
                purchased -> R.string.shop_purchase_success
                equipped -> R.string.shop_equipment_changed
                used -> R.string.shop_item_used
                coins < item.cost -> R.string.shop_not_enough_points
                item.isOwned -> R.string.shop_already_owned
                else -> R.string.shop_purchase_failed
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVoucherDialog(title: String, itemId: String) {
        val code = createVoucherCode(itemId)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.shop_voucher_redeemed_title)
            .setMessage(getString(R.string.shop_voucher_redeemed_message, title, code))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun createVoucherCode(itemId: String): String {
        val suffix = (System.currentTimeMillis() % 1_000_000L).toString().padStart(6, '0')
        return "HU-${itemId.filter { it.isLetterOrDigit() }.uppercase().takeLast(5)}-$suffix"
    }

    private fun rowLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 10.dp()
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

    private fun ShopItem.detail(): String = getString(
        when (id) {
            "starlight_armor" -> R.string.shop_starlight_detail
            "focus_goggles" -> R.string.shop_focus_goggles_detail
            "moon_cape" -> R.string.shop_moon_cape_detail
            "ocean_background" -> R.string.shop_ocean_detail
            "sunrise_background" -> R.string.shop_sunrise_detail
            "forest_background" -> R.string.shop_forest_detail
            "eye_time_ticket" -> R.string.shop_eye_time_detail
            "focus_badge" -> R.string.shop_focus_detail
            "voucher_711" -> R.string.shop_voucher_711_detail
            "voucher_familymart" -> R.string.shop_voucher_familymart_detail
            "voucher_pxmart" -> R.string.shop_voucher_pxmart_detail
            else -> R.string.shop_unknown_detail
        },
    )

    private fun ShopItem.icon(): String = when (id) {
        "starlight_armor" -> "A"
        "focus_goggles" -> "G"
        "moon_cape" -> "C"
        "ocean_background" -> "B"
        "sunrise_background" -> "S"
        "forest_background" -> "F"
        "eye_time_ticket" -> "T"
        "focus_badge" -> "F"
        "voucher_711" -> "7"
        "voucher_familymart" -> "M"
        "voucher_pxmart" -> "P"
        else -> "I"
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
