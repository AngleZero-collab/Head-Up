package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

object HeadUpAuthStore {
    private const val PREFS_NAME = "headup_auth_store"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_DEVICE_USER_ID = "device_user_id"
    private const val KEY_SUBSCRIPTION_TIER = "subscription_tier"
    private const val KEY_ROLE = "role"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_FAMILY_ID = "family_id"

    fun saveSession(
        context: Context,
        accessToken: String,
        userId: String,
        subscriptionTier: String = "individual",
        role: String = "user",
        displayName: String? = null,
        familyId: String? = null,
    ) {
        prefs(context).edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_USER_ID, userId)
            putString(KEY_SUBSCRIPTION_TIER, subscriptionTier)
            putString(KEY_ROLE, role)
            putNullable(KEY_DISPLAY_NAME, displayName)
            putNullable(KEY_FAMILY_ID, familyId)
        }
    }

    fun updateAccountMetadata(
        context: Context,
        userId: String,
        subscriptionTier: String,
        role: String,
        displayName: String? = null,
        familyId: String? = null,
    ) {
        prefs(context).edit {
            putString(KEY_USER_ID, userId)
            putString(KEY_SUBSCRIPTION_TIER, subscriptionTier)
            putString(KEY_ROLE, role)
            putNullable(KEY_DISPLAY_NAME, displayName)
            putNullable(KEY_FAMILY_ID, familyId)
        }
    }

    fun startGuestSession(context: Context): String {
        val guestId = getOrCreateDeviceUserId(context)
        prefs(context).edit {
            remove(KEY_ACCESS_TOKEN)
            putString(KEY_USER_ID, guestId)
            putString(KEY_SUBSCRIPTION_TIER, "guest")
            putString(KEY_ROLE, "guest")
            putString(KEY_DISPLAY_NAME, "Guest ${guestId.takeLast(6)}")
            remove(KEY_FAMILY_ID)
        }
        return guestId
    }

    fun clearSession(context: Context) {
        prefs(context).edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_SUBSCRIPTION_TIER)
            remove(KEY_ROLE)
            remove(KEY_DISPLAY_NAME)
            remove(KEY_FAMILY_ID)
        }
    }

    fun accessToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun currentUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, null) ?: getOrCreateDeviceUserId(context)

    fun deviceUserId(context: Context): String =
        getOrCreateDeviceUserId(context)

    fun isSignedIn(context: Context): Boolean =
        !accessToken(context).isNullOrBlank()

    fun userLabel(context: Context): String =
        displayName(context)?.takeIf { it.isNotBlank() }
            ?: if (role(context) == "guest") "Guest ${getOrCreateDeviceUserId(context).takeLast(6)}" else currentUserId(context)

    fun subscriptionTier(context: Context): String =
        prefs(context).getString(KEY_SUBSCRIPTION_TIER, null) ?: if (isSignedIn(context)) "individual" else "guest"

    fun role(context: Context): String =
        prefs(context).getString(KEY_ROLE, null) ?: if (isSignedIn(context)) "user" else "guest"

    fun displayName(context: Context): String? =
        prefs(context).getString(KEY_DISPLAY_NAME, null)

    fun familyId(context: Context): String? =
        prefs(context).getString(KEY_FAMILY_ID, null)

    fun isFamilyPlan(context: Context): Boolean =
        subscriptionTier(context) == "family" && !familyId(context).isNullOrBlank()

    fun isFamilyManager(context: Context): Boolean =
        isFamilyPlan(context) && role(context) == "family_manager"

    private fun getOrCreateDeviceUserId(context: Context): String {
        val encryptedPrefs = prefs(context)
        encryptedPrefs.getString(KEY_DEVICE_USER_ID, null)?.let { return it }
        val id = "device-${UUID.randomUUID()}"
        encryptedPrefs.edit { putString(KEY_DEVICE_USER_ID, id) }
        return id
    }

    private fun prefs(context: Context) =
        HeadUpPrefs.encryptedOrPrivate(context.applicationContext, PREFS_NAME)

    private fun android.content.SharedPreferences.Editor.putNullable(key: String, value: String?) {
        if (value.isNullOrBlank()) remove(key) else putString(key, value)
    }
}
