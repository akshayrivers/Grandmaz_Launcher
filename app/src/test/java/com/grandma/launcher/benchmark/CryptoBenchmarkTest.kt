package com.grandma.launcher.benchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.DeviceSecurityManager
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CryptoBenchmarkTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        prefs = AppPreferences(context)
    }

    @Test
    fun rsa2048KeygenSignVerify() {
        BenchmarkRunner.reportTitle("DeviceSecurityManager — RSA-2048 challenge/response")

        // Keygen is a heavyweight one-time op — measured separately, small n
        val keygenTimes = LongArray(5)
        repeat(5) { i ->
            val start = System.nanoTime()
            DeviceSecurityManager.ensureKeyPair(prefs)
            keygenTimes[i] = System.nanoTime() - start
            // reset so each iteration regenerates the pair
            prefs.devicePublicKeyPem = ""
            prefs.devicePrivateKeyPem = ""
        }
        val avgKeygenMs = keygenTimes.average() / 1_000_000.0
        println("    [one-time] RSA-2048 key pair generation: avg %.1f ms (min %.1f ms / max %.1f ms)".format(
            avgKeygenMs,
            keygenTimes.min().toDouble() / 1_000_000.0,
            keygenTimes.max().toDouble() / 1_000_000.0
        ))

        DeviceSecurityManager.ensureKeyPair(prefs)
        val challenge = "device_${java.util.UUID.randomUUID()}_nonce_abcdef123456"

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "signChallenge()  SHA256withRSA",
                iterations = 2_000,
                warmupIterations = 1_000
            ) { DeviceSecurityManager.signChallenge(challenge, prefs) }
        )

        val signature = DeviceSecurityManager.signChallenge(challenge, prefs)

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "verifySignature() SHA256withRSA",
                iterations = 2_000,
                warmupIterations = 1_000
            ) { DeviceSecurityManager.verifySignature(challenge, signature, prefs) }
        )

        check(DeviceSecurityManager.verifySignature(challenge, signature, prefs)) {
            "round-trip signature verify failed"
        }
        check(!DeviceSecurityManager.verifySignature("tampered_challenge", signature, prefs)) {
            "tampered challenge should not verify"
        }
        println("    [check] signature round-trip verified correctly (tamper rejected)")
    }

    companion object {
        @JvmStatic
        @AfterClass
        fun done() {
            BenchmarkRunner.printAccumulated()
        }
    }
}