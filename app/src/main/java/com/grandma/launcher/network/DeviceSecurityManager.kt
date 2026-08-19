package com.grandma.launcher.network

import android.util.Base64
import com.grandma.launcher.data.AppPreferences
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Handles RSA 2048-bit key pair generation, PEM encoding/decoding,
 * and signature generation for the challenge-response device verification flow.
 */
object DeviceSecurityManager {

    private const val RSA_ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"

    /**
     * Ensures an RSA KeyPair exists in AppPreferences.
     * Generates a new 2048-bit RSA KeyPair if missing. Thread-safe.
     */
    @Synchronized
    fun ensureKeyPair(appPrefs: AppPreferences) {
        if (appPrefs.devicePublicKeyPem.isBlank() || appPrefs.devicePrivateKeyPem.isBlank()) {
            val keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM)
            keyPairGenerator.initialize(2048)
            val keyPair = keyPairGenerator.generateKeyPair()

            val publicKeyPem = formatPublicKeyPem(keyPair.public)
            val privateKeyPem = formatPrivateKeyPem(keyPair.private)

            appPrefs.devicePublicKeyPem = publicKeyPem
            appPrefs.devicePrivateKeyPem = privateKeyPem
        }
    }

    /**
     * Signs the given challenge string using the stored RSA private key.
     * Returns the signature formatted as a base64 string.
     */
    fun signChallenge(challenge: String, appPrefs: AppPreferences): String {
        ensureKeyPair(appPrefs)
        val privateKey = parsePrivateKeyPem(appPrefs.devicePrivateKeyPem)

        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(challenge.toByteArray(Charsets.UTF_8))
        val signatureBytes = signer.sign()

        // Backend expects base64 signature (Buffer.from(signature, "base64"))
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    /**
     * Verifies a base64 signature produced by [signChallenge] against the stored
     * RSA public key. Used by the device side of the challenge-response handshake
     * to sanity-check the flow before the backend round-trip.
     */
    fun verifySignature(challenge: String, signatureBase64: String, appPrefs: AppPreferences): Boolean {
        ensureKeyPair(appPrefs)
        return try {
            val publicKey = parsePublicKeyPem(appPrefs.devicePublicKeyPem)
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(challenge.toByteArray(Charsets.UTF_8))
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    private fun formatPublicKeyPem(publicKey: PublicKey): String {
        val base64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    private fun formatPrivateKeyPem(privateKey: PrivateKey): String {
        val base64 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        return "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----"
    }

    private fun parsePrivateKeyPem(pem: String): PrivateKey {
        val cleanPem = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val decoded = Base64.decode(cleanPem, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePrivate(keySpec)
    }

    private fun parsePublicKeyPem(pem: String): PublicKey {
        val cleanPem = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val decoded = Base64.decode(cleanPem, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePublic(keySpec)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
