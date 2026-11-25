package com.xilon.jarvis
import android.content.Context
import android.graphics.Bitmap

internal object LocalImageAnalysis {
 data class Analysis(val vision:String,val text:String?,val ocrError:String?) {
  fun report():String=vision+"\nOCR: "+(ocrError?.let{"indisponibil: "+it} ?: text?.take(1500)?.ifBlank{"Niciun text detectat."} ?: "Niciun text detectat.")
 }
 fun analyze(context:Context,bitmap:Bitmap):Analysis {
  val vision=runCatching{LocalVision.describe(context,bitmap)}.getOrElse{"Vedere indisponibilă: "+it.message}
  val ocr=runCatching{LocalOcr.readBitmap(context,bitmap)}
  return Analysis(vision,ocr.getOrNull(),ocr.exceptionOrNull()?.message)
 }
}
