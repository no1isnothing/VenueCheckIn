package com.thebipolaroptimist.venuecheckin.domain

import kotlin.math.pow

// Standard log-distance path-loss model: distance = 10^((txPower - rssi) / (10 * n)).
// A rough estimate that's good enough for the coarse proximity buckets requested in the spec.
// pathLossExponent is a fixed generic default (free-space) rather than tuned to any real
// environment - see DECISIONS.md "What I'd do with another week" for the idea of deriving it
// from on-site measurements instead, to better handle real interference.
fun estimateDistanceMeters(
    rssi: Int,
    calibratedTxPower: Int,
    pathLossExponent: Double = 2.0,
): Double {
    return 10.0.pow((calibratedTxPower - rssi) / (10.0 * pathLossExponent))
}
