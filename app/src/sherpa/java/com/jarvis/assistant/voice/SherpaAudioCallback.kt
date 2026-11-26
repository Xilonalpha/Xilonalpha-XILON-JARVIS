package com.jarvis.assistant.voice

/** Sherpa JNI looks up invoke(float[]): Integer by signature on the concrete callback class. */
internal class SherpaAudioCallback(private val onChunk: (FloatArray) -> Boolean) : (FloatArray) -> Int {
    override fun invoke(samples: FloatArray): Int = if (onChunk(samples)) 1 else 0
}
