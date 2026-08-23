package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
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
        val prefs = securePrefs(context)
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
        if (databaseOpensWithPassphrase(databaseFile, passphrase)) {
            prefs.edit { putBoolean(KEY_DATABASE_ENCRYPTED, true) }
            return
        }

        val encryptedFile = File(context.cacheDir, "$databaseName.encrypted")
        encryptedFile.delete()
        var plaintextDatabase: SQLiteDatabase? = null
        try {
            plaintextDatabase = SQLiteDatabase.openDatabase(
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
            plaintextDatabase = null

            databaseFile.delete()
            File("${databaseFile.absolutePath}-wal").delete()
            File("${databaseFile.absolutePath}-shm").delete()
            check(encryptedFile.renameTo(databaseFile)) { "Unable to replace plaintext database" }
            prefs.edit { putBoolean(KEY_DATABASE_ENCRYPTED, true) }
        } catch (error: Exception) {
            encryptedFile.delete()
            Log.w(TAG, "Plaintext database migration failed; backing up legacy database.", error)
            backupAndResetLegacyDatabase(context, databaseFile, databaseName, prefs)
        } finally {
            try {
                plaintextDatabase?.close()
            } catch (_: Exception) {
                Unit
            }
        }
    }

    private fun databaseOpensWithPassphrase(databaseFile: File, passphrase: String): Boolean {
        var database: SQLiteDatabase? = null
        return try {
            database = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            database.rawQuery("PRAGMA user_version", emptyArray()).use { it.moveToFirst() }
            true
        } catch (_: Exception) {
            false
        } finally {
            try {
                database?.close()
            } catch (_: Exception) {
                Unit
            }
        }
    }

    private fun backupAndResetLegacyDatabase(
        context: Context,
        databaseFile: File,
        databaseName: String,
        prefs: SharedPreferences,
    ) {
        val backupFile = File(context.noBackupFilesDir, "$databaseName.legacy-${System.currentTimeMillis()}.db")
        backupFile.parentFile?.mkdirs()
        val moved = databaseFile.renameTo(backupFile)
        val removed = if (moved) {
            true
        } else {
            try {
                databaseFile.copyTo(backupFile, overwrite = true)
                databaseFile.delete()
            } catch (error: Exception) {
                Log.e(TAG, "Unable to back up incompatible posture database.", error)
                false
            }
        }

        if (removed || !databaseFile.exists()) {
            deleteSidecars(databaseFile)
            prefs.edit { putBoolean(KEY_DATABASE_ENCRYPTED, true) }
            Log.w(TAG, "Legacy posture database moved to ${backupFile.absolutePath}")
        }
    }

    private fun deleteSidecars(databaseFile: File) {
        File("${databaseFile.absolutePath}-wal").delete()
        File("${databaseFile.absolutePath}-shm").delete()
    }

    private fun securePrefs(context: Context): SharedPreferences =
        HeadUpPrefs.encryptedOrPrivate(context.applicationContext, PREFS_NAME)

    private fun String.sqlEscaped(): String = replace("'", "''")

    private fun File.sqlEscapedPath(): String = absolutePath.sqlEscaped()
}
