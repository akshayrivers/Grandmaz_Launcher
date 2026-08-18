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
     * Generates a new 2048-bit RSA KeyPair if missing.
     */
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
<<<<<<< Updated upstream
     * Returns the signature formatted as a hex string (or base64 if required).
=======
     * Returns the signature formatted as a hex string.
>>>>>>> Stashed changes
     */
    fun signChallenge(challenge: String, appPrefs: AppPreferences): String {
        ensureKeyPair(appPrefs)
        val privateKey = parsePrivateKeyPem(appPrefs.devicePrivateKeyPem)
        
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(challenge.toByteArray(Charsets.UTF_8))
        val signatureBytes = signer.sign()

<<<<<<< Updated upstream
        // Backend accepts base64 or hex signature for verification
=======
>>>>>>> Stashed changes
        return bytesToHex(signatureBytes)
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

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
