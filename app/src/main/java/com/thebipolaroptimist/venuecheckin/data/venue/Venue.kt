package com.thebipolaroptimist.venuecheckin.data.venue

data class Venue(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val beacon: BeaconIdentity,
)
