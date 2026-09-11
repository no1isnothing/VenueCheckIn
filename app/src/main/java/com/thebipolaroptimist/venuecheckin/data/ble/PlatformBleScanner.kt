package com.thebipolaroptimist.venuecheckin.data.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import com.thebipolaroptimist.venuecheckin.data.venue.BeaconIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformBleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleScanner {

    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.bluetoothLeScanner

    override fun scan(target: BeaconIdentity): Flow<BeaconSighting> = callbackFlow {
        // TODO: planning.md §6 — manual iBeacon manufacturer-data parse + ScanFilter, see gotcha notes
        TODO("planning.md §6 — BLE scan/parse not yet implemented")
    }
}
