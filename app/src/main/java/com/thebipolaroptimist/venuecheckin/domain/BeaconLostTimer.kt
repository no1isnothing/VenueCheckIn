package com.thebipolaroptimist.venuecheckin.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// 5s - short on purpose, so "beacon lost" is easy to trigger and observe while developing/testing
// against a nearby advertiser (stop it, see the state flip within a few seconds, no long wait).
// A real deployment would likely want this longer - tens of seconds - to tolerate a couple of
// dropped packets or brief scan throttling without flapping to "lost." See DECISIONS.md.
// Hard-coded for interview scope; would be configurable (server-driven or a preference) in a real app.
val DEFAULT_BEACON_LOST_TIMEOUT: Duration = 5.seconds

// Tracks whether a beacon has been seen recently.
class BeaconLostTimer(
    private val scope: CoroutineScope,
    private val timeout: Duration = DEFAULT_BEACON_LOST_TIMEOUT,
) {

    private val _lost = MutableStateFlow(false)
    val lost: StateFlow<Boolean> = _lost.asStateFlow()

    private var timeoutJob: Job? = null

    fun beaconSeen() {
        timeoutJob?.cancel()
        _lost.value = false
        timeoutJob = scope.launch {
            delay(timeout)
            _lost.value = true
        }
    }
}
