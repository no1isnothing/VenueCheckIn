package com.thebipolaroptimist.venuecheckin.domain

data class ScanLogEntry(
    val timestampMillis: Long,
    val proximity: Proximity,
    val distanceMeters: Double?, // null for beacon-lost entries
)

fun Proximity.displayName(): String = when (this) {
    Proximity.IMMEDIATE -> "Immediate"
    Proximity.NEAR -> "Near"
    Proximity.FAR -> "Far"
    Proximity.UNKNOWN -> "Unknown"
}
