package com.grandma.launcher.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.grandma.launcher.data.AppPreferences
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Symmetric encryption for small secrets at rest. */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a non-exportable key in Android Keystore.
 * Output layout: 12-byte IV followed by ciphertext+tag.
 */
class KeystoreAesGcmCipher(private val alias: String = DEFAULT_ALIAS) : SecretCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_BYTES)
        )
        return cipher.doFinal(ciphertext, IV_BYTES, ciphertext.size - IV_BYTES)
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeystoreDeviceKey.ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeystoreDeviceKey.ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "grandma_token_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/**
 * Persists the device token encrypted with [cipher]. Undecryptable data (e.g. Keystore
 * wiped by a backup restore) is treated as "no token", which triggers a fresh challenge.
 */
class DeviceTokenStore(
    private val appPrefs: AppPreferences,
    private val cipher: SecretCipher,
    private val clock: () -> Long = System::currentTimeMillis
) {

    fun save(token: String, expiresAtMs: Long) {
        val ciphertext = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
        appPrefs.deviceTokenCiphertext = Base64.getEncoder().encodeToString(ciphertext)
        appPrefs.deviceTokenExpiresAtMs = expiresAtMs
    }

    /** The stored token if it exists and is not within [EXPIRY_SKEW_MS] of expiry. */
    fun validToken(): String? {
        val stored = appPrefs.deviceTokenCiphertext
        if (stored.isEmpty()) return null
        if (appPrefs.deviceTokenExpiresAtMs - EXPIRY_SKEW_MS <= clock()) return null
        return try {
            String(cipher.decrypt(Base64.getDecoder().decode(stored)), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Stored device token unreadable; discarding")
            clear()
            null
        }
    }

    fun clear() {
        appPrefs.deviceTokenCiphertext = ""
        appPrefs.deviceTokenExpiresAtMs = 0L
    }

    companion object {
        private const val TAG = "DeviceTokenStore"
        private const val EXPIRY_SKEW_MS = 60_000L
    }
}
