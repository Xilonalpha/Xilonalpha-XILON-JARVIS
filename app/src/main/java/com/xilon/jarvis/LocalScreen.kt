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
  fun snapshot():String?=active?.readScreen()
  fun observe():ScreenObservation?=active?.structuredScreen()
  fun resolve(target:ElementTarget):ScreenElement? { val svc=active?:return null;val obs=svc.structuredScreen()?:return null;if(target.pkg!=null&&target.pkg!=obs.packageName)return null;return ElementResolver.resolve(obs,target,svc.resources.displayMetrics.widthPixels,svc.resources.displayMetrics.heightPixels) }
 }
 override fun onServiceConnected(){active=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){}
 override fun onInterrupt(){}
 override fun onDestroy(){if(active===this)active=null;super.onDestroy()}
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
     if(n.isPassword)continue
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
