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
  var count=0
  try{
   while(queue.isNotEmpty() && count<200){
    val n=queue.removeFirst();count++
    try{
     if(n.isPassword)continue
     if(n.isVisibleToUser && !n.isEditable){
      val value=(n.text ?: n.contentDescription)?.toString()?.take(240)
      if(!value.isNullOrBlank())lines.add(value)
     }
     for(i in 0 until minOf(n.childCount,30)){if(queue.size>=200)break;n.getChild(i)?.let{queue.add(it)}}
    }finally{n.recycle()}
   }
  }finally{while(queue.isNotEmpty())queue.removeFirst().recycle()}
  return lines.distinct().joinToString("\n").take(8000)
 }
}
