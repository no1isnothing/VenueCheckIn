package com.thebipolaroptimist.venuecheckin.data.ble

import com.thebipolaroptimist.venuecheckin.data.venue.BeaconIdentity

data class BeaconSighting(
    val identity: BeaconIdentity,
    val rssi: Int,
    val calibratedTxPower: Int,
    val timestampMillis: Long,
)
