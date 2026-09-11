package com.thebipolaroptimist.venuecheckin.data.geofence

import android.content.Context
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayServicesGeofenceSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : GeofenceSource {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val _transitions = MutableSharedFlow<GeofenceTransitionEvent>(extraBufferCapacity = 8)
    override val transitions: Flow<GeofenceTransitionEvent> = _transitions

    override fun registerVenues(venues: List<Venue>) {
        // TODO: planning.md §4 — PendingIntent must be FLAG_MUTABLE, see gotcha note
        TODO("planning.md §4 — geofence registration not yet implemented")
    }
}
