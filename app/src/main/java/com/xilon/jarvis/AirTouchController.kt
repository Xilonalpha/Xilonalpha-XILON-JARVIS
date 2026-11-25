package com.xilon.jarvis
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.jarvis.ai.gesture.GestureEngine
import com.jarvis.ai.gesture.GestureCursorView
class AirTouchController(private val context:Context,private val owner:LifecycleOwner,private val cursor:GestureCursorView,private val status:(String)->Unit,private val onFailure:()->Unit,private val onObservation:((String)->Unit)?=null){
 private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
 private var lastVision=0L
 private var provider:ProcessCameraProvider?=null
 private var analysis:ImageAnalysis?=null
 @Volatile private var closed=false
 private val engine=GestureEngine(context,{if(!closed)cursor.update(it)},{if(!closed)ContextCompat.getMainExecutor(context).execute{if(!closed)status(it)}})
 fun start(){
  cursor.setTracking(true)
  worker.execute{
   val result=runCatching{engine.initialize()}
   ContextCompat.getMainExecutor(context).execute{
    if(!closed)result.fold({
     val future=ProcessCameraProvider.getInstance(context)
     future.addListener({
      if(!closed)try{
       val p=future.get();provider=p
       val a=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
       analysis=a;a.setAnalyzer(worker){frame->
        try{
         val now=android.os.SystemClock.elapsedRealtime()
         if(!closed && onObservation!=null && now-lastVision>=visionInterval()){
          lastVision=now
          val report=runCatching{
           val bitmap=frame.toBitmap()
           try{
            val matrix=android.graphics.Matrix().apply{postRotate(frame.imageInfo.rotationDegrees.toFloat())}
            val rotated=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
            try{LocalVision.describe(context,rotated)}finally{if(rotated!==bitmap)rotated.recycle()}
           }finally{bitmap.recycle()}
          }.getOrNull()
          if(report!=null)ContextCompat.getMainExecutor(context).execute{if(!closed)onObservation?.invoke(report)}
         }
        }finally{engine.process(frame)}
       }
       p.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,a)
       status("Air Touch activ.")
      }catch(e:Exception){status("Camera indisponibilă: "+e.message);close();onFailure()}
     },ContextCompat.getMainExecutor(context))
    },{status("Model gesturi indisponibil: "+it.message);close();onFailure()})
   }
  }
 }
 private fun visionInterval():Long{
  val manager=context.getSystemService(android.app.ActivityManager::class.java)
  val info=android.app.ActivityManager.MemoryInfo()
  manager.getMemoryInfo(info)
  val thermal=if(android.os.Build.VERSION.SDK_INT>=29)context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus else 0
  return if(info.lowMemory || thermal>=3)10000L else if(thermal>=2 || info.availMem<768L*1024*1024)6000L else 3000L
 }
 fun close(){
  if(closed)return
  closed=true
  cursor.setTracking(false)
  analysis?.clearAnalyzer()
  analysis?.let{provider?.unbind(it)}
  worker.execute{engine.close()}
  worker.shutdown()
 }
}
