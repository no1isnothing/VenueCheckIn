package com.thebipolaroptimist.venuecheckin.domain

import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import javax.inject.Inject

class VenueStateMachine @Inject constructor() {

    // Pure reducer: (State, Event) -> State. Idempotent on duplicate ENTER/EXIT for the same
    // venue
    fun reduce(current: VenueState, event: VenueEvent): VenueState = when (event) {
        is VenueEvent.Enter -> onEnter(current, event.venue)
        is VenueEvent.Exit -> onExit(current, event.venue)
        is VenueEvent.BeaconSeen -> onBeaconSeen(current, event.rssi)
        VenueEvent.BeaconLost -> onBeaconLost(current)
    }

    // See VenueState.kt's venueOrNull() doc comment - overlapping geofences aren't handled
    // correctly yet (entering a second, overlapping venue silently drops tracking of the first).
    private fun onEnter(current: VenueState, venue: Venue): VenueState {
        return if (current.venueOrNull()?.id == venue.id) {
            // Already Inside/InRange for this venue
            // a duplicate ENTER is a no-op.
            current
        } else {
            VenueState.Inside(venue)
        }
    }

    private fun onExit(current: VenueState, venue: Venue): VenueState {
        return if (current.venueOrNull()?.id == venue.id) {
            VenueState.Outside
        } else {
            // Not the venue we're currently tracking - ignore.
            current
        }
    }

    private fun onBeaconSeen(current: VenueState, rssi: Int): VenueState {
        val venue = when (current) {
            is VenueState.Inside -> current.venue
            is VenueState.InRange -> current.venue
            VenueState.Outside -> return current // nothing to associate this reading with
        }
        return VenueState.InRange(venue, proximityFor(rssi), rssi)
    }

    private fun onBeaconLost(current: VenueState): VenueState {
        return if (current is VenueState.InRange) {
            VenueState.Inside(current.venue)
        } else {
            current
        }
    }
}
