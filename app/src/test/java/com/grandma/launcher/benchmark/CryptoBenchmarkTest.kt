package com.grandma.launcher.benchmark

import com.grandma.launcher.data.PinHasher
import com.grandma.launcher.network.DeviceSecurityManager
import com.grandma.launcher.testutil.SoftwareDeviceKey
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CryptoBenchmarkTest {

    @Test
    fun ecP256KeygenSignVerify() {
        BenchmarkRunner.reportTitle("DeviceSecurityManager — EC P-256 challenge/response (software key)")

        // Keygen is a one-time op — measured separately, small n. On device the key is
        // generated inside Android Keystore, so this is only indicative.
        val keygenTimes = LongArray(5)
        repeat(5) { i ->
            val key = SoftwareDeviceKey()
            val start = System.nanoTime()
            key.publicKey()
            keygenTimes[i] = System.nanoTime() - start
        }
        println("    [one-time] EC P-256 key pair generation: avg %.1f ms (min %.1f ms / max %.1f ms)".format(
            keygenTimes.average() / 1_000_000.0,
            keygenTimes.min().toDouble() / 1_000_000.0,
            keygenTimes.max().toDouble() / 1_000_000.0
        ))

        val security = DeviceSecurityManager(SoftwareDeviceKey())
        val challenge = "a".repeat(64)

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "signChallenge()  SHA256withECDSA",
                iterations = 2_000,
                warmupIterations = 1_000
            ) { security.signChallenge(challenge) }
        )

        val signature = security.signChallenge(challenge)

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "verifySignature() SHA256withECDSA",
                iterations = 2_000,
                warmupIterations = 1_000
            ) { security.verifySignature(challenge, signature) }
        )

        check(security.verifySignature(challenge, signature)) { "round-trip signature verify failed" }
        check(!security.verifySignature("b".repeat(64), signature)) { "tampered challenge should not verify" }
        println("    [check] signature round-trip verified correctly (tamper rejected)")
    }

    @Test
    fun pinHashing() {
        BenchmarkRunner.reportTitle("PinHasher — PBKDF2-HMAC-SHA256 (${PinHasher.DEFAULT_ITERATIONS} iterations)")
        val encoded = PinHasher.hash("1234")
        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure("PinHasher.verify()", iterations = 20, warmupIterations = 5) {
                PinHasher.verify("1234", encoded)
            }
        )
    }

    companion object {
        @JvmStatic
        @AfterClass
        fun done() {
            BenchmarkRunner.printAccumulated()
        }
    }
}
