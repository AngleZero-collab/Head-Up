package com.google.mediapipe.examples.poselandmarker

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostureDatabaseDeviceInspectionTest {
    @Test
    fun logDatabaseSummaryAndRecentPostureRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = PostureDatabase.getInstance(context)
        val sqlite = database.openHelper.readableDatabase

        sqlite.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { tables ->
            while (tables.moveToNext()) {
                val table = tables.getString(0)
                sqlite.query("SELECT COUNT(*) FROM `$table`").use { count ->
                    if (count.moveToFirst()) Log.i(TAG, "table=$table rows=${count.getLong(0)}")
                }
            }
        }

        sqlite.query(
            "SELECT id, timestampMs, parallaxCosineRatio, angularVelocity, isStable, " +
                "isFeatureSynced FROM posture_records ORDER BY timestampMs DESC LIMIT 10",
        ).use { records ->
            while (records.moveToNext()) {
                Log.i(
                    TAG,
                    "record id=${records.getLong(0)} timestampMs=${records.getLong(1)} " +
                        "ratio=${records.getFloat(2)} velocity=${records.getFloat(3)} " +
                        "stable=${records.getInt(4) != 0} synced=${records.getInt(5) != 0}",
                )
            }
        }
        sqlite.query("SELECT COUNT(*) FROM posture_records WHERE isFeatureSynced = 0").use { pending ->
            if (pending.moveToFirst()) Log.i(TAG, "pendingFeatureRecords=${pending.getLong(0)}")
        }
    }

    companion object {
        const val TAG = "HeadUpDbInspection"
    }
}
