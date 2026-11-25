package com.xilon.jarvis
internal class RecentVisualObservation {
 companion object {
  fun relevant(question:String):Boolean=Regex("\\b(imagine|fotografie|poza|obiecte|fete|vizual|fotografia|vezi|vazut|ecran|captura|captură|text|scrie|scris|citeste|citește|ocr)\\b").containsMatchIn(RomanianLexicon.normalize(question))
 }
 private var value:String?=null
 private var at=0L
 @Synchronized fun put(text:String,now:Long){value=text.take(4000);at=now}
 @Synchronized fun get(now:Long):String?=if(now>=at&&now-at<=120000L)value else null
 @Synchronized fun clear(){value=null;at=0}
}
