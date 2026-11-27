package com.xilon.jarvis
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path

/** Serializes continued strokes; at most one segment is in flight. */
internal class ContinuousAirDrag(private val service:AccessibilityService){
 private var stroke:GestureDescription.StrokeDescription?=null
 private var x=0f
 private var y=0f
 private var targetX=0f
 private var targetY=0f
 private var busy=false
 private var ending=false
 private var active=false
 fun start(px:Float,py:Float){
  if(active)return
  x=px;y=py;targetX=px;targetY=py;ending=false;active=true
  send()
 }
 fun move(px:Float,py:Float){if(active&&!ending){targetX=px;targetY=py;if(!busy)send()}}
 fun finish(){if(active){ending=true;if(!busy)send()}}
 private fun send(){
  if(!active||busy)return
  val endX=targetX;val endY=targetY;val final=ending
  val path=Path().apply{moveTo(x,y);if(x!=endX||y!=endY)lineTo(endX,endY)}
  val next=runCatching{stroke?.continueStroke(path,0,80,!final)?:GestureDescription.StrokeDescription(path,0,80,!final)}.getOrElse{reset();return}
  busy=true
  val accepted=runCatching{service.dispatchGesture(GestureDescription.Builder().addStroke(next).build(),object:AccessibilityService.GestureResultCallback(){
   override fun onCompleted(description:GestureDescription?){
    busy=false;x=endX;y=endY;stroke=next
    if(final)reset() else send()
   }
   override fun onCancelled(description:GestureDescription?){reset()}
  },null)}.getOrDefault(false)
  if(!accepted)reset()
 }
 private fun reset(){active=false;busy=false;ending=false;stroke=null}
}
