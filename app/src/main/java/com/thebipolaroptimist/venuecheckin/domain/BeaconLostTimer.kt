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

// Hard-coded for interview scope, could be configured elsewhere in a real app
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
