package com.thebipolaroptimist.venuecheckin.data.venue

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VenueRepository @Inject constructor() {

    // TODO: real, mockable coordinates — see README
    val venues: List<Venue> = listOf(
     /*   Venue(
            id = "venue-1",
            name = "Lobert",
            latitude = 30.175513,
            longitude = -97.272575,
            radiusMeters = 10f,
            beacon = BeaconIdentity(
                uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                major = 1,
                minor = 1,
            ),
        ),*/
        Venue(
            id = "venue-2",
            name = "Lot 25",
            latitude = 30.175302,
            longitude = 97.272680,
            radiusMeters = 50f,
            // Update these if you actually put a beacon over there.
            beacon = BeaconIdentity(
                uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                major = 2,
                minor = 1,
            ),
        ),
        Venue(
            id = "venue-3",
            name = "House",
            latitude = 30.175328,
            longitude = -97.272628,
            radiusMeters = 50f,
            beacon = BeaconIdentity(
                uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                major = 1,
                minor = 1,
            ),
        ),
    )
}
