package com.grandma.launcher.benchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.grandma.launcher.data.Contact
import com.grandma.launcher.data.ContactRepository
import com.grandma.launcher.data.WeatherRepository
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogicBenchmarkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun contact(id: Long) = Contact(
        id = id,
        name = "Grandma #$id",
        photoPath = "/data/user/0/com.grandma.launcher/files/contact_$id.jpg",
        phone = "+91 90000 000$id",
        isFavourite = id % 3 == 0L
    )

    @Test
    fun weatherRepository() {
        BenchmarkRunner.reportTitle("WeatherRepository — simulated local weather")

        val repo = WeatherRepository(context)
        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "getCurrentWeather()",
                iterations = 100_000,
                warmupIterations = 50_000
            ) { repo.getCurrentWeather() }
        )
    }

    @Test
    fun contactListOps() {
        BenchmarkRunner.reportTitle("Contact data — in-memory list operations")

        val repo = ContactRepository(context)
        val list = (1..15).map { contact(it.toLong()) }

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "setFavourite() (map+copy over 15)",
                iterations = 20_000,
                warmupIterations = 10_000
            ) {
                repo.setFavourite(7L, true)
            }
        )

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "indexOfFirst lookup (15)",
                iterations = 100_000,
                warmupIterations = 50_000
            ) {
                list.indexOfFirst { it.id == 7L }
            }
        )

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "filter favourites (15)",
                iterations = 100_000,
                warmupIterations = 50_000
            ) {
                list.filter { it.isFavourite }
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