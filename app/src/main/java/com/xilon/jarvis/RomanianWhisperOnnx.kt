package com.xilon.jarvis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import org.json.JSONObject

/** Greedy, Romanian-only transcription for the pinned Gntz Whisper Small merged export. */
internal class RomanianWhisperOnnx(root: File, threads: Int) : AutoCloseable {
    companion object {
        val files = listOf("encoder_model_quantized.onnx", "decoder_model_merged_quantized.onnx", "vocab.json", "generation_config.json")
        fun available(root: File) = files.all { File(root, it).let { file -> file.isFile && file.length() > 0 } }
    }

    private val env = OrtEnvironment.getEnvironment()
    private val features = WhisperLogMel()
    private val config = JSONObject(File(root, files[3]).readText())
    private val eos = config.getInt("eos_token_id")
    private val prompt = longArrayOf(config.getLong("decoder_start_token_id"), config.getJSONObject("lang_to_id").getLong("<|ro|>"),
        config.getJSONObject("task_to_id").getLong("transcribe"), config.getLong("no_timestamps_token_id"))
    private fun suppressed(key: String) = config.getJSONArray(key).let { array -> (0 until array.length()).map(array::getInt).toSet() }
    private val blocked = suppressed("suppress_tokens")
    private val initialBlocked = suppressed("begin_suppress_tokens")
    private val vocabulary = JSONObject(File(root, files[2]).readText()).let { json -> json.keys().asSequence().associateBy { json.getInt(it) } }
    private val byteValues = buildMap<Char, Int> {
        var extra = 256
        for (b in 0..255) put((if (b in 33..126 || b in 161..172 || b in 174..255) b else extra++).toChar(), b)
    }
    private val encoder: OrtSession
    private val decoder: OrtSession
    init {
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(threads)
            options.setInterOpNumThreads(1)
            encoder = env.createSession(File(root, files[0]).path, options)
            try { decoder = env.createSession(File(root, files[1]).path, options) }
            catch (failure: Throwable) { encoder.close(); throw failure }
        }
    }

    private var closed = false

    @Synchronized fun transcribe(samples: FloatArray, cancelled: () -> Boolean): String {
        check(!closed) { "Whisper decoder closed" }
        if (samples.isEmpty() || cancelled()) return ""
        val values = features.extractCancellable(samples, cancelled) ?: return ""
        OnnxTensor.createTensor(env, FloatBuffer.wrap(values), longArrayOf(1,80,3000)).use { input ->
            if (cancelled()) return ""
            encoder.run(mapOf("input_features" to input)).use { encoded ->
                return decode(encoded[0] as OnnxTensor, cancelled)
            }
        }
    }

    private fun decode(hidden: OnnxTensor, cancelled: () -> Boolean): String {
        val bytes = ByteArrayOutputStream()
        val past = decoder.inputNames.filter { it.startsWith("past_key_values.") }
        var first: OrtSession.Result? = null
        var previous: OrtSession.Result? = null
        var ids = prompt
        try {
            OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1,12,0,64)).use { empty ->
                repeat(224) { step ->
                    if (cancelled()) return ""
                    val inputs = mutableMapOf("encoder_hidden_states" to hidden)
                    for (key in past) {
                        val owner = if (".encoder." in key) first else previous
                        inputs[key] = if (owner == null) empty else owner.get(key.replace("past_key_values.", "present.")).get() as OnnxTensor
                    }
                    val result = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1,ids.size.toLong())).use { tokens ->
                        OnnxTensor.createTensor(env, booleanArrayOf(step > 0)).use { branch ->
                            inputs["input_ids"] = tokens
                            inputs["use_cache_branch"] = branch
                            decoder.run(inputs)
                        }
                    }
                    if (previous !== first) previous?.close()
                    if (first == null) first = result
                    previous = result
                    val logits = result.get("logits").get() as OnnxTensor
                    val scores = logits.floatBuffer
                    val width = logits.info.shape.last().toInt()
                    check(width > eos && scores.limit() >= width) { "Invalid Whisper logits shape" }
                    val offset = scores.limit() - width
                    var best = -1
                    var score = Float.NEGATIVE_INFINITY
                    for (token in 0..eos) {
                        if (token in blocked || (step == 0 && token in initialBlocked)) continue
                        val value = scores[offset + token]
                        if (value.isFinite() && value > score) { best = token; score = value }
                    }
                    check(best >= 0) { "Whisper a returnat scoruri invalide" }
                    if (best == eos) return if (cancelled()) "" else bytes.toString("UTF-8").trim()
                    for (ch in vocabulary.getValue(best)) bytes.write(byteValues.getValue(ch))
                    ids = longArrayOf(best.toLong())
                }
            }
            return "" // Incomplete hypotheses must not become executable commands.
        } finally {
            if (previous !== first) previous?.close()
            first?.close()
        }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        try { decoder.close() } finally { encoder.close() }
    }
}
