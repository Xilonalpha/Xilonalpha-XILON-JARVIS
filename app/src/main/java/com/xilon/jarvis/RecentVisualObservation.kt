package com.xilon.jarvis
internal class RecentVisualObservation {
 private var value:String?=null
 private var at=0L
 fun put(text:String,now:Long){value=text.take(4000);at=now}
 fun get(now:Long):String?=if(now>=at&&now-at<=120000L)value else null
 fun clear(){value=null;at=0}
}
