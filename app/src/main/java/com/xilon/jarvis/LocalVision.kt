package com.xilon.jarvis
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import java.io.File

internal object LocalVision {
 fun install(context:Context,uri:Uri,face:Boolean){
  val target=File(context.filesDir,if(face)"face_detector.tflite" else "object_detector.tflite")
  val temp=File.createTempFile("vision-", ".part",context.filesDir)
  try{
   context.contentResolver.openInputStream(uri)?.use{input->temp.outputStream().use{out->
    val buffer=ByteArray(65536);var size=0L
    while(true){val n=input.read(buffer);if(n<0)break;size+=n;require(size<=100L*1024*1024){"Model prea mare"};out.write(buffer,0,n)}
   }} ?: error("Fișier inaccesibil")
   require(temp.length()>1024){"Model invalid"}
   val base=BaseOptions.builder().setModelAssetBuffer(java.io.RandomAccessFile(temp,"r").use{it.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,it.length())}).build()
   if(face)FaceDetector.createFromOptions(context,FaceDetector.FaceDetectorOptions.builder().setBaseOptions(base).build()).close()
   else ObjectDetector.createFromOptions(context,ObjectDetector.ObjectDetectorOptions.builder().setBaseOptions(base).build()).close()
   check(temp.renameTo(target)){"Instalare eșuată"}
  }finally{temp.delete()}
 }
 fun detect(context:Context,uri:Uri,face:Boolean):String{
  val model=File(context.filesDir,if(face)"face_detector.tflite" else "object_detector.tflite")
  require(model.isFile){"Importă întâi modelul MediaPipe compatibil."}
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
  require(bounds.outWidth>0&&bounds.outHeight>0){"Imagine invalidă"}
  var sample=1
  while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1600)sample*=2
  val bitmap=context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888})}?:error("Imagine inaccesibilă")
  try{
   val image=BitmapImageBuilder(bitmap).build()
   try{
    val base=BaseOptions.builder().setModelAssetBuffer(java.io.RandomAccessFile(model,"r").use{it.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,it.length())}).build()
    val detections=if(face){
     val detector=FaceDetector.createFromOptions(context,FaceDetector.FaceDetectorOptions.builder().setBaseOptions(base).setMinDetectionConfidence(0.5f).build())
     try{detector.detect(image).detections()}finally{detector.close()}
    }else{
     val detector=ObjectDetector.createFromOptions(context,ObjectDetector.ObjectDetectorOptions.builder().setBaseOptions(base).setScoreThreshold(0.5f).setMaxResults(20).build())
     try{detector.detect(image).detections()}finally{detector.close()}
    }
    return if(face)"Fețe detectate: "+detections.size+" (fără identificare personală)" else detections.joinToString("\n"){d->d.categories().joinToString(", "){c->c.categoryName()+" — "+(c.score()*100).toInt()+"%"}}.ifBlank{"Niciun obiect detectat peste prag."}
   }finally{image.close()}
  }finally{bitmap.recycle()}
 }
}
