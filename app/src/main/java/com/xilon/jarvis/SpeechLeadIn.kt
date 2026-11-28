package com.xilon.jarvis

/** The detector and recognizer use mono PCM16 at 16 kHz. Keep only the most recent samples. */
internal class SpeechLeadIn(capacity: Int) {
    private val samples = ShortArray(capacity)
    private var next = 0
    private var size = 0

    init { require(capacity > 0) }

    fun append(frame: ShortArray, count: Int) {
        for (i in 0 until count) {
            samples[next] = frame[i]
            next = (next + 1) % samples.size
        }
        size = (size + count).coerceAtMost(samples.size)
    }

    fun snapshot(): ShortArray {
        val start = (next - size + samples.size) % samples.size
        return ShortArray(size) { samples[(start + it) % samples.size] }
    }
}
