/* MIT import from https://github.com/Serg1o2/GestureControl/tree/7db01a590d2eedd225b9305b7eadf536bca1b054; vendor/gesturecontrol/LICENSE */
package com.jarvis.ai.gesture

data class SwipeConfig(
    val trackedPoint: TrackedPoint = TrackedPoint.PALM_CENTER,
    /** Minimum horizontal travel of the tracked point. */
    val minDistance: Float = 0.25f,
    /** Minimum average horizontal speed over the swipe. */
    val minVelocity: Float = 0.8f,
    /** The whole swipe must fit into this window. */
    val maxDurationMs: Long = 600,
    /** |dx| must be at least this many times larger than |dy|. */
    val axisDominance: Float = 1.5f,
    /**
     * After a swipe, the opposite swipe is ignored for this long,
     * so that bringing the hand back does not trigger the reverse action.
     */
    val oppositeSuppressMs: Long = 900,
    /** A swipe must be seen on at least this many frames (start and end included): rejects one-frame jumps. */
    val minFrames: Int = 3,
    /**
     * Poses a palm swipe is made with; empty = any pose except the pointer poses.
     * Casual hand motion (pointing around, gesturing while talking) is mostly in undefined poses.
     */
    val poses: Set<HandPose> = emptySet(),
    /** Share of the swipe's frames that must show one of [poses]. */
    val minPoseShare: Float = 0.6f,
    /**
     * When true, [minDistance] and [minVelocity] are in hand sizes (wrist to middle MCP, isotropic)
     * instead of frame widths: the same motion counts at any distance and in any frame orientation.
     */
    val inHandSizes: Boolean = false,
)

