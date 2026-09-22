package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostureDatabaseMigrationTest {
    @Test
    fun migrationTwoToThreePreservesPostureRecordsAndCreatesAggregateTables() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-2-3-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE posture_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL, timestampMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, angleDegrees INTEGER NOT NULL, rawAngleDegrees REAL NOT NULL, neckFlexionDegrees INTEGER NOT NULL, shoulderBalanceDegrees INTEGER NOT NULL, screenDistanceCm INTEGER, landmarkConfidence REAL NOT NULL, zone TEXT NOT NULL, source TEXT NOT NULL, isRapidFall INTEGER NOT NULL, isSynced INTEGER NOT NULL, syncedAtMs INTEGER)")
            db.execSQL("INSERT INTO posture_records (userId,timestampMs,durationMs,angleDegrees,rawAngleDegrees,neckFlexionDegrees,shoulderBalanceDegrees,screenDistanceCm,landmarkConfidence,zone,source,isRapidFall,isSynced,syncedAtMs) VALUES ('legacy-user',1000,1000,8,8.0,4,1,35,0.9,'SAFE','background',0,0,NULL)")

            PostureDatabase.MIGRATION_2_3.migrate(db)

            db.query("SELECT userId, angleDegrees FROM posture_records").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy-user", cursor.getString(0))
                assertEquals(8, cursor.getInt(1))
            }
            val expectedTables = setOf(
                "monitoring_sessions",
                "posture_windows",
                "daily_posture_aggregates",
                "reminder_events",
                "education_profiles",
                "schools",
                "challenge_enrollments",
                "leaderboard_cache",
                "sync_queue",
            )
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                val tables = mutableSetOf<String>()
                while (cursor.moveToNext()) tables += cursor.getString(0)
                assertTrue(tables.containsAll(expectedTables))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}

