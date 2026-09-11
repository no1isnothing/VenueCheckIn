package com.thebipolaroptimist.venuecheckin.domain

import com.thebipolaroptimist.venuecheckin.data.venue.Venue

sealed interface VenueEvent {
    data class Enter(val venue: Venue) : VenueEvent
    data class Exit(val venue: Venue) : VenueEvent
    data class BeaconSeen(val rssi: Int) : VenueEvent
    data object BeaconLost : VenueEvent
}
