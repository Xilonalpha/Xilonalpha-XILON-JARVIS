package com.xilon.jarvis
internal class VoiceRetryPolicy {
 private var failures=0
 private var next=0L
 fun failed(now:Long){failures=(failures+1).coerceAtMost(5);next=now+(500L shl (failures-1)).coerceAtMost(8000L)}
 fun ready(now:Long)=now>=next
 fun reset(){failures=0;next=0L}
}
