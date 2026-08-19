package com.grandma.launcher.benchmark

/**
 * Lightweight micro-benchmark harness.
 *
 * Runs a warmup pass (JIT tier-up + Robolectric/static init) before measurement,
 * then records per-iteration wall-clock timings and reports the distribution
 * (mean / median / p90 / p95) plus throughput in ops/sec.
 *
 * Timings use System.nanoTime() which is monotonic and not affected by wall clock changes.
 */
object BenchmarkRunner {

    private const val DEFAULT_WARMUP_ITERATIONS = 50_000
    private const val NANO_PER_MICRO = 1_000.0
    private const val NANO_PER_MILLI = 1_000_000.0

    private val accumulated = mutableListOf<Result>()

    fun addToReport(result: Result) {
        synchronized(accumulated) { accumulated.add(result) }
    }

    fun reportTitle(title: String) {
        println()
        println("============================================================")
        println("  $title")
        println("============================================================")
    }

    fun printAccumulated() {
        if (accumulated.isEmpty()) return
        printAll(accumulated.toList())
        accumulated.clear()
    }

    fun warmup(iterations: Int = DEFAULT_WARMUP_ITERATIONS, block: () -> Unit) {
        var i = 0
        while (i < iterations) {
            block()
            i++
        }
    }

    /**
     * Measures [block] for [iterations] times after [warmupIterations] warmup rounds.
     * Returns a [Result] with the full timing distribution.
     */
    fun measure(
        label: String,
        iterations: Int,
        warmupIterations: Int = DEFAULT_WARMUP_ITERATIONS,
        block: () -> Unit
    ): Result {
        warmup(warmupIterations, block)

        val samples = LongArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            block()
            samples[i] = System.nanoTime() - start
        }

        return Result(label, iterations, samples)
    }

    data class Result(
        val label: String,
        val iterations: Int,
        val samples: LongArray
    ) {
        private val sorted: LongArray = samples.clone().also { it.sort() }
        private val count = sorted.size

        val minNanos: Double = sorted.first().toDouble()
        val maxNanos: Double = sorted.last().toDouble()

        val meanNanos: Double = sorted.average()

        val medianNanos: Double = percentile(50.0)
        val p90Nanos: Double = percentile(90.0)
        val p95Nanos: Double = percentile(95.0)

        val opsPerSecond: Double = 1_000_000_000.0 / meanNanos

        fun percentile(p: Double): Double {
            val rank = ((count - 1) * (p / 100.0)).toInt()
            return sorted[rank].toDouble()
        }

        fun render(): String {
            val fmt = { v: Double, u: String -> if (v >= 1_000_000) "%.2f ms".format(v / NANO_PER_MILLI) else if (v >= 1_000) "%.2f us".format(v / NANO_PER_MICRO) else "%.0f ns".format(v) }
            return buildString {
                appendLine("== $label (n=$iterations)")
                appendLine("    mean:      ${fmt(meanNanos)}")
                appendLine("    median:    ${fmt(medianNanos)}")
                appendLine("    p90:       ${fmt(p90Nanos)}")
                appendLine("    p95:       ${fmt(p95Nanos)}")
                appendLine("    min:       ${fmt(minNanos)}")
                appendLine("    max:       ${fmt(maxNanos)}")
                appendLine("    throughput: %.0f ops/sec".format(opsPerSecond))
            }
        }
    }

    fun printAll(results: List<Result>) {
        println()
        println("┌──────────────────────────────────────────────────────────────────────┐")
        println("│ Grandma's Launcher — benchmark report                                │")
        println("├──────────────────────────────────────────────────────────────────────┤")
        results.forEach { r ->
            println("│ ${r.label.padEnd(74)}│")
            println("│   mean ${fmt(r.meanNanos).padStart(16)}  median ${fmt(r.medianNanos).padStart(16)}│")
            println("│   p90  ${fmt(r.p90Nanos).padStart(16)}  p95   ${fmt(r.p95Nanos).padStart(16)}│")
            println("│   min  ${fmt(r.minNanos).padStart(16)}  max   ${fmt(r.maxNanos).padStart(16)}│")
            println("│   throughput ${"%.0f ops/sec".format(r.opsPerSecond).padStart(28)}│")
            println("│${"-".repeat(74)}│")
        }
        println("└──────────────────────────────────────────────────────────────────────┘")
        println()
    }

    private fun fmt(v: Double): String =
        when {
            v >= NANO_PER_MILLI -> "%.2f ms".format(v / NANO_PER_MILLI)
            v >= NANO_PER_MICRO -> "%.2f us".format(v / NANO_PER_MICRO)
            else -> "%.0f ns".format(v)
        }
}