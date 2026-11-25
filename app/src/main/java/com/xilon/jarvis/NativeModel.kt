package com.xilon.jarvis
class NativeModel(private val gpuLayers:Int=0) : AutoCloseable {
 companion object { init { System.loadLibrary("xilon_llama") } }
 @Volatile private var cancelled = false
 fun stop() { cancelled = true }
 fun isCancelled(): Boolean = cancelled
 fun resetCancellation() { cancelled = false }
 @Volatile var onProgress: ((String) -> Unit)? = null
 fun publishProgress(bytes: ByteArray) { onProgress?.invoke(bytes.toString(Charsets.UTF_8)) }
 @Volatile var generatedTokens:Int=0
 private set
 @Volatile var firstTokenNanos:Long=0
 private set
 private var startNanos=0L
 fun publishToken(){if(generatedTokens==0)firstTokenNanos=android.os.SystemClock.elapsedRealtimeNanos()-startNanos;generatedTokens++}
 private var handle = 0L
 @Synchronized fun load(path: String) { close(); handle = openModel(path,gpuLayers); check(handle != 0L) { "Model loading failed" } }
 @Synchronized fun generate(prompt: String, threads: Int = 2, context: Int = 1024, maxTokens: Int = 128, history: List<Pair<String,String>> = emptyList(), grammar:String = ""): String { check(handle != 0L); generatedTokens=0;firstTokenNanos=0;startNanos=android.os.SystemClock.elapsedRealtimeNanos(); return infer(handle,(history + ("user" to prompt)).map { it.first }.toTypedArray(),(history + ("user" to prompt)).map { it.second.toByteArray(Charsets.UTF_8) }.toTypedArray(),threads.coerceIn(1,8),context.coerceIn(512,4096),maxTokens.coerceIn(32,minOf(512,context.coerceIn(512,4096)/2)),grammar) }
 @Synchronized override fun close() { if(handle != 0L) { freeModel(handle); handle=0 } }
 @Synchronized fun supportsVisionProjector(path:String):Boolean { check(handle != 0L); return probeVision(handle,path) }
 @Synchronized fun describeImage(projector:String,bitmap:android.graphics.Bitmap,question:String,context:Int=2048,maxTokens:Int=128):String {
  check(handle!=0L);require(question.isNotBlank() && question.length<=4000)
  require(!bitmap.isRecycled && bitmap.width>0 && bitmap.height>0)
  val scale=minOf(1.0,1024.0/maxOf(bitmap.width,bitmap.height))
  val image=android.graphics.Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true)
  try {
   val pixels=IntArray(image.width*image.height);image.getPixels(pixels,0,image.width,0,0,image.width,image.height)
   val rgb=ByteArray(pixels.size*3)
   pixels.forEachIndexed{i,p->rgb[i*3]=(p shr 16).toByte();rgb[i*3+1]=(p shr 8).toByte();rgb[i*3+2]=p.toByte()}
   return inferImage(handle,projector,rgb,image.width,image.height,question.toByteArray(Charsets.UTF_8),context.coerceIn(1024,4096),maxTokens.coerceIn(16,256)).toString(Charsets.UTF_8)
  } finally {if(image!==bitmap)image.recycle()}
 }
 private external fun inferImage(handle:Long,projector:String,rgb:ByteArray,width:Int,height:Int,question:ByteArray,context:Int,limit:Int):ByteArray
 private external fun probeVision(handle:Long,path:String):Boolean
 private external fun openModel(path:String,gpuLayers:Int):Long
 private external fun infer(handle:Long,roles:Array<String>,messages:Array<ByteArray>,threads:Int,context:Int,maxTokens:Int,grammar:String):String
 private external fun freeModel(handle:Long)
}
