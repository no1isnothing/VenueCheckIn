package com.thebipolaroptimist.venuecheckin.domain

import android.Manifest
import android.os.Build
import com.thebipolaroptimist.venuecheckin.data.ble.BeaconSighting
import com.thebipolaroptimist.venuecheckin.data.ble.BleScanner
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceSource
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceTransitionEvent
import com.thebipolaroptimist.venuecheckin.data.location.LocationSource
import com.thebipolaroptimist.venuecheckin.data.permission.PermissionChecker
import com.thebipolaroptimist.venuecheckin.data.service.ServiceStarter
import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import com.thebipolaroptimist.venuecheckin.data.venue.VenueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val LOG_ENTRY_LIMIT = 20

// App-process-lifetime singleton, deliberately NOT tied to any Activity/ViewModel's lifecycle -
// per DECISIONS.md, this needs to survive independently since the whole point of the
// geofence-gated scanning requirement is that a transition (and BLE scanning while Inside) can
// happen with no UI alive.
//
// Note on where BLE scanning actually executes here not VenueMonitorService - owns the
// scan coroutine. The service's only job is holding the foreground-service protection
@Singleton
class VenueMonitor @Inject constructor(
    private val venueRepository: VenueRepository,
    private val geofenceSource: GeofenceSource,
    private val bleScanner: BleScanner,
    private val locationSource: LocationSource,
    private val stateMachine: VenueStateMachine,
    private val containmentChecker: ContainmentChecker,
    private val permissionChecker: PermissionChecker,
    private val serviceStarter: ServiceStarter,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<VenueState>(VenueState.Outside)
    val state: StateFlow<VenueState> = _state.asStateFlow()

    private val _currentReading = MutableStateFlow("Outside")
    val currentReading: StateFlow<String> = _currentReading.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _log = MutableStateFlow<List<ScanLogEntry>>(emptyList())
    val log: StateFlow<List<ScanLogEntry>> = _log.asStateFlow()

    private val _geofenceLog = MutableStateFlow<List<GeofenceLogEntry>>(emptyList())
    val geofenceLog: StateFlow<List<GeofenceLogEntry>> = _geofenceLog.asStateFlow()

    // Venue IDs currently considered "inside", per the geofence log. For deduping events.
    private val insideVenueIds = mutableSetOf<String>()

    // Scoped to one BLE scan session (started on geofence ENTER, stopped on EXIT - requirement 4),
    private var scanScope: CoroutineScope? = null
    private var scanningVenueId: String? = null

    init {
        Timber.i("VenueMonitor created (process/singleton (re)initialized)")

        scope.launch {
            geofenceSource.transitions.collect { event ->
                try {
                    onGeofenceTransition(event)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to process geofence transition: $event")
                }
            }
        }
        // Register on init rather than behind a button - only takes effect if permission is
        // already granted from a previous session. VenueScreen requests permission on launch and
        // calls registerGeofences() again once granted, to cover the not-yet-granted case.
        if (permissionChecker.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            registerGeofences()
        }
    }

    // Re-registering the same venue IDs just replaces the prior request, so this is
    // safe to call more than once
    fun registerGeofences() {
        geofenceSource.registerVenues(venueRepository.venues)

        // Fast path to get correct location on start instead of waiting for geofence api
        scope.launch {
            val point = locationSource.currentLocation() ?: return@launch
            venueRepository.venues.forEach { venue ->
                if (containmentChecker.isWithin(point, venue)) {
                    onGeofenceTransition(GeofenceTransitionEvent.Entered(venue.id))
                    serviceStarter.startMonitoring()
                }
            }
        }
    }

    private fun onGeofenceTransition(event: GeofenceTransitionEvent) {
        val venueId = when (event) {
            is GeofenceTransitionEvent.Entered -> event.venueId
            is GeofenceTransitionEvent.Exited -> event.venueId
        }

        val stateChanged = when (event) {
            is GeofenceTransitionEvent.Entered -> insideVenueIds.add(venueId)
            is GeofenceTransitionEvent.Exited -> insideVenueIds.remove(venueId)
        }
        if (!stateChanged) return

        val venue = venueRepository.venues.find { it.id == venueId }
        val venueName = venue?.name ?: venueId
        val message = when (event) {
            is GeofenceTransitionEvent.Entered -> "Entered $venueName"
            is GeofenceTransitionEvent.Exited -> "Exited $venueName"
        }

        Timber.i("VenueMonitor: $message")
        _geofenceLog.update { entries ->
            (listOf(GeofenceLogEntry(System.currentTimeMillis(), message)) + entries)
                .take(LOG_ENTRY_LIMIT)
        }

        if (venue == null) return // unknown venue id - nothing to drive the state machine with
        val venueEvent = when (event) {
            is GeofenceTransitionEvent.Entered -> VenueEvent.Enter(venue)
            is GeofenceTransitionEvent.Exited -> VenueEvent.Exit(venue)
        }
        applyStateChange(venueEvent)
    }

    // Requirement 4: BLE scanning is gated on geofence presence - starts on confirmed ENTER,
    // stops on EXIT, never runs while outside every venue.
    private fun applyStateChange(event: VenueEvent) {
        val previous = _state.value
        val updated = stateMachine.reduce(previous, event)
        if (updated === previous) return
        _state.value = updated
        _currentReading.value = updated.describe()

        val updatedVenueId = updated.venueOrNull()?.id
        if (updatedVenueId != null && updatedVenueId != scanningVenueId) {
            val venue = venueRepository.venues.find { it.id == updatedVenueId } ?: return
            startScanningForVenue(venue)
        } else if (updatedVenueId == null && scanningVenueId != null) {
            stopScanning()
        }
    }

    private fun startScanningForVenue(venue: Venue) {
        stopScanning() // tear down any existing session first (e.g. switching venues)

        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (!permissionChecker.isGranted(scanPermission)) {
            // Degrade gracefully
            return
        }

        scanningVenueId = venue.id
        val smoother = RssiSmoother()
        val sessionScope = CoroutineScope(scope.coroutineContext + Job())
        scanScope = sessionScope
        val beaconLostTimer = BeaconLostTimer(sessionScope)

        bleScanner.scan(venue.beacon)
            .onEach { sighting ->
                beaconLostTimer.beaconSeen()
                onSighting(sighting, smoother)
            }
            .catch { e -> Timber.e(e, "BLE scan error for venue ${venue.id}") }
            .launchIn(sessionScope)
        _isScanning.value = true

        sessionScope.launch {
            beaconLostTimer.lost.collect { lost -> if (lost) onBeaconLost() }
        }
    }

    private fun stopScanning() {
        scanScope?.cancel()
        scanScope = null
        scanningVenueId = null
        _isScanning.value = false
    }

    private fun onSighting(sighting: BeaconSighting, smoother: RssiSmoother) {
        val smoothedRssi = smoother.add(sighting.rssi)
        applyStateChange(VenueEvent.BeaconSeen(smoothedRssi))

        val current = _state.value
        if (current is VenueState.InRange) {
            val distanceMeters = estimateDistanceMeters(smoothedRssi, sighting.calibratedTxPower)
            _log.update { entries ->
                (listOf(ScanLogEntry(sighting.timestampMillis, current.proximity, distanceMeters)) + entries)
                    .take(LOG_ENTRY_LIMIT)
            }
        }
    }

    private fun onBeaconLost() {
        applyStateChange(VenueEvent.BeaconLost)
        _log.update { entries ->
            (listOf(ScanLogEntry(System.currentTimeMillis(), Proximity.UNKNOWN, distanceMeters = null)) + entries)
                .take(LOG_ENTRY_LIMIT)
        }
    }

    private fun VenueState.describe(): String = when (this) {
        VenueState.Outside -> "Outside"
        is VenueState.Inside -> "Inside ${venue.name} - waiting for beacon..."
        is VenueState.InRange -> "Inside ${venue.name} - ${proximity.displayName()}"
    }
}
