package com.example.util.simpletimetracker

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.database.AppDatabaseMigrations
import java.io.IOException
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDatabaseName = "migration-test"

    @Test fun migrateUnfinishedFocusKeepsOldShortRecordsAndPhotos() {
        helper.createDatabase(testDatabaseName, 39).use { database ->
            database.execSQL("INSERT INTO records (id, type_id, time_started, time_ended, comment, tag_id) VALUES (1, 0, 1000, 481000, 'old short', 0)")
            database.execSQL("INSERT INTO recordPhotos (record_id, relative_path, sha256, position) VALUES (1, 'photos/old.jpg', '${"a".repeat(64)}', 0)")
        }
        helper.runMigrationsAndValidate(testDatabaseName, 40, true, *AppDatabaseMigrations.migrations.toTypedArray()).use { database ->
            database.query("SELECT time_started, time_ended, comment, is_unfinished_focus, unfinished_reason FROM records").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1000L, cursor.getLong(0))
                assertEquals(481000L, cursor.getLong(1))
                assertEquals("old short", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertTrue(cursor.isNull(4))
            }
            database.query("SELECT record_id FROM recordPhotos").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }
        }
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateLifeClockDataPreservesLegacyTimeAndScores() {
        helper.createDatabase(testDatabaseName, 34).use { database ->
            database.execSQL("INSERT INTO records (id, type_id, time_started, time_ended, comment, tag_id) VALUES (1, 0, 1700000000123, 1700000900123, 'legacy', 0)")
            database.execSQL("INSERT INTO recordToRecordTag (record_id, record_tag_id, record_tag_numeric_value) VALUES (1, 5, 4.0), (1, 6, NULL)")
        }
        helper.runMigrationsAndValidate(testDatabaseName, 35, true, *AppDatabaseMigrations.migrations.toTypedArray()).use { database ->
            database.query("SELECT time_started, time_ended, comment, review_state, zone_id FROM records WHERE id = 1").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(1_700_000_000_123, cursor.getLong(0))
                assertEquals(1_700_000_900_123, cursor.getLong(1))
                assertEquals("legacy", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(true, cursor.isNull(4))
            }
            database.query("SELECT record_tag_numeric_value, rated_at FROM recordToRecordTag ORDER BY record_tag_id").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(4.0, cursor.getDouble(0), 0.0)
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.moveToNext())
                assertEquals(true, cursor.isNull(0))
                assertEquals(true, cursor.isNull(1))
            }
        }
    }

    @Test
    fun migrateReviewStorageKeepsExistingTemplateData() {
        helper.createDatabase(testDatabaseName, 35).use { database ->
            database.execSQL("INSERT INTO recordShortcuts (id, type_id, comment, target_type, setting_action) VALUES (5, 2, 'tea first', 0, 0)")
        }
        helper.runMigrationsAndValidate(testDatabaseName, 36, true, *AppDatabaseMigrations.migrations.toTypedArray()).use { database ->
            database.query("SELECT id, comment, name, summary FROM recordShortcuts WHERE id = 5").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(5L, cursor.getLong(0))
                assertEquals("tea first", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals("", cursor.getString(3))
            }
        }
    }

    @Test
    fun migrateRestoreMarkerPreservesDatasetIdentity() {
        helper.createDatabase(testDatabaseName, 36).use { database ->
            database.execSQL("INSERT INTO lifeClockSettings (id, data_set_id, happiness_tag_id, meaning_tag_id) VALUES (1, 'dataset', 7, 8)")
        }
        helper.runMigrationsAndValidate(testDatabaseName, 37, true, *AppDatabaseMigrations.migrations.toTypedArray()).use { database ->
            database.query("SELECT data_set_id, happiness_tag_id, meaning_tag_id, restore_operation_id FROM lifeClockSettings").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("dataset", cursor.getString(0))
                assertEquals(7L, cursor.getLong(1))
                assertEquals(8L, cursor.getLong(2))
                assertEquals(true, cursor.isNull(3))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create earliest version of the database.
        helper.createDatabase(testDatabaseName, 6)
            .apply { close() }

        // Open latest version of the database. Room validates the schema
        // once all migrations execute.
        Room
            .databaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                AppDatabase::class.java,
                testDatabaseName,
            )
            .addMigrations(
                *AppDatabaseMigrations.migrations.toTypedArray(),
            )
            .build()
            .apply { openHelper.writableDatabase.close() }
    }
}
