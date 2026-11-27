/* MIT import from https://github.com/Serg1o2/GestureControl/tree/7db01a590d2eedd225b9305b7eadf536bca1b054; vendor/gesturecontrol/LICENSE */
package com.jarvis.ai.gesture


/**
 * One Euro filter followed by a dead zone. The filter removes most jitter while keeping
 * fast moves responsive; the dead zone keeps a resting cursor perfectly still.
 */
class CursorSmoother(filter: FilterConfig, private val deadZone: Float, private val aspect: Float) {
    private val fx = OneEuroFilter(filter.minCutoff, filter.beta, filter.dCutoff)
    private val fy = OneEuroFilter(filter.minCutoff, filter.beta, filter.dCutoff)
    private var output: NormPoint? = null

    fun update(p: NormPoint, timestampMs: Long): NormPoint {
        val filtered = NormPoint(fx.filter(p.x, timestampMs), fy.filter(p.y, timestampMs))
        val current = output
        if (current == null || current.distanceTo(filtered, aspect) > deadZone) output = filtered
        return output!!
    }

    fun reset() {
        fx.reset()
        fy.reset()
        output = null
    }
}
