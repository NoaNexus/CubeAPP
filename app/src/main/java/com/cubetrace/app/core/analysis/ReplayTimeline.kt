// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

import com.cubetrace.app.core.model.MoveTimeQuality
import com.cubetrace.app.core.model.RecordedMove

/** Problems that prevent the supplied timestamps from being treated as an exact timeline. */
enum class ReplayTimelineIssue {
    NEGATIVE_TOTAL_DURATION,
    NEGATIVE_MOVE_TIME,
    NON_MONOTONIC_MOVE_TIME,
    MOVE_AFTER_TOTAL_DURATION
}

/** One move's recorded and playback completion time. Completion times are nondecreasing. */
data class ReplayTimelineStep(
    val index: Int,
    val ordinal: Int,
    val recordedElapsedMs: Long,
    val completionMs: Long,
    val timestampQuality: MoveTimeQuality,
    val timestampReliable: Boolean
)

/** A value snapshot suitable for querying from a frame-clock driven UI. */
data class ReplayTimelineSnapshot(
    val positionMs: Long,
    /** Number of moves completed at [positionMs], in the range 0..moveCount. */
    val completedStepIndex: Int,
    /** A single move to animate, if its completion has a distinct timed interval. */
    val rotatingMoveIndex: Int?,
    val animationStartMs: Long?,
    val animationEndMs: Long?,
    /** True only when all moves have reliable source timestamps and no timeline issue exists. */
    val timingReliable: Boolean,
    /** True when every move has DEVICE/RECEIVE quality and was not reconstructed. */
    val moveTimestampQualityReliable: Boolean,
    val hasTimingAnomaly: Boolean
)

/**
 * Immutable replay timing derived from each move's solve-relative [RecordedMove.elapsedMs].
 * The caller supplies the solve duration, so leading and trailing pauses remain part of playback.
 * Query this class at an absolute playback position; it owns no clock or rendering timer.
 */
class ReplayTimeline private constructor(
    val durationMs: Long,
    val steps: List<ReplayTimelineStep>,
    val issues: Set<ReplayTimelineIssue>
) {
    val moveTimestampQualityReliable: Boolean =
        steps.isNotEmpty() && steps.all { it.timestampReliable }

    val timingReliable: Boolean =
        moveTimestampQualityReliable && issues.isEmpty()

    /**
     * Finds the replay state at an absolute position. Move completions at the same time are
     * counted together. Animation is limited to the final 100 ms before a distinct completion
     * and starts no earlier than the previous move's completion.
     */
    fun snapshotAt(positionMs: Long, animationWindowMs: Long = MAX_ANIMATION_WINDOW_MS): ReplayTimelineSnapshot {
        val position = positionMs.coerceIn(0L, durationMs)
        val completed = upperBound(position)
        val next = steps.getOrNull(completed)
        val nextAfter = steps.getOrNull(completed + 1)
        val window = animationWindowMs.coerceIn(0L, MAX_ANIMATION_WINDOW_MS)

        var rotatingIndex: Int? = null
        var animationStart: Long? = null
        var animationEnd: Long? = null
        if (next != null && window > 0L && nextAfter?.completionMs != next.completionMs) {
            val previousCompletion = if (completed == 0) 0L else steps[completed - 1].completionMs
            val windowStart = next.completionMs - minOf(window, next.completionMs)
            val start = maxOf(previousCompletion, windowStart)
            if (start < next.completionMs && position >= start && position < next.completionMs) {
                rotatingIndex = next.index
                animationStart = start
                animationEnd = next.completionMs
            }
        }

        return ReplayTimelineSnapshot(
            positionMs = position,
            completedStepIndex = completed,
            rotatingMoveIndex = rotatingIndex,
            animationStartMs = animationStart,
            animationEndMs = animationEnd,
            timingReliable = timingReliable,
            moveTimestampQualityReliable = moveTimestampQualityReliable,
            hasTimingAnomaly = issues.isNotEmpty()
        )
    }

    private fun upperBound(positionMs: Long): Int {
        var low = 0
        var high = steps.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (steps[middle].completionMs <= positionMs) low = middle + 1 else high = middle
        }
        return low
    }

    companion object {
        const val MAX_ANIMATION_WINDOW_MS = 100L

        /**
         * Creates a timeline without changing the input moves. Negative timestamps are projected
         * to zero and backward timestamps to the prior effective completion time so completed
         * steps can never move backward; [issues] records every such correction.
         *
         * A move is considered timestamp-reliable only when its source is DEVICE or RECEIVE and
         * its ordinal is absent from [recoveredOrdinals]. Estimated/unknown timing is retained as
         * recorded and marked as lower confidence; it is never presented as reliable timing.
         */
        fun create(
            moves: List<RecordedMove>,
            totalDurationMs: Long,
            recoveredOrdinals: Set<Int> = emptySet()
        ): ReplayTimeline {
            val issues = linkedSetOf<ReplayTimelineIssue>()
            if (totalDurationMs < 0L) issues += ReplayTimelineIssue.NEGATIVE_TOTAL_DURATION
            val duration = totalDurationMs.coerceAtLeast(0L)
            var previousCompletion = 0L
            val steps = moves.mapIndexed { index, move ->
                val recordedTime = move.elapsedMs
                if (recordedTime < 0L) issues += ReplayTimelineIssue.NEGATIVE_MOVE_TIME
                if (recordedTime < previousCompletion) issues += ReplayTimelineIssue.NON_MONOTONIC_MOVE_TIME

                val completion = maxOf(previousCompletion, recordedTime.coerceAtLeast(0L))
                if (completion > duration) issues += ReplayTimelineIssue.MOVE_AFTER_TOTAL_DURATION
                previousCompletion = completion

                ReplayTimelineStep(
                    index = index,
                    ordinal = move.ordinal,
                    recordedElapsedMs = recordedTime,
                    completionMs = completion,
                    timestampQuality = move.timeQuality,
                    timestampReliable = move.ordinal !in recoveredOrdinals &&
                        move.timeQuality in setOf(MoveTimeQuality.DEVICE, MoveTimeQuality.RECEIVE)
                )
            }
            return ReplayTimeline(duration, steps, issues)
        }
    }
}
