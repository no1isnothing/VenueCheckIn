package com.thebipolaroptimist.venuecheckin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DistanceEstimatorTest {

    @Test
    fun `rssi equal to calibrated tx power estimates about 1 meter`() {
        val distance = estimateDistanceMeters(rssi = -59, calibratedTxPower = -59)
        assertTrue("expected ~1.0m, got $distance", abs(distance - 1.0) < 0.001)
    }

    @Test
    fun `weaker rssi than tx power estimates more than 1 meter`() {
        val distance = estimateDistanceMeters(rssi = -80, calibratedTxPower = -59)
        assertTrue(distance > 1.0)
    }

    @Test
    fun `stronger rssi than tx power estimates less than 1 meter`() {
        val distance = estimateDistanceMeters(rssi = -40, calibratedTxPower = -59)
        assertTrue(distance < 1.0)
    }

    @Test
    fun `higher path loss exponent compresses distance estimate toward 1 meter for the same gap`() {
        val rssi = -80
        val txPower = -59
        val distanceLowExponent = estimateDistanceMeters(rssi, txPower, pathLossExponent = 2.0)
        val distanceHighExponent = estimateDistanceMeters(rssi, txPower, pathLossExponent = 4.0)

        assertTrue(distanceLowExponent > 1.0)
        assertTrue(distanceHighExponent > 1.0)
        assertTrue(distanceHighExponent < distanceLowExponent)
    }

    @Test
    fun `known values match the path-loss formula directly`() {
        // 10^((txPower - rssi) / (10*n)); txPower=-59, rssi=-79, n=2 -> 10^(20/20) = 10^1 = 10.0
        val distance = estimateDistanceMeters(rssi = -79, calibratedTxPower = -59, pathLossExponent = 2.0)
        assertEquals(10.0, distance, 0.001)
    }
}
