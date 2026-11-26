package com.xilon.jarvis

import org.junit.Assert.*
import org.junit.Test

class WhisperLogMelTest {
    @Test fun silenceHasExpectedShapeAndFloor() {
        val output = WhisperLogMel().extract(FloatArray(1600))
        assertEquals(240000, output.size)
        assertTrue(output.all { kotlin.math.abs(it + 1.5f) < 0.00001f })
    }

    @Test fun cancellationDuringFramesDiscardsFeatures() {
        var checks = 0
        val result = WhisperLogMel().extractCancellable(FloatArray(16000)) { ++checks >= 5 }
        assertNull(result)
        assertEquals(5, checks)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPcmIsRejected() {
        WhisperLogMel().extract(floatArrayOf(Float.NaN))
    }

    @Test fun repeatedExtractionIsDeterministic() {
        val extractor = WhisperLogMel()
        val samples = FloatArray(1600) { kotlin.math.sin(it * 0.17).toFloat() * 0.2f }
        val first = extractor.extract(samples)
        assertTrue(first.all { it.isFinite() })
        assertArrayEquals(first, extractor.extract(samples), 0.000001f)
    }
}
