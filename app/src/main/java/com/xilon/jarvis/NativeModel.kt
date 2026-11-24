package com.xilon.jarvis
class NativeModel : AutoCloseable {
 companion object { init { System.loadLibrary("xilon_llama") } }
 private var handle = 0L
 @Synchronized fun load(path: String) { close(); handle = openModel(path); check(handle != 0L) { "Model loading failed" } }
 @Synchronized fun generate(prompt: String): String { check(handle != 0L); return infer(handle,prompt) }
 @Synchronized override fun close() { if(handle != 0L) { freeModel(handle); handle=0 } }
 private external fun openModel(path:String):Long
 private external fun infer(handle:Long,prompt:String):String
 private external fun freeModel(handle:Long)
}
