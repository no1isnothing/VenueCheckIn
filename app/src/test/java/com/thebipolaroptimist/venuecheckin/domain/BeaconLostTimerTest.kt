package com.thebipolaroptimist.venuecheckin.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BeaconLostTimerTest {

    private val timeout = 5.seconds

    @Test
    fun `lost is false before any beaconSeen`() = runTest {
        val timer = BeaconLostTimer(scope = this, timeout = timeout)
        assertFalse(timer.lost.value)
    }

    @Test
    fun `lost stays false while within the timeout`() = runTest {
        val timer = BeaconLostTimer(scope = this, timeout = timeout)
        timer.beaconSeen()

        advanceTimeBy(timeout.inWholeMilliseconds - 500)
        runCurrent()

        assertFalse(timer.lost.value)
    }

    @Test
    fun `lost flips true once the timeout elapses with no new beaconSeen`() = runTest {
        val timer = BeaconLostTimer(scope = this, timeout = timeout)
        timer.beaconSeen()

        advanceTimeBy(timeout.inWholeMilliseconds + 100)
        runCurrent()

        assertTrue(timer.lost.value)
    }

    @Test
    fun `a fresh beaconSeen before the timeout resets the countdown`() = runTest {
        val timer = BeaconLostTimer(scope = this, timeout = timeout)
        timer.beaconSeen()

        advanceTimeBy(timeout.inWholeMilliseconds - 1_000)
        timer.beaconSeen() // resets the countdown with ~1s to spare from the original deadline

        advanceTimeBy(timeout.inWholeMilliseconds - 1_000)
        runCurrent()

        // Total elapsed since the first beaconSeen exceeds `timeout`, but the second beaconSeen
        // reset the clock, so neither individual gap reached the timeout.
        assertFalse(timer.lost.value)
    }

    @Test
    fun `beaconSeen after a loss recovers lost back to false`() = runTest {
        val timer = BeaconLostTimer(scope = this, timeout = timeout)
        timer.beaconSeen()
        advanceTimeBy(timeout.inWholeMilliseconds + 100)
        runCurrent()
        assertTrue(timer.lost.value)

        timer.beaconSeen()

        assertFalse(timer.lost.value)
    }
}
