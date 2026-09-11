package com.thebipolaroptimist.venuecheckin.domain

// Starting points, not tuned against real hardware yet. Tune after watching real RSSI behavior.
private const val IMMEDIATE_RSSI_THRESHOLD = -60
private const val NEAR_RSSI_THRESHOLD = -75

fun proximityFor(rssi: Int): Proximity = when {
    rssi > IMMEDIATE_RSSI_THRESHOLD -> Proximity.IMMEDIATE
    rssi > NEAR_RSSI_THRESHOLD -> Proximity.NEAR
    else -> Proximity.FAR
}
