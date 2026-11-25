package com.xilon.jarvis
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

internal class LiveVisionController(private val context:Context,private val owner:LifecycleOwner,private val observation:(String)->Unit,private val failure:(String)->Unit){
 private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
 private val main=ContextCompat.getMainExecutor(context)
 private var provider:ProcessCameraProvider?=null
 private var analysis:ImageAnalysis?=null
 @Volatile private var closed=false
 private var last=0L
 fun start(){
  val future=ProcessCameraProvider.getInstance(context)
  future.addListener({
   if(!closed)try{
    val p=future.get();provider=p
    val a=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    analysis=a
    a.setAnalyzer(worker){frame->
     try{
      val now=android.os.SystemClock.elapsedRealtime()
      if(!closed && now-last>=2000L){
       last=now
       val bitmap=frame.toBitmap()
       try{
        val matrix=android.graphics.Matrix().apply{postRotate(frame.imageInfo.rotationDegrees.toFloat())}
        val rotated=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        try{
         val report=LocalVision.describe(context,rotated)
         main.execute{if(!closed)observation(report)}
        }finally{if(rotated!==bitmap)rotated.recycle()}
       }finally{bitmap.recycle()}
      }
     }catch(e:Exception){main.execute{if(!closed)failure("Vedere live: "+e.message)}}
     catch(e:LinkageError){main.execute{if(!closed)failure("Motor vizual indisponibil: "+e.message)}}
     finally{frame.close()}
    }
    p.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,a)
   }catch(e:Exception){if(!closed)failure("Camera live: "+e.message)}
  },main)
 }
 fun close(){
  if(closed)return
  closed=true
  analysis?.clearAnalyzer()
  analysis?.let{provider?.unbind(it)}
  worker.shutdown()
 }
}
