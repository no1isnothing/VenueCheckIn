package com.thebipolaroptimist.venuecheckin.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.thebipolaroptimist.venuecheckin.data.venue.BeaconIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val APPLE_MANUFACTURER_ID = 0x004C
private const val IBEACON_SUBTYPE: Byte = 0x02
private const val IBEACON_SUBTYPE_LENGTH: Byte = 0x15
private const val IBEACON_PAYLOAD_LENGTH = 23 // subtype(1) + length(1) + uuid(16) + major(2) + minor(2) + txPower(1)

@Singleton
class PlatformBleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleScanner {

    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.bluetoothLeScanner

    // Permission (BLUETOOTH_SCAN / ACCESS_FINE_LOCATION depending on SDK) is checked by the
    // caller before scan() is invoked - see VenueScreen's permission gate.
    @SuppressLint("MissingPermission")
    override fun scan(target: BeaconIdentity): Flow<BeaconSighting> = callbackFlow {
        val scanner = bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("BLE scanner unavailable (Bluetooth off or unsupported)"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val sighting = parseIBeaconSighting(result) ?: return
                if (sighting.identity == target) {
                    trySend(sighting)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed, errorCode=$errorCode"))
            }
        }

        scanner.startScan(listOf(buildIBeaconScanFilter(target)), defaultScanSettings(), callback)
        awaitClose { scanner.stopScan(callback) }
    }
}

private fun defaultScanSettings(): ScanSettings =
    ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

// Radio-level filter matching Apple manufacturer data + iBeacon subtype/length + this venue's
// UUID; major/minor/txPower are left as "don't care" in the mask (0x00) and matched in software
// in parseIBeaconSighting/scan, since one UUID can carry multiple major/minor beacons. Restored
// after being removed mid-debugging on a diagnosis (silently dropping all results) that turned
// out to more likely be a missing ACCESS_FINE_LOCATION grant, not this filter - see DECISIONS.md.
private fun buildIBeaconScanFilter(target: BeaconIdentity): ScanFilter {
    val data = ByteArray(IBEACON_PAYLOAD_LENGTH)
    val mask = ByteArray(IBEACON_PAYLOAD_LENGTH)

    data[0] = IBEACON_SUBTYPE
    data[1] = IBEACON_SUBTYPE_LENGTH
    mask[0] = 0xFF.toByte()
    mask[1] = 0xFF.toByte()

    uuidToBytes(target.uuid).copyInto(data, destinationOffset = 2)
    for (i in 2 until 18) {
        mask[i] = 0xFF.toByte()
    }
    // bytes 18..22 (major, minor, txPower) stay zeroed in both data and mask -> unmatched

    return ScanFilter.Builder()
        .setManufacturerData(APPLE_MANUFACTURER_ID, data, mask)
        .build()
}

private fun parseIBeaconSighting(result: ScanResult): BeaconSighting? {
    val manufacturerData = result.scanRecord?.getManufacturerSpecificData(APPLE_MANUFACTURER_ID)
        ?: return null
    if (manufacturerData.size < IBEACON_PAYLOAD_LENGTH) return null
    if (manufacturerData[0] != IBEACON_SUBTYPE || manufacturerData[1] != IBEACON_SUBTYPE_LENGTH) return null

    val uuid = bytesToUuid(manufacturerData.copyOfRange(2, 18))
    val major = readUInt16BigEndian(manufacturerData, offset = 18)
    val minor = readUInt16BigEndian(manufacturerData, offset = 20)
    val calibratedTxPower = manufacturerData[22].toInt() // signed byte, sign-extension is correct here

    return BeaconSighting(
        identity = BeaconIdentity(uuid, major, minor),
        rssi = result.rssi,
        calibratedTxPower = calibratedTxPower,
        timestampMillis = System.currentTimeMillis(),
    )
}

private fun readUInt16BigEndian(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

private fun uuidToBytes(uuid: UUID): ByteArray =
    ByteBuffer.allocate(16)
        .putLong(uuid.mostSignificantBits)
        .putLong(uuid.leastSignificantBits)
        .array()

private fun bytesToUuid(bytes: ByteArray): UUID {
    val buffer = ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long)
}
