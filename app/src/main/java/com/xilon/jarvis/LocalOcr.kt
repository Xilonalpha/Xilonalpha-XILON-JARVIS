package com.xilon.jarvis
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
object LocalOcr {
 @Synchronized fun install(context:Context,uri:Uri,language:String){
  require(language in listOf("ron","eng"))
  val dir=File(context.filesDir,"tesseract/tessdata");check(dir.isDirectory || dir.mkdirs())
  val target=File(dir,"$language.traineddata");val staging=java.nio.file.Files.createTempDirectory(dir.parentFile!!.toPath(),"ocr-").toFile()
  val stagedData=File(staging,"tessdata")
  val temp=File(stagedData,"$language.traineddata")
  try{
   check(stagedData.mkdirs())
   context.contentResolver.openInputStream(uri)?.use{src->temp.outputStream().use{dst->
    val buf=ByteArray(65536);var total=0L
    while(true){val n=src.read(buf);if(n<0)break;total+=n;require(total<=64L*1024*1024){"Model prea mare"};dst.write(buf,0,n)}
   }} ?: error("Fișier inaccesibil")
   require(temp.length()>1024){"Fișier invalid"}
   val validator=TessBaseAPI()
   try{check(validator.init(staging.absolutePath,language)){"Model OCR incompatibil"}}finally{validator.recycle()}
   release()
   check(temp.renameTo(target)){"Instalare eșuată"}
  }finally{staging.deleteRecursively()}
 }
 fun read(context:Context,uri:Uri):String{
  val root=File(context.filesDir,"tesseract")
  val langs=listOf("ron","eng").filter{File(root,"tessdata/$it.traineddata").isFile}
  require(langs.isNotEmpty()){"Importă datele OCR ron sau eng"}
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
  require(bounds.outWidth>0 && bounds.outHeight>0){"Imagine invalidă"}
  var sample=1
  while(bounds.outWidth.toLong()/sample*(bounds.outHeight.toLong()/sample)>4000000L || maxOf(bounds.outWidth,bounds.outHeight)/sample>4096)sample*=2
  val bitmap=context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})} ?: error("Imagine inaccesibilă")
  try{return readBitmap(context,bitmap)}finally{bitmap.recycle()}
 }
 private var cached:TessBaseAPI?=null
 private var revision=""
 @Synchronized fun release(){
  val old=cached;cached=null;revision=""
  old?.recycle()
 }
 @Synchronized fun readBitmap(context:Context,bitmap:android.graphics.Bitmap):String{
  val root=File(context.filesDir,"tesseract")
  val langs=listOf("ron","eng").filter{File(root,"tessdata/$it.traineddata").isFile}
  require(langs.isNotEmpty()){"Importă datele OCR ron sau eng"}
  val key=root.absolutePath+langs.joinToString("|"){lang-> val f=File(root,"tessdata/$lang.traineddata"); "$lang:${f.length()}:${f.lastModified()}"}
  val info=android.app.ActivityManager.MemoryInfo()
  context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
  val keep=!info.lowMemory && info.availMem>=1536L*1024*1024
  if(revision!=key || !keep)release()
  val tess=cached ?: TessBaseAPI().also{engine->
   try{check(engine.init(root.absolutePath,langs.joinToString("+"))){"Inițializare OCR eșuată"}}catch(e:Throwable){engine.recycle();throw e}
   cached=engine;revision=key
  }
  var success=false
  try{tess.setImage(bitmap);val result=tess.utF8Text.orEmpty().take(12000);tess.clear();success=true;return result}
  finally{if(!success || !keep)release()}
 }
}
