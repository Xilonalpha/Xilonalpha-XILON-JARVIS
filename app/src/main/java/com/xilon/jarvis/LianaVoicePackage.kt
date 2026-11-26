package com.xilon.jarvis

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Converts the pinned Piper export to Sherpa's VITS format in app-private storage. */
internal object LianaVoicePackage {
    private const val sourceName = "ro_RO-liana-medium.onnx"
    private const val modelBytes = 63516050L
    private const val configBytes = 4855L

    fun sourcesPresent(root: File) =
        File(root, sourceName).length() == modelBytes &&
            File(root, "$sourceName.json").length() == configBytes

    @Synchronized
    fun prepare(root: File, metadata: () -> InputStream): File {
        val target = File(root, "liana-sherpa-v1")
        if (File(target, "model.onnx").length() > modelBytes && File(target, "tokens.txt").length() > 0) return target
        check(sourcesPresent(root)) { "Fișiere Liana incomplete" }
        val stage = File(root, "liana-" + UUID.randomUUID())
        check(stage.mkdir())
        try {
            val model = File(stage, "model.onnx")
            File(root, sourceName).inputStream().use { src ->
                model.outputStream().use { dst -> src.copyTo(dst) }
            }
            ModelIntegrity.verify(model.inputStream(), "40c6cbe08905ba702f4df5966a3a8de2bcc232db8ad3784db74bbd948d9953d3", modelBytes)
            // Protobuf concatenation adds ONNX ModelProto.metadata_props to the original graph.
            java.io.FileOutputStream(model, true).use { dst -> metadata().use { it.copyTo(dst) } }
            val config = File(root, "$sourceName.json").readBytes()
            ModelIntegrity.verify(config.inputStream(), "bb70ba2e0181a3d98050020ea3531a279f96afd72cc86d571cf5de0920383f46", configBytes)
            val phonemes = JSONObject(config.toString(Charsets.UTF_8)).getJSONObject("phoneme_id_map")
            File(stage, "tokens.txt").bufferedWriter().use { writer ->
                for (phoneme in phonemes.keys()) {
                    val id = phonemes.getJSONArray(phoneme).getInt(0)
                    writer.append(phoneme).append(' ').appendLine(id.toString())
                }
            }
            check(stage.renameTo(target)) { "Nu pot instala vocea Liana convertită" }
            return target
        } finally {
            stage.deleteRecursively()
        }
    }

}
