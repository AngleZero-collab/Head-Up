package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File
import java.security.SecureRandom

object HeadUpDatabasePassphrase {
    private const val TAG = "HeadUpDatabaseSecret"
    private const val PREFS_NAME = "headup_database_secret"
    private const val KEY_SQLCIPHER_PASSPHRASE = "sqlcipher_passphrase"
    private const val KEY_DATABASE_ENCRYPTED = "database_encrypted"
    private const val PASSPHRASE_BYTES = 32

    fun createSupportFactory(context: Context, databaseName: String): SupportFactory {
        val appContext = context.applicationContext
        SQLiteDatabase.loadLibs(appContext)
        val passphrase = getOrCreatePassphrase(appContext)
        migratePlaintextDatabaseIfNeeded(appContext, databaseName, passphrase)
        val passphraseBytes = SQLiteDatabase.getBytes(passphrase.toCharArray())
        return SupportFactory(passphraseBytes)
    }

    private fun getOrCreatePassphrase(context: Context): String {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        prefs.getString(KEY_SQLCIPHER_PASSPHRASE, null)?.let { return it }

        val bytes = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(bytes)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        prefs.edit { putString(KEY_SQLCIPHER_PASSPHRASE, encoded) }
        return encoded
    }

    private fun migratePlaintextDatabaseIfNeeded(
        context: Context,
        databaseName: String,
        passphrase: String,
    ) {
        val prefs = securePrefs(context)
        if (prefs.getBoolean(KEY_DATABASE_ENCRYPTED, false)) return

        val databaseFile = context.getDatabasePath(databaseName)
        if (!databaseFile.exists()) {
            prefs.edit { putBoolean(KEY_DATABASE_ENCRYPTED, true) }
            return
        }

        val encryptedFile = File(context.cacheDir, "$databaseName.encrypted")
        encryptedFile.delete()
        try {
            val plaintextDatabase = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                "",
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            plaintextDatabase.rawExecSQL(
                "ATTACH DATABASE '${encryptedFile.sqlEscapedPath()}' AS encrypted KEY '${passphrase.sqlEscaped()}'",
            )
            plaintextDatabase.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plaintextDatabase.rawExecSQL("DETACH DATABASE encrypted")
            plaintextDatabase.close()

            databaseFile.delete()
            File("${databaseFile.absolutePath}-wal").delete()
            File("${databaseFile.absolutePath}-shm").delete()
            check(encryptedFile.renameTo(databaseFile)) { "Unable to replace plaintext database" }
            prefs.edit { putBoolean(KEY_DATABASE_ENCRYPTED, true) }
        } catch (error: Exception) {
            encryptedFile.delete()
            Log.w(TAG, "Plaintext database migration was skipped", error)
        }
    }

    private fun securePrefs(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun String.sqlEscaped(): String = replace("'", "''")

    private fun File.sqlEscapedPath(): String = absolutePath.sqlEscaped()
}
