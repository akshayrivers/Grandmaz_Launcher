package com.grandma.launcher.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * The device's signing identity. Production uses [AndroidKeystoreDeviceKey]; tests use a
 * software key because Robolectric has no AndroidKeyStore provider.
 */
interface DeviceKey {
    /** Public key, creating the key pair first if needed. */
    fun publicKey(): PublicKey

    /** SHA256withECDSA signature (DER) over [data]. */
    fun sign(data: ByteArray): ByteArray

    fun delete()
}

/** EC P-256 key generated inside Android Keystore; the private key never leaves it. */
class AndroidKeystoreDeviceKey(private val alias: String = DEFAULT_ALIAS) : DeviceKey {

    override fun publicKey(): PublicKey = ensureKey().certificate.publicKey

    override fun sign(data: ByteArray): ByteArray {
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(ensureKey().privateKey)
        signer.update(data)
        return signer.sign()
    }

    @Synchronized
    override fun delete() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    @Synchronized
    private fun ensureKey(): KeyStore.PrivateKeyEntry {
        val keyStore = loadKeyStore()
        (keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry)?.let { return it }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
        return loadKeyStore().getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val DEFAULT_ALIAS = "grandma_device_key_v2"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        internal const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
