package com.xilon.jarvis
import android.content.Context
import java.io.File
object ModelInventory {
 fun recordGguf(context:Context,entry:ModelCatalog.Entry,file:File){
  check(file.isFile)
  context.getSharedPreferences("catalog_inventory",Context.MODE_PRIVATE).edit().putString(entry.file,file.name).apply()
  context.getSharedPreferences("model_labels",Context.MODE_PRIVATE).edit().putString(file.name,entry.name).apply()
 }
 fun presence(context:Context,entry:ModelCatalog.Entry):String{
  val root=context.filesDir
  val file=when(entry){
   in ModelCatalog.entries->{val name=context.getSharedPreferences("catalog_inventory",Context.MODE_PRIVATE).getString(entry.file,null)?:return "Neidentificat în bibliotecă";if(!name.matches(Regex("model-[a-f0-9-]+\\.gguf")))return "Neidentificat";File(root,name)}
   in ModelCatalog.ocr->File(root,"tesseract/tessdata/"+entry.file)
   in ModelCatalog.whisper->File(root,"models/"+entry.file)
   in ModelCatalog.wake->File(root,"models/"+if(entry.file=="hey_jarvis.onnx")"jarvis_wake_word.onnx" else entry.file)
   ModelCatalog.hand->File(root,entry.file)
   ModelCatalog.kokoro->{val dir=File(root,"kokoro");return if(listOf("model.int8.onnx","voices.bin","tokens.txt").all{File(dir,it).isFile}&&File(dir,"espeak-ng-data").isDirectory)"Componente prezente" else "Pachet incomplet sau absent"}
   else->return "Necunoscut"
  }
  return if(file.isFile&&file.length()>0)"Fișier local prezent" else "Fișier absent"
 }
}
