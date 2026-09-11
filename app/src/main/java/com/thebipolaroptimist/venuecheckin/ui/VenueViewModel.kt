package com.thebipolaroptimist.venuecheckin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thebipolaroptimist.venuecheckin.data.ble.BeaconSighting
import com.thebipolaroptimist.venuecheckin.data.ble.BleScanner
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceSource
import com.thebipolaroptimist.venuecheckin.data.location.LocationSource
import com.thebipolaroptimist.venuecheckin.data.venue.VenueRepository
import com.thebipolaroptimist.venuecheckin.domain.BeaconLostTimer
import com.thebipolaroptimist.venuecheckin.domain.Proximity
import com.thebipolaroptimist.venuecheckin.domain.RssiSmoother
import com.thebipolaroptimist.venuecheckin.domain.VenueState
import com.thebipolaroptimist.venuecheckin.domain.VenueStateMachine
import com.thebipolaroptimist.venuecheckin.domain.estimateDistanceMeters
import com.thebipolaroptimist.venuecheckin.domain.proximityFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LOG_ENTRY_LIMIT = 20

@HiltViewModel
class VenueViewModel @Inject constructor(
    private val venueRepository: VenueRepository,
    private val geofenceSource: GeofenceSource,
    private val bleScanner: BleScanner,
    private val locationSource: LocationSource,
    private val stateMachine: VenueStateMachine,
) : ViewModel() {

    private val _state = MutableStateFlow<VenueState>(VenueState.Outside)
    val state: StateFlow<VenueState> = _state.asStateFlow()

    // Temporary manual BLE-scanning test harness - verifying the scan/parse path against real
    // hardware ahead of geofence ENTER/EXIT wiring it up automatically. To be replaced/hidden
    // once the state machine drives scanning itself (planning.md §7/§8).
    private val testTarget = venueRepository.venues.first().beacon

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _currentReading = MutableStateFlow("Not scanning")
    val currentReading: StateFlow<String> = _currentReading.asStateFlow()

    private val _log = MutableStateFlow<List<ScanLogEntry>>(emptyList())
    val log: StateFlow<List<ScanLogEntry>> = _log.asStateFlow()

    // Scoped to one scan session (start -> stop), separate from viewModelScope, so stopping the
    // scan also tears down the beacon-lost timer's pending delay and its collector in one shot
    // rather than tracking multiple jobs individually.
    private var scanScope: CoroutineScope? = null

    fun toggleScanning() {
        if (scanScope != null) stopScanning() else startScanning()
    }

    private fun startScanning() {
        val smoother = RssiSmoother()
        val sessionScope = CoroutineScope(viewModelScope.coroutineContext + Job())
        scanScope = sessionScope
        val beaconLostTimer = BeaconLostTimer(sessionScope)

        _currentReading.value = "Scanning - waiting for beacon..."
        bleScanner.scan(testTarget)
            .onEach { sighting ->
                beaconLostTimer.beaconSeen()
                onSighting(sighting, smoother)
            }
            .onCompletion { _isScanning.value = false }
            .launchIn(sessionScope)

        sessionScope.launch {
            beaconLostTimer.lost.collect { lost -> if (lost) onBeaconLost() }
        }

        _isScanning.value = true
    }

    private fun stopScanning() {
        scanScope?.cancel()
        scanScope = null
        _isScanning.value = false
        _currentReading.value = "Not scanning"
    }

    private fun onSighting(sighting: BeaconSighting, smoother: RssiSmoother) {
        val smoothedRssi = smoother.add(sighting.rssi)
        val distanceMeters = estimateDistanceMeters(smoothedRssi, sighting.calibratedTxPower)
        val proximity = proximityFor(smoothedRssi)
        _currentReading.value = "Scanning - %s (~%.1f m)".format(proximity.displayName(), distanceMeters)
        _log.update { entries ->
            (listOf(ScanLogEntry(sighting.timestampMillis, proximity, distanceMeters)) + entries)
                .take(LOG_ENTRY_LIMIT)
        }
    }

    private fun onBeaconLost() {
        _currentReading.value = "Scanning - ${Proximity.UNKNOWN.displayName()} (beacon lost)"
        _log.update { entries ->
            (listOf(ScanLogEntry(System.currentTimeMillis(), Proximity.UNKNOWN, distanceMeters = null)) + entries)
                .take(LOG_ENTRY_LIMIT)
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanScope?.cancel()
    }
}
