package com.xilon.jarvis
import kotlin.math.sqrt
/** Conservative reference-correlated subtraction; not a certified acoustic echo canceller. */
object LocalEchoReference {
 private val ring=FloatArray(16000)
 private var end=0L
 private var updated=0L
 @Synchronized fun clear(){end=0;updated=0;ring.fill(0f)}
 @Synchronized fun offer(samples:FloatArray,offset:Int,count:Int,rate:Int){
  if(rate<=0 || count<=0)return
  val n=(count.toLong()*16000/rate).toInt()
  for(i in 0 until n){ring[(end%ring.size).toInt()]=samples[offset+(i.toLong()*rate/16000).toInt().coerceAtMost(count-1)];end++}
  updated=System.nanoTime()/1000000
 }
 @Synchronized fun suppress(pcm:ShortArray,count:Int):Boolean {
  if(count<160 || count>pcm.size || end<count || System.nanoTime()/1000000-updated>700)return false
  var best=0.0;var gain=0.0;var start=0L
  val oldest=maxOf(0L,end-ring.size)
  var candidate=end-count
  while(candidate>=oldest && end-count-candidate<=8000){
   var xx=0.0;var yy=0.0;var xy=0.0
   for(i in 0 until count step 8){val x=ring[((candidate+i)%ring.size).toInt()].toDouble();val y=pcm[i]/32768.0;xx+=x*x;yy+=y*y;xy+=x*y}
   val corr=if(xx>0.001 && yy>0.001)xy/sqrt(xx*yy) else 0.0
   if(corr>best){best=corr;gain=xy/xx;start=candidate}
   candidate-=16
  }
  if(best<0.85 || gain !in 0.02..2.0)return false
  for(i in 0 until count){val ref=ring[((start+i)%ring.size).toInt()];pcm[i]=(pcm[i]-ref*gain*32768).toInt().coerceIn(-32768,32767).toShort()}
  return true
 }
}
