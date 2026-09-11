package com.thebipolaroptimist.venuecheckin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssiSmootherTest {

    @Test
    fun `single sample returns that sample`() {
        val smoother = RssiSmoother(windowSize = 5)
        assertEquals(-60, smoother.add(-60))
    }

    @Test
    fun `fewer than window size samples averages only what has been added`() {
        val smoother = RssiSmoother(windowSize = 5)
        smoother.add(-60)
        smoother.add(-70)
        // average of -60, -70, -65 = -65
        assertEquals(-65, smoother.add(-65))
    }

    @Test
    fun `exactly window size samples averages all of them`() {
        val smoother = RssiSmoother(windowSize = 5)
        val values = listOf(-60, -62, -64, -66, -68)
        var result = 0
        for (v in values) result = smoother.add(v)
        // average of -60,-62,-64,-66,-68 = -64
        assertEquals(-64, result)
    }

    @Test
    fun `rolling past window size drops the oldest sample`() {
        val smoother = RssiSmoother(windowSize = 3)
        smoother.add(-60)
        smoother.add(-60)
        val afterFull = smoother.add(-60)
        assertEquals(-60, afterFull)

        // window was [-60,-60,-60]; pushing -90 drops the oldest -60 -> [-60,-60,-90]
        val afterRoll = smoother.add(-90)
        assertEquals(-70, afterRoll)
    }

    @Test
    fun `smoothing reduces variance versus raw jittery input`() {
        val smoother = RssiSmoother(windowSize = 5)
        val raw = listOf(-60, -90, -55, -95, -58, -92, -57, -93, -59, -91)
        val smoothedValues = raw.map { smoother.add(it) }

        val rawRange = raw.max() - raw.min()
        val smoothedRange = smoothedValues.max() - smoothedValues.min()

        assertTrue(
            "expected smoothed range ($smoothedRange) to be less than raw range ($rawRange)",
            smoothedRange < rawRange,
        )
    }
}
