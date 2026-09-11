package com.thebipolaroptimist.venuecheckin.data.venue

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VenueRepository @Inject constructor() {

    // TODO: real, mockable coordinates — see README
    val venues: List<Venue> = listOf(
        Venue(
            id = "venue-1",
            name = "Venue One",
            latitude = 0.0,
            longitude = 0.0,
            radiusMeters = 50f,
            beacon = BeaconIdentity(
                uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                major = 1,
                minor = 1,
            ),
        ),
        Venue(
            id = "venue-2",
            name = "Venue Two",
            latitude = 0.0,
            longitude = 0.0,
            radiusMeters = 50f,
            beacon = BeaconIdentity(
                uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                major = 2,
                minor = 1,
            ),
        ),
    )
}
