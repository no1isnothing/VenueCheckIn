package com.thebipolaroptimist.venuecheckin.domain

class RssiSmoother(
    private val windowSize: Int = 5, // planning.md §6 — moving average, window of 5 (leaning)
) {

    fun add(rssi: Int): Int {
        TODO("planning.md §6 — smoothing choice still pending")
    }
}
