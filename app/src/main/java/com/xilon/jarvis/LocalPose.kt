package com.xilon.jarvis
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.io.File
internal object LocalPose {
 private val poses=VisionDetectorCache<PoseLandmarker>()
 @Synchronized fun release(){poses.release()}

 private fun open(context:Context,file:File):PoseLandmarker {
  val buffer=java.io.RandomAccessFile(file,"r").use{it.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,it.length())}
  return PoseLandmarker.createFromOptions(context,PoseLandmarker.PoseLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buffer).build()).setNumPoses(1).build())
 }
 @Synchronized fun install(context:Context,uri:Uri){
  val target=File(context.filesDir,"pose_landmarker_lite.task")
  val temp=File.createTempFile("pose-", ".part",context.filesDir)
  try{
   context.contentResolver.openInputStream(uri)?.use{src->temp.outputStream().use{dst->val buf=ByteArray(65536);var total=0L;while(true){val n=src.read(buf);if(n<0)break;total+=n;require(total<=32L*1024*1024);dst.write(buf,0,n)}}}?:error("Fișier inaccesibil")
   require(temp.length()>1024)
   open(context,temp).close()
   poses.release()
   check(temp.renameTo(target))
  }finally{temp.delete()}
 }
 @Synchronized fun describe(context:Context,bitmap:Bitmap):String{
  val file=File(context.filesDir,"pose_landmarker_lite.task")
  if(!file.isFile)return "Poziție corporală: model neinstalat"
  val copy=bitmap.copy(Bitmap.Config.ARGB_8888,false)?:error("Imagine inaccesibilă")
  try{
   val image=BitmapImageBuilder(copy).build()
   try{
    val keep=VisionDetectorCache.retain(context)
    val detector=poses.acquire(file){open(context,file)}
    try{
     val points=detector.detect(image).landmarks().firstOrNull()?:return "Poziție corporală: nedetectată"
     if(points.size<33)return "Poziție corporală: detecție incompletă"
     val indices=listOf(11 to "umăr stâng",12 to "umăr drept",15 to "încheietură stângă",16 to "încheietură dreaptă",23 to "șold stâng",24 to "șold drept",27 to "gleznă stângă",28 to "gleznă dreaptă")
     return "Repere corporale în cadru: "+indices.filter{points[it.first].visibility().orElse(0f)>=0.6f}.joinToString("; "){(i,label)->label+" ("+(points[i].x()*100).toInt()+"%, "+(points[i].y()*100).toInt()+"%)"}.ifBlank{"vizibilitate insuficientă"}
    }catch(e:Exception){poses.release();throw e}finally{if(!keep)poses.release()}
   }finally{image.close()}
  }finally{if(!copy.isRecycled)copy.recycle()}
 }
}
