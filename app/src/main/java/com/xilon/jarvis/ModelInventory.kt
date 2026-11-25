package com.xilon.jarvis
import android.content.Context
import java.io.File
object ModelInventory {
 fun recordGguf(context:Context,entry:ModelCatalog.Entry,file:File){
  check(file.isFile)
  context.getSharedPreferences("catalog_inventory",Context.MODE_PRIVATE).edit().putString(entry.file,file.name).apply()
  context.getSharedPreferences("model_labels",Context.MODE_PRIVATE).edit().putString(file.name,entry.name).apply()
 }
 fun installed(context:Context,entry:ModelCatalog.Entry):Boolean{
  val root=context.filesDir
  val file=when(entry){
   in ModelCatalog.entries->{val name=context.getSharedPreferences("catalog_inventory",Context.MODE_PRIVATE).getString(entry.file,null)?:return false;if(!name.matches(Regex("model-[a-f0-9-]+\\.gguf")))return false;File(root,name)}
   in ModelCatalog.ocr->File(root,"tesseract/tessdata/"+entry.file)
   in ModelCatalog.whisper->File(root,"models/"+entry.file)
   in ModelCatalog.wake->File(root,"models/"+if(entry.file=="hey_jarvis.onnx")"jarvis_wake_word.onnx" else entry.file)
   ModelCatalog.pose->File(root,entry.file)
   in ModelCatalog.vision->File(root,if(entry==ModelCatalog.vision[1])"face_detector.tflite" else "object_detector.tflite")
   ModelCatalog.hand->File(root,entry.file)
   ModelCatalog.kokoro->{val dir=File(root,"kokoro");return listOf("model.int8.onnx","voices.bin","tokens.txt").all{File(dir,it).let{f->f.isFile&&f.length()>0}}&&File(dir,"espeak-ng-data").let{it.isDirectory&&it.list()?.isNotEmpty()==true}}
   else->return false
  }
  return file.isFile&&file.length()>0
 }
 fun presence(context:Context,entry:ModelCatalog.Entry):String=if(installed(context,entry))"Instalat" else "Neinstalat"
 fun display(installed:Boolean,transfer:ModelDownloads.Transfer?):String=when{
  transfer?.state==android.app.DownloadManager.STATUS_RUNNING || transfer?.state==android.app.DownloadManager.STATUS_PENDING -> (if(installed)"Instalat • " else "")+transfer.label
  installed -> "Instalat"
  transfer?.state==android.app.DownloadManager.STATUS_SUCCESSFUL -> "Descărcat • instalare necesară"
  transfer?.state==android.app.DownloadManager.STATUS_FAILED -> "Neinstalat • "+transfer.label
  else -> "Neinstalat"
 }
}
