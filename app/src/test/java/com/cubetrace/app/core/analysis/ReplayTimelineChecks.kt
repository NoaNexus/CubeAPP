// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

import com.cubetrace.app.core.model.MoveTimeQuality
import com.cubetrace.app.core.model.RecordedMove

/** Dependency-free checks for replay position, pause, animation, and timestamp quality rules. */
object ReplayTimelineChecks {
    @JvmStatic
    fun main(args: Array<String>) {
        leadingAndTrailingPausesArePreserved()
        sameTimeMovesCompleteTogether()
        zeroTimeMoveCompletesAtStart()
        animationNeverCrossesPreviousCompletion()
        animationWindowIsCapped()
        timestampQualityIncludesRecoveredOrdinals()
        invalidTimesAreExplicitAndCompletionsNeverGoBackward()
        movesPastDurationRemainVisibleAsAnAnomaly()
        negativeDurationIsExplicit()
        println("ReplayTimelineChecks: 9 passed")
    }

    private fun leadingAndTrailingPausesArePreserved() {
        val timeline = ReplayTimeline.create(
            listOf(move(0, 500L), move(1, 1_200L)),
            totalDurationMs = 1_500L
        )

        val start = timeline.snapshotAt(0L)
        check(start.completedStepIndex == 0)
        check(start.rotatingMoveIndex == null)
        check(timeline.snapshotAt(399L).rotatingMoveIndex == null)

        val firstTurn = timeline.snapshotAt(450L)
        check(firstTurn.rotatingMoveIndex == 0)
        check(firstTurn.animationStartMs == 400L)
        check(firstTurn.animationEndMs == 500L)
        check(timeline.snapshotAt(500L).completedStepIndex == 1)
        check(timeline.snapshotAt(1_100L).rotatingMoveIndex == 1)

        val tail = timeline.snapshotAt(1_400L)
        check(tail.completedStepIndex == 2)
        check(tail.rotatingMoveIndex == null)
        check(tail.positionMs == 1_400L)
    }

    private fun sameTimeMovesCompleteTogether() {
        val timeline = ReplayTimeline.create(
            listOf(move(0, 1_000L), move(1, 1_000L)),
            totalDurationMs = 1_500L
        )

        val before = timeline.snapshotAt(999L)
        check(before.completedStepIndex == 0)
        // Identical timestamps do not invent an ordering or a separate animation interval.
        check(before.rotatingMoveIndex == null)
        check(timeline.snapshotAt(1_000L).completedStepIndex == 2)
    }

    private fun zeroTimeMoveCompletesAtStart() {
        val timeline = ReplayTimeline.create(listOf(move(0, 0L)), totalDurationMs = 300L)
        val atStart = timeline.snapshotAt(0L)
        check(atStart.completedStepIndex == 1)
        check(atStart.rotatingMoveIndex == null)
        check(timeline.snapshotAt(-10L).positionMs == 0L)
    }

    private fun animationNeverCrossesPreviousCompletion() {
        val timeline = ReplayTimeline.create(
            listOf(move(0, 1_000L), move(1, 1_020L)),
            totalDurationMs = 1_100L
        )
        val nextMove = timeline.snapshotAt(1_005L)
        check(nextMove.completedStepIndex == 1)
        check(nextMove.rotatingMoveIndex == 1)
        check(nextMove.animationStartMs == 1_000L)
        check(nextMove.animationEndMs == 1_020L)
    }

    private fun animationWindowIsCapped() {
        val timeline = ReplayTimeline.create(listOf(move(0, 500L)), totalDurationMs = 700L)
        val snapshot = timeline.snapshotAt(450L, animationWindowMs = 10_000L)
        check(snapshot.rotatingMoveIndex == 0)
        check(snapshot.animationStartMs == 400L)
        check(snapshot.animationEndMs == 500L)
    }

    private fun timestampQualityIncludesRecoveredOrdinals() {
        val timeline = ReplayTimeline.create(
            listOf(
                move(7, 100L, MoveTimeQuality.DEVICE),
                move(8, 200L, MoveTimeQuality.RECEIVE),
                move(9, 300L, MoveTimeQuality.ESTIMATED),
                move(10, 400L, MoveTimeQuality.DEVICE)
            ),
            totalDurationMs = 500L,
            recoveredOrdinals = setOf(10)
        )

        check(timeline.steps[0].timestampReliable)
        check(timeline.steps[1].timestampReliable)
        check(!timeline.steps[2].timestampReliable)
        check(!timeline.steps[3].timestampReliable)
        check(!timeline.moveTimestampQualityReliable)
        check(!timeline.snapshotAt(250L).timingReliable)
    }

    private fun invalidTimesAreExplicitAndCompletionsNeverGoBackward() {
        val timeline = ReplayTimeline.create(
            listOf(move(0, 300L), move(1, 100L), move(2, -20L)),
            totalDurationMs = 500L
        )

        check(timeline.steps.map { it.completionMs } == listOf(300L, 300L, 300L))
        check(ReplayTimelineIssue.NON_MONOTONIC_MOVE_TIME in timeline.issues)
        check(ReplayTimelineIssue.NEGATIVE_MOVE_TIME in timeline.issues)
        check(!timeline.timingReliable)
        check(timeline.snapshotAt(299L).completedStepIndex == 0)
        check(timeline.snapshotAt(300L).completedStepIndex == 3)
        check(timeline.snapshotAt(300L).hasTimingAnomaly)
    }

    private fun movesPastDurationRemainVisibleAsAnAnomaly() {
        val timeline = ReplayTimeline.create(listOf(move(0, 600L)), totalDurationMs = 500L)
        check(ReplayTimelineIssue.MOVE_AFTER_TOTAL_DURATION in timeline.issues)
        check(timeline.steps.single().completionMs == 600L)
        check(timeline.snapshotAt(500L).completedStepIndex == 0)
        check(!timeline.snapshotAt(500L).timingReliable)
    }

    private fun negativeDurationIsExplicit() {
        val timeline = ReplayTimeline.create(emptyList(), totalDurationMs = -1L)
        check(timeline.durationMs == 0L)
        check(ReplayTimelineIssue.NEGATIVE_TOTAL_DURATION in timeline.issues)
        check(timeline.snapshotAt(0L).hasTimingAnomaly)
    }

    private fun move(ordinal: Int, elapsedMs: Long, quality: MoveTimeQuality = MoveTimeQuality.DEVICE) =
        RecordedMove(ordinal = ordinal, code = "R", elapsedMs = elapsedMs, timeQuality = quality)
}
