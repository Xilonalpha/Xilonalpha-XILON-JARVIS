package com.xilon.jarvis
import kotlin.math.hypot
internal class SceneTracker {
 data class Detection(val label:String,val x:Float,val y:Float)
 data class Track(val id:Int,val detection:Detection,val motion:String)
 private var previous=emptyList<Track>()
 private var next=1
 private var at=0L
 fun update(items:List<Detection>,now:Long):List<Track>{
  val candidates=if(now>=at && now-at<=15000L)previous.toMutableList() else mutableListOf()
  val result=items.map{d->
   val match=candidates.filter{it.detection.label==d.label}.minByOrNull{hypot(it.detection.x-d.x,it.detection.y-d.y)}?.takeIf{hypot(it.detection.x-d.x,it.detection.y-d.y)<0.25f}
   if(match==null)Track(next++,d,"nou") else {
    candidates.remove(match)
    val dx=d.x-match.detection.x;val dy=d.y-match.detection.y
    Track(match.id,d,when{hypot(dx,dy)<0.035f->"stabil în cadru";kotlin.math.abs(dx)>=kotlin.math.abs(dy)->if(dx>0)"spre dreapta" else "spre stânga";dy>0->"spre jos";else->"spre sus"})
   }
  }
  previous=result;at=now;return result
 }
}
