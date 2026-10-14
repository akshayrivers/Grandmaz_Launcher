package com.grandma.launcher.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PinManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences
    private lateinit var pins: PinManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rawPrefs().edit().clear().commit()
        prefs = AppPreferences(context)
        pins = PinManager(prefs)
    }

    private fun rawPrefs() = context.getSharedPreferences("grandma_app_prefs", Context.MODE_PRIVATE)

    @Test
    fun formatRequiresFourToSixDigits() {
        assertFalse(PinHasher.isValidFormat(""))
        assertFalse(PinHasher.isValidFormat("123"))
        assertTrue(PinHasher.isValidFormat("1234"))
        assertTrue(PinHasher.isValidFormat("123456"))
        assertFalse(PinHasher.isValidFormat("1234567"))
        assertFalse(PinHasher.isValidFormat("12a4"))
        assertFalse(PinHasher.isValidFormat("١٢٣٤")) // non-ASCII digits
    }

    @Test
    fun correctPinVerifiesWrongAndEmptyFail() {
        pins.setPin("482916")
        assertTrue(pins.verify("482916"))
        assertFalse(pins.verify("482917"))
        assertFalse(pins.verify("4829"))
        assertFalse(pins.verify(""))
    }

    @Test
    fun emptyPinFailsEvenWhenNoPinIsSet() {
        assertFalse(pins.isPinSet())
        assertFalse(pins.verify(""))
        assertFalse(PinHasher.verify("", ""))
    }

    @Test
    fun pinIsStoredAsSaltedHashNotPlaintext() {
        pins.setPin("1234")
        val stored = prefs.caretakerPinHash
        assertNotEquals("1234", stored)
        assertTrue(stored.startsWith("pbkdf2_sha256\$"))
        assertEquals(PinHasher.DEFAULT_ITERATIONS.toString(), stored.split('$')[1])
        assertTrue(PinHasher.DEFAULT_ITERATIONS >= 10_000)
        // Random salt: same PIN hashes differently each time
        assertNotEquals(PinHasher.hash("1234"), PinHasher.hash("1234"))
        assertFalse(rawPrefs().all.values.any { it == "1234" })
    }

    @Test
    fun legacyPlaintextPinIsMigratedOnFirstRead() {
        rawPrefs().edit().putString("caretaker_pin", "2468").commit()

        assertTrue(pins.isPinSet())
        assertFalse(rawPrefs().contains("caretaker_pin"))
        assertTrue(pins.verify("2468"))
        assertFalse(pins.verify("1357"))
    }

    @Test
    fun legacyPlaintextPinIsMigratedOnVerify() {
        rawPrefs().edit().putString("caretaker_pin", "9753").commit()

        assertTrue(pins.verify("9753"))
        assertFalse(rawPrefs().contains("caretaker_pin"))
    }

    @Test
    fun malformedHashNeverVerifies() {
        assertFalse(PinHasher.verify("1234", "1234"))
        assertFalse(PinHasher.verify("1234", "pbkdf2_sha256\$x\$abc\$def"))
        assertFalse(PinHasher.verify("1234", "pbkdf2_sha256\$1000\$!!!\$???"))
    }

    @Test
    fun hashingStaysWithinTimeBudget() {
        PinHasher.hash("1234") // warm up the provider
        val start = System.nanoTime()
        val encoded = PinHasher.hash("1234")
        PinHasher.verify("1234", encoded)
        val perOpMs = (System.nanoTime() - start) / 2 / 1_000_000.0
        println("    PBKDF2 ${PinHasher.DEFAULT_ITERATIONS} iterations: %.1f ms per hash".format(perOpMs))
        assertTrue("PBKDF2 took $perOpMs ms", perOpMs < 300)
    }
}
