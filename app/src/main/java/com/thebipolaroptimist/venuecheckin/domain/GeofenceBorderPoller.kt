package com.thebipolaroptimist.venuecheckin.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Hard-coded for interview scope, could be configured elsewhere in a real app - same reasoning as
// DEFAULT_BEACON_LOST_TIMEOUT in BeaconLostTimer.kt.
val DEFAULT_GEOFENCE_BORDER_POLL_INTERVAL: Duration = 30.seconds

// Real geofence EXIT/ENTER transitions typically arrive within 2-5 minutes (see DECISIONS.md), so
// polling past that point has diminishing value
val DEFAULT_GEOFENCE_BORDER_POLL_MAX_DURATION: Duration = 5.minutes

// Periodically invokes onPoll while active. The first call happens `interval` after start()
// (not immediately), then repeats every `interval` after that, until maxDuration total has
// elapsed, at which point it stops itself.
// This allows quicker transitions at a time when users are more likely to be entering/exiting
class GeofenceBorderPoller(
    private val scope: CoroutineScope,
    private val interval: Duration = DEFAULT_GEOFENCE_BORDER_POLL_INTERVAL,
    private val maxDuration: Duration = DEFAULT_GEOFENCE_BORDER_POLL_MAX_DURATION,
    private val onPoll: suspend () -> Unit,
) {

    private var pollJob: Job? = null

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            var elapsed = Duration.ZERO
            while (isActive && elapsed < maxDuration) {
                delay(interval)
                elapsed += interval
                onPoll()
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }
}
