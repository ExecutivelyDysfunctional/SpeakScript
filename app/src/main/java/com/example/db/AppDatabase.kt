package com.example.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Transcription::class, SpeakerProfile::class, LocationProfile::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun speakerDao(): SpeakerDao
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transcriptions ADD COLUMN drive_file_id TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transcriptions ADD COLUMN location_name TEXT")
                database.execSQL("ALTER TABLE transcriptions ADD COLUMN active_speakers_csv TEXT")
                database.execSQL("ALTER TABLE transcriptions ADD COLUMN mentioned_people_csv TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS speaker_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        relationship_or_role TEXT,
                        avatar_uri TEXT,
                        color_hex TEXT,
                        golden_sample_audio_uri TEXT,
                        golden_sample_start_ms INTEGER,
                        golden_sample_end_ms INTEGER,
                        golden_sample_recording_title TEXT,
                        total_recordings_count INTEGER NOT NULL DEFAULT 0,
                        last_heard_timestamp INTEGER,
                        notes TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS location_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        address TEXT,
                        latitude REAL,
                        longitude REAL,
                        tier TEXT NOT NULL,
                        notes TEXT,
                        visit_count INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("ALTER TABLE transcriptions ADD COLUMN diarization_confidence TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "transcriptions_database"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
