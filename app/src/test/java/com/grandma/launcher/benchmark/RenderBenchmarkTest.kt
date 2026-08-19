package com.grandma.launcher.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.grandma.launcher.ui.views.AnalogClockView
import com.grandma.launcher.ui.views.SosButtonView
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderBenchmarkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun analogClockOnDraw() {
        BenchmarkRunner.reportTitle("AnalogClockView.onDraw — real Skia render")

        val view = AnalogClockView(context)
        val spec = View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, 540, 540)
        val bmp = Bitmap.createBitmap(540, 540, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "onDraw 540x540 face+ticks+hands",
                iterations = 2_000,
                warmupIterations = 1_000
            ) { view.draw(c) }
        )

        // Sanity: something was actually painted
        val painted = IntArray(540 * 540)
        bmp.getPixels(painted, 0, 540, 0, 0, 540, 540)
        val distinct = painted.distinct().size
        check(distinct > 2) { "clock rendered only $distinct colours — likely blank" }
        println("    [check] frame has $distinct distinct colours (non-blank)")
    }

    @Test
    fun sosButtonOnDraw() {
        BenchmarkRunner.reportTitle("SosButtonView.onDraw — real Skia render")

        val view = SosButtonView(context)
        val spec = View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, 480, 480)
        val bitmap = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val progressField = SosButtonView::class.java.getDeclaredField("holdProgress").apply { isAccessible = true }

        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "onDraw idle (no ring)",
                iterations = 5_000,
                warmupIterations = 2_000
            ) { view.draw(canvas) }
        )

        progressField.setFloat(view, 1.0f)
        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "onDraw full progress ring (PathMeasure segment)",
                iterations = 2_000,
                warmupIterations = 1_000
            ) { view.draw(canvas) }
        )

        progressField.setFloat(view, 0.5f)
        BenchmarkRunner.addToReport(
            BenchmarkRunner.measure(
                "onDraw half progress ring",
                iterations = 2_000,
                warmupIterations = 1_000
            ) { view.draw(canvas) }
        )
        progressField.setFloat(view, 0f)
    }

    companion object {
        @JvmStatic
        @AfterClass
        fun done() {
            BenchmarkRunner.printAccumulated()
        }
    }
}