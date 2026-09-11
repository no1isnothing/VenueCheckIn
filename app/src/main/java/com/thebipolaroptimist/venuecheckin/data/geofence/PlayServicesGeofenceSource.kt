package com.thebipolaroptimist.venuecheckin.data.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import com.thebipolaroptimist.venuecheckin.service.GeofenceBroadcastReceiver
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

    @SuppressLint("MissingPermission")
    override fun registerVenues(venues: List<Venue>) {
        val geofences = venues.map { venue ->
            Geofence.Builder()
                .setRequestId(venue.id)
                .setCircularRegion(venue.latitude, venue.longitude, venue.radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        geofencingClient.addGeofences(request, geofencePendingIntent())
    }
    fun onTransitionReceived(event: GeofenceTransitionEvent) {
        _transitions.tryEmit(event)
    }

    // Android 12+ requires FLAG_MUTABLE here. This opposite of the usual lint guidance.
    // With FLAG_IMMUTABLE, GeofencingEvent.fromIntent() can't read the transition data in the receiver and nothing fires.
    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            GEOFENCE_PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 0
    }
}
