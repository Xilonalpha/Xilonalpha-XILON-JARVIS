package com.xilon.jarvis
class NativeModel : AutoCloseable {
 companion object { init { System.loadLibrary("xilon_llama") } }
 @Volatile private var cancelled = false
 fun stop() { cancelled = true }
 fun isCancelled(): Boolean = cancelled
 fun resetCancellation() { cancelled = false }
 @Volatile var onProgress: ((String) -> Unit)? = null
 fun publishProgress(bytes: ByteArray) { onProgress?.invoke(bytes.toString(Charsets.UTF_8)) }
 private var handle = 0L
 @Synchronized fun load(path: String) { close(); handle = openModel(path); check(handle != 0L) { "Model loading failed" } }
 @Synchronized fun generate(prompt: String, threads: Int = 2, context: Int = 1024, maxTokens: Int = 128, history: List<Pair<String,String>> = emptyList()): String { check(handle != 0L); return infer(handle,(history + ("user" to prompt)).map { it.first }.toTypedArray(),(history + ("user" to prompt)).map { it.second.toByteArray(Charsets.UTF_8) }.toTypedArray(),threads.coerceIn(1,8),context.coerceIn(512,4096),maxTokens.coerceIn(32,512)) }
 @Synchronized override fun close() { if(handle != 0L) { freeModel(handle); handle=0 } }
 private external fun openModel(path:String):Long
 private external fun infer(handle:Long,roles:Array<String>,messages:Array<ByteArray>,threads:Int,context:Int,maxTokens:Int):String
 private external fun freeModel(handle:Long)
}
