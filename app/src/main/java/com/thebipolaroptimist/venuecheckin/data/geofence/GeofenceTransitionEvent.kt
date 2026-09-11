package com.thebipolaroptimist.venuecheckin.data.geofence

sealed interface GeofenceTransitionEvent {
    data class Entered(val venueId: String) : GeofenceTransitionEvent
    data class Exited(val venueId: String) : GeofenceTransitionEvent
}
