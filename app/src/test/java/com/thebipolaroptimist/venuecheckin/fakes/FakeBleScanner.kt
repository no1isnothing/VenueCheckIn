package com.thebipolaroptimist.venuecheckin.fakes

import com.thebipolaroptimist.venuecheckin.data.ble.BeaconSighting
import com.thebipolaroptimist.venuecheckin.data.ble.BleScanner
import com.thebipolaroptimist.venuecheckin.data.venue.BeaconIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class FakeBleScanner : BleScanner {

    var isScanning: Boolean = false
        private set

    var lastScanTarget: BeaconIdentity? = null
        private set

    private val sightings = MutableSharedFlow<BeaconSighting>(extraBufferCapacity = 8)

    override fun scan(target: BeaconIdentity): Flow<BeaconSighting> {
        return sightings
            .onStart {
                isScanning = true
                lastScanTarget = target
            }
            .onCompletion {
                isScanning = false
            }
    }

    suspend fun emit(sighting: BeaconSighting) {
        sightings.emit(sighting)
    }
}
