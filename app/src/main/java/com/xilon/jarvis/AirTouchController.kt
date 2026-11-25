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
 private val started=java.util.concurrent.atomic.AtomicBoolean(false)
 private var lastVision=0L
 private var lastOcr=0L
 private val tracker=SceneTracker()
 private var provider:ProcessCameraProvider?=null
 private var analysis:ImageAnalysis?=null
 @Volatile private var closed=false
 private val failed=java.util.concurrent.atomic.AtomicBoolean(false)
 private val engine=GestureEngine(context,{if(!closed && !failed.get())cursor.update(it)},{message->if(!closed && failed.compareAndSet(false,true))ContextCompat.getMainExecutor(context).execute{if(!closed){status("Air Touch oprit: "+message);close();onFailure()}}})
 fun start(){
  if(closed || !started.compareAndSet(false,true))return
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
         if(!closed && onObservation!=null && now-lastVision>=visionInterval() && VisionWorkGate.tryEnter()){
          try {
          lastVision=now
          val report=runCatching{
           val bitmap=frame.toBitmap()
           try{
            val matrix=android.graphics.Matrix().apply{postRotate(frame.imageInfo.rotationDegrees.toFloat())}
            val rotated=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
            try{val includeText=lastOcr==0L || now-lastOcr>=12000L;if(includeText)lastOcr=now;LocalVision.describeScene(context,rotated,includeText,tracker)}finally{if(rotated!==bitmap)rotated.recycle()}
           }finally{bitmap.recycle()}
          }.getOrNull()
          if(report!=null)ContextCompat.getMainExecutor(context).execute{if(!closed)onObservation?.invoke(report)}
          } finally { VisionWorkGate.leave() }
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
  if(ResourceBudget.maxConcurrentOptionalVision(info.availMem/1048576,info.lowMemory,thermal,context.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode)==0)return Long.MAX_VALUE
  return ResourceBudget.visionInterval(info.availMem/1048576,info.lowMemory,thermal,context.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode)
 }
 fun close(){
  if(closed)return
  closed=true
  cursor.setTracking(false)
  analysis?.clearAnalyzer()
  analysis?.let{provider?.unbind(it)}
  runCatching{worker.execute{engine.close()}}
  worker.shutdown()
 }
}
