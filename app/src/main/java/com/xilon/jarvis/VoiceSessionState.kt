package com.xilon.jarvis
import java.util.concurrent.atomic.AtomicLong
/** Rejects stale callbacks from cancelled or superseded local ASR turns. */
internal class VoiceSessionState {
 private val epoch=AtomicLong(0L)
 @Volatile private var closed=false
 @Synchronized fun begin():Long { check(!closed) { "Voice sessions closed" }; return epoch.incrementAndGet() }
 fun isCurrent(ticket:Long):Boolean = !closed && ticket==epoch.get()
 @Synchronized fun cancel(){epoch.incrementAndGet()}
 @Synchronized fun close(){closed=true;epoch.incrementAndGet()}
}
