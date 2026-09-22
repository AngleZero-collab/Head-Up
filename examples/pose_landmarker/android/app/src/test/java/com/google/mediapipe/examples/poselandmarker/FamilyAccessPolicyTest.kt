package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyAccessPolicyTest {
    @Test
    fun managerCanViewAndManageAllMemberHealth() {
        val policy = FamilyAccessPolicy.from("family", "family_manager", "family-1")

        assertTrue(policy.isFamilyPlan)
        assertTrue(policy.isManager)
        assertTrue(policy.canViewAllMemberHealth)
        assertTrue(policy.canManageMembers)
        assertTrue(policy.canViewFamilyLeaderboard)
    }

    @Test
    fun memberCanOnlyViewFamilyLeaderboard() {
        val policy = FamilyAccessPolicy.from("family", "family_member", "family-1")

        assertTrue(policy.isFamilyPlan)
        assertFalse(policy.isManager)
        assertFalse(policy.canViewAllMemberHealth)
        assertFalse(policy.canManageMembers)
        assertTrue(policy.canViewFamilyLeaderboard)
    }

    @Test
    fun personalAccountHasNoFamilyAccess() {
        val policy = FamilyAccessPolicy.from("individual", "user", null)

        assertFalse(policy.isFamilyPlan)
        assertFalse(policy.canViewAllMemberHealth)
        assertFalse(policy.canManageMembers)
        assertFalse(policy.canViewFamilyLeaderboard)
    }
}
