package com.thebipolaroptimist.venuecheckin.fakes

import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceSource
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceTransitionEvent
import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeGeofenceSource : GeofenceSource {

    var registeredVenues: List<Venue> = emptyList()
        private set

    private val events = MutableSharedFlow<GeofenceTransitionEvent>(extraBufferCapacity = 8)
    override val transitions: Flow<GeofenceTransitionEvent> = events

    override fun registerVenues(venues: List<Venue>) {
        registeredVenues = venues
    }

    suspend fun emit(event: GeofenceTransitionEvent) {
        events.emit(event)
    }
}
