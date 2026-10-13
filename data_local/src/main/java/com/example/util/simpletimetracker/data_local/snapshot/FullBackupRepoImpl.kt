package com.example.util.simpletimetracker.data_local.snapshot

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import androidx.room.Room
import androidx.room.withTransaction
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import com.example.util.simpletimetracker.domain.backup.model.FullBackupException
import com.example.util.simpletimetracker.domain.backup.model.FullBackupFailure
import com.example.util.simpletimetracker.domain.backup.model.FullBackupPreview
import com.example.util.simpletimetracker.domain.backup.repo.FullBackupRepo
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class FullBackupRepoImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val prefs: SharedPreferences,
    private val identity: LifeClockSettingsRepo,
    private val gate: DataRestoreGate,
    private val caches: SnapshotCacheResetter,
    private val clock: CurrentTimestampProvider,
) : FullBackupRepo, com.example.util.simpletimetracker.domain.backup.repo.CloudSnapshotSource {
    private val codec = SnapshotCodec()
    private val sql = SnapshotSql()
    private val mutex = Mutex()
    private val journal get() = AtomicFile(File(context.filesDir, "restore-journal.json"))
    private val recoveryPointer get() = AtomicFile(File(context.filesDir, "recovery-copy.json"))
    private val stagingRoot get() = File(context.cacheDir, "full-backup-staging")
    private val backups get() = File(context.filesDir, "backups")
    private data class Capture(val root: JsonObject, val files: Map<String, File>)

    override suspend fun captureForCloud(): com.example.util.simpletimetracker.domain.backup.repo.CloudSnapshotFiles = work {
        val capture = capture()
        com.example.util.simpletimetracker.domain.backup.repo.CloudSnapshotFiles(capture.root.toString(), capture.files.mapValues { it.value.absolutePath })
    }

    override suspend fun export(uri: String) = work {
        val temporary = File.createTempFile("full-backup-", ".zip", context.cacheDir)
        try {
            writeArchive(temporary, capture())
            copyToUri(temporary, uri)
        } finally {
            temporary.delete()
        }
    }

    override suspend fun prepareRestore(uri: String): FullBackupPreview = work {
        val token = UUID.randomUUID().toString()
        val directory = stage(token).apply { require(mkdirs()) }
        try {
            val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("Cannot read backup")
            val root: JsonObject
            input.use { source ->
                ZipInputStream(source.buffered()).use { zip ->
                    val first = zip.nextEntry ?: error("Empty archive")
                    require(first.name == MANIFEST && !first.isDirectory)
                    val bytes = readBounded(zip, MAX_MANIFEST)
                    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    root = SnapshotJson.parse(text)
                    SnapshotValidation.validate(root)
                    SnapshotPrefs.validate(root.getAsJsonObject("businessSettings") ?: JsonObject())
                    val descriptors = SnapshotJson.objects(SnapshotJson.array(root, "photos")).associateBy { SnapshotJson.text(it, "relativePath") }
                    val needed = descriptors.values.sumOf { SnapshotJson.number(it, "sizeBytes") }
                    if (needed + bytes.size + RESERVE > context.filesDir.usableSpace) throw FullBackupException(FullBackupFailure.NO_SPACE)
                    val seen = mutableSetOf(MANIFEST)
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(seen.add(entry.name)) { "Duplicate ZIP entry" }
                        if (entry.isDirectory) {
                            require(entry.name.startsWith("photos/") && descriptors.keys.any { it.startsWith(entry.name) })
                            SnapshotJson.safePath(entry.name.trimEnd('/').let { if (it == "photos") "photos/directory" else it })
                            continue
                        }
                        val descriptor = requireNotNull(descriptors[entry.name]) { "Unknown ZIP entry" }
                        val file = safeFile(directory, entry.name)
                        require(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
                        val limit = SnapshotJson.number(descriptor, "sizeBytes")
                        file.outputStream().use { destination ->
                            var size = 0L
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count == -1) break
                                size += count
                                require(size <= limit) { "Unexpected expanded size" }
                                destination.write(buffer, 0, count)
                            }
                            destination.fd.sync()
                            require(size == limit)
                        }
                        verifyPhoto(file, descriptor)
                    }
                    require(descriptors.keys.all { it in seen }) { "Missing photo" }
                    File(directory, MANIFEST).outputStream().use {
                        it.write(bytes)
                        it.fd.sync()
                    }
                }
            }
            val tables = SnapshotTables.decode(root)
            validateTables(tables)
            FullBackupPreview(
                token, SnapshotJson.number(root, "createdAt"), SnapshotJson.array(root, "records").size(), SnapshotJson.array(root, "runningRecords").size(),
                SnapshotJson.array(root, "photos").size(), SnapshotJson.array(root, "weeklyReviews").size(),
                SnapshotValidation.repairIds(root).size +
                    SnapshotJson.objects(SnapshotJson.array(root, "runningRecords")).count { SnapshotJson.number(it, "timeStarted") == SnapshotJson.number(root, "createdAt") },
            )
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    override suspend fun discard(token: String) = work {
        stage(token).deleteRecursively()
        Unit
    }

    override suspend fun restore(token: String): Unit = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            require(gate.begin()) { "Restore already in progress" }
            var operation: String? = null
            var oldPrefs: JsonObject? = null
            try {
                val directory = stage(token)
                val root = SnapshotJson.parse(File(directory, MANIFEST).readText(Charsets.UTF_8))
                val repair = SnapshotValidation.validate(root)
                val settings = root.getAsJsonObject("businessSettings") ?: JsonObject()
                SnapshotPrefs.validate(settings)
                val sourceTables = SnapshotTables.decode(root)
                val tables = SnapshotTables.finishRunning(sourceTables, SnapshotJson.number(root, "createdAt"), repair)
                val descriptors = SnapshotJson.objects(SnapshotJson.array(root, "photos"))
                descriptors.forEach { verifyPhoto(safeFile(directory, SnapshotJson.text(it, "relativePath")), it) }
                validateTables(tables)
                operation = UUID.randomUUID().toString()
                require(backups.mkdirs() || backups.isDirectory)
                val before = File(backups, "before-restore-$operation.zip")
                writeArchive(before, capture())
                writeAtomic(recoveryPointer, SnapshotJson.obj("file" to before.name))
                val missingBytes = descriptors.distinctBy { canonicalPath(SnapshotJson.text(it, "sha256"), SnapshotJson.text(it, "mimeType")) }
                    .filter { !safeFile(context.filesDir, canonicalPath(SnapshotJson.text(it, "sha256"), SnapshotJson.text(it, "mimeType"))).exists() }
                    .sumOf { SnapshotJson.number(it, "sizeBytes") }
                if (missingBytes + RESERVE > context.filesDir.usableSpace) throw FullBackupException(FullBackupFailure.NO_SPACE)
                val paths = descriptors.associate { descriptor ->
                    val original = SnapshotJson.text(descriptor, "relativePath")
                    val canonical = canonicalPath(SnapshotJson.text(descriptor, "sha256"), SnapshotJson.text(descriptor, "mimeType"))
                    val target = safeFile(context.filesDir, canonical)
                    val source = safeFile(directory, original)
                    if (target.exists()) {
                        verifyPhoto(target, descriptor)
                    } else {
                        require(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                        val partial = File(target.parentFile, "${target.name}.$operation.partial")
                        try {
                            source.inputStream().use { input ->
                                partial.outputStream().use { out ->
                                    input.copyTo(out)
                                    out.fd.sync()
                                }
                            }
                            verifyPhoto(partial, descriptor)
                            require(partial.renameTo(target))
                        } finally {
                            partial.delete()
                        }
                    }
                    original to canonical
                }
                SnapshotJson.objects(requireNotNull(tables["recordPhotos"])).forEach { row -> row.addProperty("relative_path", requireNotNull(paths[row.text("relative_path")])) }
                oldPrefs = SnapshotPrefs.read(prefs)
                writeAtomic(journal, SnapshotJson.obj("operationId" to operation, "oldPreferences" to oldPrefs))
                database.withTransaction {
                    sql.replace(database.openHelper.writableDatabase, tables, operation)
                    if (!SnapshotPrefs.apply(prefs, settings)) throw IOException("Preference commit failed")
                }
                gate.committed()
                caches.reset()
                journal.delete()
                directory.deleteRecursively()
                Unit
            } catch (error: Exception) {
                val committed = operation != null && database.lifeClockSettingsDao().get()?.restoreOperationId == operation
                if (!committed && oldPrefs != null) {
                    if (SnapshotPrefs.apply(prefs, requireNotNull(oldPrefs))) journal.delete()
                }
                caches.reset()
                throw classify(error)
            } finally {
                gate.end()
            }
        }
    }

    override suspend fun recoverInterruptedRestore() = withContext(NonCancellable + Dispatchers.IO) {
        if (!journal.baseFile.exists()) return@withContext
        mutex.withLock {
            require(gate.begin())
            try {
                val saved = journal.openRead().use { SnapshotJson.parse(it.reader(Charsets.UTF_8).readText()) }
                val operation = SnapshotJson.text(saved, "operationId")
                if (database.lifeClockSettingsDao().get()?.restoreOperationId != operation) {
                    require(SnapshotPrefs.apply(prefs, requireNotNull(saved.getAsJsonObject("oldPreferences"))))
                }
                caches.reset()
                journal.delete()
                gate.committed()
            } finally {
                gate.end()
            }
        }
    }

    override suspend fun hasRecoveryCopy(): Boolean = withContext(Dispatchers.IO) { recoveryFile()?.isFile == true }
    override suspend fun exportRecoveryCopy(uri: String) = work { copyToUri(requireNotNull(recoveryFile()), uri) }
    override suspend fun exportCsv(uri: String) = work { writeText(uri, SnapshotTextExport.csv(capture().root)) }
    override suspend fun exportIcs(uri: String) = work { writeText(uri, SnapshotTextExport.ics(capture().root)) }

    private suspend fun capture(): Capture {
        val (tables, now) = database.withTransaction {
            identity.getOrCreate()
            sql.read(database.openHelper.writableDatabase) to clock.get()
        }
        val files = mutableMapOf<String, File>()
        val photos = JsonArray()
        SnapshotJson.objects(requireNotNull(tables["recordPhotos"])).forEach { row ->
            val source = safeFile(context.filesDir, row.text("relative_path"))
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, options)
            val mime = options.outMimeType ?: throw FullBackupException(FullBackupFailure.INVALID_MEDIA)
            val hash = row.text("sha256")
            val path = canonicalPath(hash, mime)
            val descriptor = SnapshotJson.obj("id" to row.text("id"), "relativePath" to path, "sha256" to hash, "mimeType" to mime, "sizeBytes" to source.length())
            verifyPhoto(source, descriptor)
            photos.add(descriptor)
            files[path] = source
        }
        val settings = SnapshotPrefs.read(prefs)
        SnapshotPrefs.validate(settings)
        val root = codec.encode(tables, photos, settings, now, UUID.randomUUID().toString())
        SnapshotValidation.validate(root)
        return Capture(root, files)
    }

    private suspend fun validateTables(tables: Map<String, JsonArray>) {
        val scratch = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            scratch.withTransaction { sql.replace(scratch.openHelper.writableDatabase, tables, null) }
        } finally {
            scratch.close()
        }
    }

    private fun verifyPhoto(file: File, descriptor: JsonObject) {
        if (!file.isFile || file.length() != SnapshotJson.number(descriptor, "sizeBytes") || digest(file) != SnapshotJson.text(descriptor, "sha256")) throw FullBackupException(FullBackupFailure.INVALID_MEDIA)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outMimeType != SnapshotJson.text(descriptor, "mimeType")) throw FullBackupException(FullBackupFailure.INVALID_MEDIA)
        options.inSampleSize = 1
        while (options.outWidth / options.inSampleSize > 512 || options.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val image = BitmapFactory.decodeFile(file.absolutePath, options) ?: throw FullBackupException(FullBackupFailure.INVALID_MEDIA)
        image.recycle()
    }

    private fun writeArchive(file: File, snapshot: Capture) {
        val bytes = snapshot.root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_MANIFEST)
        if (bytes.size + snapshot.files.values.sumOf(File::length) + RESERVE > file.parentFile!!.usableSpace) throw FullBackupException(FullBackupFailure.NO_SPACE)
        val partial = File(file.parentFile, "${file.name}.partial")
        try {
            partial.outputStream().use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST))
                    zip.write(bytes)
                    zip.closeEntry()
                    snapshot.files.forEach { (path, source) ->
                        zip.putNextEntry(ZipEntry(path))
                        source.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    zip.finish()
                    zip.flush()
                    output.fd.sync()
                }
            }
            require(partial.renameTo(file))
        } finally {
            partial.delete()
        }
    }

    private fun copyToUri(file: File, uri: String) {
        val output = context.contentResolver.openOutputStream(Uri.parse(uri), "wt") ?: throw IOException("Cannot write backup")
        output.use { destination ->
            file.inputStream().use { it.copyTo(destination) }
            destination.flush()
        }
    }
    private fun writeText(uri: String, text: String) {
        val output = context.contentResolver.openOutputStream(Uri.parse(uri), "wt") ?: throw IOException("Cannot write export")
        output.use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.flush()
        }
    }
    private fun recoveryFile(): File? {
        if (!recoveryPointer.baseFile.exists()) return null
        val name = recoveryPointer.openRead().use { SnapshotJson.text(SnapshotJson.parse(it.reader(Charsets.UTF_8).readText()), "file") }
        require(name.matches(Regex("before-restore-[0-9a-f-]{36}\\.zip")))
        return File(backups, name)
    }
    private fun stage(token: String): File {
        require(UUID.fromString(token).toString() == token)
        return File(stagingRoot, token)
    }
    private fun safeFile(base: File, path: String): File {
        SnapshotJson.safePath(path)
        return File(base, path).also { require(it.canonicalPath.startsWith(base.canonicalPath + File.separator)) }
    }
    private fun canonicalPath(hash: String, mime: String): String = "photos/$hash." + when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "img"
    }
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    private fun readBounded(zip: ZipInputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = zip.read(buffer)
            if (count == -1) break
            require(output.size().toLong() + count <= limit)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private fun writeAtomic(file: AtomicFile, value: JsonObject) {
        val output = file.startWrite()
        try {
            output.write(value.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
    private suspend fun <T> work(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                action()
            } catch (error: Exception) {
                throw classify(error)
            }
        }
    }
    private fun classify(error: Exception): FullBackupException = if (error is FullBackupException) {
        error
    } else {
        FullBackupException(
            when {
                error is android.database.sqlite.SQLiteFullException || error.message?.contains("ENOSPC") == true -> FullBackupFailure.NO_SPACE
                error is IOException || error is SecurityException -> FullBackupFailure.IO
                else -> FullBackupFailure.INVALID_FORMAT
            },
            error,
        )
    }
    companion object {
        private const val MANIFEST = "snapshot.json"
        private const val MAX_MANIFEST = 64 * 1024 * 1024
        private const val RESERVE = 4L * 1024 * 1024
    }
}
