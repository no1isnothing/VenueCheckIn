package com.thebipolaroptimist.venuecheckin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thebipolaroptimist.venuecheckin.data.ble.BeaconSighting
import com.thebipolaroptimist.venuecheckin.data.ble.BleScanner
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceSource
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceTransitionEvent
import com.thebipolaroptimist.venuecheckin.data.location.LocationSource
import com.thebipolaroptimist.venuecheckin.data.venue.VenueRepository
import com.thebipolaroptimist.venuecheckin.domain.BeaconLostTimer
import com.thebipolaroptimist.venuecheckin.domain.ContainmentChecker
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
    private val containmentChecker: ContainmentChecker,
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

    // Separate from the BLE log above on purpose (see DECISIONS.md) - geofence transitions and
    // beacon readings are two different signals, kept visually and structurally distinct while
    // each is still being verified independently, ahead of combining them into one flow.
    private val _geofenceLog = MutableStateFlow<List<GeofenceLogEntry>>(emptyList())
    val geofenceLog: StateFlow<List<GeofenceLogEntry>> = _geofenceLog.asStateFlow()

    init {
        // Safe to collect before registerGeofences() is ever called - just nothing arrives yet.
        viewModelScope.launch {
            geofenceSource.transitions.collect { event -> onGeofenceTransition(event) }
        }
    }

    // Venue IDs currently considered "inside", per the geofence log - lets onGeofenceTransition
    // de-dupe rather than logging the same ENTER twice (once from the immediate manual check
    // below, once from Play Services' own INITIAL_TRIGGER_ENTER catching up later).
    private val insideVenueIds = mutableSetOf<String>()

    // Idempotent - re-registering the same venue IDs just replaces the prior request, so this is
    // safe to call more than once (e.g. if tapped again after a permission grant).
    fun registerGeofences() {
        geofenceSource.registerVenues(venueRepository.venues)

        // Play Services' own INITIAL_TRIGGER_ENTER evaluation is opportunistic - it uses
        // whatever location fix it already has, not necessarily a fresh one, and can take
        // anywhere from seconds to minutes to fire (planning.md §4 / DECISIONS.md "already
        // inside"). This fast path gets a fresh fix directly and checks containment immediately,
        // so the UI doesn't sit waiting on the OS's own timing for a venue already stood in.
        viewModelScope.launch {
            val point = locationSource.currentLocation() ?: return@launch
            venueRepository.venues.forEach { venue ->
                if (containmentChecker.isWithin(point, venue)) {
                    onGeofenceTransition(GeofenceTransitionEvent.Entered(venue.id))
                }
            }
        }
    }

    private fun onGeofenceTransition(event: GeofenceTransitionEvent) {
        val venueId = when (event) {
            is GeofenceTransitionEvent.Entered -> event.venueId
            is GeofenceTransitionEvent.Exited -> event.venueId
        }
        // Set.add()/remove() return false when there's no actual state change - skips logging
        // (and the redundant "Entered" from the slow official path once it catches up).
        val stateChanged = when (event) {
            is GeofenceTransitionEvent.Entered -> insideVenueIds.add(venueId)
            is GeofenceTransitionEvent.Exited -> insideVenueIds.remove(venueId)
        }
        if (!stateChanged) return

        val venueName = venueRepository.venues.find { it.id == venueId }?.name ?: venueId
        val message = when (event) {
            is GeofenceTransitionEvent.Entered -> "Entered $venueName"
            is GeofenceTransitionEvent.Exited -> "Exited $venueName"
        }
        _geofenceLog.update { entries ->
            (listOf(GeofenceLogEntry(System.currentTimeMillis(), message)) + entries)
                .take(LOG_ENTRY_LIMIT)
        }
    }

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
