package com.example.util.simpletimetracker.data_local.cloud

import android.content.Context
import android.content.SharedPreferences
import android.security.KeyPairGeneratorSpec
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Calendar
import java.util.Date
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import javax.security.auth.x500.X500Principal

@Singleton
class CloudCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences,
) {
    private val alias = "life-clock-cloud-credential-v1"
    val url: String get() = prefs.getString("lifeClockCloudUrl", "").orEmpty()
    val lastSuccess: Long? get() = prefs.getLong("lifeClockCloudSuccess", -1).takeIf { it >= 0 }

    @Suppress("DEPRECATION")
    private fun keys(): KeyStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }.time
            val spec = KeyPairGeneratorSpec.Builder(context).setAlias(alias).setKeySize(2048)
                .setSubject(X500Principal("CN=Life Clock Cloud")).setSerialNumber(BigInteger.ONE)
                .setStartDate(Date()).setEndDate(end).build()
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        }
        return store
    }

    fun token(): String? {
        val encrypted = prefs.getString("lifeClockCloudCredential", null) ?: return null
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keys().getKey(alias, null))
        return String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    fun save(url: String, token: String) {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keys().getCertificate(alias).publicKey)
        val encoded = Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("lifeClockCloudUrl", url).putString("lifeClockCloudCredential", encoded).remove("lifeClockCloudSuccess").commit())
    }
    fun succeeded(timestamp: Long) {
        check(prefs.edit().putLong("lifeClockCloudSuccess", timestamp).commit())
    }
}
