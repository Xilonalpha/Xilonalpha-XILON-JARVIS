package com.xilon.jarvis
internal class VoiceRetryPolicy {
 @Volatile private var failures=0
 @Volatile private var next=0L
 @Synchronized fun failed(now:Long){failures=(failures+1).coerceAtMost(5);next=now+(500L shl (failures-1)).coerceAtMost(8000L)}
 fun ready(now:Long)=now>=next
 @Synchronized fun reset(){failures=0;next=0L}
}
