package com.xilon.jarvis
import java.util.concurrent.atomic.AtomicBoolean

/** Shared single-slot gate for optional camera analysis across independent camera controllers. */
internal object VisionWorkGate {
 private val inUse=AtomicBoolean(false)
 fun tryEnter():Boolean=inUse.compareAndSet(false,true)
 fun leave(){inUse.set(false)}
}
