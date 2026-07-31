package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "posture_records",
    indices = [
        Index(value = ["timestampMs"]),
        Index(value = ["isSynced", "timestampMs"]),
    ],
)
data class PostureRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val timestampMs: Long,
    val durationMs: Long,
    val angleDegrees: Int,
    val rawAngleDegrees: Float,
    val neckFlexionDegrees: Int,
    val shoulderBalanceDegrees: Int,
    val screenDistanceCm: Int?,
    val landmarkConfidence: Float,
    val zone: String,
    val source: String,
    val isRapidFall: Boolean = false,
    val isSynced: Boolean = false,
    val syncedAtMs: Long? = null,
)

@Dao
interface PostureRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: PostureRecordEntity)

    @Query("SELECT * FROM posture_records WHERE timestampMs >= :fromMs AND timestampMs < :toMs ORDER BY timestampMs ASC")
    fun recordsBetween(fromMs: Long, toMs: Long): List<PostureRecordEntity>

    @Query("SELECT * FROM posture_records WHERE isSynced = 0 ORDER BY timestampMs ASC LIMIT :limit")
    fun unsyncedRecords(limit: Int): List<PostureRecordEntity>

    @Query("SELECT COUNT(*) FROM posture_records WHERE isSynced = 0")
    fun unsyncedCount(): Int

    @Query("UPDATE posture_records SET isSynced = 1, syncedAtMs = :syncedAtMs WHERE id IN (:ids)")
    fun markSynced(ids: List<Long>, syncedAtMs: Long)

    @Query("DELETE FROM posture_records")
    fun deleteAll()

    @Query("DELETE FROM posture_records WHERE timestampMs < :cutoffMs AND isSynced = 1")
    fun deleteOlderThan(cutoffMs: Long)
}

@Database(
    entities = [PostureRecordEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PostureDatabase : RoomDatabase() {
    abstract fun postureRecordDao(): PostureRecordDao

    companion object {
        private const val DATABASE_NAME = "headup-posture.db"

        @Volatile
        private var instance: PostureDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE posture_records ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-device'")
                db.execSQL("ALTER TABLE posture_records ADD COLUMN isRapidFall INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE posture_records ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE posture_records ADD COLUMN syncedAtMs INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_posture_records_isSynced_timestampMs ON posture_records(isSynced, timestampMs)")
            }
        }

        fun getInstance(context: Context): PostureDatabase = instance ?: synchronized(this) {
            instance ?: buildDatabase(context.applicationContext).also { instance = it }
        }

        fun databaseFile(context: Context) = context.applicationContext.getDatabasePath(DATABASE_NAME)

        private fun buildDatabase(context: Context): PostureDatabase =
            Room.databaseBuilder(context, PostureDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(HeadUpDatabasePassphrase.createSupportFactory(context, DATABASE_NAME))
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
