package com.grandma.launcher.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 hashing for the caretaker PIN.
 *
 * Encoded format: `pbkdf2_sha256$<iterations>$<salt b64>$<hash b64>` so the iteration
 * count can be raised later without invalidating stored PINs.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "pbkdf2_sha256"
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256

    // ~15-40 ms on a 2019 mid-range phone (Conscrypt); stays well under the 300 ms budget.
    const val DEFAULT_ITERATIONS = 20_000

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    private val random = SecureRandom()

    fun isValidFormat(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): String {
        require(isValidFormat(pin)) { "PIN must be $MIN_LENGTH-$MAX_LENGTH digits" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = derive(pin, salt, iterations)
        val b64 = Base64.getEncoder()
        return "$PREFIX$$iterations$${b64.encodeToString(salt)}$${b64.encodeToString(derived)}"
    }

    fun verify(pin: String, encoded: String): Boolean {
        if (pin.isEmpty() || encoded.isEmpty()) return false
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        return try {
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            MessageDigest.isEqual(derive(pin, salt, iterations), expected)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
