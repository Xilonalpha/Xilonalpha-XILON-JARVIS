package com.xilon.jarvis
import com.jarvis.mobile.core.observer.ScreenObservation
import com.jarvis.mobile.core.observer.ScreenElement
import com.jarvis.mobile.core.skills.ElementResolver
import com.jarvis.mobile.core.skills.ElementTarget
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
class LocalScreen:AccessibilityService(){
 companion object {
  @Volatile private var active:LocalScreen?=null
  internal fun service():LocalScreen?=active
  fun connected():Boolean=active!=null
  fun snapshot():String?=active?.readScreen()
  fun capture(onResult:(android.graphics.Bitmap?,String?)->Unit) {
   val svc=active
   if(svc==null){onResult(null,"Serviciul de accesibilitate nu este activ.");return}
   if(android.os.Build.VERSION.SDK_INT<30){onResult(null,"Captura necesită Android 11 sau mai nou.");return}
   svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY,svc.mainExecutor,object:AccessibilityService.TakeScreenshotCallback {
    override fun onSuccess(result:AccessibilityService.ScreenshotResult) {
     var bitmap:android.graphics.Bitmap?=null
     try {
      val buffer=result.hardwareBuffer
      try {
       bitmap=android.graphics.Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)?.copy(android.graphics.Bitmap.Config.ARGB_8888,false)
      } finally {buffer.close()}
      onResult(bitmap,if(bitmap==null)"Captura nu poate fi decodificată." else null)
     } catch(e:Exception){bitmap?.recycle();onResult(null,"Captură eșuată: "+e.javaClass.simpleName)}
    }
    override fun onFailure(errorCode:Int){onResult(null,"Android a refuzat captura (cod "+errorCode+").")}
   })
  }
  internal fun clickConfirmed(target:ElementTarget):Boolean=active?.clickExact(target)?:false
  fun observe():ScreenObservation?=active?.structuredScreen()
  fun resolve(target:ElementTarget):ScreenElement? { val svc=active?:return null;val obs=svc.structuredScreen()?:return null;if(target.pkg!=null&&target.pkg!=obs.packageName)return null;return ElementResolver.resolve(obs,target,svc.resources.displayMetrics.widthPixels,svc.resources.displayMetrics.heightPixels) }
 }
 override fun onServiceConnected(){active=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){event?.let{com.jarvis.mobile.core.skills.SkillRecorder.onAccessibilityEvent(it,packageName)}}
 override fun onInterrupt(){com.jarvis.mobile.core.skills.SkillRecorder.stop()}
 override fun onDestroy(){if(active===this){active=null;com.jarvis.mobile.core.skills.SkillRecorder.stop()};super.onDestroy()}
 internal fun editFocused(pkg:String,mode:String,value:String):Boolean {
  val root=rootInActiveWindow?:return false
  try {
   if(root.packageName?.toString()!=pkg)return false
   val n=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?:return false
   try {
    if(!n.refresh()||!n.isEditable||!n.isFocused||!n.isVisibleToUser||!n.isEnabled||n.isPassword||n.packageName?.toString()!=pkg)return false
    val hint= listOf(n.viewIdResourceName,n.contentDescription,n.hintText,n.text).joinToString(" ").lowercase()
    if(listOf("password","passcode","otp","verification","one time","one-time","pin code","parol","cod de verificare").any{hint.contains(it)})return false
    return when(mode){
     "type","clear" -> n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,if(mode=="clear")"" else value)})
     "copy" -> { val length=n.text?.length?:0; if(length==0)false else n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,android.os.Bundle().apply{putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,0);putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,length)}) && n.performAction(AccessibilityNodeInfo.ACTION_COPY) }
     "paste" -> n.performAction(AccessibilityNodeInfo.ACTION_PASTE)
     else -> false
    }
   }finally{n.recycle()}
  }finally{root.recycle()}
 }
 internal fun airTouchMotion(x:Int,y:Int,endX:Int,endY:Int,mode:String,pkg:String,fingerprint:String):Boolean {
  val obs=structuredScreen()?:return false
  if(obs.packageName!=pkg || obs.fingerprint()!=fingerprint)return false
  val m=resources.displayMetrics
  fun valid(a:Int,b:Int)=a in 0 until m.widthPixels && b in 0 until m.heightPixels
  if(!valid(x,y)||!valid(endX,endY))return false
  if(mode in setOf("tap","double","long"))return elementGesture(x,y,mode,pkg,fingerprint)
  val builder=android.accessibilityservice.GestureDescription.Builder()
  if(mode=="drag"){
   if(x==endX && y==endY)return false
   val path=android.graphics.Path().apply{moveTo(x.toFloat(),y.toFloat());lineTo(endX.toFloat(),endY.toFloat())}
   builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,900))
  }else if(mode=="zoom_in" || mode=="zoom_out"){
   val radius=minOf(x,m.widthPixels-1-x,y,m.heightPixels-1-y,160)
   if(radius<40)return false
   val near=radius/4f
   val far=radius.toFloat()
   val start=if(mode=="zoom_in")near else far
   val end=if(mode=="zoom_in")far else near
   for(sign in listOf(-1,1)){
    val path=android.graphics.Path().apply{moveTo(x+sign*start,y.toFloat());lineTo(x+sign*end,y.toFloat())}
    builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,450))
   }
  }else return false
  return dispatchGesture(builder.build(),object:GestureResultCallback(){
   override fun onCompleted(gestureDescription:android.accessibilityservice.GestureDescription?){android.util.Log.d("JarvisAirTouch","Gesture completed: "+mode)}
   override fun onCancelled(gestureDescription:android.accessibilityservice.GestureDescription?){android.util.Log.w("JarvisAirTouch","Gesture cancelled: "+mode)}
  },null)
 }
 internal fun elementGesture(x:Int,y:Int,mode:String,pkg:String?,fingerprint:String):Boolean {
  val obs=structuredScreen()?:return false
  if(pkg==null||obs.packageName!=pkg||obs.fingerprint()!=fingerprint)return false
  val m=resources.displayMetrics
  if(x !in 0 until m.widthPixels||y !in 0 until m.heightPixels)return false
  val path=android.graphics.Path().apply{moveTo(x.toFloat(),y.toFloat())}
  val builder=android.accessibilityservice.GestureDescription.Builder()
  builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,if(mode=="long")650L else 60L))
  if(mode=="double")builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,120,60))
  return dispatchGesture(builder.build(),null,null)
 }
 /**
  * Wait for Android's gesture completion callback. Dispatch acceptance alone
  * does not mean the gesture actually finished.
  */
 /**
  * Completion-aware tap, double-tap and long-press for agent UI actions.
  * An Android callback confirms gesture completion, NOT the intended UI effect.
  */
 internal suspend fun elementGestureCompleted(x:Int,y:Int,mode:String,pkg:String?,fingerprint:String):Boolean {
  val obs=structuredScreen()?:return false
  if(pkg==null||obs.packageName!=pkg||obs.fingerprint()!=fingerprint)return false
  val m=resources.displayMetrics
  if(x !in 0 until m.widthPixels||y !in 0 until m.heightPixels)return false
  if(mode !in setOf("tap","double","long"))return false
  val path=android.graphics.Path().apply{moveTo(x.toFloat(),y.toFloat())}
  val builder=android.accessibilityservice.GestureDescription.Builder()
  builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,if(mode=="long")650L else 60L))
  if(mode=="double")builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,120,60))
  val gesture=builder.build()
  return kotlinx.coroutines.withTimeoutOrNull(2500L) {
   kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
    val callback=object:AccessibilityService.GestureResultCallback(){
     override fun onCompleted(gestureDescription:android.accessibilityservice.GestureDescription?){
      if(continuation.isActive)continuation.resume(true){}
     }
     override fun onCancelled(gestureDescription:android.accessibilityservice.GestureDescription?){
      if(continuation.isActive)continuation.resume(false){}
     }
    }
    val accepted=runCatching{dispatchGesture(gesture,callback,null)}.getOrDefault(false)
    if(!accepted&&continuation.isActive)continuation.resume(false){}
   }
  } ?: false
 }
 internal suspend fun swipeCompleted(x1:Int,y1:Int,x2:Int,y2:Int):Boolean {
  val m=resources.displayMetrics
  if(listOf(x1,x2).any{it !in 0 until m.widthPixels}||listOf(y1,y2).any{it !in 0 until m.heightPixels})return false
  val path=android.graphics.Path().apply{moveTo(x1.toFloat(),y1.toFloat());lineTo(x2.toFloat(),y2.toFloat())}
  val gesture=android.accessibilityservice.GestureDescription.Builder()
   .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,350)).build()
  return kotlinx.coroutines.withTimeoutOrNull(2500L) {
   kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
    val callback=object:AccessibilityService.GestureResultCallback(){
     override fun onCompleted(gestureDescription:android.accessibilityservice.GestureDescription?){
      if(continuation.isActive)continuation.resume(true){}
     }
     override fun onCancelled(gestureDescription:android.accessibilityservice.GestureDescription?){
      if(continuation.isActive)continuation.resume(false){}
     }
    }
    val accepted=runCatching{dispatchGesture(gesture,callback,null)}.getOrDefault(false)
    if(!accepted&&continuation.isActive)continuation.resume(false){}
   }
  } ?: false
 }
 internal suspend fun scrollCompleted(forward:Boolean):Boolean {
  val m=resources.displayMetrics
  val x=m.widthPixels/2
  val top=(m.heightPixels*0.25).toInt()
  val bottom=(m.heightPixels*0.75).toInt()
  return if(forward)swipeCompleted(x,bottom,x,top) else swipeCompleted(x,top,x,bottom)
 }
 internal fun scrollConfirmed(forward:Boolean):Boolean {
  val m=resources.displayMetrics
  val x=m.widthPixels/2
  val top=(m.heightPixels*0.25).toInt()
  val bottom=(m.heightPixels*0.75).toInt()
  return if(forward)swipeConfirmed(x,bottom,x,top) else swipeConfirmed(x,top,x,bottom)
 }
 internal fun swipeConfirmed(x1:Int,y1:Int,x2:Int,y2:Int):Boolean {
  val m=resources.displayMetrics
  if(listOf(x1,x2).any{it !in 0 until m.widthPixels}||listOf(y1,y2).any{it !in 0 until m.heightPixels})return false
  val path=android.graphics.Path().apply{moveTo(x1.toFloat(),y1.toFloat());lineTo(x2.toFloat(),y2.toFloat())}
  val gesture=android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,350)).build()
  return dispatchGesture(gesture,null,null)
 }
 private fun clickExact(target:ElementTarget):Boolean{
  if(target.pkg.isNullOrBlank()||!target.hasNode)return false
  val root=rootInActiveWindow?:return false
  val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root)
  val matches=mutableListOf<AccessibilityNodeInfo>()
  var count=0
  try{
   if(root.packageName?.toString()!=target.pkg)return false
   while(queue.isNotEmpty()&&count<200){
    val n=queue.removeFirst();count++
    try{
     if(n.isPassword||n.isEditable)continue
     if(n.packageName?.toString()==target.pkg&&n.isVisibleToUser&&n.isEnabled&&n.isClickable&&
       (target.viewId==null||target.viewId==n.viewIdResourceName)&&
       (target.text==null||target.text==n.text?.toString())&&
       (target.desc==null||target.desc==n.contentDescription?.toString())){
      matches.add(AccessibilityNodeInfo.obtain(n))
     }
     for(j in 0 until n.childCount){if(queue.size>=200)return false;n.getChild(j)?.let{queue.add(it)}}
    }finally{n.recycle()}
   }
   if(queue.isNotEmpty()||matches.size!=1)return false
   val node=matches.single()
   if(!node.refresh()||!node.isVisibleToUser||!node.isEnabled||!node.isClickable||node.isPassword||node.isEditable||node.packageName?.toString()!=target.pkg)return false
   if(target.viewId!=null&&target.viewId!=node.viewIdResourceName)return false
   if(target.text!=null&&target.text!=node.text?.toString())return false
   if(target.desc!=null&&target.desc!=node.contentDescription?.toString())return false
   val current=rootInActiveWindow?:return false
   try{if(current.packageName?.toString()!=target.pkg)return false}finally{current.recycle()}
   return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
  }finally{matches.forEach{it.recycle()};while(queue.isNotEmpty())queue.removeFirst().recycle()}
 }
 private fun structuredScreen():ScreenObservation?{
  val root=rootInActiveWindow?:return null
  val pkg=root.packageName?.toString()
  val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root)
  val elements=mutableListOf<ScreenElement>();var count=0
  try{
   while(queue.isNotEmpty()&&count<200&&elements.size<60){
    val n=queue.removeFirst();count++
    try{
     // Expose safe field geometry, never editable text, hints, descriptions or password nodes.
     if(n.isPassword)continue
     if(n.isVisibleToUser&&n.packageName?.toString()==pkg){
      val rect=android.graphics.Rect();n.getBoundsInScreen(rect)
      val editable=n.isEditable
      val label=if(editable)null else n.text?.toString()?.take(120)
      val desc=if(editable)null else n.contentDescription?.toString()?.take(120)
      if(!rect.isEmpty&&(editable||n.isClickable||n.isScrollable||!label.isNullOrBlank()||!desc.isNullOrBlank())){
       elements.add(ScreenElement(elements.size,if(editable)"edittext" else if(n.isClickable)"button" else "other",label,desc,if(editable)null else n.viewIdResourceName,n.className?.toString(),rect.left,rect.top,rect.right,rect.bottom,n.isClickable&&n.isEnabled,editable,n.isScrollable,n.isSelected,false))
      }
     }
     for(j in 0 until minOf(n.childCount,30)){if(queue.size>=200)break;n.getChild(j)?.let{queue.add(it)}}
    }finally{n.recycle()}
   }
   return ScreenObservation(pkg,null,elements,truncated=queue.isNotEmpty())
  }finally{while(queue.isNotEmpty())queue.removeFirst().recycle()}
 }
 private fun readScreen():String?{
  val root=rootInActiveWindow ?: return null
  val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
  queue.add(root)
  val lines=mutableListOf<String>()
  val packageName=root.packageName?.toString() ?: "necunoscut"
  var count=0
  try{
   while(queue.isNotEmpty() && count<200){
    val n=queue.removeFirst();count++
    try{
     if(n.isPassword||n.isEditable)continue
     if(n.isVisibleToUser && !n.isEditable){
      val value=(n.text ?: n.contentDescription)?.toString()?.take(240)
      if(!value.isNullOrBlank()){
       val bounds=android.graphics.Rect();n.getBoundsInScreen(bounds)
       lines.add(value + if(n.isClickable && n.isEnabled) " [interactiv; ${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]" else "")
      }
     }
     for(i in 0 until minOf(n.childCount,30)){if(queue.size>=200)break;n.getChild(i)?.let{queue.add(it)}}
    }finally{n.recycle()}
   }
  }finally{while(queue.isNotEmpty())queue.removeFirst().recycle()}
  return ("Aplicație: "+packageName+"\n"+lines.distinct().joinToString("\n").ifBlank{"Niciun text vizibil."}).take(8000)
 }
}
