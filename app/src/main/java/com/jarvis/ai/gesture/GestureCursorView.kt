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
    private var cursorAspect=1f
    private var cursorSmoother=CursorSmoother(FilterConfig(minCutoff=1f,beta=8f),0.006f,cursorAspect)
    private var cursorVisible = true
    var onCustomGesture:(Long)->Unit={}
    var onTwoFingerScroll:(Int)->Unit={onScroll(it)}
    var onFistHold:()->Unit={}
    var onScroll: (Int)->Unit={}
    var onHorizontal: (Int)->Unit={}
    private var palmX:Float?=null
    private var scrollPose:HandGesture?=null
    private val scrollDetector=ScrollDetector(ScrollConfig())
    private var swipeThreshold=0.22f
    private var swipeDetector=SwipeDetector(SwipeConfig(minDistance=swipeThreshold))
    var onPinchMove: ((Float,Float)->Unit)?=null
    var onPinchRelease: ((Float,Float)->Unit)?=null
    var onTrackingLost: ()->Unit={}
    private var held=false
    var onPointTap: ((Float,Float)->Unit)?=null
    private val profile = context.getSharedPreferences("air_touch_profile", Context.MODE_PRIVATE)
    var smoothing=profile.getFloat("smoothing", 0.22f).coerceIn(0.1f,0.65f)
        set(value) { field=value.coerceIn(0.1f,0.65f); profile.edit().putFloat("smoothing",field).apply() }
    private var palmAnchor:Float?=null
    private var lastScroll=0L
    private var fistSince=0L
    private var fistFired=false
    @Volatile private var active=false
    @Volatile private var trackingEpoch=0L
    private val calibrationPoints=mutableListOf<NormPoint>()
    private var calibrationActive=false
    private var calibrationStart=0L
    private var calibrationAnchor:NormPoint?=null
    private val calibrationSamples=mutableListOf<NormPoint>()
    private var mapper=ScreenMapper.fromRegion()
    private var mapperLoaded=false
    var onCalibrationProgress: (Int,Float)->Unit={_,_->}
    var onCalibrationFinished: (Boolean)->Unit={}
    fun startCalibration(){
        calibrationPoints.clear()
        calibrationSamples.clear()
        calibrationAnchor=null
        calibrationActive=true
        hasPosition=false
        invalidate()
        onCalibrationProgress(0,0f)
    }
    fun cancelCalibration(){
        calibrationActive=false
        calibrationPoints.clear()
        calibrationSamples.clear()
        calibrationAnchor=null
        invalidate()
    }
    private fun captureCalibration(frame:GestureFrame){
        val p=frame.point
        if(frame.gesture!=HandGesture.POINTING || p==null){
            calibrationAnchor=null
            calibrationSamples.clear()
            return
        }
        val tip=NormPoint(1f-p.x,p.y)
        val now=android.os.SystemClock.elapsedRealtime()
        val anchor=calibrationAnchor
        if(anchor==null || anchor.distanceTo(tip)>0.02f){
            calibrationAnchor=tip
            calibrationStart=now
            calibrationSamples.clear()
        }
        calibrationSamples.add(tip)
        val progress=((now-calibrationStart)/1000f).coerceIn(0f,1f)
        onCalibrationProgress(calibrationPoints.size,progress)
        if(progress<1f)return
        calibrationPoints.add(NormPoint(calibrationSamples.map{it.x}.average().toFloat(),calibrationSamples.map{it.y}.average().toFloat()))
        calibrationAnchor=null
        calibrationSamples.clear()
        if(calibrationPoints.size==3){
            val solved=Calibration.solve(calibrationPoints)
            if(solved!=null){
                mapper=solved
                mapperLoaded=true
                profile.edit().putString("screen_mapper",solved.serialize()).apply()
                cursorSmoother.reset()
            }
            cancelCalibration()
            onCalibrationFinished(solved!=null)
        }else onCalibrationProgress(calibrationPoints.size,0f)
    }
    fun setTracking(enabled:Boolean){held=false;onTrackingLost();trackingEpoch++;if(!enabled)cancelCalibration();swipeDetector.reset();scrollDetector.resetStroke();scrollPose=null;cursorSmoother.reset();palmAnchor=null;palmX=null;lastScroll=0L;active=enabled;hasPosition=false;fistSince=0;fistFired=false;cursorVisible=true;invalidate()}

    /** Updates the smoothed position and handles open-palm, fist, and pinch transitions. */
    fun update(frame: GestureFrame) {
        val epoch=trackingEpoch
        post {
            if(!active || epoch!=trackingEpoch)return@post
            if(calibrationActive){captureCalibration(frame);invalidate();return@post}
            if(frame.customGestureId!=null){held=false;onTrackingLost();onCustomGesture(frame.customGestureId);return@post}
            if(frame.point==null){held=false;onTrackingLost();swipeDetector.reset();scrollDetector.resetStroke();scrollPose=null;cursorSmoother.reset();palmAnchor=null;palmX=null;hasPosition=false;fistSince=0;fistFired=false;invalidate();return@post}
            if(frame.gesture!=HandGesture.FIST){fistSince=0;fistFired=false}
            frame.point?.let { normalized ->
                val now=android.os.SystemClock.elapsedRealtime()
                val aspect=height.toFloat()/width.coerceAtLeast(1)
                if(aspect>0f && aspect!=cursorAspect){
                    cursorAspect=aspect
                    cursorSmoother=CursorSmoother(FilterConfig(minCutoff=1f,beta=8f),0.006f,aspect)
                }
                if(!mapperLoaded){mapper=profile.getString("screen_mapper",null)?.let{ScreenMapper.parse(it)}?:ScreenMapper.fromRegion();mapperLoaded=true}
                val mapped=mapper.map(NormPoint(1f-normalized.x,normalized.y))
                val filtered=cursorSmoother.update(mapped,now)
                val targetX=filtered.x*(width-1).coerceAtLeast(0)
                val targetY=filtered.y*(height-1).coerceAtLeast(0)
                if (!hasPosition) {
                    cursor.set(targetX, targetY)
                    hasPosition = true
                } else {
                    cursor.x += smoothing.coerceIn(0.1f,0.65f) * (targetX - cursor.x)
                    cursor.y += smoothing.coerceIn(0.1f,0.65f) * (targetY - cursor.y)
                }
            }
            if(held && !frame.pinchHeld){onPinchRelease?.invoke(cursor.x,cursor.y)}
            held=frame.pinchHeld
            if(held && frame.gesture!=HandGesture.PINCH_TAP){onPinchMove?.invoke(cursor.x,cursor.y);cursorVisible=true;invalidate();return@post}
            if(frame.gesture!=scrollPose){swipeDetector.reset();scrollDetector.resetStroke();palmAnchor=null;palmX=null;scrollPose=frame.gesture}
            when (frame.gesture) {
                HandGesture.FIST -> {
                    cursorVisible=false
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(fistSince==0L)fistSince=now
                    if(!fistFired && now-fistSince>=900L){fistFired=true;onFistHold()}
                }
                HandGesture.TWO_FINGERS -> {
                    cursorVisible=true
                    val point=frame.point
                    if(point!=null) {
                        when(scrollDetector.update(android.os.SystemClock.elapsedRealtime(),point.x,point.y)){
                            VerticalDirection.UP -> onTwoFingerScroll(1)
                            VerticalDirection.DOWN -> onTwoFingerScroll(-1)
                            null -> Unit
                        }
                    }
                }
                HandGesture.OPEN_PALM -> {
                    cursorVisible=true
                    val point=frame.point
                    if(point!=null){
                        val now=android.os.SystemClock.elapsedRealtime()
                        val threshold=profile.getFloat("horizontal_threshold",0.22f).coerceIn(0.08f,0.40f)
                        if(threshold!=swipeThreshold){
                            swipeThreshold=threshold
                            swipeDetector=SwipeDetector(SwipeConfig(minDistance=threshold))
                        }
                        if(now-lastScroll>=900L){
                            val direction=swipeDetector.update(now,point.x,point.y)
                            if(direction!=null){
                                onHorizontal(if(direction==SwipeDirection.LEFT)1 else -1)
                                lastScroll=now
                                palmAnchor=point.y
                            }else{
                                val anchor=palmAnchor
                                if(anchor==null){palmAnchor=point.y;palmX=point.x}
                                else {
                                    val dy=point.y-anchor
                                    val dx=point.x-(palmX?:point.x)
                                    if(kotlin.math.abs(dy)>=profile.getFloat("vertical_threshold",0.18f).coerceIn(0.08f,0.40f) && kotlin.math.abs(dy)>=kotlin.math.abs(dx)*1.5f){
                                        onScroll(if(dy<0)1 else -1)
                                        lastScroll=now
                                        palmAnchor=point.y
                                        palmX=point.x
                                        swipeDetector.reset()
                                    }
                                }
                            }
                        }else swipeDetector.reset()
                    }
                }
                HandGesture.PINCH_TAP -> {
                    cursorVisible = true
                    val action=onPointTap
                    val at=frame.pressPoint?.let{mapper.map(NormPoint(1f-it.x,it.y))}
                    if(action!=null && hasPosition)action(at?.x?.times((width-1).coerceAtLeast(0))?:cursor.x,at?.y?.times((height-1).coerceAtLeast(0))?:cursor.y) else onPinchTap()
                }
                HandGesture.POINTING -> { cursorVisible=true }
                HandGesture.NONE -> {cursorVisible=true}
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if(calibrationActive){
            val target=Calibration.TARGETS.getOrNull(calibrationPoints.size) ?: return
            val x=target.x*width
            val y=target.y*height
            canvas.drawCircle(x,y,18f*density,outlinePaint)
            canvas.drawCircle(x,y,5f*density,cursorPaint)
            return
        }
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
