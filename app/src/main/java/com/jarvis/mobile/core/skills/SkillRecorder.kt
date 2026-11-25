package com.jarvis.mobile.core.skills
import android.view.accessibility.AccessibilityEvent
import android.os.SystemClock
/** Adapted from ansaribilal14/jarvis MIT SkillRecorder event feed and deduplication. */
object SkillRecorder {
 const val MAX_STEPS=200
 private var packages:Set<String> = emptySet()
 private var lastStepKey:Pair<ElementTarget,Long>?=null
 private val steps=mutableListOf<ElementTarget>()
 @Synchronized fun start(packageNames:Set<String>){
  require(packageNames.isNotEmpty()&&packageNames.none{it.isBlank()})
  steps.clear();lastStepKey=null;packages=packageNames.toSet()
 }
 @Synchronized fun stop():List<ElementTarget>{packages=emptySet();return steps.toList().also{steps.clear();lastStepKey=null}}
 @Synchronized fun onAccessibilityEvent(e:AccessibilityEvent,selfPkg:String){
  val selected=e.packageName?.toString()?:return
  if(selected !in packages)return
  val now=SystemClock.elapsedRealtime()
  if(steps.size>=MAX_STEPS){packages=emptySet();return}
  if(e.eventType!=AccessibilityEvent.TYPE_VIEW_CLICKED||e.packageName?.toString()!=selected||selected==selfPkg)return
  val src=e.source?:return
  try{
   if(src.isPassword||src.isEditable||!src.isVisibleToUser||!src.isClickable||src.packageName?.toString()!=selected)return
   val id=src.viewIdResourceName?.takeIf{it.isNotBlank()}?:return
   val target=ElementTarget(viewId=id,pkg=selected)
   val last=lastStepKey
   if(last!=null&&last.first==target&&now-last.second<500)return
   lastStepKey=target to now;steps.add(target)
  }finally{src.recycle()}
 }
}
