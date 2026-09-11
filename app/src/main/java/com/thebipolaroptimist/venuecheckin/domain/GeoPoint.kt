package com.thebipolaroptimist.venuecheckin.domain

// Framework-free on purpose — see planning.md §10 (testability: no android.location.Location
// in domain-layer contracts).
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)
