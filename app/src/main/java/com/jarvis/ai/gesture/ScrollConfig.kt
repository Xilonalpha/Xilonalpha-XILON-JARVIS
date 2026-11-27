/* MIT import from https://github.com/Serg1o2/GestureControl/tree/7db01a590d2eedd225b9305b7eadf536bca1b054; vendor/gesturecontrol/LICENSE */
package com.jarvis.ai.gesture

enum class VerticalDirection { UP, DOWN }
data class ScrollConfig(
    val minDistance: Float = 0.12f,
    val minVelocity: Float = 0.4f,
    val maxDurationMs: Long = 500,
    val axisDominance: Float = 1.5f,
    /** Minimum time between two scroll steps. */
    val cooldownMs: Long = 350,
    /** Moving the hand back is ignored for this long after a scroll. */
    val oppositeSuppressMs: Long = 600,
    /** A stroke must be seen on at least this many frames (start and end included). */
    val minFrames: Int = 3,
    /**
     * A stroke is reported only after it has held for this long without coming back by more than
     * [maxReturnShare] of its length: a two-finger tap (down and back up) is not a scroll. 0 = immediate.
     */
    val confirmMs: Long = 150,
    val maxReturnShare: Float = 0.5f,
    /** When true, [minDistance] and [minVelocity] are in hand sizes instead of frame heights. */
    val inHandSizes: Boolean = false,
)
