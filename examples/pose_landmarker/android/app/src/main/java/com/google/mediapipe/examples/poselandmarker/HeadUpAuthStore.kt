package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

object HeadUpAuthStore {
    private const val PREFS_NAME = "headup_auth_store"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_DEVICE_USER_ID = "device_user_id"

    fun saveSession(context: Context, accessToken: String, userId: String) {
        prefs(context).edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_USER_ID, userId)
        }
    }

    fun startGuestSession(context: Context): String {
        val guestId = getOrCreateDeviceUserId(context)
        prefs(context).edit {
            remove(KEY_ACCESS_TOKEN)
            putString(KEY_USER_ID, guestId)
        }
        return guestId
    }

    fun clearSession(context: Context) {
        prefs(context).edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_USER_ID)
        }
    }

    fun accessToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun currentUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, null) ?: getOrCreateDeviceUserId(context)

    fun isSignedIn(context: Context): Boolean =
        !accessToken(context).isNullOrBlank()

    fun userLabel(context: Context): String =
        if (isSignedIn(context)) currentUserId(context) else "Guest ${getOrCreateDeviceUserId(context).takeLast(6)}"

    private fun getOrCreateDeviceUserId(context: Context): String {
        val encryptedPrefs = prefs(context)
        encryptedPrefs.getString(KEY_DEVICE_USER_ID, null)?.let { return it }
        val id = "device-${UUID.randomUUID()}"
        encryptedPrefs.edit { putString(KEY_DEVICE_USER_ID, id) }
        return id
    }

    private fun prefs(context: Context) =
        HeadUpPrefs.encryptedOrPrivate(context.applicationContext, PREFS_NAME)
}
