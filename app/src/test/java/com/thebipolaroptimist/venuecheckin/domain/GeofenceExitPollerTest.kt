package com.thebipolaroptimist.venuecheckin.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GeofenceExitPollerTest {

    private val interval = 30.seconds

    @Test
    fun `onPoll is not called before the interval elapses`() = runTest {
        var pollCount = 0
        val poller = GeofenceExitPoller(scope = this, interval = interval) { pollCount++ }

        poller.start()
        advanceTimeBy(interval.inWholeMilliseconds - 500)
        runCurrent()

        assertEquals(0, pollCount)
    }

    @Test
    fun `onPoll is called once the interval elapses`() = runTest {
        var pollCount = 0
        val poller = GeofenceExitPoller(scope = this, interval = interval) { pollCount++ }

        poller.start()
        advanceTimeBy(interval.inWholeMilliseconds + 100)
        runCurrent()

        assertEquals(1, pollCount)
    }

    @Test
    fun `onPoll repeats every interval while active`() = runTest {
        var pollCount = 0
        val poller = GeofenceExitPoller(scope = this, interval = interval) { pollCount++ }

        poller.start()
        advanceTimeBy(interval.inWholeMilliseconds + 100)
        runCurrent()
        assertEquals(1, pollCount)

        advanceTimeBy(interval.inWholeMilliseconds)
        runCurrent()
        assertEquals(2, pollCount)
    }

    @Test
    fun `stop prevents further calls`() = runTest {
        var pollCount = 0
        val poller = GeofenceExitPoller(scope = this, interval = interval) { pollCount++ }

        poller.start()
        advanceTimeBy(interval.inWholeMilliseconds + 100)
        runCurrent()
        assertEquals(1, pollCount)

        poller.stop()
        advanceTimeBy(interval.inWholeMilliseconds * 3)
        runCurrent()

        assertEquals(1, pollCount) // no further polls after stop()
    }

    @Test
    fun `calling start twice without stop does not double-schedule`() = runTest {
        var pollCount = 0
        val poller = GeofenceExitPoller(scope = this, interval = interval) { pollCount++ }

        poller.start()
        poller.start() // second call should be a no-op - doesn't reset or double the countdown

        advanceTimeBy(interval.inWholeMilliseconds + 100)
        runCurrent()

        assertEquals(1, pollCount)
    }
}
