package com.google.mediapipe.examples.poselandmarker

data class FamilyAccessPolicy(
    val isFamilyPlan: Boolean,
    val isManager: Boolean,
    val canViewAllMemberHealth: Boolean,
    val canManageMembers: Boolean,
    val canViewFamilyLeaderboard: Boolean,
) {
    companion object {
        fun from(plan: String, role: String, familyId: String?): FamilyAccessPolicy {
            val hasFamily = plan == "family" && !familyId.isNullOrBlank()
            val manager = hasFamily && role in setOf("family_manager", "admin")
            return FamilyAccessPolicy(
                isFamilyPlan = hasFamily,
                isManager = manager,
                canViewAllMemberHealth = manager,
                canManageMembers = manager,
                canViewFamilyLeaderboard = hasFamily,
            )
        }
    }
}
