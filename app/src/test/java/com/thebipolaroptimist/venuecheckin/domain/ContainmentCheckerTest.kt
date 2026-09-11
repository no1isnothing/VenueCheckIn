package com.thebipolaroptimist.venuecheckin.domain

import com.thebipolaroptimist.venuecheckin.data.venue.BeaconIdentity
import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ContainmentCheckerTest {

    private val checker = ContainmentChecker()

    private val venueA = Venue(
        id = "venue-a",
        name = "Venue A",
        latitude = 37.7749,
        longitude = -122.4194,
        radiusMeters = 100f,
        beacon = BeaconIdentity(UUID.randomUUID(), 1, 1),
    )

    private val venueB = Venue(
        id = "venue-b",
        name = "Venue B",
        latitude = 34.0522,
        longitude = -118.2437,
        radiusMeters = 100f,
        beacon = BeaconIdentity(UUID.randomUUID(), 2, 1),
    )

    @Test
    fun `point exactly at venue center is within`() {
        val center = GeoPoint(venueA.latitude, venueA.longitude)
        assertTrue(checker.isWithin(center, venueA))
    }

    @Test
    fun `point well inside the radius is within`() {
        // roughly 80m north of center; radius is 100m
        val insidePoint = GeoPoint(venueA.latitude + 0.0007194, venueA.longitude)
        assertTrue(checker.isWithin(insidePoint, venueA))
    }

    @Test
    fun `point just outside the radius is not within`() {
        // roughly 120m north of center; radius is 100m
        val outsidePoint = GeoPoint(venueA.latitude + 0.0010791, venueA.longitude)
        assertFalse(checker.isWithin(outsidePoint, venueA))
    }

    @Test
    fun `point far away is not within`() {
        val farPoint = GeoPoint(40.7128, -74.0060) // New York, thousands of km away
        assertFalse(checker.isWithin(farPoint, venueA))
    }

    @Test
    fun `containment is evaluated independently per venue`() {
        val nearVenueA = GeoPoint(venueA.latitude + 0.0007194, venueA.longitude)
        assertTrue(checker.isWithin(nearVenueA, venueA))
        assertFalse(checker.isWithin(nearVenueA, venueB))
    }
}
