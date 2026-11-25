package com.jarvis.ai.gesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.view.View
import androidx.core.graphics.withSave
import kotlin.math.min

/** Draws a low-pass-filtered floating cursor and applies the hand shortcut visibility rules. */
class GestureCursorView(
    context: Context,
    private val onPinchTap: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF42D9C8.toInt()
        style = Paint.Style.FILL
        setShadowLayer(14f * density, 0f, 0f, 0xAA42D9C8.toInt())
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF1F7FA.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private var cursor = PointF(-1f, -1f)
    private var hasPosition = false
    private var cursorVisible = true
    var onFistHold:()->Unit={}
    var onScroll: (Int)->Unit={}
    var onHorizontal: (Int)->Unit={}
    private var palmX:Float?=null
    var onPointTap: ((Float,Float)->Unit)?=null
    private val profile = context.getSharedPreferences("air_touch_profile", Context.MODE_PRIVATE)
    var smoothing=profile.getFloat("smoothing", 0.22f).coerceIn(0.1f,0.65f)
        set(value) { field=value.coerceIn(0.1f,0.65f); profile.edit().putFloat("smoothing",field).apply() }
    private var palmAnchor:Float?=null
    private var lastScroll=0L
    private var fistSince=0L
    private var fistFired=false
    private var active=false
    fun setTracking(enabled:Boolean){palmAnchor=null;palmX=null;lastScroll=0L;active=enabled;hasPosition=false;fistSince=0;fistFired=false;cursorVisible=true;invalidate()}

    /** Updates the smoothed position and handles open-palm, fist, and pinch transitions. */
    fun update(frame: GestureFrame) {
        post {
            if(!active)return@post
            if(frame.point==null){palmAnchor=null;palmX=null;hasPosition=false;fistSince=0;fistFired=false;invalidate();return@post}
            if(frame.gesture!=HandGesture.FIST){fistSince=0;fistFired=false}
            frame.point?.let { normalized ->
                val mirrorX = 1f - normalized.x
                val targetX = mirrorX.coerceIn(0f, 1f) * (width-1).coerceAtLeast(0)
                val targetY = normalized.y.coerceIn(0f, 1f) * (height-1).coerceAtLeast(0)
                if (!hasPosition) {
                    cursor.set(targetX, targetY)
                    hasPosition = true
                } else {
                    cursor.x += smoothing.coerceIn(0.1f,0.65f) * (targetX - cursor.x)
                    cursor.y += smoothing.coerceIn(0.1f,0.65f) * (targetY - cursor.y)
                }
            }
            if(frame.gesture!=HandGesture.OPEN_PALM){palmAnchor=null;palmX=null}
            when (frame.gesture) {
                HandGesture.FIST -> {
                    cursorVisible=false
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(fistSince==0L)fistSince=now
                    if(!fistFired && now-fistSince>=900L){fistFired=true;onFistHold()}
                }
                HandGesture.OPEN_PALM -> {
                    cursorVisible=true
                    val y=frame.point?.y
                    if(y!=null){
                        val anchor=palmAnchor
                        val now=android.os.SystemClock.elapsedRealtime()
                        if(anchor==null){palmAnchor=y;palmX=frame.point?.x}
                        else if(kotlin.math.abs(y-anchor)>profile.getFloat("vertical_threshold",0.18f).coerceIn(0.08f,0.40f) && now-lastScroll>700L){
                            onScroll(if(y<anchor)1 else -1);lastScroll=now;palmAnchor=y;palmX=frame.point?.x
                        }else{
                            val x=frame.point?.x
                            val ax=palmX
                            if(x!=null && ax!=null && kotlin.math.abs(x-ax)>profile.getFloat("horizontal_threshold",0.22f).coerceIn(0.08f,0.40f) && now-lastScroll>900L){
                                onHorizontal(if(x<ax)1 else -1);lastScroll=now;palmX=x;palmAnchor=y
                            }
                        }
                    }
                }
                HandGesture.PINCH_TAP -> {
                    cursorVisible = true
                    val action=onPointTap
                    if(action!=null && hasPosition)action(cursor.x,cursor.y) else onPinchTap()
                }
                HandGesture.NONE -> Unit
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!cursorVisible || !hasPosition) return
        canvas.withSave {
            val radius = min(width, height).coerceAtLeast(1) * CURSOR_RADIUS_FRACTION
            drawCircle(cursor.x, cursor.y, radius, cursorPaint)
            drawCircle(cursor.x, cursor.y, radius + 4f * density, outlinePaint)
            drawCircle(cursor.x, cursor.y, 2.5f * density, outlinePaint)
        }
    }

    private companion object {
        const val LOW_PASS_ALPHA = 0.22f
        const val CURSOR_RADIUS_FRACTION = 0.012f
    }
}
