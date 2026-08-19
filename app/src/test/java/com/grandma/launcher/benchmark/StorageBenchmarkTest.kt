package com.grandma.launcher.benchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.data.Contact
import com.grandma.launcher.data.ContactRepository
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageBenchmarkTest {

    private lateinit var context: Context
    private lateinit var repo: ContactRepository
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = ContactRepository(context)
        prefs = AppPreferences(context)
    }

    private fun contact(id: Long) = Contact(
        id = id,
        name = "Grandma #$id",
        photoPath = "/data/user/0/com.grandma.launcher/files/contact_$id.jpg",
        phone = "+91 90000 000$id",
        isFavourite = id % 3 == 0L
    )

    private fun seed(count: Int): List<Contact> {
        val list = (1..count).map { contact(it.toLong()) }
        list.forEach { repo.save(it) }
        return list
    }

    @Test
    fun contactRepositoryReadWrite() {
        BenchmarkRunner.reportTitle("ContactRepository — SharedPreferences + JSON")

        listOf(5, 15, 50).forEach { count ->
            val seedList = seed(count)

            BenchmarkRunner.addToReport(
                BenchmarkRunner.measure(
                    "getAll()  read ${count} contacts",
                    iterations = 5_000,
                    warmupIterations = 5_000
                ) { repo.getAll() }
            )

            // Write path: replace an existing contact so the list size stays stable
            BenchmarkRunner.addToReport(
                BenchmarkRunner.measure(
                    "save()    write ${count} contacts",
                    iterations = 2_000,
                    warmupIterations = 2_000
                ) { repo.save(seedList.first()) }
            )

            BenchmarkRunner.addToReport(
                BenchmarkRunner.measure(
                    "getFavourites() filter ${count} contacts",
                    iterations = 5_000,
                    warmupIterations = 5_000
                ) { repo.getFavourites() }
            )
        }
    }

    @Test
    fun appPreferencesOps() {
        BenchmarkRunner.reportTitle("AppPreferences — settings I/O")

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "emergencyNumber get/set",
                iterations = 10_000,
                warmupIterations = 5_000
            ) {
                prefs.emergencyNumber = "112"
                val v = prefs.emergencyNumber
            }
        )

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "addCaretakerEmail",
                iterations = 2_000,
                warmupIterations = 1_000
            ) {
                prefs.addCaretakerEmail("helper@family.com")
            }
        )

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "getCaretakerEmails() (split+dedupe)",
                iterations = 10_000,
                warmupIterations = 5_000
            ) { prefs.getCaretakerEmails() }
        )

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "verifyPin()",
                iterations = 10_000,
                warmupIterations = 5_000
            ) {
                prefs.caretakerPin = "1234"
                prefs.verifyPin("1234")
            }
        )

        // One-time lazy costs — measured directly, small n
        val t0 = System.nanoTime()
        prefs.deviceId
        val deviceIdOnceUs = (System.nanoTime() - t0) / 1_000.0
        println("    [one-time] first deviceId() call (UUID gen + persist): %.2f us".format(deviceIdOnceUs))
    }

    companion object {
        @JvmStatic
        @AfterClass
        fun done() {
            BenchmarkRunner.printAccumulated()
        }
    }
}