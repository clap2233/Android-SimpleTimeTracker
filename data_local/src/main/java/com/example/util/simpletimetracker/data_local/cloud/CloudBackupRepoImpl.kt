package com.example.util.simpletimetracker.data_local.cloud

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.AtomicFile
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupException
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupRepo
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupResult
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupState
import com.example.util.simpletimetracker.domain.backup.repo.CloudSnapshotSource
import com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

@Singleton
class CloudBackupRepoImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentials: CloudCredentialStore,
    private val tls: CloudTls,
    private val source: CloudSnapshotSource,
    private val gate: DataRestoreGate,
    private val identity: com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo,
) : CloudBackupRepo {
    private val mutex = Mutex()
    private val pending get() = AtomicFile(File(context.filesDir, "cloud-pending.json"))
    private fun hasPending() = pending.baseFile.exists() || File(pending.baseFile.path + ".bak").exists()

    override suspend fun state(): CloudBackupState = withContext(Dispatchers.IO) {
        CloudBackupState(credentials.url, runCatching { credentials.token() != null }.getOrDefault(false), hasPending(), credentials.lastSuccess)
    }

    override suspend fun configure(serverUrl: String, writeToken: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val address = URI(serverUrl.trim())
            val localDebug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && address.host in setOf("127.0.0.1", "localhost", "10.0.2.2")
            if (address.host.isNullOrBlank() || address.userInfo != null || address.query != null || address.fragment != null || address.path.orEmpty() !in setOf("", "/") ||
                !(address.scheme == "https" || localDebug && address.scheme == "http")
            ) {
                throw CloudBackupException("INVALID_ADDRESS")
            }
            if (!writeToken.matches(Regex("[A-Za-z0-9_-]{32,128}"))) throw CloudBackupException("INVALID_CREDENTIAL")
            if (hasPending()) throw CloudBackupException("PENDING_UPLOAD")
            credentials.save(serverUrl.trim().trimEnd('/'), writeToken)
        }
    }

    override suspend fun discardPending() = withContext(Dispatchers.IO) { mutex.withLock { pending.delete() } }

    override suspend fun upload(): CloudBackupResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val token = runCatching { credentials.token() }.getOrNull() ?: throw CloudBackupException("NOT_CONFIGURED")
            val epoch = gate.token()
            if (!gate.canWrite(epoch)) throw CloudBackupException("DATA_RESTORING")
            val job = if (hasPending()) {
                JSONObject(String(pending.readFully(), Charsets.UTF_8))
            } else {
                val snapshot = source.captureForCloud()
                if (!gate.canWrite(epoch)) throw CloudBackupException("DATA_RESTORING")
                if (snapshot.snapshotJson.toByteArray(Charsets.UTF_8).size > 64 * 1024 * 1024) throw CloudBackupException("MANIFEST_TOO_LARGE")
                JSONObject().put("snapshotJson", snapshot.snapshotJson).put("files", JSONObject(snapshot.photos)).put("url", credentials.url)
                    .put("credentialHash", digest(token.toByteArray(Charsets.UTF_8))).also(::save)
            }
            if (job.getString("url") != credentials.url || job.getString("credentialHash") != digest(token.toByteArray(Charsets.UTF_8))) throw CloudBackupException("PENDING_UPLOAD")
            val manifest = JSONObject(job.getString("snapshotJson"))
            if (identity.getOrCreate().dataSetId != manifest.getString("dataSetId")) throw CloudBackupException("DATASET_CHANGED")
            val id = manifest.getString("snapshotId")
            if (!job.has("parentSnapshotId")) {
                val latest = request("GET", "latest", token).optJSONObject("latest")
                if (latest != null && latest.getString("dataSetId") != manifest.getString("dataSetId")) throw CloudBackupException("DATASET_CONFLICT")
                job.put("parentSnapshotId", latest?.getString("snapshotId") ?: JSONObject.NULL)
                save(job)
            }
            val parent = job.get("parentSnapshotId")
            val prepared = request(
                "POST", "prepare", token,
                JSONObject().put("snapshotJson", job.getString("snapshotJson")).put("parentSnapshotId", parent).toString().toByteArray(Charsets.UTF_8),
            )
            if (prepared.getString("snapshotId") != id) throw CloudBackupException("INVALID_RESPONSE")
            val missing = prepared.getJSONArray("missingPhotos")
            for (index in 0 until missing.length()) {
                currentCoroutineContext().ensureActive()
                if (!gate.canWrite(epoch)) throw CloudBackupException("DATA_RESTORING")
                val hash = missing.getString(index)
                if (!hash.matches(Regex("[a-f0-9]{64}"))) throw CloudBackupException("INVALID_RESPONSE")
                val descriptors = manifest.getJSONArray("photos")
                val descriptor = (0 until descriptors.length()).map { descriptors.getJSONObject(it) }.firstOrNull { it.getString("sha256") == hash }
                    ?: throw CloudBackupException("INVALID_RESPONSE")
                val file = File(job.getJSONObject("files").getString(descriptor.getString("relativePath")))
                val root = context.filesDir.canonicalPath + File.separator
                if (!file.canonicalPath.startsWith(root) || !file.isFile || file.length() != descriptor.getLong("sizeBytes")) throw CloudBackupException("PHOTO_MISSING")
                val connection = open("PUT", "$id/photos/$hash", token)
                try {
                    connection.setRequestProperty("Content-Type", "application/octet-stream")
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(file.length())
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        connection.outputStream.use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (digest.digest().joinToString("") { "%02x".format(it) } != hash) throw CloudBackupException("PHOTO_CHANGED")
                    read(connection)
                } finally {
                    connection.disconnect()
                }
            }
            if (!gate.canWrite(epoch)) throw CloudBackupException("DATA_RESTORING")
            currentCoroutineContext().ensureActive()
            val committed = request("POST", "$id/commit", token, JSONObject().put("parentSnapshotId", parent).toString().toByteArray(Charsets.UTF_8))
            if (committed.getJSONObject("committed").getString("snapshotId") != id) throw CloudBackupException("INVALID_RESPONSE")
            val isLatest = committed.optJSONObject("latest")?.optString("snapshotId") == id
            if (isLatest) credentials.succeeded(manifest.getLong("createdAt"))
            pending.delete()
            CloudBackupResult(id, manifest.getLong("createdAt"), isLatest)
        }
    }

    private fun save(job: JSONObject) {
        val stream = pending.startWrite()
        try {
            stream.write(job.toString().toByteArray(Charsets.UTF_8))
            pending.finishWrite(stream)
        } catch (error: Exception) {
            pending.failWrite(stream)
            throw error
        }
    }

    private fun open(method: String, endpoint: String, token: String): HttpURLConnection =
        (URL("${credentials.url}/api/backups/$endpoint").openConnection() as HttpURLConnection).apply {
            tls.apply(this)
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 120000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
    private fun request(method: String, endpoint: String, token: String, body: ByteArray? = null): JSONObject {
        val connection = open(method, endpoint, token)
        try {
            body?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(it.size)
                connection.outputStream.use { output -> output.write(it) }
            }
            return read(connection)
        } catch (error: IOException) {
            throw CloudBackupException("NETWORK_ERROR")
        } finally {
            connection.disconnect()
        }
    }
    private fun read(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes = input?.use { it.readBytesLimited(4 * 1024 * 1024) } ?: ByteArray(0)
        val json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
        if (code !in 200..299) throw CloudBackupException(json?.optString("error")?.takeIf { it.matches(Regex("[A-Z_]{1,64}")) } ?: "SERVER_ERROR")
        return json ?: throw CloudBackupException("INVALID_RESPONSE")
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count == -1) break
            if (output.size() + count > limit) throw CloudBackupException("INVALID_RESPONSE")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
