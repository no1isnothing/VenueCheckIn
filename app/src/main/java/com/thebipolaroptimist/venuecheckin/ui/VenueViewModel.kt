package com.thebipolaroptimist.venuecheckin.ui

import androidx.lifecycle.ViewModel
import com.thebipolaroptimist.venuecheckin.domain.GeofenceLogEntry
import com.thebipolaroptimist.venuecheckin.domain.ScanLogEntry
import com.thebipolaroptimist.venuecheckin.domain.VenueMonitor
import com.thebipolaroptimist.venuecheckin.domain.VenueState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// Thin pass-through to VenueMonitor (an app-process-lifetime singleton, not tied to this
// ViewModel's lifecycle) - see DECISIONS.md "Scanning ownership moved to VenueMonitor". All the
// actual state/scanning logic lives there now; this class exists only because Compose's
// hiltViewModel() is the idiomatic way to hand a Composable its data.
@HiltViewModel
class VenueViewModel @Inject constructor(
    private val venueMonitor: VenueMonitor,
) : ViewModel() {

    val state: StateFlow<VenueState> = venueMonitor.state
    val currentReading: StateFlow<String> = venueMonitor.currentReading
    val isScanning: StateFlow<Boolean> = venueMonitor.isScanning
    val log: StateFlow<List<ScanLogEntry>> = venueMonitor.log
    val geofenceLog: StateFlow<List<GeofenceLogEntry>> = venueMonitor.geofenceLog

    fun registerGeofences() = venueMonitor.registerGeofences()
}
