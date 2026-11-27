package com.jarvis.ai.gesture

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runs MediaPipe Hand Landmarker and emits an index-tip position plus simple hand shortcuts. */
class GestureEngine(
    context: Context,
    private val onFrame: (GestureFrame) -> Unit,
    private val onFailure: (String) -> Unit,
) : Closeable {
    private val appContext = context.applicationContext
    private var landmarker: HandLandmarker? = null
    private var lastTimestampMs = 0L
    val customGestures = CustomGestureSession()
    private val throttle = PowerSaveThrottler()
    private val settings = appContext.getSharedPreferences("air_touch_profile", Context.MODE_PRIVATE)
    private var lastPinchAtMs = 0L
    private val trackingGuard = TrackingGuard(StabilityConfig(settleMs = 250), 150)
    private var pointerThreshold=0.32f
    private var pointer=PointerEngine(PointerConfig(pinchClose=pointerThreshold,pinchOpen=pointerThreshold+0.12f,pinchMinClosedFrames=3,pinchArmMs=250,scrollEnabled=false))
    private var pinchFrames=0
    private var pinchLatched=false

    /** Loads an imported MediaPipe task model from private app storage. */
    fun initialize() {
        val modelFile = File(appContext.filesDir, "hand_landmarker.task")
        check(modelFile.isFile && modelFile.length() in (MIN_TASK_FILE_BYTES + 1)..(64L * 1024 * 1024)) {
            "Import hand_landmarker.task from the JarvisAI dashboard before enabling Air Touch."
        }
        val modelBuffer = ByteBuffer.allocateDirect(modelFile.length().toInt())
            .order(ByteOrder.nativeOrder())
        modelFile.inputStream().use { input ->
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                modelBuffer.put(bytes, 0, count)
            }
        }
        modelBuffer.rewind()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(modelBuffer).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.55f)
            .setMinHandPresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.55f)
            .build()
        landmarker = HandLandmarker.createFromOptions(appContext, options)
    }

    /** Processes and closes one CameraX frame; MediaPipe work stays on CameraX's analyzer thread. */
    @OptIn(ExperimentalGetImage::class)
    fun process(imageProxy: ImageProxy) {
        val activeLandmarker = landmarker
        if (activeLandmarker == null) {
            imageProxy.close()
            return
        }
        val frameTime = SystemClock.elapsedRealtime()
        if (!throttle.shouldProcess(frameTime)) { imageProxy.close(); return }
        var mpImage: com.google.mediapipe.framework.image.MPImage? = null
        var bitmap: android.graphics.Bitmap? = null
        try {
            val source = imageProxy.toBitmap()
            bitmap = source
            if (source.config != android.graphics.Bitmap.Config.ARGB_8888) {
                bitmap = source.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                source.recycle()
            }
            if(settings.getBoolean("low_light",false)){
                val input=checkNotNull(bitmap)
                val pixels=IntArray(input.width*input.height)
                input.getPixels(pixels,0,input.width,0,0,input.width,input.height)
                val config=LowLightConfig(enabled=true)
                val luma=LowLight.meanLuma(pixels,input.width,input.height)
                if(luma<config.darkLuma){
                    LowLight.applyLut(pixels,LowLight.gammaLut(luma,config.targetLuma,config.minGamma))
                    val corrected=android.graphics.Bitmap.createBitmap(pixels,input.width,input.height,android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap=corrected
                    input.recycle()
                }
            }
            mpImage = BitmapImageBuilder(checkNotNull(bitmap)).build()
            val timestamp = maxOf(SystemClock.uptimeMillis(), lastTimestampMs + 1)
            lastTimestampMs = timestamp
            val imageOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
                .build()
            val result = activeLandmarker.detectForVideo(mpImage, imageOptions, timestamp)
            val landmarks = result.landmarks().firstOrNull()
            throttle.onProcessed(frameTime, landmarks != null && landmarks.size >= REQUIRED_LANDMARKS)
            if (landmarks == null || landmarks.size < REQUIRED_LANDMARKS) {
                trackingGuard.onNoHand(timestamp)
                pointer.process(HandFrame(timestamp,null))
                customGestures.process(HandFrame(timestamp,null))
                emit( GestureFrame(point = null, gesture = HandGesture.NONE) )
            } else {
                val aspect = if (imageProxy.imageInfo.rotationDegrees % 180 == 0) imageProxy.width.toFloat() / imageProxy.height else imageProxy.height.toFloat() / imageProxy.width
                val raw = landmarks.map { Landmark(it.x(), it.y(), it.z()) }
                val restart = trackingGuard.onHand(timestamp, raw, aspect)
                if (restart) { pointer.reset(); emit(GestureFrame(null, HandGesture.NONE)) }
                val threshold=settings.getFloat("pinch_ratio",PINCH_RATIO).coerceIn(0.15f,0.55f)
                if(threshold!=pointerThreshold){pointerThreshold=threshold;pointer=PointerEngine(PointerConfig(pinchClose=threshold,pinchOpen=threshold+0.12f,pinchMinClosedFrames=3,pinchArmMs=250,scrollEnabled=false))}
                val handFrame=HandFrame(timestamp,raw,aspect)
                val custom=customGestures.process(handFrame)
                if(customGestures.isRecording()){emit(GestureFrame(null,HandGesture.NONE));return}
                if(custom!=null && trackingGuard.settled(timestamp)){emit(GestureFrame(null,HandGesture.NONE,customGestureId=custom.template.id));return}
                val output=pointer.process(handFrame)
                val positions = landmarks.map { PointF(it.x(), it.y()) }
                val gesture = if (trackingGuard.settled(timestamp)) classify(raw) else HandGesture.NONE
                val indexTip = if (gesture == HandGesture.TWO_FINGERS) PointF((positions[8].x + positions[12].x)/2f, (positions[8].y + positions[12].y)/2f) else if (gesture == HandGesture.OPEN_PALM) raw.centroid(TrackedPoint.PALM_CENTER.indices).let { PointF(it.first,it.second) } else positions[INDEX_TIP]
                emit(
                    GestureFrame(
                        point = PointF(indexTip.x.coerceIn(0f, 1f), indexTip.y.coerceIn(0f, 1f)),
                        gesture = if(output.event is PointerEvent.Press && trackingGuard.settled(timestamp)) HandGesture.PINCH_TAP else if(gesture==HandGesture.PINCH_TAP) HandGesture.NONE else gesture,
                        pressPoint = (output.event as? PointerEvent.Press)?.at,
                        pinchHeld = output.pinchClosed && trackingGuard.settled(timestamp),
                    ),
                )
            }
        } catch (error: Exception) {
            onFailure(error.message ?: "Hand tracking could not process a camera frame.")
        } finally {
            runCatching { mpImage?.close() }
            bitmap?.recycle()
            imageProxy.close()
        }
    }

    /** Releases MediaPipe's native model resources. */
    override fun close() {
        runCatching { landmarker?.close() }
        landmarker = null
        customGestures.cancel()
    }

    private val poseClassifier = StaticGestureClassifier(PalmConfig())
    private fun classify(points: List<Landmark>): HandGesture {
        val pose = poseClassifier.classify(points)
        val threshold = settings.getFloat("pinch_ratio", PINCH_RATIO).coerceIn(0.15f, 0.55f)
        if (pose != HandPose.FIST && PinchGeometry.isPinchHand(points, 1.15f) &&
            PinchGeometry.ratio(points) < threshold) return HandGesture.PINCH_TAP
        return when (pose) {
            HandPose.FIST -> HandGesture.FIST
            HandPose.OPEN_PALM -> HandGesture.OPEN_PALM
            HandPose.TWO_FINGERS -> HandGesture.TWO_FINGERS
            HandPose.POINTING -> HandGesture.POINTING
            else -> HandGesture.NONE
        }
    }

    private fun emit(frame: GestureFrame) {
        onFrame(frame)
    }

    private companion object {
        const val MIN_TASK_FILE_BYTES = 1_024L
        const val REQUIRED_LANDMARKS = 21
        const val WRIST = 0
        const val THUMB_TIP = 4
        const val INDEX_TIP = 8
        const val MIDDLE_MCP = 9
        val FINGER_TIPS = intArrayOf(8, 12, 16, 20)
        val FINGER_MCPS = intArrayOf(5, 9, 13, 17)
        const val PINCH_RATIO = 0.32f
        const val FINGER_EXTENSION_RATIO = 1.16f
        const val PINCH_DEBOUNCE_MILLIS = 450L
    }
}

/** A normalized index-tip coordinate and the recognized shortcut for one camera frame. */
data class GestureFrame(
    val point: PointF?,
    val gesture: HandGesture,
    val pressPoint: NormPoint? = null,
    val customGestureId: Long? = null,
    val pinchHeld: Boolean = false,
)

/** Shortcut classes supported by Jarvis Air Touch. */
enum class HandGesture {
    NONE,
    PINCH_TAP,
    FIST,
    OPEN_PALM,
    TWO_FINGERS,
    POINTING,
}
