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
