package com.example.util.simpletimetracker

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.record.RecordDBO
import com.example.util.simpletimetracker.data_local.record.RunningRecordDBO
import com.example.util.simpletimetracker.data_local.recordPhoto.RecordPhotoDBO
import com.example.util.simpletimetracker.data_local.recordTag.RecordTagDBO
import com.example.util.simpletimetracker.data_local.recordTag.RecordToRecordTagDBO
import com.example.util.simpletimetracker.data_local.recordType.RecordTypeDBO
import com.example.util.simpletimetracker.data_local.snapshot.SnapshotCacheResetter
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import com.example.util.simpletimetracker.domain.backup.repo.FullBackupRepo
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import androidx.room.withTransaction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockFullBackupTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var backup: FullBackupRepo
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var prefs: SharedPreferences
    @Inject lateinit var identity: LifeClockSettingsRepo
    @Inject lateinit var caches: SnapshotCacheResetter
    @Inject lateinit var records: RecordRepo
    @Inject lateinit var gate: DataRestoreGate
    @Inject lateinit var mutations: RecordMutationRepo
    @Inject lateinit var planning: com.example.util.simpletimetracker.domain.planning.PlanningRepo
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val created = mutableListOf<File>()
    private val start = 1_700_000_000_123L

    @Before fun before() = runBlocking {
        hilt.inject()
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS m6_fail_record")
        }
        caches.reset()
        prefs.edit().clear().commit()
        File(context.filesDir, "restore-journal.json").delete()
        gate.end()
    }
    @After fun after() {
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS m6_fail_record")
        created.forEach(File::delete)
        gate.end()
    }

    @Test fun exportReleaseMigrationFixture() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("releaseFixture") == "true")
        seed()
        val monday = 1_791_158_400_000L // 2026-10-05 08:00 Asia/Shanghai.
        val record = requireNotNull(database.recordDao().get(10)).record
        database.recordDao().insert(record.copy(timeStarted = monday, timeEnded = monday + 900_000))
        val now = System.currentTimeMillis()
        database.runningRecordDao().insert(RunningRecordDBO(id = 2, timeStarted = now - 120_123, comment = "migration ongoing", tagId = 0, summary = "walking"))
        database.recordPhotoDao().upsert(database.recordPhotoDao().getAll().single().copy(id = 14, recordId = null, runningRecordId = 2))
        prefs.edit().putBoolean("enablePomodoroMode", true).putBoolean("showGoalsSeparately", true).commit()
        caches.reset()
        val archive = File(context.cacheDir, "m8-migration.zip")
        backup.export(Uri.fromFile(archive).toString())
        val preview = backup.prepareRestore(Uri.fromFile(archive).toString())
        assertEquals(1, preview.recordCount)
        assertEquals(1, preview.runningCount)
        assertEquals(2, preview.photoCount)
        assertEquals(1, preview.reviewCount)
        backup.discard(preview.token)
    }

    @Test fun completeRoundTripKeepsIdsNullScoresPhotosAndBusinessSettingsAndRefreshesCache() = runBlocking {
        seed()
        val archive = temp("zip")
        backup.export(Uri.fromFile(archive).toString())
        val original = manifest(archive)
        val cached = records.get(10)
        assertEquals("reading", cached?.summary)
        database.recordDao().insert(requireNotNull(database.recordDao().get(10)).record.copy(summary = "changed"))
        prefs.edit().putBoolean("showSeconds", false).commit()
        val preview = backup.prepareRestore(Uri.fromFile(archive).toString())
        assertEquals(1, preview.photoCount)
        backup.restore(preview.token)
        assertEquals("reading", records.get(10)?.summary)
        assertTrue(prefs.getBoolean("showSeconds", false))
        val scores = requireNotNull(records.get(10)).tags
        val meta = identity.getOrCreate()
        assertEquals(4.0, scores.single { it.tagId == meta.happinessTagId }.numericValue)
        assertNull(scores.single { it.tagId == meta.meaningTagId }.numericValue)
        val photo = database.recordPhotoDao().getAll().single()
        assertEquals(13L, photo.id)
        assertTrue(File(context.filesDir, photo.relativePath).isFile)
        assertTrue(backup.hasRecoveryCopy())
        val second = temp("zip")
        backup.export(Uri.fromFile(second).toString())
        val restored = manifest(second)
        listOf("records", "recordTypes", "categories", "tags", "photos", "ratingTagIds", "templates", "weeklyReviews", "trials", "businessSettings", "upstreamData").forEach {
            assertEquals(it, original.get(it), restored.get(it))
        }
        assertFalse(original.getAsJsonObject("businessSettings").has("automaticBackupUri"))
        assertEquals("private-device-uri", prefs.getString("automaticBackupUri", ""))
    }

    @Test fun v3ZipRestoresNegativeScoreRecoveryMovedPlanAndPhotoTogether() = runBlocking {
        seed()
        val meta = identity.getOrCreate()
        val row = requireNotNull(database.recordDao().get(10)).record
        database.recordDao().insert(row.copy(timeEnded = start + 760_000, recovery = "MORE_TIRED"))
        database.recordToRecordTagDao().insert(listOf(RecordToRecordTagDBO(10, meta.meaningTagId, -3.0, start + 1)))
        val plan = com.example.util.simpletimetracker.domain.planning.PlanSchedule(title = "reading", firstDate = "2026-10-12",
            startMinute = 540, durationMinutes = 30, repeat = com.example.util.simpletimetracker.domain.planning.PlanRepeat.DAILY)
        planning.save(plan)
        planning.save(com.example.util.simpletimetracker.domain.planning.PlanException(plan.id, "2026-10-13", movedDate = "2026-11-01",
            title = "morning reading", startMinute = 420, remind = true))
        planning.save(com.example.util.simpletimetracker.domain.planning.PlanRecordLink(recordId = 10, occurrenceId = "${plan.id}@2026-10-13",
            caption = "reading", timeStarted = start, timeEnded = start + 760_000))
        caches.reset()
        val archive = temp("zip")
        backup.export(Uri.fromFile(archive).toString())
        val original = manifest(archive)
        assertEquals(2, original.get("schemaVersion").asInt)
        planning.clear()
        database.recordDao().insert(row.copy(summary = "changed"))
        val preview = backup.prepareRestore(Uri.fromFile(archive).toString())
        backup.restore(preview.token)
        val restored = requireNotNull(records.get(10))
        assertEquals(760_000L, restored.timeEnded - restored.timeStarted)
        assertEquals(com.example.util.simpletimetracker.domain.rhythm.Recovery.MORE_TIRED, restored.recovery)
        assertEquals(-3.0, restored.tags.single { it.tagId == meta.meaningTagId }.numericValue)
        assertEquals("2026-11-01", planning.exceptions().single().movedDate)
        assertEquals("${plan.id}@2026-10-13", planning.links().single().occurrenceId)
        val photo = database.recordPhotoDao().getAll().single()
        assertTrue(File(context.filesDir, photo.relativePath).isFile)
        val second = temp("zip")
        backup.export(Uri.fromFile(second).toString())
        val after = manifest(second)
        listOf("records", "photos", "planSchedules", "planExceptions", "planRecordLinks").forEach {
            assertEquals(it, original.get(it), after.get(it))
        }
        if (InstrumentationRegistry.getArguments().getString("transferFixture") == "export") {
            backup.export(Uri.fromFile(File(context.getExternalFilesDir(null), "v3-transfer.zip")).toString())
        }
    }

    @Test fun restoreTransferredV3Fixture() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("transferFixture") == "restore")
        val input = File(context.getExternalFilesDir(null), "v3-transfer.zip")
        val original = manifest(input)
        val preview = backup.prepareRestore(Uri.fromFile(input).toString())
        backup.restore(preview.token)
        val row = requireNotNull(records.get(10))
        assertEquals(760_000L, row.duration)
        assertEquals(com.example.util.simpletimetracker.domain.rhythm.Recovery.MORE_TIRED, row.recovery)
        assertEquals(-3.0, row.tags.single { it.tagId == identity.getOrCreate().meaningTagId }.numericValue)
        assertEquals("2026-11-01", planning.exceptions().single().movedDate)
        val photo = database.recordPhotoDao().getAll().single()
        assertTrue(File(context.filesDir, photo.relativePath).isFile)
        val output = File(context.getExternalFilesDir(null), "v3-transfer-restored.zip")
        backup.export(Uri.fromFile(output).toString())
        val restored = manifest(output)
        listOf("dataSetId", "records", "photos", "recordTypes", "ratingTagIds", "templates", "weeklyReviews", "trials",
            "businessSettings", "planSchedules", "planExceptions", "planRecordLinks").forEach {
            assertEquals(it, original.get(it), restored.get(it))
        }
    }

    @Test fun corruptedPhotoAndTraversalDoNotModifyCurrentData() = runBlocking {
        seed()
        val archive = temp("zip")
        backup.export(Uri.fromFile(archive).toString())
        val before = requireNotNull(records.get(10))
        val entries = entries(archive)
        val photoPath = entries.keys.first { it.startsWith("photos/") }
        val corrupt = temp("zip")
        writeZip(corrupt, entries + (photoPath to byteArrayOf(1, 2, 3)))
        assertTrue(runCatching { backup.prepareRestore(Uri.fromFile(corrupt).toString()) }.isFailure)
        val root = manifest(archive)
        root.getAsJsonArray("photos")[0].asJsonObject.addProperty("relativePath", "photos/../../outside.png")
        val traversal = temp("zip")
        writeZip(traversal, entries + ("snapshot.json" to root.toString().toByteArray()))
        assertTrue(runCatching { backup.prepareRestore(Uri.fromFile(traversal).toString()) }.isFailure)
        assertEquals(before, records.get(10))
        assertEquals(1, database.recordPhotoDao().getAll().size)
    }

    @Test fun databaseFailureRollsBackRecordsPhotosAndPreferences() = runBlocking {
        seed()
        val archive = temp("zip")
        backup.export(Uri.fromFile(archive).toString())
        database.recordDao().insert(requireNotNull(database.recordDao().get(10)).record.copy(summary = "keep current"))
        caches.reset()
        prefs.edit().putBoolean("showSeconds", false).commit()
        val preview = backup.prepareRestore(Uri.fromFile(archive).toString())
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER m6_fail_record BEFORE INSERT ON records WHEN NEW.summary = 'reading' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertTrue(runCatching { backup.restore(preview.token) }.isFailure)
        assertEquals("keep current", records.get(10)?.summary)
        assertFalse(prefs.getBoolean("showSeconds", true))
        assertEquals(1, database.recordPhotoDao().getAll().size)
        assertFalse(File(context.filesDir, "restore-journal.json").exists())
    }

    @Test fun interruptedRestoreRollsBackPreferencesUnlessDatabaseCommitMarkerMatches() = runBlocking {
        seed()
        val operation = UUID.randomUUID().toString()
        val journal = File(context.filesDir, "restore-journal.json")
        val saved = JsonObject().apply {
            addProperty("operationId", operation)
            add("oldPreferences", JsonObject().apply { addProperty("showSeconds", true) })
        }
        prefs.edit().putBoolean("showSeconds", false).commit()
        journal.writeText(saved.toString())
        backup.recoverInterruptedRestore()
        assertTrue(prefs.getBoolean("showSeconds", false))
        database.openHelper.writableDatabase.execSQL("UPDATE lifeClockSettings SET restore_operation_id = ? WHERE id = 1", arrayOf(operation))
        prefs.edit().putBoolean("showSeconds", false).commit()
        journal.writeText(saved.toString())
        backup.recoverInterruptedRestore()
        assertFalse(prefs.getBoolean("showSeconds", true))
        assertFalse(journal.exists())
    }

    @Test fun ongoingRecordRestoresOnlyToSnapshotTimeWithItsPhoto() = runBlocking {
        seed()
        val now = System.currentTimeMillis()
        database.runningRecordDao().insert(RunningRecordDBO(id = 2, timeStarted = now - 120_123, comment = "ongoing", tagId = 0, summary = "walking"))
        val oldPhoto = database.recordPhotoDao().getAll().single()
        database.recordPhotoDao().upsert(oldPhoto.copy(id = 14, recordId = null, runningRecordId = 2))
        val archive = temp("zip")
        backup.export(Uri.fromFile(archive).toString())
        val root = manifest(archive)
        val cutoff = root.get("createdAt").asLong
        val preview = backup.prepareRestore(Uri.fromFile(archive).toString())
        assertEquals(1, preview.runningCount)
        backup.restore(preview.token)
        assertTrue(database.runningRecordDao().getAll().isEmpty())
        val ended = database.recordDao().getAll().map { it.record }.single { it.summary == "walking" }
        assertEquals(cutoff, ended.timeEnded)
        assertEquals(now - 120_123, ended.timeStarted)
        assertEquals(ended.id, database.recordPhotoDao().getAll().single { it.id == 14L }.recordId)
    }

    @Test fun restoreBlocksWritesAndRejectsAWriteQueuedBeforeTheDatasetChanged() = runBlocking {
        seed()
        val proposal = Record(typeId = 1, timeStarted = start + 900_000, timeEnded = start + 1_800_000, comment = "new", tags = emptyList())
        gate.begin()
        val blocked = runCatching { mutations.save(listOf(proposal)) }.exceptionOrNull()
        assertEquals(RecordMutationError.DATA_RESTORING, (blocked as RecordMutationException).reason)
        gate.end()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transaction = async {
            database.withTransaction {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val queued = async(start = CoroutineStart.UNDISPATCHED) { runCatching { mutations.save(listOf(proposal)) }.exceptionOrNull() }
        gate.begin()
        gate.committed()
        gate.end()
        release.complete(Unit)
        transaction.await()
        assertEquals(RecordMutationError.DATA_RESTORING, (queued.await() as RecordMutationException).reason)
        assertEquals(1, database.recordDao().getAll().size)
    }

    @Test fun zeroDurationOngoingSnapshotKeepsAttachmentAsPendingRepairAndNeverResumes() = runBlocking {
        seed()
        val now = System.currentTimeMillis()
        database.runningRecordDao().insert(RunningRecordDBO(id = 2, timeStarted = now, comment = "zero", tagId = 0, summary = "zero-start"))
        database.recordPhotoDao().upsert(database.recordPhotoDao().getAll().single().copy(id = 14, recordId = null, runningRecordId = 2))
        val archive = temp("zip")
        backup.export(Uri.fromFile(archive).toString())
        val entries = entries(archive)
        val root = manifest(archive).apply { addProperty("createdAt", now) }
        val zero = temp("zip")
        writeZip(zero, entries + ("snapshot.json" to root.toString().toByteArray()))
        val preview = backup.prepareRestore(Uri.fromFile(zero).toString())
        assertEquals(1, preview.repairCount)
        backup.restore(preview.token)
        val saved = database.recordDao().getAll().map { it.record }.single { it.summary == "zero-start" }
        assertEquals(now, saved.timeStarted)
        assertEquals(now, saved.timeEnded)
        assertEquals(0, saved.reviewState)
        assertTrue(database.runningRecordDao().getAll().isEmpty())
        assertEquals(saved.id, database.recordPhotoDao().getAll().single { it.id == 14L }.recordId)
    }

    @Test fun preferenceCommitFailureRollsBackTheDatabaseAndRestoresOriginalSettings() = runBlocking {
        seed()
        val archive = temp("zip")
        backup.export(Uri.fromFile(archive).toString())
        database.recordDao().insert(requireNotNull(database.recordDao().get(10)).record.copy(summary = "current"))
        prefs.edit().putBoolean("showSeconds", false).commit()
        var commits = 0
        val failingPrefs = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val editor = prefs.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean = if (commits++ == 0) false else editor.commit()
                }
            }
        }
        val isolated = com.example.util.simpletimetracker.data_local.snapshot.FullBackupRepoImpl(
            context, database, failingPrefs, identity, gate, caches, com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider(),
        )
        val preview = isolated.prepareRestore(Uri.fromFile(archive).toString())
        assertTrue(runCatching { isolated.restore(preview.token) }.isFailure)
        assertEquals("current", records.get(10)?.summary)
        assertFalse(prefs.getBoolean("showSeconds", true))
        assertFalse(File(context.filesDir, "restore-journal.json").exists())
    }

    private suspend fun seed() {
        for (id in 1L..2L) database.recordTypeDao().insert(RecordTypeDBO(id, "type $id", "", 0, "", false, defaultDuration = 0, note = ""))
        val meta = identity.getOrCreate()
        database.recordTagDao().insert(RecordTagDBO(31, 0, "Alice", "", 0, "", 0, false, "", null, null, 1))
        database.recordTagDao().insert(RecordTagDBO(32, 0, "Home", "", 0, "", 0, false, "", null, null, 2))
        database.recordDao().insert(RecordDBO(10, 1, start, start + 900_000, "notes", 0, reviewState = 1, summary = "reading", zoneId = "Asia/Shanghai", templateId = 7))
        database.recordToRecordTagDao().insert(listOf(RecordToRecordTagDBO(10, meta.happinessTagId, 4.0, start + 1), RecordToRecordTagDBO(10, meta.meaningTagId, null, null), RecordToRecordTagDBO(10, 31, null), RecordToRecordTagDBO(10, 32, null)))
        database.recordShortcutDao().insert(com.example.util.simpletimetracker.data_local.recordShortcut.RecordShortcutDBO(7, 1, "tea first", 0, 0, "tea and reading", "read"))
        val reviewMapper = com.example.util.simpletimetracker.data_local.review.ReviewDataLocalMapper()
        database.reviewDao().upsertReview(
            reviewMapper.map(
                com.example.util.simpletimetracker.domain.review.model.WeeklyReview(
                    id = 5, weekStart = "2026-10-05", zoneId = "Asia/Shanghai", keep = com.example.util.simpletimetracker.domain.review.model.ReviewChoice("keep reading", recordId = 888), pinnedTemplateIds = listOf(7),
                ),
            ),
        )
        database.reviewDao().upsertTrial(
            reviewMapper.map(
                com.example.util.simpletimetracker.domain.review.model.ReviewTrial(
                    id = 6, reviewId = 5, source = com.example.util.simpletimetracker.domain.review.model.ReviewRecordReference(888, "deleted source"), description = "try mornings", relatedRecordIds = listOf(10),
                ),
            ),
        )
        database.openHelper.writableDatabase.execSQL("INSERT INTO favouriteColors (id, color_int) VALUES (8, '-16777216')")
        prefs.edit().putBoolean("showSeconds", true).putString("automaticBackupUri", "private-device-uri").commit()
        val directory = File(context.filesDir, "photos").apply { mkdirs() }
        val source = File.createTempFile("m6-test-", ".png", directory).also(created::add)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        database.recordPhotoDao().upsert(RecordPhotoDBO(13, 10, null, "photos/${source.name}", hash))
        caches.reset()
    }
    private fun temp(extension: String) = File.createTempFile("m6-test-", ".$extension", context.cacheDir).also(created::add)
    private fun entries(file: File): LinkedHashMap<String, ByteArray> = linkedMapOf<String, ByteArray>().apply {
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
            }
        }
    }
    private fun manifest(file: File) = JsonParser.parseString(requireNotNull(entries(file)["snapshot.json"]).toString(Charsets.UTF_8)).asJsonObject
    private fun writeZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
