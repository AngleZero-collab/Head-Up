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

data class VisionDragonType(
    val id: String,
    val nameRes: Int,
    val traitRes: Int,
    val icon: String,
    val accentColorRes: Int,
)

object VisionDragonCatalog {
    const val DEFAULT_DRAGON_ID = "little_blue"

    val all: List<VisionDragonType> = listOf(
        VisionDragonType(DEFAULT_DRAGON_ID, R.string.dragon_little_blue, R.string.dragon_trait_little_blue, "B", R.color.headup_primary),
        VisionDragonType("ember_red", R.string.dragon_ember_red, R.string.dragon_trait_ember_red, "R", R.color.headup_danger),
        VisionDragonType("mint_leaf", R.string.dragon_mint_leaf, R.string.dragon_trait_mint_leaf, "M", R.color.headup_safe),
        VisionDragonType("violet_star", R.string.dragon_violet_star, R.string.dragon_trait_violet_star, "V", R.color.headup_purple),
        VisionDragonType("sunny_gold", R.string.dragon_sunny_gold, R.string.dragon_trait_sunny_gold, "G", R.color.headup_warning),
    )

    fun byId(id: String): VisionDragonType =
        all.firstOrNull { it.id == id } ?: all.first()
}

enum class ShopItemCategory {
    EQUIPMENT,
    BACKGROUND,
    CONSUMABLE,
    BADGE,
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

enum class DragonInteraction {
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
    val selectedDragonId: String = VisionDragonCatalog.DEFAULT_DRAGON_ID,
    val coins: Int = 0,
    val lastUpdatedMs: Long = 0L,
    val calibrationProfile: CalibrationProfile? = null,
    val ownedShopItems: Set<String> = emptySet(),
    val equippedShopItems: Set<String> = emptySet(),
    val claimedTasks: Set<String> = emptySet(),
    val isAlarmEnabled: Boolean = false,
) {
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

    val selectedDragon: VisionDragonType
        get() = VisionDragonCatalog.byId(selectedDragonId)

    val shopItems: List<ShopItem>
        get() = listOf(
            ShopItem("starlight_armor", 300, "starlight_armor" in ownedShopItems, ShopItemCategory.EQUIPMENT, "starlight_armor" in equippedShopItems),
            ShopItem("focus_goggles", 260, "focus_goggles" in ownedShopItems, ShopItemCategory.EQUIPMENT, "focus_goggles" in equippedShopItems),
            ShopItem("moon_cape", 220, "moon_cape" in ownedShopItems, ShopItemCategory.EQUIPMENT, "moon_cape" in equippedShopItems),
            ShopItem("ocean_background", 180, "ocean_background" in ownedShopItems, ShopItemCategory.BACKGROUND, "ocean_background" in equippedShopItems),
            ShopItem("eye_time_ticket", 120, "eye_time_ticket" in ownedShopItems, ShopItemCategory.CONSUMABLE),
            ShopItem("focus_badge", 220, "focus_badge" in ownedShopItems, ShopItemCategory.BADGE, "focus_badge" in equippedShopItems),
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
