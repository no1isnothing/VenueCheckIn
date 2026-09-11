package com.thebipolaroptimist.venuecheckin.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceTransitionEvent
import com.thebipolaroptimist.venuecheckin.data.geofence.PlayServicesGeofenceSource
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var geofenceSource: PlayServicesGeofenceSource

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Timber.w("GeofencingEvent.fromIntent(intent) returned null")
            return
        }
        if (event.hasError()) {
            Timber.e("Geofencing error, code=%d", event.errorCode)
            return
        }

        val venueIds = event.triggeringGeofences?.map { it.requestId } ?: emptyList()
        if (venueIds.isEmpty()) return

        when (event.geofenceTransition) {

            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Timber.i("GeofenceBroadcastReceiver: real ENTER for %s", venueIds)
                venueIds.forEach { venueId ->
                    geofenceSource.onTransitionReceived(GeofenceTransitionEvent.Entered(venueId))
                }
                context.startForegroundService(Intent(context, VenueMonitorService::class.java))
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Timber.i("GeofenceBroadcastReceiver: real EXIT for %s", venueIds)
                venueIds.forEach { venueId ->
                    geofenceSource.onTransitionReceived(GeofenceTransitionEvent.Exited(venueId))
                }
                // No-op if the service isn't running
                context.stopService(Intent(context, VenueMonitorService::class.java))
            }

            else -> {
                // Only ENTER/EXIT are ever registered (see PlayServicesGeofenceSource) - anything
                // else is unexpected; ignore defensively rather than crash.
                Timber.w("Unexpected geofence transition type: %d", event.geofenceTransition)
            }
        }
    }
}
