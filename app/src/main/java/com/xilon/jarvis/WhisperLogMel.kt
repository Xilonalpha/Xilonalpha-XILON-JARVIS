package com.xilon.jarvis

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10

/** Whisper's 16 kHz periodic Hann STFT and 80 Slaney-normalized Mel channels. */
internal class WhisperLogMel {
    private val fft = FloatFFT_1D(400)
    private val window = FloatArray(400) { (0.5 - 0.5 * cos(2.0 * PI * it / 400)).toFloat() }
    private val filters = Array(80) { band ->
        fun edge(offset: Int): Double {
            val mel = (15.0 + ln(8.0) / (ln(6.4) / 27)) * (band + offset) / 81
            return if (mel < 15) mel * (200.0 / 3) else 1000 * exp((mel - 15) * ln(6.4) / 27)
        }
        val left = edge(0)
        val center = edge(1)
        val right = edge(2)
        (0..200).mapNotNull { bin ->
            val hz = bin * 40.0
            val weight = minOf((hz-left)/(center-left), (right-hz)/(right-center)) * 2 / (right-left)
            if (weight > 0) bin to weight.toFloat() else null
        }
    }

    fun extract(samples: FloatArray): FloatArray {
        require(samples.size in 1..480000)
        val mel = FloatArray(80 * 3000) { -10f }
        val frame = FloatArray(400)
        val power = FloatArray(201)
        var peak = -10f
        for (time in 0 until minOf(3000, (samples.size + 359) / 160)) {
            for (i in frame.indices) {
                val pos = abs(time * 160 + i - 200)
                val index = if (pos >= 480000) 959998 - pos else pos
                frame[i] = if (index < samples.size) samples[index] * window[i] else 0f
            }
            fft.realForward(frame)
            power[0] = frame[0] * frame[0]
            power[200] = frame[1] * frame[1]
            for (k in 1 until 200) power[k] = frame[2*k]*frame[2*k] + frame[2*k+1]*frame[2*k+1]
            for (band in filters.indices) {
                var sum = 0f
                for ((bin, weight) in filters[band]) sum += power[bin] * weight
                val energy = log10(maxOf(1e-10f, sum))
                mel[band*3000 + time] = energy
                peak = maxOf(peak, energy)
            }
        }
        for (i in mel.indices) mel[i] = (maxOf(mel[i], peak - 8f) + 4f) / 4f
        return mel
    }
}
