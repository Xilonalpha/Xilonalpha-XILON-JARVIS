package com.xilon.jarvis
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
class LocalScreen:AccessibilityService(){
 companion object {
  @Volatile private var active:LocalScreen?=null
  fun snapshot():String?=active?.readScreen()
 }
 override fun onServiceConnected(){active=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){}
 override fun onInterrupt(){}
 override fun onDestroy(){if(active===this)active=null;super.onDestroy()}
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
