package com.thebipolaroptimist.venuecheckin.ui

import com.thebipolaroptimist.venuecheckin.data.ble.BeaconSighting
import com.thebipolaroptimist.venuecheckin.data.venue.VenueRepository
import com.thebipolaroptimist.venuecheckin.domain.ContainmentChecker
import com.thebipolaroptimist.venuecheckin.domain.DEFAULT_BEACON_LOST_TIMEOUT
import com.thebipolaroptimist.venuecheckin.domain.Proximity
import com.thebipolaroptimist.venuecheckin.domain.VenueStateMachine
import com.thebipolaroptimist.venuecheckin.fakes.FakeBleScanner
import com.thebipolaroptimist.venuecheckin.fakes.FakeGeofenceSource
import com.thebipolaroptimist.venuecheckin.fakes.FakeLocationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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

// Exercises the "start/stop actually gates scanning" contract on the BLE side, via
// FakeBleScanner. VenueStateMachine.reduce() is still a stub (owned by a separate
// in-progress geofencing task) and isn't on this path yet - toggleScanning() drives
// BleScanner directly.
@OptIn(ExperimentalCoroutinesApi::class)
class VenueViewModelBleScanningTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var venueRepository: VenueRepository
    private lateinit var bleScanner: FakeBleScanner
    private lateinit var viewModel: VenueViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        venueRepository = VenueRepository()
        bleScanner = FakeBleScanner()
        viewModel = VenueViewModel(
            venueRepository = venueRepository,
            geofenceSource = FakeGeofenceSource(),
            bleScanner = bleScanner,
            locationSource = FakeLocationSource(),
            stateMachine = VenueStateMachine(),
            containmentChecker = ContainmentChecker(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleScanning starts scanning against the first venue's beacon`() = runTest(testDispatcher) {
        viewModel.toggleScanning()

        assertTrue(bleScanner.isScanning)
        assertEquals(venueRepository.venues.first().beacon, bleScanner.lastScanTarget)
        assertTrue(viewModel.isScanning.value)
    }

    @Test
    fun `emitting a sighting updates currentReading and prepends to the log`() = runTest(testDispatcher) {
        viewModel.toggleScanning()

        bleScanner.emit(
            BeaconSighting(
                identity = venueRepository.venues.first().beacon,
                rssi = -60,
                calibratedTxPower = -59,
                timestampMillis = 1_000L,
            ),
        )

        val entries = viewModel.log.value
        assertEquals(1, entries.size)

        val entry = entries.first()
        // rssi -60 is not > IMMEDIATE_RSSI_THRESHOLD (-60) but is > NEAR_RSSI_THRESHOLD (-75) -> NEAR
        assertEquals(Proximity.NEAR, entry.proximity)
        assertEquals(1_000L, entry.timestampMillis)
        // rssi (-60) slightly weaker than txPower (-59) -> distance estimate slightly over 1m
        assertNotNull(entry.distanceMeters)
        assertTrue(entry.distanceMeters!! > 1.0)

        assertTrue(viewModel.currentReading.value.contains("Scanning"))
    }

    @Test
    fun `beacon marked lost after timeout with no new sighting`() = runTest(testDispatcher) {
        viewModel.toggleScanning()

        bleScanner.emit(
            BeaconSighting(
                identity = venueRepository.venues.first().beacon,
                rssi = -60,
                calibratedTxPower = -59,
                timestampMillis = 1_000L,
            ),
        )
        assertEquals(Proximity.NEAR, viewModel.log.value.first().proximity)

        testDispatcher.scheduler.advanceTimeBy(DEFAULT_BEACON_LOST_TIMEOUT.inWholeMilliseconds + 100)
        testDispatcher.scheduler.runCurrent()

        val entries = viewModel.log.value
        val lostEntry = entries.first()
        assertEquals(Proximity.UNKNOWN, lostEntry.proximity)
        assertNull(lostEntry.distanceMeters)
        assertTrue(viewModel.currentReading.value.contains("Unknown"))
    }

    @Test
    fun `beacon reappearing before timeout does not mark it lost`() = runTest(testDispatcher) {
        viewModel.toggleScanning()

        bleScanner.emit(
            BeaconSighting(
                identity = venueRepository.venues.first().beacon,
                rssi = -60,
                calibratedTxPower = -59,
                timestampMillis = 1_000L,
            ),
        )

        testDispatcher.scheduler.advanceTimeBy(DEFAULT_BEACON_LOST_TIMEOUT.inWholeMilliseconds - 500)
        testDispatcher.scheduler.runCurrent()

        bleScanner.emit(
            BeaconSighting(
                identity = venueRepository.venues.first().beacon,
                rssi = -60,
                calibratedTxPower = -59,
                timestampMillis = 2_000L,
            ),
        )

        testDispatcher.scheduler.advanceTimeBy(DEFAULT_BEACON_LOST_TIMEOUT.inWholeMilliseconds - 500)
        testDispatcher.scheduler.runCurrent()

        // Second sighting reset the timer, so total elapsed time (timeout - 500 + timeout - 500)
        // being >= one full timeout doesn't matter - neither gap alone reached the timeout.
        assertEquals(Proximity.NEAR, viewModel.log.value.first().proximity)
    }

    @Test
    fun `log caps at 20 entries, newest first`() = runTest(testDispatcher) {
        viewModel.toggleScanning()

        for (i in 1..25) {
            bleScanner.emit(
                BeaconSighting(
                    identity = venueRepository.venues.first().beacon,
                    rssi = -60,
                    calibratedTxPower = -59,
                    timestampMillis = i.toLong(),
                ),
            )
        }

        val entries = viewModel.log.value
        assertEquals(20, entries.size)
        assertEquals(25L, entries.first().timestampMillis) // newest emitted -> first
        assertEquals(6L, entries.last().timestampMillis) // oldest surviving entry
    }

    @Test
    fun `toggling scanning again stops it`() = runTest(testDispatcher) {
        viewModel.toggleScanning()
        assertTrue(bleScanner.isScanning)

        viewModel.toggleScanning()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(bleScanner.isScanning)
        assertFalse(viewModel.isScanning.value)
        assertEquals("Not scanning", viewModel.currentReading.value)
    }
}
