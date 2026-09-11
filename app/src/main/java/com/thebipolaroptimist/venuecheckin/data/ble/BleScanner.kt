package com.thebipolaroptimist.venuecheckin.data.ble

import com.thebipolaroptimist.venuecheckin.data.venue.BeaconIdentity
import kotlinx.coroutines.flow.Flow

interface BleScanner {
    // Scanning is active only while the returned Flow is collected; cancelling collection
    // must synchronously stop the radio scan (planning.md §7 — EXIT must stop scanning
    // immediately, no delay). Not enforced by this interface, just the contract impls must honor.
    fun scan(target: BeaconIdentity): Flow<BeaconSighting>
}
