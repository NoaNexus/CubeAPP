// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

import com.cubetrace.app.core.device.Quaternion
import com.cubetrace.app.core.model.CubeRotationAxis
import com.cubetrace.app.core.model.CubeRotationEvent
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Recognises deliberate whole-cube rotations from gyro orientation.
 *
 * Samples are expressed relative to the pose at timer start, projected onto
 * the 24 orthogonal cube poses, and accepted only after a short stable hold.
 * Face turns therefore cannot create a rotation event and hand jitter near a
 * pose boundary is rejected by angular hysteresis.
 */
class CubeRotationDetector(
    private val stableHoldMs: Long = 160L,
    enterToleranceRadians: Double = Math.toRadians(18.0),
    exitToleranceRadians: Double = Math.toRadians(28.0)
) {
    private val enterTolerance = enterToleranceRadians.coerceAtLeast(0.01)
    private val exitTolerance = exitToleranceRadians.coerceAtLeast(enterTolerance)
    private var reference: Quaternion? = null
    private var acceptedIndex = IDENTITY_INDEX
    private var candidateIndex: Int? = null
    private var candidateSinceMs = 0L
    private var candidateBestError = Double.POSITIVE_INFINITY

    var tracked: Boolean = false
        private set

    fun reset(initialOrientation: Quaternion? = null) {
        reference = initialOrientation
        acceptedIndex = IDENTITY_INDEX
        candidateIndex = null
        candidateSinceMs = 0L
        candidateBestError = Double.POSITIVE_INFINITY
        tracked = initialOrientation != null
    }

    fun accept(orientation: Quaternion, elapsedMs: Long): List<CubeRotationEvent> {
        tracked = true
        val baseline = reference ?: run {
            reference = orientation
            return emptyList()
        }
        val relative = orientation.relativeTo(baseline)
        val nearest = nearestPose(relative)
        val tolerance = if (candidateIndex == nearest.index) exitTolerance else enterTolerance
        if (nearest.errorRadians > tolerance) {
            candidateIndex = null
            candidateBestError = Double.POSITIVE_INFINITY
            return emptyList()
        }
        if (nearest.index == acceptedIndex) {
            candidateIndex = null
            candidateBestError = Double.POSITIVE_INFINITY
            return emptyList()
        }
        if (candidateIndex != nearest.index) {
            candidateIndex = nearest.index
            candidateSinceMs = elapsedMs
            candidateBestError = nearest.errorRadians
            return emptyList()
        }
        candidateBestError = minOf(candidateBestError, nearest.errorRadians)
        if (elapsedMs - candidateSinceMs < stableHoldMs) return emptyList()

        val steps = shortestPath(acceptedIndex, nearest.index)
        val confidence = (1.0 - candidateBestError / enterTolerance).coerceIn(0.35, 1.0)
        val startedAt = candidateSinceMs.coerceAtLeast(0L)
        val duration = (elapsedMs - startedAt).coerceAtLeast(1L)
        val events = steps.mapIndexed { index, step ->
            val eventStart = startedAt + duration * index / steps.size.coerceAtLeast(1)
            val eventEnd = startedAt + duration * (index + 1) / steps.size.coerceAtLeast(1)
            CubeRotationEvent(
                ordinal = 0,
                axis = step.axis,
                amount = step.amount,
                startedAtMs = eventStart,
                endedAtMs = eventEnd,
                confidence = confidence
            )
        }
        acceptedIndex = nearest.index
        candidateIndex = null
        candidateBestError = Double.POSITIVE_INFINITY
        return events
    }

    private data class NearestPose(val index: Int, val errorRadians: Double)

    private fun nearestPose(value: Quaternion): NearestPose {
        var bestIndex = 0
        var bestDot = -1.0
        POSES.forEachIndexed { index, pose ->
            val dot = abs(value.dot(pose))
            if (dot > bestDot) {
                bestDot = dot
                bestIndex = index
            }
        }
        return NearestPose(bestIndex, 2.0 * acos(bestDot.coerceIn(-1.0, 1.0)))
    }

    private data class RotationStep(val axis: CubeRotationAxis, val amount: Int)
    private data class PathNode(val pose: Int, val path: List<RotationStep>)

    private fun shortestPath(from: Int, to: Int): List<RotationStep> {
        if (from == to) return emptyList()
        val seen = BooleanArray(POSES.size)
        val queue = ArrayDeque<PathNode>()
        seen[from] = true
        queue.add(PathNode(from, emptyList()))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            STEPS.forEachIndexed { stepIndex, step ->
                val next = TRANSITIONS[current.pose][stepIndex]
                if (seen[next]) return@forEachIndexed
                val path = current.path + step
                if (next == to) return path
                seen[next] = true
                queue.add(PathNode(next, path))
            }
        }
        return emptyList()
    }

    companion object {
        private val STEPS = listOf(
            RotationStep(CubeRotationAxis.X, 1),
            RotationStep(CubeRotationAxis.X, -1),
            RotationStep(CubeRotationAxis.X, 2),
            RotationStep(CubeRotationAxis.Y, 1),
            RotationStep(CubeRotationAxis.Y, -1),
            RotationStep(CubeRotationAxis.Y, 2),
            RotationStep(CubeRotationAxis.Z, 1),
            RotationStep(CubeRotationAxis.Z, -1),
            RotationStep(CubeRotationAxis.Z, 2)
        )

        private fun rotation(step: RotationStep): Quaternion {
            val radians = step.amount * PI / 2.0
            val sine = sin(radians / 2.0)
            val cosine = cos(radians / 2.0)
            return when (step.axis) {
                CubeRotationAxis.X -> Quaternion.normalized(sine, 0.0, 0.0, cosine)
                CubeRotationAxis.Y -> Quaternion.normalized(0.0, sine, 0.0, cosine)
                CubeRotationAxis.Z -> Quaternion.normalized(0.0, 0.0, sine, cosine)
            }
        }

        private val POSES: List<Quaternion> = buildList {
            add(Quaternion.identity())
            var cursor = 0
            val quarterTurns = STEPS.filter { abs(it.amount) == 1 }
            while (cursor < size) {
                val current = this[cursor++]
                quarterTurns.forEach { step ->
                    val candidate = current.multiplied(rotation(step))
                    if (none { abs(it.dot(candidate)) > 0.999999 }) add(candidate)
                }
            }
            check(size == 24) { "cube orientation group must contain 24 poses" }
        }
        private const val IDENTITY_INDEX = 0
        private val TRANSITIONS: Array<IntArray> = Array(POSES.size) { poseIndex ->
            IntArray(STEPS.size) { stepIndex ->
                val target = POSES[poseIndex].multiplied(rotation(STEPS[stepIndex]))
                POSES.indices.maxBy { abs(POSES[it].dot(target)) }
            }
        }
    }
}
