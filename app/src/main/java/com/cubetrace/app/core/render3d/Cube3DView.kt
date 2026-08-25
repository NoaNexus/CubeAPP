// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.render3d

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.cubetrace.app.core.cube.CubeState
import com.cubetrace.app.core.cube.normalizedMoves
import com.cubetrace.app.core.device.Quaternion
import com.cubetrace.app.core.model.SmartCubeFrame
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A small native 3D cube renderer.
 *
 * The cube is made from 27 cubies. Each cubie contributes its real outside
 * faces and each face has its own sticker quad. During playback the affected
 * layer is rotated continuously around the cube axis; the facelet string is
 * only committed by the caller after the animation completes. This avoids the
 * old frame-by-frame "teleporting" effect while keeping the renderer light
 * enough for a 120 Hz phone.
 */
@Composable
fun Cube3DView(
    facelets: String,
    modifier: Modifier = Modifier,
    animationFromFacelets: String? = null,
    animateMove: String? = null,
    animationKey: Int = 0,
    reducedMotion: Boolean = false,
    mutedSolved: Boolean = false,
    focusF2L: Boolean = false,
    focusFacelets: String? = null,
    animationSpeed: Float = 1f,
    sceneOrientation: Quaternion? = null,
    sceneOrientationFlow: StateFlow<Quaternion?>? = null,
    modelScale: Float = 1f,
    cubeFrame: SmartCubeFrame = SmartCubeFrame.PERSONAL_YELLOW_BLUE,
    interactiveView: Boolean = false,
    resetViewKey: Int = 0
) {
    val focusSource = focusFacelets ?: facelets
    val focusPlan = remember(focusF2L, focusSource) {
        if (focusF2L) f2lFocus(focusSource) else null
    }
    // During a layer animation the renderer paints animationFromFacelets and
    // rotates that source state, while facelets is already the final target
    // state. F2L masking must follow the painted source state; otherwise the
    // pair's indexes are looked up after the move and the moving pieces turn
    // gray or disappear halfway through the animation.
    val focusAnimationSource = animationFromFacelets ?: facelets
    val focusModel = remember(focusAnimationSource, focusPlan) {
        focusPlan?.atState(focusAnimationSource)
    }
    val targetFocusModel = remember(facelets, focusPlan) {
        focusPlan?.atState(facelets)
    }
    val rendererRef = remember { arrayOfNulls<Cube3DAndroidRenderer>(1) }
    androidx.compose.runtime.LaunchedEffect(sceneOrientationFlow) {
        val flow = sceneOrientationFlow ?: return@LaunchedEffect
        flow.collect { rendererRef[0]?.updateTargetOrientation(it) }
    }
    AndroidView(
        factory = { context ->
            Cube3DAndroidRenderer(context).also { rendererRef[0] = it }
        },
        modifier = modifier.clipToBounds().semantics {
            contentDescription = "3D cube, ${cubeFrame.label}${animateMove?.let { ", move $it" } ?: ""}" +
                if (interactiveView) ", 可拖动查看各面" else ""
        },
        update = { renderer ->
            rendererRef[0] = renderer
            renderer.configure(
                facelets = facelets,
                animationFromFacelets = animationFromFacelets,
                animateMove = animateMove,
                animationKey = animationKey,
                reducedMotion = reducedMotion,
                mutedSolved = mutedSolved,
                focusModel = focusModel,
                targetFocusModel = targetFocusModel,
                sceneOrientation = sceneOrientationFlow?.value ?: sceneOrientation,
                modelScale = modelScale,
                animationSpeed = animationSpeed,
                cubeFrame = cubeFrame,
                interactiveView = interactiveView,
                resetViewKey = resetViewKey
            )
        }
    )
}

private data class AnimationIdentity(val animationKey: Int, val move: String?)

/**
 * Hardware accelerated renderer used by the timer and formula pages.
 *
 * Keeping the frame loop inside a View is intentional: Compose remains
 * responsible for layout and state changes, while Android's display scheduler
 * redraws only this small surface during gyro follow or a layer turn.
 */
private class Cube3DAndroidRenderer(context: Context) : View(context) {
    private class RenderItem {
        var centerX = 0f
        var centerY = 0f
        var centerZ = 0f
        var horizontalX = 0f
        var horizontalY = 0f
        var horizontalZ = 0f
        var verticalX = 0f
        var verticalY = 0f
        var verticalZ = 0f
        var normalX = 0f
        var normalY = 0f
        var normalZ = 0f
        var halfSize = 0f
        var depthKey = 0
        var stableOrder = 0
        var color = COLOR_SHELL
        var sticker = false
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SHADOW }
    private val path = AndroidPath()
    private val shadowRect = RectF()
    private val items = Array(cubeQuads.size) { RenderItem() }
    private val order = IntArray(cubeQuads.size)
    private val projection = FloatArray(2)
    private val orientationValues = FloatArray(4)
    private val transformedCenter = FloatArray(3)
    private val transformedHorizontal = FloatArray(3)
    private val transformedVertical = FloatArray(3)
    private val transformedNormal = FloatArray(3)

    private var identity: AnimationIdentity? = null
    private var fromFacelets = CubeState.solved().asFacelets()
    private var targetFacelets = fromFacelets
    private var activeTurn: ActiveTurn? = null
    private var animationStartNs = 0L
    private var animationDurationNs = 1L
    private var animationProgress = 1f
    private var targetOrientation: Quaternion? = null
    private var renderedOrientation: Quaternion? = null
    private var previousFrameNs = 0L
    private var mutedSolved = false
    private var focusModel: F2LFocus? = null
    private var targetFocusModel: F2LFocus? = null
    private var modelScale = 1f
    private var cubeFrame = SmartCubeFrame.PERSONAL_YELLOW_BLUE
    private var interactiveView = false
    private var resetViewKey = 0
    private var manualYaw = 0.0
    private var manualPitch = 0.0
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var draggingView = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        // This view is deliberately opaque only where it draws; the parent
        // supplies the page background. Hardware mode keeps Path rasterisation
        // on the GPU instead of rebuilding Compose paths on the UI tree.
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setWillNotDraw(false)
        isClickable = true
    }

    fun configure(
        facelets: String,
        animationFromFacelets: String?,
        animateMove: String?,
        animationKey: Int,
        reducedMotion: Boolean,
        mutedSolved: Boolean,
        focusModel: F2LFocus?,
        targetFocusModel: F2LFocus?,
        sceneOrientation: Quaternion?,
        modelScale: Float,
        animationSpeed: Float,
        cubeFrame: SmartCubeFrame,
        interactiveView: Boolean,
        resetViewKey: Int
    ) {
        val nextFacelets = validFacelets(facelets, targetFacelets)
        val nextIdentity = AnimationIdentity(animationKey, animateMove)
        if (identity != nextIdentity) {
            identity = nextIdentity
            fromFacelets = validFacelets(animationFromFacelets, targetFacelets)
            targetFacelets = nextFacelets
            activeTurn = resolveActiveTurn(animateMove)
            animationProgress = if (activeTurn == null) 1f else 0f
            animationStartNs = System.nanoTime()
            val baseDurationNs = if (reducedMotion) 120_000_000L else 360_000_000L
            animationDurationNs = (baseDurationNs / animationSpeed.coerceIn(0.5f, 2f)).toLong()
                .coerceAtLeast(1L)
        } else {
            targetFacelets = nextFacelets
        }
        this.mutedSolved = mutedSolved
        this.focusModel = focusModel
        this.targetFocusModel = targetFocusModel
        this.modelScale = modelScale
        this.cubeFrame = cubeFrame
        this.interactiveView = interactiveView
        if (this.resetViewKey != resetViewKey) {
            this.resetViewKey = resetViewKey
            manualYaw = 0.0
            manualPitch = 0.0
        }
        updateTargetOrientation(sceneOrientation)
        postInvalidateOnAnimation()
    }

    fun updateTargetOrientation(value: Quaternion?) {
        if (targetOrientation == value) return
        targetOrientation = value
        if (value == null) {
            renderedOrientation = null
        } else if (renderedOrientation == null) {
            renderedOrientation = value
        }
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactiveView) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                draggingView = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val totalX = event.x - touchDownX
                val totalY = event.y - touchDownY
                if (!draggingView && totalX * totalX + totalY * totalY >= touchSlop * touchSlop) {
                    draggingView = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (draggingView) {
                    manualYaw += (event.x - lastTouchX) * MANUAL_ORBIT_RADIANS_PER_PIXEL
                    manualPitch += (event.y - lastTouchY) * MANUAL_ORBIT_RADIANS_PER_PIXEL
                    lastTouchX = event.x
                    lastTouchY = event.y
                    postInvalidateOnAnimation()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!draggingView) performClick()
                draggingView = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                draggingView = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val progress = sampleAnimation(now)
        val orientation = composeViewOrientation(sampleOrientation(now))
        val facelets = if (activeTurn != null && progress < 1f) fromFacelets else targetFacelets
        val focus = if (activeTurn != null && progress < 1f) {
            focusModel
        } else {
            targetFocusModel ?: focusModel
        }
        // Layer animation and whole-cube gyro orientation are independent:
        // accelerating the turn must not freeze the user's physical pose.
        drawCube(canvas, facelets, activeTurn, progress, orientation, focus)

        val animationRunning = activeTurn != null && progress < 1f
        val orientationRunning = targetOrientation != null && renderedOrientation != null &&
            renderedOrientation!!.angularDistance(targetOrientation!!) >= ORIENTATION_DEADBAND
        if (animationRunning || orientationRunning) postInvalidateOnAnimation()
    }

    private fun sampleAnimation(now: Long): Float {
        val turn = activeTurn ?: return 1f
        if (animationProgress >= 1f) return 1f
        val linear = ((now - animationStartNs).toDouble() / animationDurationNs.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
        animationProgress = linear * linear * (3f - 2f * linear)
        if (linear >= 1f) animationProgress = 1f
        // Keep the local reference alive for the duration of a turn; it also
        // makes the intent explicit for the compiler and future optimisers.
        check(turn.signedTurns != 0)
        return animationProgress
    }

    private fun sampleOrientation(now: Long): Quaternion? {
        val target = targetOrientation ?: run {
            renderedOrientation = null
            previousFrameNs = now
            return null
        }
        val current = renderedOrientation ?: target
        val deltaSeconds = if (previousFrameNs == 0L) {
            1.0 / 120.0
        } else {
            ((now - previousFrameNs) / 1_000_000_000.0).coerceIn(0.001, 0.05)
        }
        previousFrameNs = now
        val angle = current.angularDistance(target)
        renderedOrientation = if (angle >= ORIENTATION_DEADBAND) {
            Quaternion.slerp(current, target, 1.0 - exp(-deltaSeconds * 24.0))
        } else {
            target
        }
        return renderedOrientation
    }

    private fun composeViewOrientation(physical: Quaternion?): Quaternion? {
        if (manualYaw == 0.0 && manualPitch == 0.0) return physical
        val yawHalf = manualYaw / 2.0
        val pitchHalf = manualPitch / 2.0
        val yaw = Quaternion.normalized(0.0, sin(yawHalf), 0.0, cos(yawHalf))
        val pitch = Quaternion.normalized(sin(pitchHalf), 0.0, 0.0, cos(pitchHalf))
        val orbit = yaw.multiplied(pitch)
        return physical?.let { orbit.multiplied(it) } ?: orbit
    }

    private fun drawCube(
        canvas: AndroidCanvas,
        facelets: String,
        turn: ActiveTurn?,
        progress: Float,
        orientation: Quaternion?,
        focusModel: F2LFocus?
    ) {
        val scale = (min(width / 5.8f, height / 5.2f) * modelScale).coerceAtLeast(10f)
        val centerX = width / 2f
        val centerY = height / 2f - scale * 0.02f
        shadowRect.set(
            centerX - scale * 1.65f,
            centerY + scale * 1.05f,
            centerX + scale * 1.65f,
            centerY + scale * 1.53f
        )
        canvas.drawOval(shadowRect, shadowPaint)

        val turnActive = turn != null && progress < 1f
        val turnAngle = if (turnActive) {
            turn!!.direction * turn.signedTurns * (PI.toFloat() / 2f) * progress
        } else 0f
        val turnCos = cos(turnAngle)
        val turnSin = sin(turnAngle)
        val axis = turn?.axis
        val hasOrientation = orientation != null
        if (orientation != null) {
            orientationValues[0] = orientation.x.toFloat()
            orientationValues[1] = orientation.y.toFloat()
            orientationValues[2] = orientation.z.toFloat()
            orientationValues[3] = orientation.w.toFloat()
        }
        val frameOrientation = if (hasOrientation) orientationValues else null

        var visibleCount = 0
        for (index in cubeQuads.indices) {
            val quad = cubeQuads[index]
            val item = items[index]
            val rotateLayer = turnActive && turn!!.affects(quad.cubie)
            transform(
                quad.center, rotateLayer, axis, turnCos, turnSin, frameOrientation,
                transformedCenter
            )
            transform(
                quad.horizontal, rotateLayer, axis, turnCos, turnSin, frameOrientation,
                transformedHorizontal
            )
            transform(
                quad.vertical, rotateLayer, axis, turnCos, turnSin, frameOrientation,
                transformedVertical
            )
            transform(
                quad.normal, rotateLayer, axis, turnCos, turnSin, frameOrientation,
                transformedNormal
            )
            item.centerX = transformedCenter[0]
            item.centerY = transformedCenter[1]
            item.centerZ = transformedCenter[2]
            item.horizontalX = transformedHorizontal[0]
            item.horizontalY = transformedHorizontal[1]
            item.horizontalZ = transformedHorizontal[2]
            item.verticalX = transformedVertical[0]
            item.verticalY = transformedVertical[1]
            item.verticalZ = transformedVertical[2]
            item.normalX = transformedNormal[0]
            item.normalY = transformedNormal[1]
            item.normalZ = transformedNormal[2]
            item.halfSize = quad.halfSize
            item.stableOrder = index
            item.sticker = quad.stickerIndex != null
            item.color = shadedColor(baseColor(quad, facelets, focusModel), item.normalX, item.normalY, item.normalZ)

            val facing = item.normalX * cameraDirection.x +
                item.normalY * cameraDirection.y + item.normalZ * cameraDirection.z
            if (facing <= 0.025f) continue
            item.depthKey = (
                (item.centerX * cameraDirection.x +
                    item.centerY * cameraDirection.y +
                    item.centerZ * cameraDirection.z) * 256f
                ).roundToInt()
            order[visibleCount++] = index
        }

        for (i in 1 until visibleCount) {
            val value = order[i]
            var j = i - 1
            while (j >= 0 && compareItems(order[j], value) > 0) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = value
        }

        for (i in 0 until visibleCount) {
            val item = items[order[i]]
            path.reset()
            addProjectedPoint(
                path,
                item.centerX - item.horizontalX * item.halfSize - item.verticalX * item.halfSize,
                item.centerY - item.horizontalY * item.halfSize - item.verticalY * item.halfSize,
                item.centerZ - item.horizontalZ * item.halfSize - item.verticalZ * item.halfSize,
                centerX, centerY, scale, true
            )
            addProjectedPoint(
                path,
                item.centerX + item.horizontalX * item.halfSize - item.verticalX * item.halfSize,
                item.centerY + item.horizontalY * item.halfSize - item.verticalY * item.halfSize,
                item.centerZ + item.horizontalZ * item.halfSize - item.verticalZ * item.halfSize,
                centerX, centerY, scale, false
            )
            addProjectedPoint(
                path,
                item.centerX + item.horizontalX * item.halfSize + item.verticalX * item.halfSize,
                item.centerY + item.horizontalY * item.halfSize + item.verticalY * item.halfSize,
                item.centerZ + item.horizontalZ * item.halfSize + item.verticalZ * item.halfSize,
                centerX, centerY, scale, false
            )
            addProjectedPoint(
                path,
                item.centerX - item.horizontalX * item.halfSize + item.verticalX * item.halfSize,
                item.centerY - item.horizontalY * item.halfSize + item.verticalY * item.halfSize,
                item.centerZ - item.horizontalZ * item.halfSize + item.verticalZ * item.halfSize,
                centerX, centerY, scale, false
            )
            path.close()
            fillPaint.color = item.color
            canvas.drawPath(path, fillPaint)
            strokePaint.color = if (item.sticker) COLOR_STICKER_EDGE else COLOR_SHELL_EDGE
            strokePaint.strokeWidth = if (item.sticker) 0.9f else 0.55f
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun baseColor(quad: ModelQuad, facelets: String, focusModel: F2LFocus?): Int {
        val index = quad.stickerIndex ?: return COLOR_SHELL
        val value = facelets.getOrNull(index) ?: quad.stickerFace ?: 'U'
        val focus = focusModel
        return if (
            focus != null &&
            index !in focus.target &&
            index !in focus.solved &&
            index !in focus.crossSolved &&
            index !in focus.referenceCenters
        ) {
            COLOR_MUTED
        } else if (mutedSolved && value == quad.stickerFace) {
            COLOR_MUTED
        } else {
            stickerColorInt(value, cubeFrame)
        }
    }

    private fun transform(
        source: Vec3F,
        rotateLayer: Boolean,
        axis: Vec3F?,
        turnCos: Float,
        turnSin: Float,
        orientation: FloatArray?,
        out: FloatArray
    ) {
        var x = source.x
        var y = source.y
        var z = source.z
        if (rotateLayer && axis != null) {
            val dot = axis.x * x + axis.y * y + axis.z * z
            val crossX = axis.y * z - axis.z * y
            val crossY = axis.z * x - axis.x * z
            val crossZ = axis.x * y - axis.y * x
            val oneMinusCos = 1f - turnCos
            x = x * turnCos + crossX * turnSin + axis.x * dot * oneMinusCos
            y = y * turnCos + crossY * turnSin + axis.y * dot * oneMinusCos
            z = z * turnCos + crossZ * turnSin + axis.z * dot * oneMinusCos
        }
        if (orientation != null) {
            val qx = orientation[0]
            val qy = orientation[1]
            val qz = orientation[2]
            val qw = orientation[3]
            val tx = 2f * (qy * z - qz * y)
            val ty = 2f * (qz * x - qx * z)
            val tz = 2f * (qx * y - qy * x)
            val rotatedX = x + qw * tx + (qy * tz - qz * ty)
            val rotatedY = y + qw * ty + (qz * tx - qx * tz)
            val rotatedZ = z + qw * tz + (qx * ty - qy * tx)
            x = rotatedX
            y = rotatedY
            z = rotatedZ
        }
        out[0] = x
        out[1] = y
        out[2] = z
    }

    private fun compareItems(leftIndex: Int, rightIndex: Int): Int {
        val left = items[leftIndex]
        val right = items[rightIndex]
        if (left.depthKey != right.depthKey) return left.depthKey - right.depthKey
        if (left.sticker != right.sticker) return if (left.sticker) 1 else -1
        return left.stableOrder - right.stableOrder
    }

    private fun addProjectedPoint(
        path: AndroidPath,
        x: Float,
        y: Float,
        z: Float,
        centerX: Float,
        centerY: Float,
        scale: Float,
        first: Boolean
    ) {
        val depth = x * cameraDirection.x + y * cameraDirection.y + z * cameraDirection.z
        val perspective = (1f + depth * 0.025f).coerceIn(0.92f, 1.08f)
        projection[0] = centerX + (x * screenRight.x + y * screenRight.y + z * screenRight.z) * scale * perspective
        projection[1] = centerY - (x * screenUp.x + y * screenUp.y + z * screenUp.z) * scale * perspective
        if (first) path.moveTo(projection[0], projection[1]) else path.lineTo(projection[0], projection[1])
    }

    private fun shadedColor(base: Int, normalX: Float, normalY: Float, normalZ: Float): Int {
        val amount = (1.02f + (normalX * lightDirection.x + normalY * lightDirection.y + normalZ * lightDirection.z) * 0.08f)
            .coerceIn(0.96f, 1.08f)
        val red = (((base shr 16) and 0xFF) * amount).roundToInt().coerceIn(0, 255)
        val green = (((base shr 8) and 0xFF) * amount).roundToInt().coerceIn(0, 255)
        val blue = ((base and 0xFF) * amount).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun stickerColorInt(value: Char, frame: SmartCubeFrame): Int = when {
        frame == SmartCubeFrame.OFFICIAL_WHITE_GREEN && value == 'U' -> COLOR_WHITE
        frame == SmartCubeFrame.OFFICIAL_WHITE_GREEN && value == 'F' -> COLOR_GREEN
        frame == SmartCubeFrame.OFFICIAL_WHITE_GREEN && value == 'D' -> COLOR_YELLOW
        frame == SmartCubeFrame.OFFICIAL_WHITE_GREEN && value == 'B' -> COLOR_BLUE
        value == 'U' -> COLOR_YELLOW
        value == 'R' -> COLOR_RED
        value == 'F' -> COLOR_BLUE
        value == 'D' -> COLOR_WHITE
        value == 'L' -> COLOR_ORANGE
        value == 'B' -> COLOR_GREEN
        else -> COLOR_WHITE
    }

    private fun validFacelets(value: String?, fallback: String): String =
        if (value != null && value.length == 54 && value.all { it in "URFDLB" }) value else fallback

    private companion object {
        const val COLOR_YELLOW = 0xFFFFD500.toInt()
        const val COLOR_RED = 0xFFEF3340.toInt()
        const val COLOR_BLUE = 0xFF1675D1.toInt()
        const val COLOR_WHITE = 0xFFFFFEF8.toInt()
        const val COLOR_ORANGE = 0xFFFF8A00.toInt()
        const val COLOR_GREEN = 0xFF00B86B.toInt()
        const val COLOR_MUTED = 0xFF404040.toInt()
        const val COLOR_SHELL = 0xFF17232C.toInt()
        const val COLOR_SHADOW = 0x1F000000.toInt()
        const val COLOR_STICKER_EDGE = 0xBF14232D.toInt()
        const val COLOR_SHELL_EDGE = 0x5517232C.toInt()
        const val ORIENTATION_DEADBAND = 0.018
        const val MANUAL_ORBIT_RADIANS_PER_PIXEL = 0.007
    }
}

data class F2LFocus(
    val target: Set<Int>,
    val solved: Set<Int>,
    val crossSolved: Set<Int> = emptySet(),
    val referenceCenters: Set<Int> = emptySet(),
    val targetPieces: List<Set<Char>> = emptyList(),
    val solvedPieces: List<Set<Char>> = emptyList(),
    val crossPieces: List<Set<Char>> = emptyList()
)

private data class Vec3F(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3F) = Vec3F(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3F) = Vec3F(x - other.x, y - other.y, z - other.z)
    operator fun times(value: Float) = Vec3F(x * value, y * value, z * value)

    fun dot(other: Vec3F): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3F): Vec3F = Vec3F(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun normalized(): Vec3F {
        val length = sqrt(dot(this)).coerceAtLeast(0.0001f)
        return this * (1f / length)
    }

    fun rotated(axis: Vec3F, angle: Float): Vec3F {
        val unit = axis.normalized()
        val cosine = cos(angle)
        val sine = sin(angle)
        return this * cosine + unit.cross(this) * sine + unit * (unit.dot(this) * (1f - cosine))
    }
}

private data class FaceBasis(
    val face: Char,
    val normal: Vec3F,
    val horizontal: Vec3F,
    val vertical: Vec3F
)

// Coordinate frame: x = right, y = up, z = front.
// This is the yellow-top / blue-front convention requested by the user.
private val faceBases = listOf(
    FaceBasis('U', Vec3F(0f, 1f, 0f), Vec3F(1f, 0f, 0f), Vec3F(0f, 0f, 1f)),
    FaceBasis('R', Vec3F(1f, 0f, 0f), Vec3F(0f, 0f, -1f), Vec3F(0f, -1f, 0f)),
    FaceBasis('F', Vec3F(0f, 0f, 1f), Vec3F(1f, 0f, 0f), Vec3F(0f, -1f, 0f)),
    FaceBasis('D', Vec3F(0f, -1f, 0f), Vec3F(1f, 0f, 0f), Vec3F(0f, 0f, -1f)),
    FaceBasis('L', Vec3F(-1f, 0f, 0f), Vec3F(0f, 0f, 1f), Vec3F(0f, -1f, 0f)),
    FaceBasis('B', Vec3F(0f, 0f, -1f), Vec3F(-1f, 0f, 0f), Vec3F(0f, -1f, 0f))
)

private data class ModelQuad(
    val cubie: Vec3F,
    val center: Vec3F,
    val horizontal: Vec3F,
    val vertical: Vec3F,
    val normal: Vec3F,
    val halfSize: Float,
    val stickerIndex: Int? = null,
    val stickerFace: Char? = null
)

private data class RenderQuad(
    val center: Vec3F,
    val horizontal: Vec3F,
    val vertical: Vec3F,
    val normal: Vec3F,
    val halfSize: Float,
    val color: Color,
    val isSticker: Boolean,
    val stableOrder: Int
)

private fun Quaternion.rotate(vector: Vec3F): Vec3F {
    // Optimized q * v * q^-1 multiplication. The manager exposes a smoothed
    // orientation relative to the first sensor sample, so the cube starts in
    // the same neutral frame as the physical cube.
    val qx = x.toFloat()
    val qy = y.toFloat()
    val qz = z.toFloat()
    val qw = w.toFloat()
    val tx = 2f * (qy * vector.z - qz * vector.y)
    val ty = 2f * (qz * vector.x - qx * vector.z)
    val tz = 2f * (qx * vector.y - qy * vector.x)
    return Vec3F(
        vector.x + qw * tx + (qy * tz - qz * ty),
        vector.y + qw * ty + (qz * tx - qx * tz),
        vector.z + qw * tz + (qx * ty - qy * tx)
    )
}

private fun Quaternion.angularDistance(other: Quaternion): Double =
    2.0 * acos(abs(dot(other)).coerceIn(0.0, 1.0))

private fun RenderQuad.rotatedBy(orientation: Quaternion): RenderQuad = copy(
    center = orientation.rotate(center),
    horizontal = orientation.rotate(horizontal),
    vertical = orientation.rotate(vertical),
    normal = orientation.rotate(normal)
)

private val cubeQuads: List<ModelQuad> = buildCubeQuads()

private fun buildCubeQuads(): List<ModelQuad> {
    val quads = ArrayList<ModelQuad>(81)
    val shell = Color(0xFF17232C)

    // Real outer faces for each of the 27 cubies. These create the dark seams
    // and let a turning layer expose its side faces during the animation.
    for (x in -1..1) {
        for (y in -1..1) {
            for (z in -1..1) {
                val cubie = Vec3F(x.toFloat(), y.toFloat(), z.toFloat())
                faceBases.forEach { basis ->
                    if (cubie.dot(basis.normal).roundToInt() == 1) {
                        quads += ModelQuad(
                            cubie = cubie,
                            center = cubie + basis.normal * 0.5f,
                            horizontal = basis.horizontal,
                            vertical = basis.vertical,
                            normal = basis.normal,
                            // Leave a fixed shell margin under the sticker.
                            // The old coplanar edge overlap could change order
                            // while a layer was rotating and look like color
                            // flicker on fast displays.
                            halfSize = 0.49f
                        )
                    }
                }
            }
        }
    }

    // Sticker positions use the same URFDLB geometry as CubeEngine. The
    // larger, fixed lift keeps the sticker pass unambiguously in front of its
    // shell even during a wide-layer turn.
    faceBases.forEachIndexed { faceIndex, basis ->
        repeat(3) { row ->
            repeat(3) { col ->
                val cubie = logicalCubie(basis.face, row, col)
                quads += ModelQuad(
                    cubie = cubie,
                    center = cubie + basis.normal * 0.56f,
                    horizontal = basis.horizontal,
                    vertical = basis.vertical,
                    normal = basis.normal,
                    halfSize = 0.43f,
                    stickerIndex = faceIndex * 9 + row * 3 + col,
                    stickerFace = basis.face
                )
            }
        }
    }
    return quads
}

private fun logicalCubie(face: Char, row: Int, col: Int): Vec3F = when (face) {
    'U' -> Vec3F(col - 1f, 1f, row - 1f)
    'R' -> Vec3F(1f, 1f - row, 1f - col)
    'F' -> Vec3F(col - 1f, 1f - row, 1f)
    'D' -> Vec3F(col - 1f, -1f, 1f - row)
    'L' -> Vec3F(-1f, 1f - row, col - 1f)
    else -> Vec3F(1f - col, 1f - row, -1f)
}

private data class F2LSlot(
    val cornerColors: Set<Char>,
    val edgeColors: Set<Char>,
    val cornerPosition: Vec3F,
    val edgePosition: Vec3F
)

private val f2lSlots = listOf(
    F2LSlot(setOf('D', 'F', 'R'), setOf('F', 'R'), Vec3F(1f, -1f, 1f), Vec3F(1f, 0f, 1f)),
    F2LSlot(setOf('D', 'L', 'F'), setOf('L', 'F'), Vec3F(-1f, -1f, 1f), Vec3F(-1f, 0f, 1f)),
    F2LSlot(setOf('D', 'B', 'L'), setOf('B', 'L'), Vec3F(-1f, -1f, -1f), Vec3F(-1f, 0f, -1f)),
    F2LSlot(setOf('D', 'R', 'B'), setOf('R', 'B'), Vec3F(1f, -1f, -1f), Vec3F(1f, 0f, -1f))
)

private data class CrossSlot(
    val edgeColors: Set<Char>,
    val edgePosition: Vec3F
)

private val crossSlots = listOf(
    CrossSlot(setOf('D', 'F'), Vec3F(0f, -1f, 1f)),
    CrossSlot(setOf('D', 'R'), Vec3F(1f, -1f, 0f)),
    CrossSlot(setOf('D', 'B'), Vec3F(0f, -1f, -1f)),
    CrossSlot(setOf('D', 'L'), Vec3F(-1f, -1f, 0f))
)

// Keep every fixed-center sticker visible in a focused F2L playback. A wide
// move such as r temporarily carries the U/F/R center cubies through the
// middle slice; masking by only the original R/F indexes made those colors
// turn charcoal while the layer was rotating.
private val f2lReferenceCenterIndexes = setOf(4, 13, 22, 31, 40, 49)

private val stickerIndexesByCubie: Map<Vec3F, List<Int>> = cubeQuads
    .filter { it.stickerIndex != null }
    .groupBy { it.cubie }
    .mapValues { (_, quads) -> quads.mapNotNull { it.stickerIndex }.sorted() }

private fun pieceColors(facelets: String, indexes: List<Int>): Set<Char> =
    indexes.mapNotNull(facelets::getOrNull).toSet()

private fun isSolvedPiece(facelets: String, indexes: List<Int>): Boolean =
    indexes.all { index ->
        val face = cubeQuads.firstOrNull { it.stickerIndex == index }?.stickerFace
        face != null && facelets.getOrNull(index) == face
    }

private fun pieceIndexes(position: Vec3F): List<Int> = stickerIndexesByCubie[position].orEmpty()

private fun locatePiece(facelets: String, colors: Set<Char>): List<Int> =
    stickerIndexesByCubie.values.firstOrNull { indexes ->
        indexes.size == colors.size && pieceColors(facelets, indexes) == colors
    }.orEmpty()

private fun F2LFocus.atState(facelets: String): F2LFocus = copy(
    target = targetPieces.flatMap { locatePiece(facelets, it) }.toSet(),
    solved = solvedPieces.flatMap { locatePiece(facelets, it) }.toSet(),
    crossSolved = crossPieces.flatMap { locatePiece(facelets, it) }.toSet()
)

/**
 * Finds the one F2L slot that is not solved, then locates that slot's actual
 * corner and edge wherever they are on the cube. The preview can therefore
 * show the pair in U/D/middle positions without painting unrelated stickers.
 */
fun f2lFocus(facelets: String): F2LFocus {
    // PresetCatalog normalizes every canonical state back to the fixed U/R/F
    // center frame. Do not rotate a second, heuristic copy of the state here:
    // doing so made y-prefixed cases (notably 12, 18 and 22) select a target
    // slot in one frame and paint its stickers in another.
    val alignedFacelets = facelets

    // CubeRoot's F2L setup diagrams are all authored for the FR reference
    // pair (D/F/R corner plus F/R edge). Keeping this slot fixed is important:
    // choosing the most mismatched slot makes cases such as F2L08 highlight a
    // different pair when another slot happens to contain more stickers that
    // differ from the solved cube.
    val targetSlot = f2lSlots.first()

    val targetPieces = listOf(targetSlot.cornerColors, targetSlot.edgeColors)
    val targetCorner = locatePiece(facelets, targetSlot.cornerColors).ifEmpty { pieceIndexes(targetSlot.cornerPosition) }
    val targetEdge = locatePiece(facelets, targetSlot.edgeColors).ifEmpty { pieceIndexes(targetSlot.edgePosition) }

    val solvedPieces = f2lSlots
        .filterNot { it == targetSlot }
        .flatMap { slot ->
            val corner = pieceIndexes(slot.cornerPosition)
            val edge = pieceIndexes(slot.edgePosition)
            if (isSolvedPiece(alignedFacelets, corner) && isSolvedPiece(alignedFacelets, edge)) {
                listOf(slot.cornerColors, slot.edgeColors)
            } else emptyList()
        }
    // F2L diagrams hide unrelated stickers, but a completed cross is part of
    // the learner's reference state. Keep the four D-edge pieces visible so
    // the F/R bottom-edge stickers are not mistaken for unknown gray pieces.
    // Use the original orientation here: the app's convention is always
    // yellow top / blue front, even when pair detection checks a y-aligned
    // candidate to find the active slot.
    val crossPieces = crossSlots
        .filter { slot -> isSolvedPiece(facelets, pieceIndexes(slot.edgePosition)) }
        .map { it.edgeColors }
    val initialFocus = F2LFocus(
        target = (targetCorner + targetEdge).toSet(),
        solved = solvedPieces.flatMap { locatePiece(facelets, it) }.toSet(),
        crossSolved = crossPieces.flatMap { locatePiece(facelets, it) }.toSet(),
        // The six center positions stay visible so a double-layer animation
        // cannot make a moving U/F/R center appear to lose its color. Only the
        // U/R/F ones are normally visible from this camera; keeping all six
        // positions also covers the brief intermediate state of r/f/l moves.
        referenceCenters = f2lReferenceCenterIndexes,
        targetPieces = targetPieces,
        solvedPieces = solvedPieces,
        crossPieces = crossPieces
    )

    return initialFocus
}

private data class ActiveTurn(
    val axis: Vec3F,
    val layers: Set<Int>,
    val direction: Float,
    val signedTurns: Int
) {
    fun affects(cubie: Vec3F): Boolean = layers.contains(cubie.dot(axis).roundToInt())
}

private fun resolveActiveTurn(notation: String?): ActiveTurn? {
    val move = notation?.let { normalizedMoves(it).firstOrNull() } ?: return null
    val symbol = move.symbol.firstOrNull()?.uppercaseChar() ?: return null
    val spec = when (symbol) {
        'U' -> ActiveTurn(Vec3F(0f, 1f, 0f), setOf(1), -1f, 0)
        'D' -> ActiveTurn(Vec3F(0f, -1f, 0f), setOf(1), -1f, 0)
        'R' -> ActiveTurn(Vec3F(1f, 0f, 0f), setOf(1), -1f, 0)
        'L' -> ActiveTurn(Vec3F(-1f, 0f, 0f), setOf(1), -1f, 0)
        'F' -> ActiveTurn(Vec3F(0f, 0f, 1f), setOf(1), -1f, 0)
        'B' -> ActiveTurn(Vec3F(0f, 0f, -1f), setOf(1), -1f, 0)
        'M' -> ActiveTurn(Vec3F(1f, 0f, 0f), setOf(0), 1f, 0)
        'E' -> ActiveTurn(Vec3F(0f, 1f, 0f), setOf(0), 1f, 0)
        'S' -> ActiveTurn(Vec3F(0f, 0f, 1f), setOf(0), -1f, 0)
        'X' -> ActiveTurn(Vec3F(1f, 0f, 0f), setOf(-1, 0, 1), -1f, 0)
        'Y' -> ActiveTurn(Vec3F(0f, 1f, 0f), setOf(-1, 0, 1), -1f, 0)
        'Z' -> ActiveTurn(Vec3F(0f, 0f, 1f), setOf(-1, 0, 1), -1f, 0)
        else -> return null
    }
    val layers = if (move.wide && symbol in "URFDLB") spec.layers + 0 else spec.layers
    val signedTurns = when (move.turns.mod(4)) {
        1 -> 1
        2 -> 2
        3 -> -1
        else -> 0
    }
    return if (signedTurns == 0) null else spec.copy(layers = layers, signedTurns = signedTurns)
}

private val cameraDirection = Vec3F(5f, 4.2f, 7f).normalized()
private val screenRight = Vec3F(1f, 0f, -1f).normalized()
private val screenUp = Vec3F(-0.32f, 0.86f, -0.32f).normalized()
// Keep the renderer's palette aligned with the bright diagram colors. The
// previous renderer multiplied every face by a fairly aggressive dark shade,
// which made white lower-layer stickers look gray on high-refresh phones.
private val faceYellow = Color(0xFFFFD500)
private val faceRed = Color(0xFFEF3340)
private val faceBlue = Color(0xFF1675D1)
private val faceWhite = Color(0xFFFFFEF8)
private val faceOrange = Color(0xFFFF8A00)
private val faceGreen = Color(0xFF00B86B)
// Match the neutral stickers used by the F2L diagrams. Unrelated cubies are
// intentionally charcoal, not white, so the learner's active pair and cross
// remain visually dominant in both the diagram and 3D preview.
private val mutedSticker = Color(0xFF404040)
private val seamColor = Color(0xFF14232D)
private val lightDirection = Vec3F(-2f, 5f, 7f).normalized()

private fun stickerColor(value: Char): Color = when (value) {
    'U' -> faceYellow
    'R' -> faceRed
    'F' -> faceBlue
    'D' -> faceWhite
    'L' -> faceOrange
    'B' -> faceGreen
    else -> faceWhite
}

private fun ModelQuad.render(turn: ActiveTurn?, progress: Float, color: Color, stableOrder: Int): RenderQuad {
    if (turn == null || progress <= 0f || !turn.affects(cubie)) {
        return RenderQuad(center, horizontal, vertical, normal, halfSize, shade(color, normal), stickerIndex != null, stableOrder)
    }
    val angle = turn.direction * turn.signedTurns * (PI.toFloat() / 2f) * progress
    val rotatedNormal = normal.rotated(turn.axis, angle)
    return RenderQuad(
        center = center.rotated(turn.axis, angle),
        horizontal = horizontal.rotated(turn.axis, angle),
        vertical = vertical.rotated(turn.axis, angle),
        normal = rotatedNormal,
        halfSize = halfSize,
        color = shade(color, rotatedNormal),
        isSticker = stickerIndex != null,
        stableOrder = stableOrder
    )
}

private fun shade(color: Color, normal: Vec3F): Color {
    // A narrow, bright range preserves saturation on every visible face while
    // retaining enough directional variation to read as a 3D model.
    val amount = (1.02f + normal.dot(lightDirection) * 0.08f).coerceIn(0.96f, 1.08f)
    return Color(
        red = (color.red * amount).coerceIn(0f, 1f),
        green = (color.green * amount).coerceIn(0f, 1f),
        blue = (color.blue * amount).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}

private fun DrawScope.drawCube(
    facelets: String,
    activeTurn: ActiveTurn?,
    progress: Float,
    mutedSolved: Boolean,
    f2lFocus: F2LFocus?,
    sceneOrientation: Quaternion?,
    modelScale: Float
) {
    val scale = (min(size.width / 5.8f, size.height / 5.2f) * modelScale).coerceAtLeast(10f)
    val center = Offset(size.width / 2f, size.height / 2f - scale * 0.02f)

    // A soft grounding shadow gives the model a physical base instead of a
    // floating collection of colored squares.
    drawOval(
        color = Color.Black.copy(alpha = 0.12f),
        topLeft = Offset(center.x - scale * 1.65f, center.y + scale * 1.05f),
        size = Size(scale * 3.3f, scale * 0.48f)
    )

    val renderQuads = cubeQuads.mapIndexed { stableOrder, quad ->
        val color = if (quad.stickerIndex == null) {
            seamColor
        } else {
            val index = quad.stickerIndex
            val value = facelets.getOrNull(index) ?: quad.stickerFace ?: 'U'
            val base = when {
                f2lFocus != null && index !in f2lFocus.target && index !in f2lFocus.solved && index !in f2lFocus.crossSolved && index !in f2lFocus.referenceCenters -> mutedSticker
                mutedSolved && value == quad.stickerFace -> mutedSticker
                else -> stickerColor(value)
            }
            base
        }
        quad.render(activeTurn, progress, color, stableOrder).let { rendered ->
            sceneOrientation?.let(rendered::rotatedBy) ?: rendered
        }
    }.filter { it.normal.dot(cameraDirection) > 0.025f }
        // Painter's order is used instead of a GPU depth buffer. Quantizing
        // the depth key makes nearly coplanar shell/sticker faces deterministic
        // across consecutive frames; sticker faces are then always later than
        // their shell at the same depth.
        .sortedWith(
            compareBy<RenderQuad> { (it.center.dot(cameraDirection) * 256f).roundToInt() }
                .thenBy { if (it.isSticker) 1 else 0 }
                .thenBy { it.stableOrder }
        )

    renderQuads.forEach { quad ->
        val points = quad.corners().map { project(it, center, scale) }
        drawPolygon(points, quad.color)
        if (quad.isSticker) {
            drawPolygon(points, Color(0x8C14232D), strokeWidth = 0.9f)
        } else {
            drawPolygon(points, Color(0x3317232C), strokeWidth = 0.55f)
        }
    }
}

private fun RenderQuad.corners(): List<Vec3F> = listOf(
    center - horizontal * halfSize - vertical * halfSize,
    center + horizontal * halfSize - vertical * halfSize,
    center + horizontal * halfSize + vertical * halfSize,
    center - horizontal * halfSize + vertical * halfSize
)

private fun project(point: Vec3F, center: Offset, scale: Float): Offset {
    val depth = point.dot(cameraDirection)
    val perspective = (1f + depth * 0.025f).coerceIn(0.92f, 1.08f)
    return center + Offset(
        point.dot(screenRight) * scale * perspective,
        -point.dot(screenUp) * scale * perspective
    )
}

private fun DrawScope.drawPolygon(
    points: List<Offset>,
    fill: Color,
    strokeWidth: Float = 0f
) {
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    if (strokeWidth <= 0f) {
        drawPath(path, fill)
    } else {
        // Outline passes must not paint a second translucent fill over the
        // sticker. That accidental overlay was making the otherwise bright
        // palette look muddy, especially on blue/red F2L pieces.
        drawPath(path, seamColor.copy(alpha = 0.75f), style = Stroke(width = strokeWidth))
    }
}
