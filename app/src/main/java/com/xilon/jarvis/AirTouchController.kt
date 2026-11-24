package com.xilon.jarvis
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.jarvis.ai.gesture.GestureEngine
import com.jarvis.ai.gesture.GestureCursorView
class AirTouchController(private val context:Context,private val owner:LifecycleOwner,private val cursor:GestureCursorView,private val status:(String)->Unit,private val onFailure:()->Unit){
 private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
 private var provider:ProcessCameraProvider?=null
 private var analysis:ImageAnalysis?=null
 @Volatile private var closed=false
 private val engine=GestureEngine(context,{if(!closed)cursor.update(it)},{if(!closed)ContextCompat.getMainExecutor(context).execute{if(!closed)status(it)}})
 fun start(){
  worker.execute{
   val result=runCatching{engine.initialize()}
   ContextCompat.getMainExecutor(context).execute{
    if(!closed)result.fold({
     val future=ProcessCameraProvider.getInstance(context)
     future.addListener({
      if(!closed)try{
       val p=future.get();provider=p
       val a=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
       analysis=a;a.setAnalyzer(worker,engine::process)
       p.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,a)
       status("Air Touch activ.")
      }catch(e:Exception){status("Camera indisponibilă: "+e.message);close();onFailure()}
     },ContextCompat.getMainExecutor(context))
    },{status("Model gesturi indisponibil: "+it.message);close();onFailure()})
   }
  }
 }
 fun close(){
  if(closed)return
  closed=true
  analysis?.clearAnalyzer()
  analysis?.let{provider?.unbind(it)}
  worker.execute{engine.close()}
  worker.shutdown()
 }
}
