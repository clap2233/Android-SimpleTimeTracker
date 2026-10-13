package com.example.util.simpletimetracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.simpletimetracker.data_local.cloud.CloudTls
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifeClockHttpsTest {
    @Test fun serverCertificateAndIpAreTrustedByTheCloudClient() {
        val connection = open("httpsProbe")
        try {
            assertEquals(200, connection.responseCode)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            assertEquals("ok", JSONObject(body).getString("status"))
        } finally {
            connection.disconnect()
        }
    }

    @Test fun wrongPeerIpIsRejected() = reject("httpsWrongIp", false)
    @Test fun untrustedCertificateIsRejected() = reject("httpsUntrusted", true)

    private fun reject(argument: String, certificate: Boolean) {
        val connection = open(argument)
        try {
            val error = runCatching { connection.responseCode }.exceptionOrNull()
            assertTrue(error.toString(), if (certificate) error is SSLHandshakeException else error is SSLPeerUnverifiedException || error?.message?.contains("Hostname", ignoreCase = true) == true)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(argument: String): HttpsURLConnection {
        val endpoint = InstrumentationRegistry.getArguments().getString(argument)
        assumeTrue(endpoint != null)
        val port = InstrumentationRegistry.getArguments().getString("httpsProxyPort")?.toInt()
        val url = URL(requireNotNull(endpoint))
        val connection = (if (port == null) url.openConnection() else url.openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress("10.0.2.2", port)))) as HttpsURLConnection
        CloudTls(InstrumentationRegistry.getInstrumentation().targetContext).apply(connection)
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return connection
    }
}
