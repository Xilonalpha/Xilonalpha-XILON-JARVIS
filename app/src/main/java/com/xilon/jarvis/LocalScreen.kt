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
  fun connected():Boolean=active!=null
  fun snapshot():String?=active?.readScreen()
  internal fun clickConfirmed(target:ElementTarget):Boolean=active?.clickExact(target)?:false
  fun observe():ScreenObservation?=active?.structuredScreen()
  fun resolve(target:ElementTarget):ScreenElement? { val svc=active?:return null;val obs=svc.structuredScreen()?:return null;if(target.pkg!=null&&target.pkg!=obs.packageName)return null;return ElementResolver.resolve(obs,target,svc.resources.displayMetrics.widthPixels,svc.resources.displayMetrics.heightPixels) }
 }
 override fun onServiceConnected(){active=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){event?.let{com.jarvis.mobile.core.skills.SkillRecorder.onAccessibilityEvent(it,packageName)}}
 override fun onInterrupt(){com.jarvis.mobile.core.skills.SkillRecorder.stop()}
 override fun onDestroy(){if(active===this){active=null;com.jarvis.mobile.core.skills.SkillRecorder.stop()};super.onDestroy()}
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
     if(n.isPassword||n.isEditable)continue
     if(n.isVisibleToUser&&n.packageName?.toString()==pkg){
      val rect=android.graphics.Rect();n.getBoundsInScreen(rect)
      val label=n.text?.toString()?.take(120);val desc=n.contentDescription?.toString()?.take(120)
      if(!rect.isEmpty&&(n.isClickable||n.isScrollable||!label.isNullOrBlank()||!desc.isNullOrBlank())){
       elements.add(ScreenElement(elements.size,if(n.isClickable)"button" else "other",label,desc,n.viewIdResourceName,n.className?.toString(),rect.left,rect.top,rect.right,rect.bottom,n.isClickable&&n.isEnabled,false,n.isScrollable,n.isSelected,false))
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
