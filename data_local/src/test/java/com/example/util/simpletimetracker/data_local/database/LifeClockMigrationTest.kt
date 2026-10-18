package com.example.util.simpletimetracker.data_local.database

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LifeClockMigrationTest {
    @Test fun unfinishedFocusMigrationPreservesOldShortExperiencesAsFormal() = withDatabase(39) { connection ->
        connection.execute("INSERT INTO records (id, type_id, time_started, time_ended, comment, tag_id) VALUES (9, 2, 1000, 481000, 'old short record', 0)")
        migrate(connection, 39, 40)
        assertEquals(listOf(listOf(1000, 481000, "old short record", 0, null)), connection.rows("SELECT time_started, time_ended, comment, is_unfinished_focus, unfinished_reason FROM records WHERE id = 9"))
        assertSchemaMatches(connection, 40)
    }

    @Test fun v32AddsGroupsWithoutChangingLegacyData() = withDatabase(38) { connection ->
        connection.execute("INSERT INTO recordTypes (id, name, icon, color, color_int, hidden, instant, instantDuration, note) VALUES (7, 'learning', 'old', 0, '', 0, 0, 0, 'legacy')")
        connection.execute("INSERT INTO lifeClockSettings (id, data_set_id, happiness_tag_id, meaning_tag_id) VALUES (1, 'dataset', 7, 8)")
        migrate(connection, 38, 39)
        assertEquals(listOf(listOf(7, "learning", "old", null, null)), connection.rows("SELECT id, name, icon, parent_type_id, preset_key FROM recordTypes"))
        assertEquals(listOf(listOf(0)), connection.rows("SELECT activity_defaults_version FROM lifeClockSettings"))
        assertSchemaMatches(connection, 39)
    }


    @Test
    fun v3AddsNullableFieldsWithoutChangingExistingTimesOrReviewState() = withDatabase(37) { connection ->
        connection.execute("INSERT INTO records (id, type_id, time_started, time_ended, comment, tag_id, review_state) VALUES (9, 2, 1000, 761123, 'old notes', 0, 0)")
        migrate(connection, 37, 38)
        assertEquals(listOf(listOf(1000, 761123, "old notes", 0, null)), connection.rows("SELECT time_started, time_ended, comment, review_state, recovery FROM records WHERE id = 9"))
        assertSchemaMatches(connection, 38)
    }

    @Test
    fun migrationPreservesLegacyDataAndOnlyConfirmsValidPastIntervals() = withDatabase(34) { connection ->
        val start = 1_700_000_000_123L
        val future = System.currentTimeMillis() + 86_400_000
        val beforeMidnight = Instant.parse("2023-11-14T15:55:00.123Z").toEpochMilli()
        val intervals = listOf(
            start to start + 900_000,
            start + 900_000 to start + 1_800_000, // Adjacent, not overlapping.
            start + 3_000_000 to start + 3_740_000, // Not a quarter hour.
            start + 5_000_000 to start + 5_900_000,
            start + 5_600_000 to start + 6_500_000, // Both overlapping records stay pending.
            start + 7_000_000 to start + 7_000_000,
            beforeMidnight to beforeMidnight + 900_000, // Crosses midnight in Asia/Shanghai.
            start + 9_000_000 to start + 9_900_000, // Unclassified still counts as recorded time.
            future to future + 900_000,
            start + 11_000_000 to start + 11_900_000, // Overlaps an ongoing timer.
        )
        intervals.forEachIndexed { index, (from, to) ->
            connection.execute(
                "INSERT INTO records (id, type_id, time_started, time_ended, comment, tag_id) VALUES (?, ?, ?, ?, ?, 0)",
                index + 1, if (index == 7) 0 else 2, from, to, "legacy $index",
            )
        }
        connection.execute(
            "INSERT INTO runningRecords (id, time_started, comment, tag_id) VALUES (2, ?, 'ongoing', 0)",
            start + 11_500_000,
        )
        connection.execute(
            "INSERT INTO recordToRecordTag (record_id, record_tag_id, record_tag_numeric_value) VALUES (1, 5, 4.0), (2, 5, NULL), (3, 6, 9.0)",
        )
        connection.execute(
            "INSERT INTO runningRecordToRecordTag (running_record_id, record_tag_id, record_tag_numeric_value) VALUES (2, 5, 3.0)",
        )
        connection.execute(
            "INSERT INTO recordTags (id, type_id, name, icon, color, color_int, icon_color_source, archived, note, value_type, value_suffix) " +
                "VALUES (5, 0, 'happiness', '', 0, '', 0, 0, 'legacy numeric tag', 1, NULL)",
        )
        val recordsBefore = connection.rows("SELECT id, type_id, time_started, time_ended, comment, tag_id FROM records ORDER BY id")
        val tagsBefore = connection.rows("SELECT * FROM recordToRecordTag ORDER BY record_id")
        val runningBefore = connection.rows("SELECT * FROM runningRecords")
        val tagDefinitionsBefore = connection.rows("SELECT * FROM recordTags")

        migrate(connection, 34, 35)

        assertEquals(recordsBefore, connection.rows("SELECT id, type_id, time_started, time_ended, comment, tag_id FROM records ORDER BY id"))
        assertEquals(tagsBefore, connection.rows("SELECT record_id, record_tag_id, record_tag_numeric_value FROM recordToRecordTag ORDER BY record_id"))
        assertEquals(runningBefore, connection.rows("SELECT id, time_started, comment, tag_id FROM runningRecords"))
        assertEquals(tagDefinitionsBefore, connection.rows("SELECT id, type_id, name, icon, color, color_int, icon_color_source, archived, note, value_type, value_suffix FROM recordTags"))
        assertEquals(listOf(listOf(0)), connection.rows("SELECT purpose FROM recordTags"))
        assertEquals(listOf(1, 1, 0, 0, 0, 0, 1, 1, 0, 0), connection.rows("SELECT review_state FROM records ORDER BY id").map { it.single() })
        connection.rows("SELECT summary, zone_id, template_id FROM records").forEach {
            assertEquals("", it[0])
            assertNull(it[1])
            assertNull(it[2])
        }
        assertEquals(listOf(listOf(null), listOf(null), listOf(null)), connection.rows("SELECT rated_at FROM recordToRecordTag"))
        assertEquals(listOf(listOf(null)), connection.rows("SELECT rated_at FROM runningRecordToRecordTag"))
        assertSchemaMatches(connection, 35)
    }

    @Test
    fun quarterHourInTheFutureStaysPendingWithoutAnOngoingTimer() = withDatabase(34) { connection ->
        val future = System.currentTimeMillis() + 86_400_000
        connection.execute(
            "INSERT INTO records (id, type_id, time_started, time_ended, comment, tag_id) VALUES (1, 2, ?, ?, '', 0)",
            future, future + 900_000,
        )

        migrate(connection, 34, 35)

        assertEquals(listOf(listOf(0)), connection.rows("SELECT review_state FROM records"))
    }

    @Test
    fun migrationTo36PreservesRecordsPhotosTagsAndShortcuts() = withDatabase(35) { connection ->
        connection.execute("INSERT INTO records (id, type_id, time_started, time_ended, comment, tag_id, summary, zone_id, template_id, review_state) VALUES (9, 2, 123, 900123, 'notes', 0, 'reading', 'Asia/Shanghai', 5, 1)")
        connection.execute("INSERT INTO recordShortcuts (id, type_id, comment, target_type, setting_action) VALUES (5, 2, 'tea first', 0, 0)")
        connection.execute("INSERT INTO recordPhotos (record_id, relative_path, sha256, position) VALUES (9, 'photos/a.jpg', ?, 0)", "a".repeat(64))
        connection.execute("INSERT INTO recordToRecordTag (record_id, record_tag_id, record_tag_numeric_value, rated_at) VALUES (9, 8, 4, 900124)")
        val records = connection.rows("SELECT * FROM records")
        val photos = connection.rows("SELECT * FROM recordPhotos")
        val tags = connection.rows("SELECT * FROM recordToRecordTag")
        val shortcuts = connection.rows("SELECT * FROM recordShortcuts")

        migrate(connection, 35)

        assertEquals(records, connection.rows("SELECT * FROM records"))
        assertEquals(photos, connection.rows("SELECT * FROM recordPhotos"))
        assertEquals(tags, connection.rows("SELECT * FROM recordToRecordTag"))
        assertEquals(shortcuts, connection.rows("SELECT id, type_id, comment, target_type, setting_action FROM recordShortcuts"))
        assertEquals(listOf(listOf("", "")), connection.rows("SELECT name, summary FROM recordShortcuts"))
        assertEquals(emptyList<List<Any?>>(), connection.rows("SELECT * FROM lifeClockSettings"))
        assertSchemaMatches(connection, 36)
    }

    @Test
    fun reviewReferencesKeepTextWhenTheirSourceIsDeleted() = withDatabase(35) { connection ->
        migrate(connection, 35)
        connection.execute("INSERT INTO records (id, type_id, time_started, time_ended, comment, tag_id) VALUES (9, 2, 123, 900123, '', 0)")
        connection.execute("INSERT INTO recordShortcuts (id, type_id, comment, target_type, setting_action) VALUES (5, 2, '', 0, 0)")
        val reviewText = "{\"keep\":{\"text\":\"keep reading\",\"recordId\":9},\"pinnedTemplateIds\":[5]}"
        val trialText = "{\"description\":\"try reading in the morning\",\"source\":{\"recordId\":9}}"
        connection.execute("INSERT INTO weeklyReviews (id, week_start, zone_id, content_json) VALUES (1, '2026-10-05', 'Asia/Shanghai', ?)", reviewText)
        connection.execute("INSERT INTO reviewTrials (id, review_id, content_json) VALUES (2, 1, ?)", trialText)
        assertThrows(SQLException::class.java) {
            connection.execute("INSERT INTO weeklyReviews (week_start, zone_id, content_json) VALUES ('2026-10-05', 'Asia/Shanghai', '{}')")
        }

        connection.execute("DELETE FROM records WHERE id = 9")
        connection.execute("DELETE FROM recordShortcuts WHERE id = 5")
        assertEquals(listOf(listOf(reviewText)), connection.rows("SELECT content_json FROM weeklyReviews"))
        connection.execute("DELETE FROM weeklyReviews WHERE id = 1")
        assertEquals(listOf(listOf(null, trialText)), connection.rows("SELECT review_id, content_json FROM reviewTrials"))
    }

    @Test
    fun fullMigrationChainMatchesCurrentRoomSchema() = withDatabase(6) { connection ->
        migrate(connection, 6)
        assertSchemaMatches(connection, 36)
    }

    @Test
    fun photoForeignKeysPreventOrphansAndDeleteOnlyTheirOwnersPhotos() = withDatabase(34) { connection ->
        migrate(connection, 34, 35)
        connection.execute("INSERT INTO records (id, type_id, time_started, time_ended, comment, tag_id) VALUES (1, 2, 0, 900000, '', 0)")
        connection.execute("INSERT INTO runningRecords (id, time_started, comment, tag_id) VALUES (2, 900000, '', 0)")
        val sql = "INSERT INTO recordPhotos (record_id, running_record_id, relative_path, sha256, position) VALUES (?, ?, 'photos/a.jpg', ?, 0)"
        assertThrows(SQLException::class.java) { connection.execute(sql, 99, null, "a".repeat(64)) }
        connection.execute(sql, 1, null, "a".repeat(64))
        connection.execute(sql, null, 2, "b".repeat(64))
        connection.execute("DELETE FROM records WHERE id = 1")
        assertEquals(listOf(listOf(2)), connection.rows("SELECT running_record_id FROM recordPhotos"))
        connection.execute("DELETE FROM runningRecords WHERE id = 2")
        assertEquals(emptyList<List<Any?>>(), connection.rows("SELECT * FROM recordPhotos"))
    }

    private fun withDatabase(version: Int, block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.execute("PRAGMA foreign_keys = ON")
            schema(version).getAsJsonArray("entities").forEach { element ->
                val entity = element.asJsonObject
                val tableName = entity.get("tableName").asString
                connection.execute(entity.get("createSql").asString.replace("\${TABLE_NAME}", tableName))
                entity.getAsJsonArray("indices").forEach {
                    connection.execute(it.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", tableName))
                }
            }
            block(connection)
        }
    }

    @Test
    fun migrationTo37AddsOnlyTheRestoreCommitMarker() = withDatabase(36) { connection ->
        connection.execute("INSERT INTO lifeClockSettings (id, data_set_id, happiness_tag_id, meaning_tag_id) VALUES (1, 'dataset', 7, 8)")
        migrate(connection, 36, 37)
        assertEquals(listOf(listOf(1, "dataset", 7, 8, null)), connection.rows("SELECT * FROM lifeClockSettings"))
        assertSchemaMatches(connection, 37)
    }

    private fun migrate(connection: Connection, fromVersion: Int, toVersion: Int = 36) {
        val database = mock<SupportSQLiteDatabase>()
        doAnswer {
            connection.execute(it.getArgument<String>(0))
            null
        }.whenever(database).execSQL(any<String>())
        doAnswer {
            connection.execute(it.getArgument<String>(0), *it.getArgument<Array<Any?>>(1))
            null
        }.whenever(database).execSQL(any<String>(), any<Array<Any?>>())
        AppDatabaseMigrations.migrations.filter { it.startVersion >= fromVersion && it.endVersion <= toVersion }
            .forEach { it.migrate(database) }
    }

    private fun assertSchemaMatches(connection: Connection, version: Int) {
        schema(version).getAsJsonArray("entities").forEach { element ->
            val entity = element.asJsonObject
            val tableName = entity.get("tableName").asString
            val primaryKeys = entity.getAsJsonObject("primaryKey").getAsJsonArray("columnNames").map { it.asString }
            val expectedColumns = entity.getAsJsonArray("fields").associate { fieldElement ->
                val field = fieldElement.asJsonObject
                val name = field.get("columnName").asString
                name to listOf(field.get("affinity").asString, if (field.get("notNull").asBoolean) 1 else 0, primaryKeys.indexOf(name) + 1)
            }
            val actualColumns = connection.rows("PRAGMA table_info('$tableName')").associate { it[1] to listOf(it[2], it[3], it[5]) }
            assertEquals("Columns of $tableName", expectedColumns, actualColumns)
            entity.getAsJsonArray("fields").forEach { fieldElement ->
                val field = fieldElement.asJsonObject
                field.get("defaultValue")?.let { default ->
                    val column = connection.rows("PRAGMA table_info('$tableName')").single { it[1] == field.get("columnName").asString }
                    assertEquals("Default of $tableName.${column[1]}", default.asString, column[4])
                }
            }
            val expectedIndices = entity.getAsJsonArray("indices").map { indexElement ->
                val index = indexElement.asJsonObject
                listOf(index.get("name").asString, if (index.get("unique").asBoolean) 1 else 0, index.getAsJsonArray("columnNames").map { it.asString })
            }.toSet()
            val actualIndices = connection.rows("PRAGMA index_list('$tableName')").filter { it[3] == "c" }.map {
                listOf(it[1], it[2], connection.rows("PRAGMA index_info('${it[1]}')").map { column -> column[2] })
            }.toSet()
            assertEquals("Indices of $tableName", expectedIndices, actualIndices)
            val expectedForeignKeys = entity.getAsJsonArray("foreignKeys").map { keyElement ->
                val key = keyElement.asJsonObject
                listOf(key.get("table").asString, key.getAsJsonArray("columns").single().asString, key.getAsJsonArray("referencedColumns").single().asString, key.get("onUpdate").asString, key.get("onDelete").asString)
            }.toSet()
            val actualForeignKeys = connection.rows("PRAGMA foreign_key_list('$tableName')").map { listOf(it[2], it[3], it[4], it[5], it[6]) }.toSet()
            assertEquals("Foreign keys of $tableName", expectedForeignKeys, actualForeignKeys)
        }
    }

    private fun schema(version: Int): JsonObject = JsonParser.parseString(
        File("schemas/com.example.util.simpletimetracker.data_local.database.AppDatabase/$version.json").readText(),
    ).asJsonObject.getAsJsonObject("database")

    private fun Connection.execute(sql: String, vararg args: Any?) {
        prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.execute()
        }
    }

    private fun Connection.rows(sql: String): List<List<Any?>> = createStatement().use { statement ->
        statement.executeQuery(sql).use { result ->
            buildList {
                while (result.next()) add((1..result.metaData.columnCount).map { result.getObject(it) })
            }
        }
    }
}
