// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

/** One monotonic clock shared by the native render loop and the slower controls. */
data class ReplayClock(
    val positionMs: Double = 0.0,
    val anchorNanos: Long = 0L,
    val speed: Float = 1f,
    val running: Boolean = false
) {
    fun positionAt(nowNanos: Long, durationMs: Long): Double =
        (positionMs + if (running) (nowNanos - anchorNanos).coerceAtLeast(0L) / 1_000_000.0 * speed else 0.0)
            .coerceIn(0.0, durationMs.toDouble())
}

data class ReplayVisualFrame(val completed: Int, val activeIndex: Int?, val progress: Float)

/** Immutable commands; no Compose state is read or written in the View's draw loop. */
class ReplayPlayback(
    val timeline: ReplayTimeline,
    val states: List<String>,
    val moveCodes: List<String>,
    val clock: ReplayClock,
    val realTiming: Boolean,
    val reducedMotion: Boolean,
    val manualStep: Int?
) {
    fun frameAt(positionMs: Double): ReplayVisualFrame {
        val sample = timeline.snapshotAt(positionMs.toLong(), if (reducedMotion) 0 else 100)
        val completed = manualStep ?: sample.completedStepIndex
        val active = when {
            manualStep != null || reducedMotion -> null
            !realTiming -> completed.takeIf { it < moveCodes.size }
            else -> sample.rotatingMoveIndex
        }
        val progress = if (active != null) {
            val start = if (realTiming) sample.animationStartMs!! else timeline.steps.getOrNull(active - 1)?.completionMs ?: 0L
            val end = timeline.steps[active].completionMs
            val linear = ((positionMs - start) / (end - start).coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
            linear * linear * (3f - 2f * linear)
        } else 1f
        return ReplayVisualFrame(completed, active, progress)
    }
}
