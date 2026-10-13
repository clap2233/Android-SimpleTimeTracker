package com.example.util.simpletimetracker.data_local.recordPhoto

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.domain.record.model.RecordPhoto
import com.example.util.simpletimetracker.domain.record.repo.RecordPhotoRepo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Singleton
class RecordPhotoRepoImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) : RecordPhotoRepo {
    override suspend fun discardUnused(photos: List<RecordPhoto>) = withContext(Dispatchers.IO) {
        val referenced = database.recordPhotoDao().getAll().map { it.relativePath }.toSet()
        photos.filter { it.id == 0L && it.relativePath !in referenced }.forEach { File(path(it)).delete() }
    }

    override suspend fun get(ownerId: Long, running: Boolean): List<RecordPhoto> = withContext(Dispatchers.IO) {
        val dao = database.recordPhotoDao()
        (if (running) dao.getByRunningRecord(ownerId) else dao.getByRecord(ownerId)).map {
            RecordPhoto(it.id, it.relativePath, it.sha256)
        }
    }

    override fun path(photo: RecordPhoto): String {
        val relative = photo.relativePath
        require(relative.startsWith("photos/") && '\\' !in relative && ':' !in relative)
        require(relative.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
        return File(context.filesDir, relative).absolutePath
    }

    override suspend fun importPhoto(uri: String): RecordPhoto {
        var created: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val directory = File(context.filesDir, "photos")
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create photo directory")
                val file = File(directory, "${UUID.randomUUID()}.img")
                created = file
                val partial = File(directory, "${file.name}.partial")
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("Cannot read photo")
                    input.use { source ->
                        partial.outputStream().use { destination ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer)
                                if (count == -1) break
                                total += count
                                if (total > MAX_BYTES) throw IOException("Photo exceeds 50 MB")
                                digest.update(buffer, 0, count)
                                destination.write(buffer, 0, count)
                            }
                            destination.fd.sync()
                        }
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(partial.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid image")
                    if (!partial.renameTo(file)) throw IOException("Cannot commit photo")
                    currentCoroutineContext().ensureActive()
                    RecordPhoto(relativePath = "photos/${file.name}", sha256 = digest.digest().joinToString("") { "%02x".format(it) })
                } catch (error: Exception) {
                    partial.delete()
                    file.delete()
                    throw error
                }
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                created?.let {
                    File(it.parentFile, "${it.name}.partial").delete()
                    it.delete()
                }
            }
            throw error
        }
    }

    companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
    }
}
