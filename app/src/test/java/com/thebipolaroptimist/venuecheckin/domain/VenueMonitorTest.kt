package com.thebipolaroptimist.venuecheckin.domain

import android.Manifest
import com.thebipolaroptimist.venuecheckin.data.ble.BeaconSighting
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceTransitionEvent
import com.thebipolaroptimist.venuecheckin.data.venue.VenueRepository
import com.thebipolaroptimist.venuecheckin.fakes.FakeBleScanner
import com.thebipolaroptimist.venuecheckin.fakes.FakeGeofenceSource
import com.thebipolaroptimist.venuecheckin.fakes.FakeLocationSource
import com.thebipolaroptimist.venuecheckin.fakes.FakePermissionChecker
import com.thebipolaroptimist.venuecheckin.fakes.FakeServiceStarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Exercises requirement 4 (scanning gated on geofence presence) end to end through VenueMonitor -
// the app-process-lifetime singleton that owns this now (moved out of VenueViewModel; see
// DECISIONS.md "Scanning ownership moved to VenueMonitor"). A real geofence ENTER starts scanning
// for that venue's beacon, EXIT stops it, everything routes through the real VenueStateMachine.
@OptIn(ExperimentalCoroutinesApi::class)
class VenueMonitorTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var venueRepository: VenueRepository
    private lateinit var geofenceSource: FakeGeofenceSource
    private lateinit var bleScanner: FakeBleScanner
    private lateinit var locationSource: FakeLocationSource
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var serviceStarter: FakeServiceStarter
    private lateinit var monitor: VenueMonitor

    private val allPermissionsGranted = setOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        venueRepository = VenueRepository()
        geofenceSource = FakeGeofenceSource()
        bleScanner = FakeBleScanner()
        locationSource = FakeLocationSource()
        permissionChecker = FakePermissionChecker(granted = allPermissionsGranted)
        serviceStarter = FakeServiceStarter()
        monitor = VenueMonitor(
            venueRepository = venueRepository,
            geofenceSource = geofenceSource,
            bleScanner = bleScanner,
            locationSource = locationSource,
            stateMachine = VenueStateMachine(),
            containmentChecker = ContainmentChecker(),
            permissionChecker = permissionChecker,
            serviceStarter = serviceStarter,
        )
    }

    @After
    fun tearDown() {
        // Must run before resetMain() - see VenueMonitor.cancelForTesting() doc. Without this, a
        // test that leaves GeofenceBorderPoller's loop running (beacon lost, never confirmed
        // outside) hangs runTest's own cleanup instead of failing cleanly.
        monitor.cancelForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun `geofence ENTER starts scanning for that venue's beacon`() = runTest(testDispatcher) {
        val venue = venueRepository.venues.first()

        geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))

        assertTrue(bleScanner.isScanning)
        assertTrue(monitor.isScanning.value)
        assertEquals(venue.beacon, bleScanner.lastScanTarget)
        assertEquals(VenueState.Inside(venue), monitor.state.value)
        assertTrue(monitor.currentReading.value.contains(venue.name))
    }

    @Test
    fun `geofence EXIT stops scanning`() = runTest(testDispatcher) {
        val venue = venueRepository.venues.first()
        geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))
        assertTrue(bleScanner.isScanning)

        geofenceSource.emit(GeofenceTransitionEvent.Exited(venue.id))

        assertFalse(bleScanner.isScanning)
        assertFalse(monitor.isScanning.value)
        assertEquals(VenueState.Outside, monitor.state.value)
        assertEquals("Outside", monitor.currentReading.value)
    }

    @Test
    fun `entering a different venue retargets scanning to it`() = runTest(testDispatcher) {
        // VenueMonitor resolves a GeofenceTransitionEvent's venue ID via
        // venueRepository.venues.find { } - there's no fake/interface for VenueRepository (it's
        // plain hardcoded data, not a framework boundary), so this test's fixture venues have to
        // actually be in there. Skip rather than fail if fewer than 2 are currently configured
        // (e.g. mid real-device testing with venues temporarily commented out) - this isn't
        // exercising anything specific to a particular pair of venues, just "switching targets."
        org.junit.Assume.assumeTrue(
            "requires at least 2 configured venues",
            venueRepository.venues.size >= 2,
        )
        val venueA = venueRepository.venues[0]
        val venueB = venueRepository.venues[1]

        geofenceSource.emit(GeofenceTransitionEvent.Entered(venueA.id))
        assertEquals(venueA.beacon, bleScanner.lastScanTarget)

        geofenceSource.emit(GeofenceTransitionEvent.Entered(venueB.id))

        assertTrue(bleScanner.isScanning)
        assertEquals(venueB.beacon, bleScanner.lastScanTarget)
        assertEquals(VenueState.Inside(venueB), monitor.state.value)
    }

    @Test
    fun `real EXIT is applied when a fresh location check confirms outside`() = runTest(testDispatcher) {
        val venue = venueRepository.venues.first()
        locationSource.location = GeoPoint(venue.latitude, venue.longitude)
        geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))
        assertEquals(VenueState.Inside(venue), monitor.state.value)

        locationSource.location = GeoPoint(venue.latitude + 0.01, venue.longitude)
        geofenceSource.emit(GeofenceTransitionEvent.Exited(venue.id))

        assertEquals(VenueState.Outside, monitor.state.value)
        assertFalse(bleScanner.isScanning)
    }

    @Test
    fun `stale delayed EXIT for an earlier visit is ignored when location still confirms inside`() =
        runTest(testDispatcher) {
            val venue = venueRepository.venues.first()

            // Real ENTER for the current visit - a fresh location check confirms we're inside.
            locationSource.location = GeoPoint(venue.latitude, venue.longitude)
            geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))
            assertEquals(VenueState.Inside(venue), monitor.state.value)

            // A stale/delayed EXIT arrives (e.g. Play Services' late notice of an earlier
            // departure that's since been re-entered) - location, checked fresh right now, still
            // confirms we're inside, so it must be ignored rather than forcing Outside. This is
            // the exact bug documented in DECISIONS.md under "stale/out-of-order geofence
            // transitions."
            geofenceSource.emit(GeofenceTransitionEvent.Exited(venue.id))

            assertEquals(VenueState.Inside(venue), monitor.state.value)
            assertTrue(bleScanner.isScanning)
            assertTrue(monitor.geofenceLog.value.none { it.message.startsWith("Exited") })
        }

    @Test
    fun `geofence transition falls back to trusting the raw event when location is unavailable`() =
        runTest(testDispatcher) {
            val venue = venueRepository.venues.first()
            locationSource.location = null

            geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))

            assertEquals(VenueState.Inside(venue), monitor.state.value)
            assertTrue(bleScanner.isScanning)
        }

    @Test
    fun `scanning does not start without scan permission`() = runTest(testDispatcher) {
        // Deny everything, not just BLUETOOTH_SCAN: Build.VERSION.SDK_INT reads as 0 in a plain
        // JVM unit test (no Robolectric), so the SDK-gated permission check always takes the
        // ACCESS_FINE_LOCATION branch here regardless of real device API level.
        permissionChecker.granted = emptySet()
        val venue = venueRepository.venues.first()

        geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))

        assertFalse(bleScanner.isScanning)
        assertFalse(monitor.isScanning.value)
        // State still updates (Inside) even though scanning couldn't start - degrades gracefully.
        assertEquals(VenueState.Inside(venue), monitor.state.value)
    }

    @Test
    fun `a sighting updates state to InRange and prepends to the log`() = runTest(testDispatcher) {
        val venue = venueRepository.venues.first()
        geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))

        bleScanner.emit(
            BeaconSighting(
                identity = venue.beacon,
                rssi = -60,
                calibratedTxPower = -59,
                timestampMillis = 1_000L,
            ),
        )

        val state = monitor.state.value
        assertTrue(state is VenueState.InRange)
        // rssi -60 is not > IMMEDIATE_RSSI_THRESHOLD (-60) but is > NEAR_RSSI_THRESHOLD (-75) -> NEAR
        assertEquals(Proximity.NEAR, (state as VenueState.InRange).proximity)

        val entries = monitor.log.value
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals(Proximity.NEAR, entry.proximity)
        assertEquals(1_000L, entry.timestampMillis)
        assertNotNull(entry.distanceMeters)
        assertTrue(entry.distanceMeters!! > 1.0)
    }

    @Test
    fun `beacon log caps at 20 entries, newest first`() = runTest(testDispatcher) {
        val venue = venueRepository.venues.first()
        geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))

        for (i in 1..25) {
            bleScanner.emit(
                BeaconSighting(
                    identity = venue.beacon,
                    rssi = -60,
                    calibratedTxPower = -59,
                    timestampMillis = i.toLong(),
                ),
            )
        }

        val entries = monitor.log.value
        assertEquals(20, entries.size)
        assertEquals(25L, entries.first().timestampMillis)
        assertEquals(6L, entries.last().timestampMillis)
    }

    @Test
    fun `beacon marked lost after timeout with no new sighting`() = runTest(testDispatcher) {
        val venue = venueRepository.venues.first()
        geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))
        bleScanner.emit(
            BeaconSighting(
                identity = venue.beacon,
                rssi = -60,
                calibratedTxPower = -59,
                timestampMillis = 1_000L,
            ),
        )
        assertTrue(monitor.state.value is VenueState.InRange)

        testDispatcher.scheduler.advanceTimeBy(DEFAULT_BEACON_LOST_TIMEOUT.inWholeMilliseconds + 100)
        testDispatcher.scheduler.runCurrent()

        // Beacon lost falls back to Inside(venue) in the state machine - the beacon-lost log
        // entry (Proximity.UNKNOWN) is logged separately, since Inside carries no proximity.
        assertEquals(VenueState.Inside(venue), monitor.state.value)
        val lostEntry = monitor.log.value.first()
        assertEquals(Proximity.UNKNOWN, lostEntry.proximity)
        assertNull(lostEntry.distanceMeters)
    }

    @Test
    fun `exit poller forces EXIT when location confirms outside after beacon lost`() = runTest(testDispatcher) {
        val venue = venueRepository.venues.first()
        geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))
        bleScanner.emit(
            BeaconSighting(
                identity = venue.beacon,
                rssi = -60,
                calibratedTxPower = -59,
                timestampMillis = 1_000L,
            ),
        )
        assertTrue(monitor.state.value is VenueState.InRange)

        // Beacon lost.
        testDispatcher.scheduler.advanceTimeBy(DEFAULT_BEACON_LOST_TIMEOUT.inWholeMilliseconds + 100)
        testDispatcher.scheduler.runCurrent()
        assertEquals(VenueState.Inside(venue), monitor.state.value)

        // Location confirms we're well outside the venue's radius.
        locationSource.location = GeoPoint(venue.latitude + 0.01, venue.longitude)

        testDispatcher.scheduler.advanceTimeBy(DEFAULT_GEOFENCE_BORDER_POLL_INTERVAL.inWholeMilliseconds + 100)
        testDispatcher.scheduler.runCurrent()

        assertEquals(VenueState.Outside, monitor.state.value)
        assertEquals("Exited ${venue.name}", monitor.geofenceLog.value.first().message)
        assertFalse(bleScanner.isScanning)
        assertFalse(monitor.isScanning.value)
    }

    @Test
    fun `exit poller does not force EXIT when location still confirms inside`() = runTest(testDispatcher) {
        val venue = venueRepository.venues.first()
        geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))
        bleScanner.emit(
            BeaconSighting(
                identity = venue.beacon,
                rssi = -60,
                calibratedTxPower = -59,
                timestampMillis = 1_000L,
            ),
        )

        testDispatcher.scheduler.advanceTimeBy(DEFAULT_BEACON_LOST_TIMEOUT.inWholeMilliseconds + 100)
        testDispatcher.scheduler.runCurrent()

        // Location still confirms we're at the venue's center - well within the radius.
        locationSource.location = GeoPoint(venue.latitude, venue.longitude)

        testDispatcher.scheduler.advanceTimeBy(DEFAULT_GEOFENCE_BORDER_POLL_INTERVAL.inWholeMilliseconds + 100)
        testDispatcher.scheduler.runCurrent()

        assertEquals(VenueState.Inside(venue), monitor.state.value)
        assertTrue(monitor.geofenceLog.value.none { it.message.startsWith("Exited") })
    }

    @Test
    fun `border poller flips back to Entered when the user returns after a forced EXIT`() =
        runTest(testDispatcher) {
            // Reproduces the real bug this was built to fix: exit via the poller, then a quick
            // re-entry before any real geofence ENTER has a chance to fire - previously nothing
            // was left watching for this, since stopScanning() (triggered by the forced EXIT)
            // used to tear down the poller along with the rest of the scan session.
            val venue = venueRepository.venues.first()
            geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))
            bleScanner.emit(
                BeaconSighting(
                    identity = venue.beacon,
                    rssi = -60,
                    calibratedTxPower = -59,
                    timestampMillis = 1_000L,
                ),
            )

            testDispatcher.scheduler.advanceTimeBy(DEFAULT_BEACON_LOST_TIMEOUT.inWholeMilliseconds + 100)
            testDispatcher.scheduler.runCurrent()

            // First poll: confirmed outside - forces EXIT.
            locationSource.location = GeoPoint(venue.latitude + 0.01, venue.longitude)
            testDispatcher.scheduler.advanceTimeBy(DEFAULT_GEOFENCE_BORDER_POLL_INTERVAL.inWholeMilliseconds + 100)
            testDispatcher.scheduler.runCurrent()
            assertEquals(VenueState.Outside, monitor.state.value)
            assertFalse(bleScanner.isScanning)

            // User quickly returns. The same poller instance (never torn down) should catch this
            // on its next poll and flip back to Entered, resuming scanning.
            locationSource.location = GeoPoint(venue.latitude, venue.longitude)
            testDispatcher.scheduler.advanceTimeBy(DEFAULT_GEOFENCE_BORDER_POLL_INTERVAL.inWholeMilliseconds + 100)
            testDispatcher.scheduler.runCurrent()

            assertEquals(VenueState.Inside(venue), monitor.state.value)
            assertTrue(bleScanner.isScanning)
            assertEquals("Entered ${venue.name}", monitor.geofenceLog.value.first().message)
        }

    @Test
    fun `border poller keeps running its full window even after the beacon reappears`() =
        runTest(testDispatcher) {
            // Deliberate design choice, see DECISIONS.md "GeofenceBorderPoller": once triggered,
            // the poller runs its full maxDuration window regardless of subsequent beacon
            // sightings, rather than being stopped early - this is what lets it also catch a
            // later exit within the same window. The tradeoff is exactly what this test shows: a
            // stale/inaccurate location reading can force an EXIT even while the beacon is
            // actively being seen.
            val venue = venueRepository.venues.first()
            geofenceSource.emit(GeofenceTransitionEvent.Entered(venue.id))
            bleScanner.emit(
                BeaconSighting(
                    identity = venue.beacon,
                    rssi = -60,
                    calibratedTxPower = -59,
                    timestampMillis = 1_000L,
                ),
            )

            testDispatcher.scheduler.advanceTimeBy(DEFAULT_BEACON_LOST_TIMEOUT.inWholeMilliseconds + 100)
            testDispatcher.scheduler.runCurrent()

            bleScanner.emit(
                BeaconSighting(
                    identity = venue.beacon,
                    rssi = -60,
                    calibratedTxPower = -59,
                    timestampMillis = 2_000L,
                ),
            )
            assertTrue(monitor.state.value is VenueState.InRange)

            locationSource.location = GeoPoint(venue.latitude + 0.01, venue.longitude)
            testDispatcher.scheduler.advanceTimeBy(DEFAULT_GEOFENCE_BORDER_POLL_INTERVAL.inWholeMilliseconds + 100)
            testDispatcher.scheduler.runCurrent()

            assertEquals(VenueState.Outside, monitor.state.value)
        }

    @Test
    fun `already-inside fast path starts scanning and monitoring immediately on registerGeofences`() =
        runTest(testDispatcher) {
            val venue = venueRepository.venues.first()
            val fakeLocation = FakeLocationSource().apply {
                location = GeoPoint(venue.latitude, venue.longitude)
            }
            val vm = VenueMonitor(
                venueRepository = venueRepository,
                geofenceSource = FakeGeofenceSource(),
                bleScanner = bleScanner,
                locationSource = fakeLocation,
                stateMachine = VenueStateMachine(),
                containmentChecker = ContainmentChecker(),
                permissionChecker = permissionChecker,
                serviceStarter = serviceStarter,
            )

            vm.registerGeofences()

            assertTrue(bleScanner.isScanning)
            assertEquals(venue.beacon, bleScanner.lastScanTarget)
            assertEquals(VenueState.Inside(venue), vm.state.value)
            // The fast path is responsible for protecting the process (starting the foreground
            // service) since it isn't triggered by GeofenceBroadcastReceiver, which handles that
            // itself for real transitions - see DECISIONS.md.
            assertTrue(serviceStarter.isMonitoring)

            vm.cancelForTesting()
        }
}
