package com.thebipolaroptimist.venuecheckin.domain

import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

class ContainmentChecker @Inject constructor() {

    fun isWithin(point: GeoPoint, venue: Venue): Boolean {
        val distanceMeters = haversineMeters(point, GeoPoint(venue.latitude, venue.longitude))
        return distanceMeters <= venue.radiusMeters
    }

    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1Rad = Math.toRadians(a.latitude)
        val lat2Rad = Math.toRadians(b.latitude)
        val deltaLatRad = Math.toRadians(b.latitude - a.latitude)
        val deltaLonRad = Math.toRadians(b.longitude - a.longitude)

        val h = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
            cos(lat1Rad) * cos(lat2Rad) * sin(deltaLonRad / 2) * sin(deltaLonRad / 2)
        val centralAngle = 2 * atan2(sqrt(h), sqrt(1 - h))
        return EARTH_RADIUS_METERS * centralAngle
    }
}
