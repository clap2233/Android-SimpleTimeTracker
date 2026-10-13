package com.example.util.simpletimetracker.data_local.cloud

import android.content.Context
import android.net.http.X509TrustManagerExtensions
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@Singleton
class CloudTls @Inject constructor(@ApplicationContext private val context: Context) {
    fun apply(connection: HttpURLConnection) {
        if (Build.VERSION.SDK_INT < 26 && connection is HttpsURLConnection) {
            connection.sslSocketFactory = legacyFactory(connection.url.host)
        }
    }

    private val managers by lazy {
        val platform = manager(null)
        val root = context.assets.open("certificates/isrg_root_x1.pem").use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("isrg-root-x1", root)
        }
        platform to manager(store)
    }

    private fun legacyFactory(host: String): javax.net.ssl.SSLSocketFactory {
        val (platform, additional) = managers
        val platformChecks = X509TrustManagerExtensions(platform)
        val additionalChecks = X509TrustManagerExtensions(additional)
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = platform.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    platformChecks.checkServerTrusted(chain, authType, host)
                } catch (_: CertificateException) {
                    additionalChecks.checkServerTrusted(chain, authType, host)
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers + additional.acceptedIssuers
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }.socketFactory
    }

    private fun manager(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }.trustManagers.filterIsInstance<X509TrustManager>().single()
}
