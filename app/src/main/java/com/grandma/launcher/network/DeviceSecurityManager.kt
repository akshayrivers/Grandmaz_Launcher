package com.grandma.launcher.network

import java.security.Signature
import java.util.Base64

/**
 * Device identity for the challenge-response flow: EC P-256 public key as PEM (SPKI) and
 * SHA256withECDSA signatures (DER, base64 without line wrapping) over the UTF-8 bytes of
 * the challenge hex string.
 */
class DeviceSecurityManager(private val deviceKey: DeviceKey) {

    fun publicKeyPem(): String {
        val body = Base64.getMimeEncoder(PEM_LINE_LENGTH, "\n".toByteArray())
            .encodeToString(deviceKey.publicKey().encoded)
        return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----"
    }

    fun signChallenge(challenge: String): String =
        Base64.getEncoder().encodeToString(deviceKey.sign(challenge.toByteArray(Charsets.UTF_8)))

    /** Local sanity check of a [signChallenge] result against our own public key. */
    fun verifySignature(challenge: String, signatureBase64: String): Boolean = try {
        val verifier = Signature.getInstance(AndroidKeystoreDeviceKey.SIGNATURE_ALGORITHM)
        verifier.initVerify(deviceKey.publicKey())
        verifier.update(challenge.toByteArray(Charsets.UTF_8))
        verifier.verify(Base64.getDecoder().decode(signatureBase64))
    } catch (e: Exception) {
        false
    }

    /** Discards the key pair; the next [publicKeyPem] creates a new one. */
    fun resetKey() = deviceKey.delete()

    companion object {
        const val KEY_ALGORITHM = "EC_P256"
        private const val PEM_LINE_LENGTH = 64
    }
}
