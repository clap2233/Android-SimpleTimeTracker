package com.example.util.simpletimetracker

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.snapshot.SnapshotCacheResetter
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupException
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupRepo
import com.example.util.simpletimetracker.domain.backup.repo.CloudSnapshotSource
import com.example.util.simpletimetracker.domain.color.model.AppColor
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordReviewState
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordPhotoRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordRepo
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.recordType.repo.RecordTypeRepo
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.net.ServerSocket
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LifeClockCloudBackupTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var cloud: CloudBackupRepo
    @Inject lateinit var source: CloudSnapshotSource
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var prefs: SharedPreferences
    @Inject lateinit var types: RecordTypeRepo
    @Inject lateinit var mutations: RecordMutationRepo
    @Inject lateinit var photos: RecordPhotoRepo
    @Inject lateinit var identity: LifeClockSettingsRepo
    @Inject lateinit var records: RecordRepo
    @Inject lateinit var caches: SnapshotCacheResetter
    private val token = "T".repeat(43)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun before() = runBlocking {
        hilt.inject()
        database.clearAllTables()
        caches.reset()
        prefs.edit().clear().commit()
        cloud.discardPending()
    }
    @After fun after() = runBlocking { cloud.discardPending() }

    @Test fun credentialsAreEncryptedExcludedFromSnapshotAndInsecureAddressesAreRejected() = runBlocking {
        cloud.configure("https://124.221.50.129", token)
        assertTrue(cloud.state().configured)
        assertTrue(prefs.all.values.none { it.toString().contains(token) })
        assertFalse(source.captureForCloud().snapshotJson.contains("lifeClockCloudCredential"))
        assertFalse(source.captureForCloud().snapshotJson.contains(token))
        for (address in listOf("http://124.221.50.129", "https://user:pass@example.com", "https://example.com/path", "https://example.com?key=secret")) {
            assertEquals("INVALID_ADDRESS", (runCatching { cloud.configure(address, token) }.exceptionOrNull() as CloudBackupException).code)
        }
    }

    @Test fun failedCommitFreezesTheSnapshotAndRetryDoesNotMixNewRecordsOrRepeatPhotos() = runBlocking {
        TestServer(failCommitOnce = true).use { server ->
            val type = types.add(RecordType(name = "Cloud test", icon = "", color = AppColor(0, "-14132154"), defaultDuration = 0, note = ""))
            val score = identity.getOrCreate()
            val now = System.currentTimeMillis()
            val first = Record(
                typeId = type, timeStarted = now - 3600000, timeEnded = now - 2700000, comment = "first", summary = "Reading",
                tags = listOf(RecordBase.Tag(score.happinessTagId, 4.0)), reviewState = RecordReviewState.CONFIRMED,
            )
            val imageFile = File(context.cacheDir, "m7-cloud-photo.png")
            val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
            imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val photo = photos.importPhoto(Uri.fromFile(imageFile).toString())
            imageFile.delete()
            mutations.saveDetails(first, listOf(photo))
            cloud.configure(server.url, token)
            assertEquals("BACKUP_UNAVAILABLE", (runCatching { cloud.upload() }.exceptionOrNull() as CloudBackupException).code)
            assertTrue(cloud.state().pending)
            val frozen = server.prepared.first()
            val pendingFile = File(context.filesDir, "cloud-pending.json")
            assertTrue(pendingFile.renameTo(File(pendingFile.path + ".bak")))
            assertTrue(cloud.state().pending)
            mutations.save(listOf(first.copy(id = 0, timeStarted = now - 1800000, timeEnded = now - 900000, summary = "After failed upload", tags = emptyList())))
            val retried = cloud.upload()
            assertEquals(frozen.getString("snapshotId"), retried.snapshotId)
            assertEquals(server.prepared.first().toString(), server.prepared[1].toString())
            assertEquals(1, server.last!!.getJSONArray("records").length())
            assertEquals(1, server.photoCount)
            assertFalse(cloud.state().pending)
            val next = cloud.upload()
            assertNotEquals(retried.snapshotId, next.snapshotId)
            assertEquals(2, server.last!!.getJSONArray("records").length())
            assertEquals(2, records.getAll().size)
            assertEquals(1, server.photoCount)
        }
    }

    @Test fun offlineFailureRetainsPendingDataAndDiscardDoesNotDeleteLocalRecords() = runBlocking {
        val unused = ServerSocket(0).use { it.localPort }
        cloud.configure("http://127.0.0.1:$unused", token)
        val before = source.captureForCloud().snapshotJson
        assertEquals("NETWORK_ERROR", (runCatching { cloud.upload() }.exceptionOrNull() as CloudBackupException).code)
        assertTrue(cloud.state().pending)
        assertEquals("PENDING_UPLOAD", (runCatching { cloud.configure("https://124.221.50.129", token) }.exceptionOrNull() as CloudBackupException).code)
        cloud.discardPending()
        assertFalse(cloud.state().pending)
        assertEquals(JSONObject(before).getString("dataSetId"), JSONObject(source.captureForCloud().snapshotJson).getString("dataSetId"))
    }

    @Test fun realServiceReceivesTheNativeSnapshotAndPrivatePhotoWithoutExposingCredentials() = runBlocking {
        val type = types.add(RecordType(name = "Phone service test", icon = "", color = AppColor(0, "-14132154"), defaultDuration = 0, note = ""))
        val rating = identity.getOrCreate()
        val now = System.currentTimeMillis()
        val record = Record(
            typeId = type, timeStarted = now - 3600000, timeEnded = now - 2700000, comment = "phone-to-service",
            summary = "Native snapshot", tags = listOf(RecordBase.Tag(rating.happinessTagId, 4.0), RecordBase.Tag(rating.meaningTagId, 5.0)), reviewState = RecordReviewState.CONFIRMED,
        )
        val original = File(context.cacheDir, "m7-live-photo.png")
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val photo = photos.importPhoto(Uri.fromFile(original).toString())
        original.delete()
        val id = mutations.saveDetails(record, listOf(photo))
        val endpoint = InstrumentationRegistry.getArguments().getString("m7ServerUrl") ?: "http://10.0.2.2:4178"
        cloud.configure(endpoint, "W".repeat(43))
        val result = cloud.upload()
        assertTrue(result.isLatest)
        fun read(path: String): ByteArray {
            val connection = URL("$endpoint/api/backups/$path").openConnection() as HttpURLConnection
            try {
                connection.setRequestProperty("Authorization", "Bearer " + "R".repeat(43))
                assertEquals(200, connection.responseCode)
                return connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
        val manifest = JSONObject(String(read(result.snapshotId), Charsets.UTF_8))
        assertEquals(rating.dataSetId, manifest.getString("dataSetId"))
        assertEquals(id.toString(), manifest.getJSONArray("records").getJSONObject(0).getString("id"))
        assertEquals(result.createdAt, manifest.getLong("createdAt"))
        assertEquals(27, manifest.getJSONObject("upstreamData").getJSONObject("tables").length())
        val bytes = read("${result.snapshotId}/photos/${manifest.getJSONArray("photos").getJSONObject(0).getString("sha256")}")
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(4, decoded.width)
        decoded.recycle()
        assertFalse(manifest.toString().contains("lifeClockCloudCredential"))
        File(context.cacheDir, "m7-live-manifest.json").writeText(manifest.toString())
    }

    private class TestServer(failCommitOnce: Boolean) : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newSingleThreadExecutor()
        private val failure = AtomicBoolean(failCommitOnce)
        val url = "http://127.0.0.1:${socket.localPort}"
        val prepared = Collections.synchronizedList(mutableListOf<JSONObject>())
        @Volatile var last: JSONObject? = null
        @Volatile var photoCount = 0
        private val hashes = mutableSetOf<String>()
        init {
            worker.submit {
                while (!socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    client.use {
                        val input = client.getInputStream()
                        fun line(): String {
                            val bytes = java.io.ByteArrayOutputStream()
                            while (true) {
                                val b = input.read()
                                if (b < 0 || b == 10) break
                                if (b != 13) bytes.write(b)
                            }
                            return bytes.toString("UTF-8")
                        }
                        val request = line().split(' ')
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val header = line()
                            if (header.isEmpty()) break
                            headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
                        }
                        val size = headers["content-length"]?.toInt() ?: 0
                        val body = ByteArray(size)
                        var position = 0
                        while (position < size) {
                            val count = input.read(body, position, size - position)
                            if (count < 0) break
                            position += count
                        }
                        var status = 200
                        val response = when {
                            request[1].endsWith("/latest") -> JSONObject().put("latest", last?.let { JSONObject().put("snapshotId", it.getString("snapshotId")).put("dataSetId", it.getString("dataSetId")) } ?: JSONObject.NULL)
                            request[1].endsWith("/prepare") -> {
                                val manifest = JSONObject(JSONObject(String(body)).getString("snapshotJson"))
                                prepared.add(manifest)
                                val missing = org.json.JSONArray()
                                val photos = manifest.getJSONArray("photos")
                                for (i in 0 until photos.length()) {
                                    val hash = photos.getJSONObject(i).getString("sha256")
                                    if (hash !in hashes) missing.put(hash)
                                }
                                JSONObject().put("snapshotId", manifest.getString("snapshotId")).put("missingPhotos", missing)
                            }
                            request[0] == "PUT" -> {
                                hashes.add(request[1].substringAfterLast('/'))
                                photoCount++
                                JSONObject().put("uploaded", true)
                            }
                            failure.getAndSet(false) -> {
                                status = 503
                                JSONObject().put("error", "BACKUP_UNAVAILABLE")
                            }
                            else -> {
                                last = prepared.last()
                                val info = JSONObject().put("snapshotId", last!!.getString("snapshotId"))
                                JSONObject().put("committed", info).put("latest", info)
                            }
                        }
                        val bytes = response.toString().toByteArray(Charsets.UTF_8)
                        client.getOutputStream().use { out ->
                            out.write("HTTP/1.1 $status OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                            out.write(bytes)
                            out.flush()
                        }
                    }
                }
            }
        }
        override fun close() {
            socket.close()
            worker.shutdownNow()
        }
    }
}
