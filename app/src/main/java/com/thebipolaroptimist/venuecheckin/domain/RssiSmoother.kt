package com.thebipolaroptimist.venuecheckin.domain

// moving average, window of 5 for now. this could also be tuned with different sizes or weights
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
