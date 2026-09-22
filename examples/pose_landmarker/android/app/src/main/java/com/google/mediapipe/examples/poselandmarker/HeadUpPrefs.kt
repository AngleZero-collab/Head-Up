package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object HeadUpPrefs {
    private const val TAG = "HeadUpPrefs"
    private const val FALLBACK_SUFFIX = "_private_fallback"
    private val encryptedUnavailable = mutableSetOf<String>()

    fun encryptedOrPrivate(context: Context, prefsName: String): SharedPreferences {
        val appContext = context.applicationContext
        if (!encryptedUnavailable.contains(prefsName)) {
            try {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    appContext,
                    prefsName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (error: Exception) {
                encryptedUnavailable += prefsName
                Log.w(TAG, "Encrypted preferences unavailable for $prefsName; using private fallback.", error)
            }
        }
        return appContext.getSharedPreferences("$prefsName$FALLBACK_SUFFIX", Context.MODE_PRIVATE)
    }
}
