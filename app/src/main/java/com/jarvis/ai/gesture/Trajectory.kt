/* MIT import from https://github.com/Serg1o2/GestureControl/tree/7db01a590d2eedd225b9305b7eadf536bca1b054; vendor/gesturecontrol/LICENSE */
package com.jarvis.ai.gesture

import kotlin.math.sqrt

/** Time-ordered buffer of 2D points with a bounded time window. */
internal class Trajectory(private val windowMs: Long) {
    data class Point(val t: Long, val x: Float, val y: Float)

    private val points = ArrayDeque<Point>()

    val last: Point? get() = points.lastOrNull()
    fun asList(): List<Point> = points

    fun add(t: Long, x: Float, y: Float) {
        points.addLast(Point(t, x, y))
        while (points.isNotEmpty() && t - points.first().t > windowMs) points.removeFirst()
    }

    fun clear() = points.clear()

    /** Average speed (units/s) between the oldest point within [spanMs] and the newest point. */
    fun speed(spanMs: Long): Float {
        val end = points.lastOrNull() ?: return 0f
        val start = points.firstOrNull { end.t - it.t <= spanMs } ?: return 0f
        val dt = (end.t - start.t) / 1000f
        if (dt <= 0f) return 0f
        val dx = end.x - start.x
        val dy = end.y - start.y
        return sqrt(dx * dx + dy * dy) / dt
    }
}

internal fun List<Landmark>.centroid(indices: List<Int>): Pair<Float, Float> {
    var x = 0f
    var y = 0f
    for (i in indices) {
        x += this[i].x
        y += this[i].y
    }
    return x / indices.size to y / indices.size
}
