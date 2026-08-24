// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

import com.cubetrace.app.core.cube.CubeState
import com.cubetrace.app.core.cube.equivalentFaceTurnAmount
import com.cubetrace.app.core.cube.PresetCatalog
import com.cubetrace.app.core.cube.f2lSolvedSlotsOn
import com.cubetrace.app.core.cube.isCubeSolvedRelativeToCenters
import com.cubetrace.app.core.cube.isCrossSolvedOn
import com.cubetrace.app.core.cube.isLastLayerOrientedOn
import com.cubetrace.app.core.cube.normalizedMoves
import com.cubetrace.app.core.cube.validateTimerScramble
import com.cubetrace.app.core.model.Completeness
import com.cubetrace.app.core.model.RecordedMove
import com.cubetrace.app.core.model.SolveRecord
import com.cubetrace.app.core.model.SolveSource
import com.cubetrace.app.core.model.Stage

/**
 * Dependency-free regression checks for the replay contract. The Android
 * project can run this class from a JVM test task or directly from a Kotlin
 * runner; using check() keeps the fixtures independent of a test framework
 * and avoids adding a runtime dependency to the APK.
 */
object AnalysisRegressionChecks {
    @JvmStatic
    fun main(args: Array<String>) {
        noMoves()
        malformedScramble()
        malformedMove()
        ordinalGap()
        missingCheckpoint()
        startMismatch()
        finalMismatch()
        nonMonotonicTime()
        explicitGap()
        firstEvidenceCanCompleteOnTheFinalState()
        burstKeepsSameTimestamp()
        incompleteSolveIsNotCtssInput()
        sequenceWrapAround()
        sequenceJumpIsRejected()
        singleMissingMoveIsRecoveredUniquely()
        halfTurnCannotConsumeOneMissingV10Sequence()
        manualSolveNeverUsesV10Recovery()
        reportedV10SingleGapCanBeRecovered()
        crossFaceMetadataDoesNotRestrictDetection()
        incompleteSolveNeverBecomesComplete()
        gapHidesEveryPhase()
        metadataSurvivesTheModel()
        centerRelativeCrossPredicate()
        finalCheckpointIsRequired()
        unknownPhaseDoesNotHaveMetrics()
        skipAvailabilityIsExplicit()
        canonicalSkipHasCompletePhaseSum()
        firstEvidenceKeepsStageTimes()
        coachingFallsBackToSingleObservation()
        coachingUsesPersonalPauseBaseline()
        techniqueMatchesVerifiedPllCase()
        catalogLastLayerRecognitionNeverMislabels()
        ordinaryFaceTurnsAreNotWideMoves()
        timerScrambleValidationIsStrictAndHelpful()
        equivalentSmartScrambleTurnsAreRecognized()
        recoveredPhaseDoesNotClaimFormulaOrFingerEvidence()
        skillPresenterExplainsCurrentState()
        skillEstimateResistsOneExtremeSolve()
        reproducibleLevelUsesLongTermState()
        skillEstimateStillTracksSustainedImprovement()
        offlineRankBandsAreMonotonic()
        rollingStatsExposeCurrentAndBestAverages()
        pbThresholdBoundaryIsExact()
        println("AnalysisRegressionChecks: 43 passed")
    }

    private fun oneMoveSolve(
        scramble: String = "R",
        moveCode: String = "R'",
        startFacelets: String? = CubeState.solved().apply(normalizedMoves("R")).asFacelets(),
        endFacelets: String? = CubeState.solved().asFacelets(),
        elapsedMs: Long = 100L,
        move: RecordedMove = RecordedMove(0, moveCode, elapsedMs, sequence = 1)
    ): SolveRecord = SolveRecord(
        id = "fixture",
        sessionId = "test",
        sessionName = "test",
        scramble = scramble,
        durationMs = elapsedMs,
        startedAt = 1L,
        source = SolveSource.V10_AI,
        completeness = Completeness.COMPLETE,
        moves = listOf(move),
        startFacelets = startFacelets,
        endFacelets = endFacelets,
        startSequence = 0,
        endSequence = 1
    )

    private fun noMoves() {
        val analysis = analyzeSolve(oneMoveSolve().copy(moves = emptyList()), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.NO_MOVES)
        check(analysis.phases.all { it.availability == PhaseAvailability.UNAVAILABLE })
    }

    private fun malformedScramble() {
        val analysis = analyzeSolve(oneMoveSolve(scramble = "R?"), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.UNPARSEABLE_SCRAMBLE)
    }

    private fun malformedMove() {
        val analysis = analyzeSolve(oneMoveSolve(moveCode = "R?"), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.UNPARSEABLE_MOVE)
    }

    private fun ordinalGap() {
        val analysis = analyzeSolve(oneMoveSolve(move = RecordedMove(2, "R'", 100L, sequence = 1)), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.SEQUENCE_GAP)
    }

    private fun missingCheckpoint() {
        val analysis = analyzeSolve(oneMoveSolve(startFacelets = null, endFacelets = null), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.CHECKPOINT_MISSING) { "reason=${analysis.reasonCode}" }
        check(analysis.totalMetricsReliable)
    }

    private fun startMismatch() {
        val analysis = analyzeSolve(oneMoveSolve(startFacelets = CubeState.solved().asFacelets()), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.START_STATE_MISMATCH)
    }

    private fun finalMismatch() {
        val analysis = analyzeSolve(oneMoveSolve(endFacelets = CubeState.solved().apply(normalizedMoves("R")).asFacelets()), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.FINAL_STATE_MISMATCH)
    }

    private fun nonMonotonicTime() {
        val analysis = analyzeSolve(oneMoveSolve(move = RecordedMove(0, "R'", 101L, sequence = 1)), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.TIME_NON_MONOTONIC)
    }

    private fun explicitGap() {
        val analysis = analyzeSolve(oneMoveSolve(move = RecordedMove(0, "—", 100L, sequence = 1, gap = true)), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.SEQUENCE_GAP)
    }

    private fun firstEvidenceCanCompleteOnTheFinalState() {
        val analysis = analyzeSolve(oneMoveSolve(), 250)!!
        check(analysis.status == AnalysisStatus.COMPLETE) { "status=${analysis.status}, reason=${analysis.reasonCode}" }
        check(analysis.phases.all { it.availability != PhaseAvailability.UNAVAILABLE })
    }

    private fun burstKeepsSameTimestamp() {
        val start = CubeState.solved().apply(normalizedMoves("R2")).asFacelets()
        val solve = SolveRecord(
            id = "burst",
            sessionId = "test",
            sessionName = "test",
            scramble = "R2",
            durationMs = 200L,
            startedAt = 2L,
            source = SolveSource.V10_AI,
            completeness = Completeness.COMPLETE,
            moves = listOf(
                // Legacy recordings saved the final packet sequence on every
                // move in a two-move burst. Counter 0 -> 2 still proves that
                // both physical turns were received.
                RecordedMove(0, "R'", 200L, sequence = 2),
                RecordedMove(1, "R'", 200L, sequence = 2)
            ),
            startFacelets = start,
            endFacelets = CubeState.solved().asFacelets(),
            startSequence = 0,
            endSequence = 2
        )
        check(solve.moves[0].elapsedMs == solve.moves[1].elapsedMs)
        val analysis = analyzeSolve(solve, 250)!!
        check(analysis.recordedMoveCount == 2)
        check(analysis.replay?.sequenceComplete == true)
    }

    private fun incompleteSolveIsNotCtssInput() {
        val incomplete = oneMoveSolve(startFacelets = null, endFacelets = null).copy(completeness = Completeness.INCOMPLETE)
        check(Ctss1Estimator.estimate(listOf(incomplete), 250).sampleCount == 0)
    }

    private fun sequenceWrapAround() {
        val solve = oneMoveSolve(move = RecordedMove(0, "R'", 100L, sequence = 0)).copy(startSequence = 255, endSequence = 0)
        val analysis = analyzeSolve(solve, 250)!!
        check(analysis.replay?.sequenceComplete == true)
        check(analysis.replay?.recoveredMoveCount == 0)
    }

    private fun sequenceJumpIsRejected() {
        val solve = oneMoveSolve(move = RecordedMove(0, "R'", 100L, sequence = 2)).copy(startSequence = 0, endSequence = 2)
        check(analyzeSolve(solve, 250)!!.reasonCode == AnalysisReasonCode.SEQUENCE_GAP)
    }

    private fun singleMissingMoveIsRecoveredUniquely() {
        val solve = SolveRecord(
            id = "single-gap-recovery",
            sessionId = "test",
            sessionName = "test",
            scramble = "R",
            durationMs = 300L,
            startedAt = 2L,
            source = SolveSource.V10_AI,
            completeness = Completeness.COMPLETE,
            moves = listOf(
                RecordedMove(0, "U", 100L, sequence = 1),
                // Sequence 2 was dropped by BLE. U' is the only legal face
                // turn that makes the saved final checkpoint reachable.
                RecordedMove(1, "R'", 300L, sequence = 3)
            ),
            startFacelets = CubeState.solved().apply(normalizedMoves("R")).asFacelets(),
            endFacelets = CubeState.solved().asFacelets(),
            startSequence = 0,
            endSequence = 3
        )
        val analysis = analyzeSolve(solve, 250)!!
        check(analysis.status == AnalysisStatus.COMPLETE) {
            "status=${analysis.status}, reason=${analysis.reasonCode}"
        }
        check(analysis.replay?.recoveredMoveCount == 1)
        check(analysis.replay?.parsedMoves?.map { it.code } == listOf("D", "D'", "R'"))
        check(analysis.total.moveCount == 3)
        check(!analysis.totalMetricsReliable)
        check(analysis.phases.any { it.availability == PhaseAvailability.GAP_AFFECTED })
        check(Ctss1Estimator.estimate(listOf(solve), 250).sampleCount == 0)
        check(analysis.confidence in 0.85..0.95)
    }

    private fun halfTurnCannotConsumeOneMissingV10Sequence() {
        val start = CubeState.solved().apply(normalizedMoves("R")).asFacelets()
        val solve = SolveRecord(
            id = "half-turn-gap",
            sessionId = "test",
            sessionName = "test",
            scramble = "R",
            durationMs = 400L,
            startedAt = 2L,
            source = SolveSource.V10_AI,
            completeness = Completeness.COMPLETE,
            moves = listOf(
                RecordedMove(0, "U", 100L, sequence = 1),
                // The absent physical action would have to be U2. V10 emits
                // that as two counter steps, so one missing count cannot prove it.
                RecordedMove(1, "U", 300L, sequence = 3),
                RecordedMove(2, "R'", 400L, sequence = 4)
            ),
            startFacelets = start,
            endFacelets = CubeState.solved().asFacelets(),
            startSequence = 0,
            endSequence = 4
        )
        val analysis = analyzeSolve(solve, 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.SEQUENCE_GAP)
        check(analysis.replay?.recoveredMoveCount == 0)
    }

    private fun manualSolveNeverUsesV10Recovery() {
        val smart = SolveRecord(
            id = "manual-gap",
            sessionId = "test",
            sessionName = "test",
            scramble = "R",
            durationMs = 300L,
            startedAt = 2L,
            source = SolveSource.MANUAL,
            completeness = Completeness.COMPLETE,
            moves = listOf(
                RecordedMove(0, "U", 100L, sequence = 1),
                RecordedMove(1, "R'", 300L, sequence = 3)
            ),
            startFacelets = CubeState.solved().apply(normalizedMoves("R")).asFacelets(),
            endFacelets = CubeState.solved().asFacelets(),
            startSequence = 0,
            endSequence = 3
        )
        val analysis = analyzeSolve(smart, 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.SEQUENCE_GAP)
        check(analysis.replay?.recoveredMoveCount == 0)
    }

    /** The 18.095 s field report had one absent counter value (149). */
    private fun reportedV10SingleGapCanBeRecovered() {
        val codes = "R' R' F' B' R' B U R' U' B D' B' D L' D L F' D D F D' D' F' D F D' F D F' F D F' D' F D F' D D' B' D B D' D' R' D' R D R D' R' D R D R' F D L D' L' F' D D R' B R F' R' B' R F D' D' L D L D L D' L' D' L L".split(" ")
        check(codes.size == 82)
        val solve = SolveRecord(
            id = "reported-v10-single-gap",
            sessionId = "test",
            sessionName = "test",
            scramble = "R D2 R2 B2 R2 D2 B U2 B' U R' U2 F D' F L2 U' F D2 R'",
            durationMs = 18_095L,
            startedAt = 3L,
            source = SolveSource.V10_AI,
            completeness = Completeness.COMPLETE,
            moves = codes.mapIndexed { index, code ->
                val sequence = if (index <= 70) 78 + index else 79 + index
                RecordedMove(index, code, (index + 1L) * 200L, sequence = sequence)
            },
            startFacelets = "FUFUUUDLRDBUDRFBRBLBFLFBDDDRRLDDFURLUFFBLFRLBLLRDBRUUB",
            endFacelets = CubeState.solved().asFacelets(),
            startSequence = 77,
            endSequence = 160,
            crossFace = 'D'
        )
        val analysis = analyzeSolve(solve, 250)!!
        check(analysis.status == AnalysisStatus.COMPLETE) {
            "status=${analysis.status}, reason=${analysis.reasonCode}, recovered=${analysis.replay?.recoveredMoveCount}"
        }
        check(analysis.replay?.recoveredMoveCount == 1)
        check(analysis.replay?.parsedMoves?.size == 83)
        check(analysis.replay?.parsedMoves?.get(71)?.sequence == 149)
        check(!analysis.totalMetricsReliable)
        check(Ctss1Estimator.estimate(listOf(solve), 250).sampleCount == 0)
    }

    private fun crossFaceMetadataDoesNotRestrictDetection() {
        val durationMs = 200L
        val solve = SolveRecord(
            id = "dynamic-cross",
            sessionId = "test",
            sessionName = "test",
            scramble = "D",
            durationMs = durationMs,
            startedAt = 2L,
            source = SolveSource.V10_AI,
            completeness = Completeness.COMPLETE,
            moves = listOf(RecordedMove(0, "D'", durationMs, sequence = 1)),
            startFacelets = CubeState.solved().apply(normalizedMoves("U")).asFacelets(),
            endFacelets = CubeState.solved().asFacelets(),
            startSequence = 0,
            endSequence = 1,
            crossFace = 'X'
        )
        val analysis = analyzeSolve(solve, 250)!!
        check(analysis.status == AnalysisStatus.COMPLETE) { "status=${analysis.status}, reason=${analysis.reasonCode}" }
        check(analysis.reasonCode != AnalysisReasonCode.NON_CFOP_PROGRESSION)
        check(analysis.phases.first().availability == PhaseAvailability.PROVEN_SKIP)
        check(analysis.phases.last().summary.moveCount == 1)
    }

    private fun incompleteSolveNeverBecomesComplete() {
        val solve = oneMoveSolve().copy(completeness = Completeness.INCOMPLETE)
        check(analyzeSolve(solve, 250)!!.status == AnalysisStatus.INCOMPLETE)
    }

    private fun gapHidesEveryPhase() {
        val solve = oneMoveSolve(move = RecordedMove(0, "—", 100L, sequence = 1, gap = true))
        check(analyzeSolve(solve, 250)!!.phases.all { it.availability == PhaseAvailability.UNAVAILABLE })
    }

    private fun metadataSurvivesTheModel() {
        val move = RecordedMove(0, "R'", 100L, sequence = 1, deviceTimeMs = 44L, receivedAtElapsedMs = 1234L, timeQuality = com.cubetrace.app.core.model.MoveTimeQuality.DEVICE)
        check(move.deviceTimeMs == 44L && move.receivedAtElapsedMs == 1234L && move.timeQuality.name == "DEVICE")
    }

    private fun centerRelativeCrossPredicate() {
        val solved = CubeState.solved().asFacelets()
        check("URFDLB".all { isCrossSolvedOn(solved, it) })
        check(!isCrossSolvedOn(solved, 'X'))
    }

    private fun finalCheckpointIsRequired() {
        val analysis = analyzeSolve(oneMoveSolve(endFacelets = null), 250)!!
        check(analysis.reasonCode == AnalysisReasonCode.CHECKPOINT_MISSING)
    }

    private fun unknownPhaseDoesNotHaveMetrics() {
        val analysis = analyzeSolve(oneMoveSolve(startFacelets = null, endFacelets = null), 250)!!
        check(analysis.phases.all { it.summary.totalTps == null && it.summary.durationMs == 0L })
    }

    private fun skipAvailabilityIsExplicit() {
        check(PhaseAvailability.PROVEN_SKIP != PhaseAvailability.UNAVAILABLE)
        check(PhaseAvailability.SAME_MOVE_COMPLETION != PhaseAvailability.UNAVAILABLE)
    }

    /** A real replay fixture: an official-frame D/D' pair is an app-frame U/U'
     * last-layer-only solve, so Cross/F2L/OLL are proven skips and PLL owns the
     * one move. This also guards the telescoping phase-time contract. */
    private fun canonicalSkipHasCompletePhaseSum() {
        val durationMs = 200L
        val solve = SolveRecord(
            id = "canonical-skip",
            sessionId = "test",
            sessionName = "test",
            scramble = "D",
            durationMs = durationMs,
            startedAt = 3L,
            source = SolveSource.V10_AI,
            completeness = Completeness.COMPLETE,
            moves = listOf(RecordedMove(0, "D'", durationMs, sequence = 1)),
            startFacelets = CubeState.solved().apply(normalizedMoves("U")).asFacelets(),
            endFacelets = CubeState.solved().asFacelets(),
            startSequence = 0,
            endSequence = 1,
            crossFace = 'D'
        )
        val analysis = analyzeSolve(solve, 250)!!
        check(analysis.status == AnalysisStatus.COMPLETE) { "status=${analysis.status}, reason=${analysis.reasonCode}" }
        check(analysis.replay?.valid == true)
        check(analysis.phases.sumOf { it.summary.durationMs } == durationMs)
        check(analysis.phases.first().availability == PhaseAvailability.PROVEN_SKIP)
        check(analysis.phases.last().summary.moveCount == 1)
    }

    /** Reported 0.2.0 V10 solve: first evidence remains valid after later turns. */
    private fun firstEvidenceKeepsStageTimes() {
        val codes = "L' F' R U L U F' D' F F' D D F D D B D B' L D' D' L' D' B' D' B D D F' D D F D' F' D F D' R' D D R D' R' D R D' D' F D L D' L' F' D' D' F D D F' D' F D' F' D F D F' D' F' R F F D' F' D' F D F' R' D L D' L D L D L D' L' L L' D' L L".split(" ")
        check(codes.size == 94) { "diagnostic move count=${codes.size}" }
        val solve = SolveRecord(
            id = "real-v10-diagnostic",
            sessionId = "diagnostic",
            sessionName = "diagnostic",
            scramble = "U R2 U2 F U2 L' U' L' F R2 D R' F2 D B U2 L2 D F2 L",
            durationMs = 18_689L,
            startedAt = 1L,
            source = SolveSource.V10_AI,
            completeness = Completeness.COMPLETE,
            moves = codes.mapIndexed { index, code ->
                RecordedMove(index, code, (index + 1L) * 100L, sequence = 145 + index)
            },
            startFacelets = "LLDFUUULDLFBBRUFLRRFFBFRDBDBDRDDUFUBBRFRLLUFLRDURBDUBL",
            endFacelets = CubeState.solved().asFacelets(),
            startSequence = 144,
            endSequence = 238,
            crossFace = 'D'
        )
        val analysis = analyzeSolve(solve, 250)!!
        check(analysis.status == AnalysisStatus.COMPLETE) { "status=${analysis.status}, reason=${analysis.reasonCode}" }
        check(analysis.reasonCode == null)
        check(analysis.replay?.valid == true)
        check(analysis.phases.all { it.availability != PhaseAvailability.UNAVAILABLE })
        check(analysis.phases.first().endOrdinalInclusive == 6)
        check(analysis.phases.sumOf { it.summary.durationMs } == solve.durationMs)
        check(analysis.phases.count { it.summary.durationMs > 0L } >= 3) {
            "phase durations=${analysis.phases.map { it.summary.durationMs }}"
        }
        check(analysis.phases.last().summary.durationMs < solve.durationMs) {
            "PLL consumed the whole solve: ${analysis.phases.map { it.summary.durationMs }}"
        }
        check(Ctss1Estimator.estimate(listOf(solve), 250).sampleCount == 1)
    }

    private fun coachingFallsBackToSingleObservation() {
        val analysis = analyzeSolve(oneMoveSolve(), 250)!!
        val insights = buildCoachingInsights(analysis, null)
        check(insights.size == 1)
        check(insights.first().priority == CoachingPriority.PRIMARY)
        check(insights.first().scope == CoachingScope.SOLVE)
        check(insights.first().evidence.endMs >= insights.first().evidence.startMs)
    }

    private fun coachingUsesPersonalPauseBaseline() {
        val phase = PhaseMetric(
            code = PhaseCode.F2,
            startMs = 1_000L,
            endMs = 3_000L,
            startOrdinalExclusive = 8,
            endOrdinalInclusive = 15,
            summary = MetricSummary(
                durationMs = 2_000L,
                moveCount = 8,
                pauseMs = 700L,
                pauseRate = 0.35,
                totalTps = 4.0,
                activeTps = 6.15,
                longestGapMs = 500L
            ),
            confidence = 0.97,
            availability = PhaseAvailability.AVAILABLE
        )
        val analysis = SolveAnalysis(
            solveId = "coach",
            analyzerVersion = ANALYZER_VERSION,
            status = AnalysisStatus.COMPLETE,
            confidence = 0.97,
            total = phase.summary,
            phases = listOf(phase),
            totalMetricsReliable = true,
            recordedMoveCount = 8
        )
        val forecast = SkillForecast(
            timeMs = interval(1_200.0, 1_500.0),
            moves = interval(7.0, 9.0),
            pauseRate = interval(0.12, 0.18),
            practicalTps = 5.8,
            activeTps = 6.0
        )
        val estimate = SkillEstimate(
            sampleCount = 12,
            reliableCount = 12,
            status = SkillStatus.USABLE,
            total = forecast,
            phases = listOf(SkillPhaseForecast(PhaseCode.F2, forecast, 12, SkillStatus.USABLE))
        )
        val insight = buildCoachingInsights(analysis, estimate).first()
        check(insight.rule == CoachingRule.PAUSE_DOMINANT)
        check(insight.scope == CoachingScope.TREND)
        check(insight.sampleCount == 12)
    }

    private fun techniqueMatchesVerifiedPllCase() {
        val cubeCase = PresetCatalog.all().first { it.stage == Stage.PLL }
        val cubeMoves = normalizedMoves(cubeCase.variant.notation)
        val recorded = cubeMoves.mapIndexed { index, move ->
            RecordedMove(index, move.normalized, (index + 1L) * 100L, sequence = index + 1)
        }
        val states = buildList {
            var state = CubeState.fromFacelets(cubeCase.canonicalState)!!
            add(state.asFacelets())
            cubeMoves.forEach { move ->
                state = state.apply(move)
                add(state.asFacelets())
            }
        }
        val duration = recorded.lastOrNull()?.elapsedMs ?: 0L
        val phase = PhaseMetric(
            code = PhaseCode.P,
            startMs = 0L,
            endMs = duration,
            startOrdinalExclusive = 0,
            endOrdinalInclusive = recorded.size,
            summary = MetricSummary(duration, recorded.size, 0L, 0.0, null, null, null),
            confidence = 0.97,
            availability = PhaseAvailability.AVAILABLE
        )
        val analysis = SolveAnalysis(
            solveId = "pll-technique",
            analyzerVersion = ANALYZER_VERSION,
            status = AnalysisStatus.COMPLETE,
            confidence = 0.97,
            total = phase.summary,
            phases = listOf(phase),
            replay = ReplayValidation(
                valid = true,
                parsedMoves = recorded,
                states = states,
                startMatches = true,
                finalMatches = true,
                finalSolved = true,
                sequenceComplete = true,
                timeMonotonic = true,
                checkpointComplete = true
            ),
            totalMetricsReliable = true,
            recordedMoveCount = recorded.size,
            detectedCrossFace = 'D'
        )
        val tips = buildTechniqueRecommendations(analysis, phase, null)
        check(tips.any { it.kind == CoachingTechniqueKind.FORMULA && it.caseId == cubeCase.stableId }) {
            "tips=$tips"
        }
        check(tips.any { it.kind == CoachingTechniqueKind.FINGER_PRACTICE })
        check(tips.any { it.kind == CoachingTechniqueKind.START_PLAN })
    }

    private fun catalogLastLayerRecognitionNeverMislabels() {
        var verifiedMatches = 0
        PresetCatalog.all().filter { it.stage == Stage.OLL || it.stage == Stage.PLL }.forEach { cubeCase ->
            val phase = PhaseMetric(
                code = if (cubeCase.stage == Stage.OLL) PhaseCode.O else PhaseCode.P,
                startMs = 0L,
                endMs = 1L,
                startOrdinalExclusive = 0,
                endOrdinalInclusive = 0,
                summary = MetricSummary(1L, 0, 0L, 0.0, null, null, null),
                confidence = 0.97,
                availability = PhaseAvailability.AVAILABLE
            )
            val analysis = SolveAnalysis(
                solveId = "signature-${cubeCase.stableId}",
                analyzerVersion = ANALYZER_VERSION,
                status = AnalysisStatus.COMPLETE,
                confidence = 0.97,
                total = phase.summary,
                phases = listOf(phase),
                replay = ReplayValidation(valid = true, states = listOf(cubeCase.canonicalState)),
                detectedCrossFace = 'D'
            )
            val formula = buildTechniqueRecommendations(analysis, phase, null)
                .singleOrNull { it.kind == CoachingTechniqueKind.FORMULA }
            if (formula != null) {
                check(formula.caseId == cubeCase.stableId) {
                    "${cubeCase.stableId} was mislabeled as ${formula.caseId}"
                }
                verifiedMatches += 1
            }
        }
        check(verifiedMatches >= 70) {
            "Only $verifiedMatches last-layer cases had both a unique signature and a formula that verified against the stage goal"
        }
    }

    private fun ordinaryFaceTurnsAreNotWideMoves() {
        check(!usesWideSliceOrRotation(listOf("R", "U", "R'", "U'")))
        check(usesWideSliceOrRotation(listOf("r", "U", "r'")))
        check(usesWideSliceOrRotation(listOf("M2")))
        check(usesWideSliceOrRotation(listOf("y'")))
    }

    private fun timerScrambleValidationIsStrictAndHelpful() {
        val valid = validateTimerScramble("(R U R' U')2")
        check(valid.valid)
        check(valid.notation == "R U R' U' R U R' U'") { "notation=${valid.notation}" }
        check(!validateTimerScramble("").valid)
        check(!validateTimerScramble("r U").valid)
        check(!validateTimerScramble("M2").valid)
        check(!validateTimerScramble("x R").valid)
        check(!validateTimerScramble("R U F", maxMoves = 2).valid)
    }

    private fun equivalentSmartScrambleTurnsAreRecognized() {
        val solved = CubeState.solved()
        val u2 = normalizedMoves("U2").single()
        val r = normalizedMoves("R").single()
        check(
            equivalentFaceTurnAmount(
                solved.asFacelets(),
                solved.apply(normalizedMoves("U' U'")).asFacelets(),
                u2
            ) == 2
        )
        check(
            equivalentFaceTurnAmount(
                solved.asFacelets(),
                solved.apply(normalizedMoves("R' R' R'")).asFacelets(),
                r
            ) == 1
        )
        check(
            equivalentFaceTurnAmount(
                solved.asFacelets(),
                solved.apply(normalizedMoves("F'")).asFacelets(),
                r
            ) == null
        )
    }

    private fun recoveredPhaseDoesNotClaimFormulaOrFingerEvidence() {
        val cubeCase = PresetCatalog.all().first { it.stage == Stage.PLL }
        val phase = PhaseMetric(
            code = PhaseCode.P,
            startMs = 0L,
            endMs = 100L,
            startOrdinalExclusive = 0,
            endOrdinalInclusive = 1,
            summary = MetricSummary(100L, 1, 0L, 0.0, null, null, null),
            confidence = 0.90,
            availability = PhaseAvailability.GAP_AFFECTED,
            flags = setOf("RECOVERED_MOVE")
        )
        val move = RecordedMove(0, "U", 100L, sequence = 1)
        val start = CubeState.fromFacelets(cubeCase.canonicalState)!!
        val analysis = SolveAnalysis(
            solveId = "recovered-technique",
            analyzerVersion = ANALYZER_VERSION,
            status = AnalysisStatus.COMPLETE,
            confidence = 0.90,
            total = phase.summary,
            phases = listOf(phase),
            replay = ReplayValidation(
                valid = true,
                parsedMoves = listOf(move),
                states = listOf(start.asFacelets(), start.apply(normalizedMoves("U")).asFacelets()),
                recoveredMoveCount = 1,
                recoveredOrdinals = setOf(0)
            ),
            totalMetricsReliable = true,
            recordedMoveCount = 1,
            detectedCrossFace = 'D'
        )
        val tips = buildTechniqueRecommendations(analysis, phase, null)
        check(tips.isEmpty())
    }

    private fun skillPresenterExplainsCurrentState() {
        val forecast = SkillForecast(
            timeMs = interval(18_000.0, 20_000.0).copy(stable = 17_500.0, formDelta = 500.0),
            moves = interval(60.0, 70.0),
            pauseRate = interval(0.22, 0.30),
            practicalTps = 3.4,
            activeTps = 4.8
        )
        val estimate = SkillEstimate(
            sampleCount = 20,
            reliableCount = 20,
            status = SkillStatus.USABLE,
            total = forecast,
            phases = listOf(SkillPhaseForecast(PhaseCode.F3, forecast, 20, SkillStatus.USABLE)),
            recentDeltaMs = 500.0
        )
        val assessment = SkillLevelPresenter.present(estimate)
        check(assessment.state == SkillAssessmentState.ATTENTION)
        check(assessment.focusPhase == PhaseCode.F3)
        check(assessment.recommendation.contains("F3"))
        check(assessment.evidence.contains("20 个样本"))
    }

    private fun skillEstimateResistsOneExtremeSolve() {
        val minute = 60_000L
        val normal = (0 until 12).map { index ->
            oneMoveSolve(
                elapsedMs = 20_000L,
                move = RecordedMove(0, "R'", 20_000L, sequence = 1)
            ).copy(id = "stable-$index", startedAt = (index + 1L) * minute)
        }
        val baseline = Ctss1Estimator.estimate(normal, 250)
        val extreme = oneMoveSolve(
            elapsedMs = 100_000L,
            move = RecordedMove(0, "R'", 100_000L, sequence = 1)
        ).copy(id = "extreme", startedAt = 13L * minute)
        val updated = Ctss1Estimator.estimate(normal + extreme, 250)
        val before = baseline.total?.timeMs?.median ?: error("baseline estimate missing")
        val after = updated.total?.timeMs?.median ?: error("updated estimate missing")
        check(kotlin.math.abs(after - before) < 1_000.0) {
            "one extreme solve moved the estimate too far: before=$before after=$after"
        }
        val stableBefore = baseline.reproducibleTime?.median ?: error("baseline reproducible estimate missing")
        val stableAfter = updated.reproducibleTime?.median ?: error("updated reproducible estimate missing")
        check(kotlin.math.abs(stableAfter - stableBefore) < 500.0) {
            "one extreme solve moved the long-term level too far: before=$stableBefore after=$stableAfter"
        }
        check(Ctss1Estimator.estimate(normal.take(8), 250).recentDeltaMs == null)
    }

    private fun reproducibleLevelUsesLongTermState() {
        val forecast = interval(9_000.0, 11_000.0).copy(
            median = 10_000.0,
            stable = 20_000.0,
            formDelta = -10_000.0
        )
        val reproducible = forecast.reproducibleInterval()
        check(reproducible.median == 20_000.0)
        check(reproducible.p50Low == 18_000.0)
        check(reproducible.p50High == 22_000.0)
    }

    private fun skillEstimateStillTracksSustainedImprovement() {
        val minute = 60_000L
        val improving = (0 until 20).map { index ->
            val elapsed = 26_000L - index * 600L
            oneMoveSolve(
                elapsedMs = elapsed,
                move = RecordedMove(0, "R'", elapsed, sequence = 1)
            ).copy(id = "improving-$index", startedAt = (index + 1L) * minute)
        }
        val estimate = Ctss1Estimator.estimate(improving, 250)
        val current = estimate.total?.timeMs?.median ?: error("improvement estimate missing")
        check(current < 22_000.0) { "sustained improvement was over-smoothed: $current" }
        check((estimate.recentDeltaMs ?: 0.0) < 0.0) {
            "sustained improvement should report a negative recent delta: ${estimate.recentDeltaMs}"
        }
    }

    private fun offlineRankBandsAreMonotonic() {
        val fast = OfflineWcaRankEstimator.estimate(9_000.0, 10_000.0)!!
        val slow = OfflineWcaRankEstimator.estimate(18_000.0, 20_000.0)!!
        val point = OfflineWcaRankEstimator.estimate(18_000.0, 18_000.0)!!
        check(fast.world.bestRank <= fast.world.worstRank)
        check(fast.china.bestRank <= fast.china.worstRank)
        check(fast.world.worstRank < slow.world.bestRank)
        check(fast.china.worstRank < slow.china.bestRank)
        check(point.world.bestRank == point.world.worstRank)
        check(point.china.bestRank == point.china.worstRank)
    }

    private fun rollingStatsExposeCurrentAndBestAverages() {
        val durations = List(12) { 10_000L } + List(4) { 20_000L }
        val records = durations.mapIndexed { index, duration ->
            SolveRecord(
                id = "rolling-$index",
                sessionId = "test",
                sessionName = "test",
                scramble = "",
                durationMs = duration,
                startedAt = index.toLong()
            )
        }
        val stats = rollingStats(records)
        check(stats.currentAo5.valueMs != null && stats.bestAo5.valueMs != null)
        check(stats.currentAo12.valueMs != null && stats.bestAo12.valueMs != null)
        check(stats.currentAo5.valueMs!! > stats.bestAo5.valueMs!!)
        check(stats.currentAo12.valueMs!! > stats.bestAo12.valueMs!!)
    }

    private fun pbThresholdBoundaryIsExact() {
        val durations = listOf(11_000L, 12_000L, 13_000L, 14_000L, 15_000L, 9_000L, 13_000L, 14_000L, 15_000L)
        val records = durations.mapIndexed { index, duration ->
            SolveRecord(
                id = "pb-$index",
                sessionId = "test",
                sessionName = "test",
                scramble = "",
                durationMs = duration,
                startedAt = index.toLong()
            )
        }
        val threshold = nextPbThreshold(records, 5)
        check(threshold is PbThreshold.AtMost)
        check(threshold.rawMs == 11_999L) { "threshold=${threshold.rawMs}" }
    }

    private fun interval(low: Double, high: Double) = IntervalForecast(
        median = (low + high) / 2.0,
        p50Low = low,
        p50High = high,
        p80Low = low * 0.9,
        p80High = high * 1.1,
        stable = (low + high) / 2.0,
        formDelta = 0.0
    )
}
