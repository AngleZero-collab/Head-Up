package com.google.mediapipe.examples.poselandmarker

data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f,
    val presence: Float = 1f,
)

enum class PostureZone {
    SAFE,
    WARNING,
    DANGER,
}

data class CalibrationProfile(
    val angleDegrees: Float,
    val postureRatio: Float,
    val shoulderWidth: Float,
    val eyeDistancePixels: Float? = null,
    val distanceConstantK: Float? = null,
    val calibratedAtMs: Long = System.currentTimeMillis(),
)

data class PostureMetrics(
    val angleDegrees: Int,
    val zone: PostureZone,
    val postureRatio: Float,
    val headTiltLabel: String,
    val neckCurvatureLabel: String,
    val shoulderBalanceDegrees: Int,
    val shoulderBalanceLabel: String,
    val rawAngleDegrees: Float = angleDegrees.toFloat(),
    val relativeAngleDegrees: Int = angleDegrees,
    val neckFlexionDegrees: Int = 0,
    val screenDistanceCm: Int? = null,
    val isTooClose: Boolean = false,
    val eyeDistancePixels: Float? = null,
    val smoothedEyeDistancePixels: Float? = null,
    val landmarkConfidence: Float = 0f,
    val shoulderWidth: Float = 0f,
    val deviceTiltDegrees: Int = 0,
    val isDeviceFlat: Boolean = false,
    val isRapidFall: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val isGoodPosture: Boolean
        get() = zone == PostureZone.SAFE
}

data class HeadUpTask(
    val id: String,
    val reward: Int,
    val progress: Int,
    val max: Int,
    val claimed: Boolean = false,
) {
    val isComplete: Boolean
        get() = progress >= max
}

data class VirtualPetType(
    val id: String,
    val nameRes: Int,
    val traitRes: Int,
    val icon: String,
    val accentColorRes: Int,
    val happyImageRes: Int,
    val happyAnimationRes: Int,
    val alertImageRes: Int,
    val alertAnimationRes: Int,
)

object VirtualPetCatalog {
    const val DEFAULT_PET_ID = "little_blue"

    val all: List<VirtualPetType> = listOf(
        dragon(DEFAULT_PET_ID, R.string.dragon_little_blue, R.string.dragon_trait_little_blue, "B", R.color.headup_primary, R.drawable.vision_dragon_little_blue_cutout, R.raw.dragon_happy_blue),
        dragon("ember_red", R.string.dragon_ember_red, R.string.dragon_trait_ember_red, "R", R.color.headup_danger, R.drawable.vision_dragon_ember_red_cutout, R.raw.dragon_happy_red),
        dragon("mint_leaf", R.string.dragon_mint_leaf, R.string.dragon_trait_mint_leaf, "M", R.color.headup_safe, R.drawable.vision_dragon_mint_leaf_cutout, R.raw.dragon_happy_mint),
        dragon("violet_star", R.string.dragon_violet_star, R.string.dragon_trait_violet_star, "V", R.color.headup_purple, R.drawable.vision_dragon_violet_star_cutout, R.raw.dragon_happy_violet),
        dragon("sunny_gold", R.string.dragon_sunny_gold, R.string.dragon_trait_sunny_gold, "G", R.color.headup_warning, R.drawable.vision_dragon_sunny_gold_cutout, R.raw.dragon_happy_gold),
        VirtualPetType(
            id = "uplift_fox",
            nameRes = R.string.pet_uplift_fox,
            traitRes = R.string.pet_trait_uplift_fox,
            icon = "F",
            accentColorRes = R.color.headup_warning,
            happyImageRes = R.drawable.virtual_pet_fox_happy,
            happyAnimationRes = R.raw.pet_fox_happy,
            alertImageRes = R.drawable.virtual_pet_fox_alert,
            alertAnimationRes = R.raw.pet_fox_alert,
        ),
        VirtualPetType(
            id = "focus_lizard",
            nameRes = R.string.pet_focus_lizard,
            traitRes = R.string.pet_trait_focus_lizard,
            icon = "L",
            accentColorRes = R.color.headup_safe,
            happyImageRes = R.drawable.virtual_pet_lizard_happy,
            happyAnimationRes = R.raw.pet_lizard_happy,
            alertImageRes = R.drawable.virtual_pet_lizard_alert,
            alertAnimationRes = R.raw.pet_lizard_alert,
        ),
        VirtualPetType(
            id = "cozy_bunny",
            nameRes = R.string.pet_cozy_bunny,
            traitRes = R.string.pet_trait_cozy_bunny,
            icon = "C",
            accentColorRes = R.color.headup_primary,
            happyImageRes = R.drawable.virtual_pet_bunny_happy,
            happyAnimationRes = R.raw.pet_bunny_happy,
            alertImageRes = R.drawable.virtual_pet_bunny_alert,
            alertAnimationRes = R.raw.pet_bunny_alert,
        ),
        VirtualPetType(
            id = "playful_husky",
            nameRes = R.string.pet_playful_husky,
            traitRes = R.string.pet_trait_playful_husky,
            icon = "H",
            accentColorRes = R.color.headup_primary,
            happyImageRes = R.drawable.virtual_pet_husky_happy,
            happyAnimationRes = R.raw.pet_husky_happy,
            alertImageRes = R.drawable.virtual_pet_husky_alert,
            alertAnimationRes = R.raw.pet_husky_alert,
        ),
        VirtualPetType(
            id = "gentle_ragdoll",
            nameRes = R.string.pet_gentle_ragdoll,
            traitRes = R.string.pet_trait_gentle_ragdoll,
            icon = "K",
            accentColorRes = R.color.headup_purple,
            happyImageRes = R.drawable.virtual_pet_ragdoll_happy,
            happyAnimationRes = R.raw.pet_ragdoll_happy,
            alertImageRes = R.drawable.virtual_pet_ragdoll_alert,
            alertAnimationRes = R.raw.pet_ragdoll_alert,
        ),
    )

    fun byId(id: String): VirtualPetType =
        all.firstOrNull { it.id == id } ?: all.first()

    private fun dragon(
        id: String,
        nameRes: Int,
        traitRes: Int,
        icon: String,
        accentColorRes: Int,
        happyImageRes: Int,
        happyAnimationRes: Int,
    ) = VirtualPetType(
        id = id,
        nameRes = nameRes,
        traitRes = traitRes,
        icon = icon,
        accentColorRes = accentColorRes,
        happyImageRes = happyImageRes,
        happyAnimationRes = happyAnimationRes,
        alertImageRes = R.drawable.vision_dragon_angry_cutout,
        alertAnimationRes = R.raw.dragon_angry_red,
    )
}

enum class ShopItemCategory {
    EQUIPMENT,
    BACKGROUND,
    CONSUMABLE,
    BADGE,
    VOUCHER,
}

data class ShopItem(
    val id: String,
    val cost: Int,
    val isOwned: Boolean,
    val category: ShopItemCategory,
    val isEquipped: Boolean = false,
) {
    val isEquippable: Boolean
        get() = category == ShopItemCategory.EQUIPMENT ||
            category == ShopItemCategory.BACKGROUND ||
            category == ShopItemCategory.BADGE
}

enum class PetInteraction {
    FEED,
    PLAY,
    REST,
}

data class HeadUpUiState(
    val metrics: PostureMetrics = PostureAnalyzer.defaultMetrics(),
    val goodPostureSecondsToday: Long = 0L,
    val warningSecondsToday: Long = 0L,
    val dangerSecondsToday: Long = 0L,
    val eyeRestCountToday: Int = 0,
    val consecutiveDays: Int = 0,
    val dragonEnergy: Int = 50,
    val dragonLevel: Int = 1,
    val dragonBond: Int = 0,
    val selectedPetId: String = VirtualPetCatalog.DEFAULT_PET_ID,
    val coins: Int = 0,
    val lastUpdatedMs: Long = 0L,
    val calibrationProfile: CalibrationProfile? = null,
    val ownedShopItems: Set<String> = emptySet(),
    val equippedShopItems: Set<String> = emptySet(),
    val claimedTasks: Set<String> = emptySet(),
    val isAlarmEnabled: Boolean = false,
    val isPostureAlertActive: Boolean = false,
    val monitoringMode: MonitoringMode = MonitoringMode.OFF,
    val targetInferenceFps: Int = 5,
) {
    val feedbackZone: PostureZone
        get() = when {
            isPostureAlertActive -> PostureZone.DANGER
            metrics.zone == PostureZone.SAFE -> PostureZone.SAFE
            else -> PostureZone.WARNING
        }

    val goodPostureMinutesToday: Int
        get() = (goodPostureSecondsToday / 60L).toInt()

    val totalTrackedSecondsToday: Long
        get() = goodPostureSecondsToday + warningSecondsToday + dangerSecondsToday

    val protectEyesPercent: Int
        get() = if (totalTrackedSecondsToday == 0L) 0 else
            ((goodPostureSecondsToday * 100L) / totalTrackedSecondsToday).toInt().coerceIn(0, 100)

    val tasks: List<HeadUpTask>
        get() = listOf(
            HeadUpTask("good_posture", 50, goodPostureMinutesToday.coerceAtMost(30), 30, "good_posture" in claimedTasks),
            HeadUpTask("eye_rest", 50, eyeRestCountToday.coerceAtMost(3), 3, "eye_rest" in claimedTasks),
            HeadUpTask("posture_challenge", 100, if (protectEyesPercent >= 80 && totalTrackedSecondsToday >= 600L) 1 else 0, 1, "posture_challenge" in claimedTasks),
        )

    val selectedPet: VirtualPetType
        get() = VirtualPetCatalog.byId(selectedPetId)

    val shopItems: List<ShopItem>
        get() = listOf(
            ShopItem("starlight_armor", 300, "starlight_armor" in ownedShopItems, ShopItemCategory.EQUIPMENT, "starlight_armor" in equippedShopItems),
            ShopItem("focus_goggles", 260, "focus_goggles" in ownedShopItems, ShopItemCategory.EQUIPMENT, "focus_goggles" in equippedShopItems),
            ShopItem("moon_cape", 220, "moon_cape" in ownedShopItems, ShopItemCategory.EQUIPMENT, "moon_cape" in equippedShopItems),
            ShopItem("ocean_background", 180, "ocean_background" in ownedShopItems, ShopItemCategory.BACKGROUND, "ocean_background" in equippedShopItems),
            ShopItem("sunrise_background", 180, "sunrise_background" in ownedShopItems, ShopItemCategory.BACKGROUND, "sunrise_background" in equippedShopItems),
            ShopItem("forest_background", 180, "forest_background" in ownedShopItems, ShopItemCategory.BACKGROUND, "forest_background" in equippedShopItems),
            ShopItem("eye_time_ticket", 120, "eye_time_ticket" in ownedShopItems, ShopItemCategory.CONSUMABLE),
            ShopItem("focus_badge", 220, "focus_badge" in ownedShopItems, ShopItemCategory.BADGE, "focus_badge" in equippedShopItems),
            ShopItem("voucher_711", 700, "voucher_711" in ownedShopItems, ShopItemCategory.VOUCHER),
            ShopItem("voucher_familymart", 700, "voucher_familymart" in ownedShopItems, ShopItemCategory.VOUCHER),
            ShopItem("voucher_pxmart", 900, "voucher_pxmart" in ownedShopItems, ShopItemCategory.VOUCHER),
        )
}

data class DailyPostureSummary(
    val dayStartMs: Long,
    val safeSeconds: Long,
    val warningSeconds: Long,
    val dangerSeconds: Long,
    val dangerEvents: Int,
) {
    val totalSeconds: Long
        get() = safeSeconds + warningSeconds + dangerSeconds

    val riskSeconds: Long
        get() = warningSeconds + dangerSeconds

    val safePercent: Int
        get() = if (totalSeconds == 0L) 0 else ((safeSeconds * 100L) / totalSeconds).toInt().coerceIn(0, 100)
}

data class DailyHealthTrendSummary(
    val dayStartMs: Long,
    val safeSeconds: Long,
    val warningSeconds: Long,
    val dangerSeconds: Long,
    val dangerEvents: Int,
    val averageAngleDegrees: Int,
    val peakAngleDegrees: Int,
    val closeScreenSeconds: Long,
    val veryCloseScreenSeconds: Long,
    val shoulderImbalanceEvents: Int,
    val rapidFallEvents: Int,
    val averageScreenDistanceCm: Int?,
) {
    val totalSeconds: Long
        get() = safeSeconds + warningSeconds + dangerSeconds

    val riskSeconds: Long
        get() = warningSeconds + dangerSeconds

    val safePercent: Int
        get() = if (totalSeconds == 0L) 0 else ((safeSeconds * 100L) / totalSeconds).toInt().coerceIn(0, 100)

    val riskPercent: Int
        get() = if (totalSeconds == 0L) 0 else ((riskSeconds * 100L) / totalSeconds).toInt().coerceIn(0, 100)
}

data class PostureInsight(
    val title: String,
    val description: String,
    val level: InsightLevel = InsightLevel.INFO,
)

enum class InsightLevel {
    INFO,
    SUCCESS,
    WARNING,
}

data class PostureDashboard(
    val today: DailyPostureSummary = DailyPostureSummary(0L, 0L, 0L, 0L, 0),
    val week: List<DailyPostureSummary> = emptyList(),
    val todayHealth: DailyHealthTrendSummary = DailyHealthTrendSummary(0L, 0L, 0L, 0L, 0, 0, 0, 0L, 0L, 0, 0, null),
    val weekHealth: List<DailyHealthTrendSummary> = emptyList(),
    val insights: List<PostureInsight> = emptyList(),
)
