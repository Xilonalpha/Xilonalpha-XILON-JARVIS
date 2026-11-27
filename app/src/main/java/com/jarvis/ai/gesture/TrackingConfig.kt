/* MIT import from https://github.com/Serg1o2/GestureControl/tree/7db01a590d2eedd225b9305b7eadf536bca1b054; vendor/gesturecontrol/LICENSE */
package com.jarvis.ai.gesture

data class StabilityConfig(
    /**
     * A palm jump between consecutive detections larger than this (in hand sizes, isotropic)
     * means the tracker lost the hand or switched to another one: tracking restarts as for a new hand.
     * 0 disables the check. Default chosen on the replay bench (docs/phantoms.md).
     */
    val maxJumpHandSizes: Float = 2f,
    /**
     * After the hand appears (or tracking restarts) nothing fires for this long and swipes start
     * from scratch, so the motion of a hand entering the frame is not a swipe. 0 disables.
     */
    val settleMs: Long = 0,
)

enum class TrackedPoint(val indices: List<Int>) {
    WRIST(listOf(HandLandmarks.WRIST)),
    INDEX_TIP(listOf(HandLandmarks.INDEX_TIP)),

    /** Average of the wrist and finger MCP joints: the most stable choice. */
    PALM_CENTER(
        listOf(
            HandLandmarks.WRIST, HandLandmarks.INDEX_MCP, HandLandmarks.MIDDLE_MCP,
            HandLandmarks.RING_MCP, HandLandmarks.PINKY_MCP,
        ),
    ),
}

