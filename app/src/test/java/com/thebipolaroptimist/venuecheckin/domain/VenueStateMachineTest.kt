package com.thebipolaroptimist.venuecheckin.domain

import com.thebipolaroptimist.venuecheckin.data.venue.BeaconIdentity
import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.UUID

class VenueStateMachineTest {

    private val machine = VenueStateMachine()

    private val venueA = Venue(
        id = "venue-a",
        name = "Venue A",
        latitude = 0.0,
        longitude = 0.0,
        radiusMeters = 50f,
        beacon = BeaconIdentity(UUID.randomUUID(), 1, 1),
    )

    private val venueB = Venue(
        id = "venue-b",
        name = "Venue B",
        latitude = 1.0,
        longitude = 1.0,
        radiusMeters = 50f,
        beacon = BeaconIdentity(UUID.randomUUID(), 2, 1),
    )

    @Test
    fun `Enter from Outside moves to Inside`() {
        val result = machine.reduce(VenueState.Outside, VenueEvent.Enter(venueA))
        assertEquals(VenueState.Inside(venueA), result)
    }

    @Test
    fun `duplicate Enter for the same venue while Inside is a no-op`() {
        val current = VenueState.Inside(venueA)
        val result = machine.reduce(current, VenueEvent.Enter(venueA))
        assertSame(current, result)
    }

    @Test
    fun `duplicate Enter for the same venue while InRange is a no-op`() {
        val current = VenueState.InRange(venueA, Proximity.NEAR, -70)
        val result = machine.reduce(current, VenueEvent.Enter(venueA))
        assertSame(current, result)
    }

    @Test
    fun `Enter for a different venue while Inside another switches venues`() {
        val current = VenueState.Inside(venueA)
        val result = machine.reduce(current, VenueEvent.Enter(venueB))
        assertEquals(VenueState.Inside(venueB), result)
    }

    @Test
    fun `Exit while Inside that venue produces Outside`() {
        val current = VenueState.Inside(venueA)
        val result = machine.reduce(current, VenueEvent.Exit(venueA))
        assertEquals(VenueState.Outside, result)
    }

    @Test
    fun `Exit while InRange that venue produces Outside`() {
        val current = VenueState.InRange(venueA, Proximity.IMMEDIATE, -50)
        val result = machine.reduce(current, VenueEvent.Exit(venueA))
        assertEquals(VenueState.Outside, result)
    }

    @Test
    fun `duplicate Exit for a venue we are not tracking is a no-op`() {
        val current = VenueState.Outside
        val result = machine.reduce(current, VenueEvent.Exit(venueA))
        assertSame(current, result)
    }

    @Test
    fun `Exit for a venue that is not the current one is a no-op`() {
        val current = VenueState.Inside(venueA)
        val result = machine.reduce(current, VenueEvent.Exit(venueB))
        assertSame(current, result)
    }

    @Test
    fun `BeaconSeen while Inside moves to InRange with derived proximity`() {
        val current = VenueState.Inside(venueA)
        val result = machine.reduce(current, VenueEvent.BeaconSeen(-55))
        assertEquals(VenueState.InRange(venueA, Proximity.IMMEDIATE, -55), result)
    }

    @Test
    fun `BeaconSeen proximity buckets follow the rssi thresholds`() {
        val current = VenueState.Inside(venueA)
        assertEquals(
            Proximity.IMMEDIATE,
            (machine.reduce(current, VenueEvent.BeaconSeen(-59)) as VenueState.InRange).proximity,
        )
        assertEquals(
            Proximity.NEAR,
            (machine.reduce(current, VenueEvent.BeaconSeen(-70)) as VenueState.InRange).proximity,
        )
        assertEquals(
            Proximity.FAR,
            (machine.reduce(current, VenueEvent.BeaconSeen(-90)) as VenueState.InRange).proximity,
        )
    }

    @Test
    fun `BeaconSeen while already InRange updates proximity and rssi in place`() {
        val current = VenueState.InRange(venueA, Proximity.FAR, -90)
        val result = machine.reduce(current, VenueEvent.BeaconSeen(-55))
        assertEquals(VenueState.InRange(venueA, Proximity.IMMEDIATE, -55), result)
    }

    @Test
    fun `BeaconSeen while Outside is a no-op`() {
        val current = VenueState.Outside
        val result = machine.reduce(current, VenueEvent.BeaconSeen(-55))
        assertSame(current, result)
    }

    @Test
    fun `BeaconLost while InRange falls back to Inside`() {
        val current = VenueState.InRange(venueA, Proximity.NEAR, -70)
        val result = machine.reduce(current, VenueEvent.BeaconLost)
        assertEquals(VenueState.Inside(venueA), result)
    }

    @Test
    fun `BeaconLost while not InRange is a no-op`() {
        val current = VenueState.Inside(venueA)
        val result = machine.reduce(current, VenueEvent.BeaconLost)
        assertSame(current, result)
    }

    @Test
    fun `BeaconLost while Outside is a no-op`() {
        val current = VenueState.Outside
        val result = machine.reduce(current, VenueEvent.BeaconLost)
        assertSame(current, result)
    }
}
