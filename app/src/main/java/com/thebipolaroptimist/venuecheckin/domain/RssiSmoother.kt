package com.thebipolaroptimist.venuecheckin.domain

// planning.md §6 / DECISIONS.md — moving average, window of 5 (decided).
class RssiSmoother(
    private val windowSize: Int = 5,
) {

    private val samples = ArrayDeque<Int>()

    fun add(rssi: Int): Int {
        samples.addLast(rssi)
        while (samples.size > windowSize) {
            samples.removeFirst()
        }
        return samples.sum() / samples.size
    }
}
