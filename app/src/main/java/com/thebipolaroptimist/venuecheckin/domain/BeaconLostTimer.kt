package com.thebipolaroptimist.venuecheckin.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// planning.md §6 — N = 10s beacon-lost timeout (leaning). Reset on every beaconSeen();
// if nothing arrives within timeout, `lost` flips to true.
class BeaconLostTimer(
    private val scope: CoroutineScope,
    private val timeout: Duration = 10.seconds,
) {

    private val _lost = MutableStateFlow(false)
    val lost: Flow<Boolean> = _lost.asStateFlow()

    fun beaconSeen() {
        TODO("planning.md §6 — beacon-lost timer logic still pending")
    }
}
