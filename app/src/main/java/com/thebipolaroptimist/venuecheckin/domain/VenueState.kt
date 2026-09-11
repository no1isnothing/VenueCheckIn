package com.thebipolaroptimist.venuecheckin.domain

import com.thebipolaroptimist.venuecheckin.data.venue.Venue

enum class Proximity { IMMEDIATE, NEAR, FAR, UNKNOWN }

sealed interface VenueState {

    data object Outside : VenueState

    data class Inside(val venue: Venue) : VenueState

    data class InRange(
        val venue: Venue,
        val proximity: Proximity,
        val smoothedRssi: Int,
    ) : VenueState
}

// Shared by VenueStateMachine and VenueViewModel - which venue (if any) this state is tracking.
//
// KNOWN GAP - overlapping geofences: VenueState tracks at most one venue at a time. If two
// venues' geofences overlap (real symptom seen in manual testing: two 50m-radius venues ~71.5m
// apart do overlap, since 71.5m < 100m = sum of radii) and the device is in the overlap zone,
// entering the second venue while still "Inside" the first just switches state to the new venue
// (VenueStateMachine.onEnter) - the first venue's still-active geofence membership is silently
// dropped from tracking. If the device then leaves the second venue's radius while still inside
// the first's, the resulting EXIT looks correct locally but the app has already lost track of
// still being inside the first venue, so it goes to Outside instead of back to Inside(first).
// Needs a real design pass (e.g. VenueState holding a Set<Venue> instead of a single Venue) before
// this is production-solid for venues that can geographically overlap - not attempted here.
fun VenueState.venueOrNull(): Venue? = when (this) {
    is VenueState.Inside -> venue
    is VenueState.InRange -> venue
    VenueState.Outside -> null
}
