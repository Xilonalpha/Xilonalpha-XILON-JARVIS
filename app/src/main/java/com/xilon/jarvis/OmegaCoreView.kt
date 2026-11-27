package com.xilon.jarvis

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import com.xilon.jarvis.ui.*

class OmegaCoreView(context: Context) : AbstractComposeView(context), SensorEventListener {
    var session: OmegaCoreView? = null
    var active by mutableStateOf(false)
    var speaking by mutableStateOf(false)
    var listening by mutableStateOf(false)
    var level by mutableFloatStateOf(0f)
    var mode by mutableStateOf(VoiceVisualMode.VOID_HOLOGRAM)
    var onTap: () -> Unit = {}
    private var visible by mutableStateOf(false)
    private var tiltX by mutableFloatStateOf(0f)
    private var tiltY by mutableFloatStateOf(0f)
    private val sensors = context.getSystemService(SensorManager::class.java)

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visible = isVisible
        sensors.unregisterListener(this)
        if (isVisible) sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onDetachedFromWindow() {
        sensors.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onSensorChanged(event: SensorEvent) {
        tiltX = (-event.values[0] * 2f).coerceIn(-20f,20f)
        tiltY = (event.values[1] * 2f).coerceIn(-20f,20f)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Composable override fun Content() {
        if (!visible) return
        val source = session ?: this
        val phase = when {
            source.speaking -> VoiceSessionPhase.SPEAKING
            source.listening -> VoiceSessionPhase.LISTENING
            source.active -> VoiceSessionPhase.THINKING
            else -> VoiceSessionPhase.IDLE
        }
        val cyan = Color(0xff55e5ff)
        val blue = Color(0xff678aff)
        Box(Modifier.fillMaxSize()) {
            if (mode == VoiceVisualMode.CAMERA_HOLOGRAM) FrontCameraPreviewSurface(Modifier.fillMaxSize())
            if (mode == VoiceVisualMode.PYRAMID_4X)
                PeppersGhostPyramidStage(phase, source.level, cyan, blue, onTap, Modifier.fillMaxSize())
            else
                SciFiHologramStage(phase, source.level, cyan, blue, tiltX, tiltY, true, onTap, Modifier.fillMaxSize())
        }
    }
}
