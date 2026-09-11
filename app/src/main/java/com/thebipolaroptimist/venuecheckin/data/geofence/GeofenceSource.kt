package com.thebipolaroptimist.venuecheckin.data.geofence

import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import kotlinx.coroutines.flow.Flow

interface GeofenceSource {
    fun registerVenues(venues: List<Venue>)
    val transitions: Flow<GeofenceTransitionEvent>
}
