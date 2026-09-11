package com.thebipolaroptimist.venuecheckin.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Hard-coded for interview scope, could be configured elsewhere in a real app - same reasoning as
// DEFAULT_BEACON_LOST_TIMEOUT in BeaconLostTimer.kt.
val DEFAULT_GEOFENCE_EXIT_POLL_INTERVAL: Duration = 30.seconds

// Periodically invokes onPoll while active. The first call happens `interval` after start()
// (not immediately), then repeats every `interval` after that. start() while already running is
// a no-op (doesn't reset the interval). stop() cancels immediately.
class GeofenceExitPoller(
    private val scope: CoroutineScope,
    private val interval: Duration = DEFAULT_GEOFENCE_EXIT_POLL_INTERVAL,
    private val onPoll: suspend () -> Unit,
) {

    private var pollJob: Job? = null

    fun start() {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (isActive) {
                delay(interval)
                onPoll()
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }
}
