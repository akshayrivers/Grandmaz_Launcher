package com.grandma.launcher.testutil

import com.grandma.launcher.network.DeviceKey
import com.grandma.launcher.network.SecretCipher
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** In-memory EC P-256 key: Robolectric has no AndroidKeyStore provider. */
class SoftwareDeviceKey : DeviceKey {
    private var keyPair: KeyPair? = null

    @Synchronized
    private fun pair(): KeyPair = keyPair ?: KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair().also { keyPair = it }

    override fun publicKey(): PublicKey = pair().public

    override fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(pair().private)
        update(data)
        sign()
    }

    override fun delete() {
        keyPair = null
    }
}

class SoftwareAesGcmCipher : SecretCipher {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext, 0, 12))
        return cipher.doFinal(ciphertext, 12, ciphertext.size - 12)
    }
}
