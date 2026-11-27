package com.xilon.jarvis.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner


import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class VoiceSessionPhase { IDLE, LISTENING, THINKING, SPEAKING, PAUSED }

enum class VoiceVisualMode(val labelRo: String) {
    CAMERA_HOLOGRAM("Hologramă Cam"),
    VOID_HOLOGRAM("Hologramă 3D"),
    PYRAMID_4X("Piramidă 4X"),
    ORB("Sferă"),
}

/**
 * Hardware-accelerated live Front-Camera preview using CameraX + [TextureView]
 * (no extra `camera-view` dependency needed). Mirrors horizontally for selfie/desk view.
 */
@Composable
fun FrontCameraPreviewSurface(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val holder = remember {
        object {
            var provider: ProcessCameraProvider? = null
            var surfaceTexture: SurfaceTexture? = null
            var pendingRequest: SurfaceRequest? = null

            fun tryFulfill() {
                val req = pendingRequest ?: return
                val st = surfaceTexture ?: return
                pendingRequest = null
                val res = req.resolution
                st.setDefaultBufferSize(res.width, res.height)
                val surface = Surface(st)
                req.provideSurface(surface, mainExecutor) {
                    surface.release()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        var disposed = false
        var boundPreview: Preview? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (disposed) return@addListener
                runCatching {
                    val provider = future.get()
                    holder.provider = provider
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider { request ->
                        holder.pendingRequest?.willNotProvideSurface()
                        holder.pendingRequest = request
                        holder.tryFulfill()
                    }
                    boundPreview = preview
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                    )
                }
            },
            mainExecutor,
        )
        onDispose {
            holder.pendingRequest?.willNotProvideSurface()
            holder.pendingRequest = null
            disposed = true
            boundPreview?.let { holder.provider?.unbind(it) }
            holder.provider = null
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                scaleX = -1f // Mirror front camera like a natural desk hologram mirror
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        holder.surfaceTexture = st
                        holder.tryFulfill()
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        holder.surfaceTexture = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Full sci-fi JARVIS Hologram Projection Stage.
 * Renders a volumetric light cone emerging directly from the top-center front camera emitter,
 * projecting a 3D wireframe geodesic sphere, counter-rotating Stark orbital rings, audio-reactive
 * radial equalizer, scanlines, chromatic shimmer, and live gyroscope/accelerometer 3D parallax.
 */
@Composable
fun SciFiHologramStage(
    phase: VoiceSessionPhase,
    audioLevel: Float,
    primaryColor: Color,
    secondaryColor: Color,
    tiltXDeg: Float,
    tiltYDeg: Float,
    showCameraEmitterBeam: Boolean,
    onTapHologram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "holoStage")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (phase) {
                    VoiceSessionPhase.SPEAKING -> 4200
                    VoiceSessionPhase.THINKING -> 2600
                    VoiceSessionPhase.LISTENING -> 5600
                    else -> 8800
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "holoSpin",
    )
    val scanProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "holoScan",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "holoPulse",
    )

    val smoothLevel by animateFloatAsState(
        targetValue = when (phase) {
            VoiceSessionPhase.LISTENING -> audioLevel.coerceIn(0.10f, 1f)
            VoiceSessionPhase.SPEAKING -> (0.42f + audioLevel * 0.58f).coerceIn(0.42f, 1f)
            VoiceSessionPhase.THINKING -> 0.55f
            VoiceSessionPhase.PAUSED, VoiceSessionPhase.IDLE -> 0.08f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "holoLevel",
    )

    val smoothTiltX by animateFloatAsState(
        targetValue = tiltXDeg.coerceIn(-25f, 25f),
        animationSpec = tween(180),
        label = "holoTiltX",
    )
    val smoothTiltY by animateFloatAsState(
        targetValue = tiltYDeg.coerceIn(-25f, 25f),
        animationSpec = tween(180),
        label = "holoTiltY",
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .semantics {
                contentDescription = "Hologramă 3D XILON JARVIS. Atinge pentru a vorbi sau a întrerupe."
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onTapHologram,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val parallaxX = (smoothTiltX / 25f) * w * 0.055f
            val parallaxY = (smoothTiltY / 25f) * h * 0.045f
            val cx = w * 0.5f + parallaxX
            val cy = h * 0.46f + parallaxY
            val holoRadius = minOf(w, h) * 0.255f * pulse * (1f + smoothLevel * 0.12f)

            // 1. Volumetric Projection Cone emerging from Top Front-Camera Lens (w/2, 0)
            if (showCameraEmitterBeam) {
                val emitterX = w * 0.5f
                val emitterY = 10f
                val spreadX = holoRadius * 1.48f
                val beamBottomY = cy + holoRadius * 1.15f

                val conePath = Path().apply {
                    moveTo(emitterX - 16f, emitterY)
                    lineTo(emitterX + 16f, emitterY)
                    lineTo(cx + spreadX, beamBottomY)
                    lineTo(cx - spreadX, beamBottomY)
                    close()
                }
                drawPath(
                    path = conePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.42f + smoothLevel * 0.16f),
                            primaryColor.copy(alpha = 0.28f + smoothLevel * 0.14f),
                            secondaryColor.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        startY = emitterY,
                        endY = beamBottomY,
                    ),
                )

                // Twin crisp laser edge rays framing the camera projection beam
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.85f), primaryColor.copy(alpha = 0.45f), Color.Transparent),
                        startY = emitterY,
                        endY = beamBottomY,
                    ),
                    start = Offset(emitterX - 10f, emitterY),
                    end = Offset(cx - spreadX * 0.94f, beamBottomY),
                    strokeWidth = 2.2f,
                )
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.85f), primaryColor.copy(alpha = 0.45f), Color.Transparent),
                        startY = emitterY,
                        endY = beamBottomY,
                    ),
                    start = Offset(emitterX + 10f, emitterY),
                    end = Offset(cx + spreadX * 0.94f, beamBottomY),
                    strokeWidth = 2.2f,
                )

                // Top Camera Lens Emitter Core + Anamorphic Sci-Fi Flare
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            primaryColor.copy(alpha = 0.72f),
                            Color.Transparent,
                        ),
                        center = Offset(emitterX, emitterY + 8f),
                        radius = 74f + smoothLevel * 28f,
                    ),
                    radius = 74f + smoothLevel * 28f,
                    center = Offset(emitterX, emitterY + 8f),
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, primaryColor, Color.White, primaryColor, Color.Transparent),
                        startX = emitterX - w * 0.34f,
                        endX = emitterX + w * 0.34f,
                    ),
                    start = Offset(emitterX - w * 0.34f, emitterY + 8f),
                    end = Offset(emitterX + w * 0.34f, emitterY + 8f),
                    strokeWidth = 3f,
                )

                // Floating holographic photons rising along the projection beam
                for (i in 0 until 24) {
                    val t = ((scanProgress + i * 0.0417f) % 1f)
                    val py = beamBottomY - t * (beamBottomY - emitterY - 24f)
                    val beamHalfAtY = 14f + (1f - t) * spreadX * 0.86f
                    val px = emitterX + sin(i * 1.9f + spin * 1.3f) * beamHalfAtY * 0.82f
                    val alpha = (sin(t * PI).toFloat() * (0.35f + smoothLevel * 0.45f)).coerceIn(0f, 0.9f)
                    drawCircle(
                        color = if (i % 3 == 0) Color.White.copy(alpha = alpha) else primaryColor.copy(alpha = alpha),
                        radius = 2.2f + (i % 3) * 1.1f,
                        center = Offset(px, py),
                    )
                }
            }

            // 2. Holographic Perspective Base Disc beneath the floating core
            val baseY = cy + holoRadius * 1.06f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.30f + smoothLevel * 0.18f),
                        secondaryColor.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, baseY),
                    radius = holoRadius * 1.1f,
                ),
                topLeft = Offset(cx - holoRadius * 0.95f, baseY - holoRadius * 0.22f),
                size = Size(holoRadius * 1.9f, holoRadius * 0.44f),
            )
            drawOval(
                color = primaryColor.copy(alpha = 0.42f),
                topLeft = Offset(cx - holoRadius * 0.78f, baseY - holoRadius * 0.16f),
                size = Size(holoRadius * 1.56f, holoRadius * 0.32f),
                style = Stroke(width = 1.6f),
            )

            // 3. Draw the True 3D Hologram Core (sphere + orbital HUD rings + spectrum)
            drawJarvis3DHologramCore(
                cx = cx,
                cy = cy,
                radius = holoRadius,
                spin = spin,
                scanProgress = scanProgress,
                smoothLevel = smoothLevel,
                tiltXRad = (smoothTiltX * PI / 180.0).toFloat(),
                tiltYRad = (smoothTiltY * PI / 180.0).toFloat(),
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
            )

            // 4. Sweeping CRT Hologram Scanline Overlay across the floating core
            val scanY = (cy - holoRadius * 1.1f) + scanProgress * (holoRadius * 2.2f)
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, primaryColor.copy(alpha = 0.75f), Color.White.copy(alpha = 0.9f), primaryColor.copy(alpha = 0.75f), Color.Transparent),
                    startX = cx - holoRadius * 1.25f,
                    endX = cx + holoRadius * 1.25f,
                ),
                start = Offset(cx - holoRadius * 1.25f, scanY),
                end = Offset(cx + holoRadius * 1.25f, scanY),
                strokeWidth = 2.4f,
            )
        }
    }
}

/**
 * Real 4-Way Pepper's Ghost Pyramid Desk Hologram.
 * Lay the phone flat on a table and place a transparent 4-sided acrylic hologram pyramid on the
 * center reticle to see JARVIS float in genuine 3D mid-air while talking hands-free!
 */
@Composable
fun PeppersGhostPyramidStage(
    phase: VoiceSessionPhase,
    audioLevel: Float,
    primaryColor: Color,
    secondaryColor: Color,
    onTapHologram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "pyramidHolo")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pyramidSpin",
    )
    val scanProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pyramidScan",
    )
    val smoothLevel by animateFloatAsState(
        targetValue = when (phase) {
            VoiceSessionPhase.LISTENING -> audioLevel.coerceIn(0.12f, 1f)
            VoiceSessionPhase.SPEAKING -> (0.45f + audioLevel * 0.55f).coerceIn(0.45f, 1f)
            VoiceSessionPhase.THINKING -> 0.55f
            else -> 0.10f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pyramidLevel",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                contentDescription = "Proiecție Hologramă Piramidă 4X Pepper's Ghost. Atinge pentru a vorbi."
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onTapHologram,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = minOf(size.width, size.height)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val offsetDist = minDim * 0.305f
            val quadRadius = minDim * 0.145f * (1f + smoothLevel * 0.10f)

            // Center acrylic pyramid placement guide (subtle diamond)
            val guide = minDim * 0.085f
            val diamond = Path().apply {
                moveTo(cx, cy - guide)
                lineTo(cx + guide, cy)
                lineTo(cx, cy + guide)
                lineTo(cx - guide, cy)
                close()
            }
            drawPath(diamond, color = primaryColor.copy(alpha = 0.24f), style = Stroke(width = 1.4f))

            // 4 synchronized 90°-rotated views (Top, Right, Bottom, Left)
            val quadrants = listOf(
                Triple(cx, cy - offsetDist, 180f), // Top faces outward/upward into top prism face
                Triple(cx + offsetDist, cy, 270f), // Right
                Triple(cx, cy + offsetDist, 0f),   // Bottom
                Triple(cx - offsetDist, cy, 90f),  // Left
            )
            quadrants.forEach { (qx, qy, deg) ->
                rotate(degrees = deg, pivot = Offset(qx, qy)) {
                    drawJarvis3DHologramCore(
                        cx = qx,
                        cy = qy,
                        radius = quadRadius,
                        spin = spin,
                        scanProgress = scanProgress,
                        smoothLevel = smoothLevel,
                        tiltXRad = 0f,
                        tiltYRad = 0f,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                    )
                }
            }
        }
    }
}

/**
 * Shared 3D Math + Sci-Fi Hologram Core renderer used by both Camera AR Hologram and 4X Pyramid Mode.
 */
private fun DrawScope.drawJarvis3DHologramCore(
    cx: Float,
    cy: Float,
    radius: Float,
    spin: Float,
    scanProgress: Float,
    smoothLevel: Float,
    tiltXRad: Float,
    tiltYRad: Float,
    primaryColor: Color,
    secondaryColor: Color,
) {
    // 1. Volumetric Core Ambient Aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f + smoothLevel * 0.25f),
                primaryColor.copy(alpha = 0.34f + smoothLevel * 0.20f),
                secondaryColor.copy(alpha = 0.12f),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = radius * 1.55f,
        ),
        radius = radius * 1.55f,
        center = Offset(cx, cy),
    )

    // 2. Outer Stark HUD Orbital Ring (segmented arcs + degree ticks)
    val outerR = radius * 1.24f
    val degSpin = (spin * 180.0 / PI).toFloat()
    rotate(degrees = degSpin * 0.6f, pivot = Offset(cx, cy)) {
        for (seg in 0 until 4) {
            drawArc(
                color = primaryColor.copy(alpha = 0.65f),
                startAngle = seg * 90f + 10f,
                sweepAngle = 56f,
                useCenter = false,
                topLeft = Offset(cx - outerR, cy - outerR),
                size = Size(outerR * 2f, outerR * 2f),
                style = Stroke(width = 2.6f, cap = StrokeCap.Round),
            )
        }
    }
    rotate(degrees = -degSpin * 1.15f, pivot = Offset(cx, cy)) {
        val midR = radius * 1.08f
        for (seg in 0 until 6) {
            drawArc(
                color = secondaryColor.copy(alpha = 0.55f),
                startAngle = seg * 60f + 8f,
                sweepAngle = 32f,
                useCenter = false,
                topLeft = Offset(cx - midR, cy - midR),
                size = Size(midR * 2f, midR * 2f),
                style = Stroke(width = 1.8f, cap = StrokeCap.Round),
            )
        }
    }

    // 3. 48-Bar Audio-Reactive Holographic Equalizer Ring
    val barCount = 48
    val eqBaseR = radius * 1.14f
    val maxBar = radius * 0.24f
    for (i in 0 until barCount) {
        val angle = (i.toFloat() / barCount) * (2.0 * PI).toFloat()
        val harmonic = abs(sin(angle * 4f + spin * 2.2f) * cos(angle * 3f - spin * 1.5f))
        val barLen = 3f + maxBar * smoothLevel * (0.20f + 0.80f * harmonic)
        val sx = cx + eqBaseR * cos(angle)
        val sy = cy + eqBaseR * sin(angle)
        val ex = cx + (eqBaseR + barLen) * cos(angle)
        val ey = cy + (eqBaseR + barLen) * sin(angle)
        drawLine(
            color = if (i % 3 == 0) Color.White.copy(alpha = 0.45f + smoothLevel * 0.45f)
            else primaryColor.copy(alpha = 0.38f + smoothLevel * 0.52f),
            start = Offset(sx, sy),
            end = Offset(ex, ey),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round,
        )
    }

    // 4. True 3D Projected Rotating Wireframe Sphere (Latitude + Longitude + Chromatic Echo)
    val rotY = spin
    val rotX = 0.42f + tiltYRad * 0.85f
    val rotZ = tiltXRad * 0.65f
    val sphereR = radius * 0.82f
    val cameraDist = 3.2f

    fun project3D(x0: Float, y0: Float, z0: Float): Triple<Float, Float, Float> {
        // Rotate around Y
        val x1 = x0 * cos(rotY) + z0 * sin(rotY)
        val z1 = -x0 * sin(rotY) + z0 * cos(rotY)
        // Rotate around X
        val y2 = y0 * cos(rotX) - z1 * sin(rotX)
        val z2 = y0 * sin(rotX) + z1 * cos(rotX)
        // Rotate around Z
        val x3 = x1 * cos(rotZ) - y2 * sin(rotZ)
        val y3 = x1 * sin(rotZ) + y2 * cos(rotZ)
        val perspective = cameraDist / (cameraDist - z2)
        return Triple(cx + x3 * sphereR * perspective, cy + y3 * sphereR * perspective, z2)
    }

    // Latitude rings (5 parallels)
    val latSteps = 28
    for (latIdx in -2..2) {
        val phi = (latIdx * 0.27f * PI).toFloat()
        val y = sin(phi)
        val ringR = cos(phi)
        var prev: Triple<Float, Float, Float>? = null
        for (s in 0..latSteps) {
            val theta = (s.toFloat() / latSteps) * (2.0 * PI).toFloat()
            val waveMod = 1f + smoothLevel * 0.08f * sin(theta * 4f + spin * 2f)
            val pt = project3D(ringR * cos(theta) * waveMod, y, ringR * sin(theta) * waveMod)
            prev?.let { p ->
                val depthAlpha = (((pt.third + 1.1f) * 0.45f).coerceIn(0.14f, 0.92f))
                // Subtle chromatic aberration echo for authentic sci-fi hologram feel
                drawLine(
                    color = secondaryColor.copy(alpha = depthAlpha * 0.35f),
                    start = Offset(p.first + 2.2f, p.second),
                    end = Offset(pt.first + 2.2f, pt.second),
                    strokeWidth = 1.4f,
                )
                drawLine(
                    color = primaryColor.copy(alpha = depthAlpha),
                    start = Offset(p.first, p.second),
                    end = Offset(pt.first, pt.second),
                    strokeWidth = if (latIdx == 0) 2.2f else 1.5f,
                )
            }
            prev = pt
        }
    }

    // Longitude meridians (8 slices) + Glowing Neural Vertices
    val lonCount = 8
    val merSteps = 20
    for (m in 0 until lonCount) {
        val theta = (m.toFloat() / lonCount) * (2.0 * PI).toFloat()
        var prev: Triple<Float, Float, Float>? = null
        for (s in 0..merSteps) {
            val phi = ((s.toFloat() / merSteps) - 0.5f) * PI.toFloat()
            val x = cos(phi) * cos(theta)
            val y = sin(phi)
            val z = cos(phi) * sin(theta)
            val pt = project3D(x, y, z)
            prev?.let { p ->
                val depthAlpha = (((pt.third + 1.1f) * 0.42f).coerceIn(0.12f, 0.85f))
                drawLine(
                    color = primaryColor.copy(alpha = depthAlpha * 0.78f),
                    start = Offset(p.first, p.second),
                    end = Offset(pt.first, pt.second),
                    strokeWidth = 1.35f,
                )
            }
            if (s % 4 == 0 && pt.third > -0.2f) {
                drawCircle(
                    color = Color.White.copy(alpha = (0.35f + pt.third * 0.45f).coerceIn(0.2f, 0.95f)),
                    radius = 2.6f + smoothLevel * 1.8f,
                    center = Offset(pt.first, pt.second),
                )
            }
            prev = pt
        }
    }

    // 5. Inner Glowing AI Arc Reactor Nucleus
    val nucleusR = radius * (0.28f + smoothLevel * 0.14f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.96f),
                primaryColor.copy(alpha = 0.88f),
                secondaryColor.copy(alpha = 0.35f),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = nucleusR * 1.6f,
        ),
        radius = nucleusR * 1.6f,
        center = Offset(cx, cy),
    )

    // Subtle horizontal hologram scan shimmer bands inside the core
    val bandY = cy - radius * 0.7f + ((scanProgress * 1.5f) % 1f) * radius * 1.4f
    drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = Offset(cx - radius * 0.75f, bandY),
        end = Offset(cx + radius * 0.75f, bandY),
        strokeWidth = 1.4f,
        blendMode = BlendMode.SrcOver,
    )
}
