package com.xilon.jarvis
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
object LocalOcr {
 fun install(context:Context,uri:Uri,language:String){
  require(language in listOf("ron","eng"))
  val dir=File(context.filesDir,"tesseract/tessdata");check(dir.isDirectory || dir.mkdirs())
  val target=File(dir,"$language.traineddata");val temp=File.createTempFile("ocr-", ".part",dir)
  try{
   context.contentResolver.openInputStream(uri)?.use{src->temp.outputStream().use{dst->
    val buf=ByteArray(65536);var total=0L
    while(true){val n=src.read(buf);if(n<0)break;total+=n;require(total<=64L*1024*1024){"Model prea mare"};dst.write(buf,0,n)}
   }} ?: error("Fișier inaccesibil")
   require(temp.length()>1024){"Fișier invalid"}
   check(temp.renameTo(target)){"Instalare eșuată"}
  }finally{temp.delete()}
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
 fun readBitmap(context:Context,bitmap:android.graphics.Bitmap):String{
  val root=File(context.filesDir,"tesseract")
  val langs=listOf("ron","eng").filter{File(root,"tessdata/$it.traineddata").isFile}
  require(langs.isNotEmpty()){"Importă datele OCR ron sau eng"}
   val tess=TessBaseAPI()
   try{check(tess.init(root.absolutePath,langs.joinToString("+"))){"Inițializare OCR eșuată"};tess.setImage(bitmap);return tess.utF8Text.take(12000)}
   finally{tess.recycle()}
 }
}
