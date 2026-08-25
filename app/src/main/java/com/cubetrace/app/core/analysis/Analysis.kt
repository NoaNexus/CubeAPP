// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

import com.cubetrace.app.core.cube.CubeState
import com.cubetrace.app.core.cube.cfopCrossFaces
import com.cubetrace.app.core.cube.f2lSolvedSlotsOn
import com.cubetrace.app.core.cube.isCubeSolvedRelativeToCenters
import com.cubetrace.app.core.cube.isF2lSolvedOn
import com.cubetrace.app.core.cube.isCrossSolvedOn
import com.cubetrace.app.core.cube.isLastLayerOrientedOn
import com.cubetrace.app.core.cube.moyuMoveToYellowTopBlueFront
import com.cubetrace.app.core.cube.MoveParser
import com.cubetrace.app.core.model.Completeness
import com.cubetrace.app.core.model.MoveTimeQuality
import com.cubetrace.app.core.model.Penalty
import com.cubetrace.app.core.model.RecordedMove
import com.cubetrace.app.core.model.SolveRecord
import com.cubetrace.app.core.model.SolveSource
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val ANALYZER_VERSION = "2.1.0"
private const val MODEL_VERSION = "CTSS-1.2.0"
private const val DAY_MS = 86_400_000L

enum class PhaseCode(val label: String) {
    C("十字"),
    F1("F1"),
    F2("F2"),
    F3("F3"),
    F4("F4"),
    O("OLL"),
    P("PLL")
}

enum class AnalysisStatus(val label: String) {
    COMPLETE("完整"),
    INCOMPLETE("不完整")
}

/** Machine-readable failure reason; UI text is derived from this code. */
enum class AnalysisReasonCode(val label: String) {
    NO_MOVES("没有动作事件"),
    UNPARSEABLE_SCRAMBLE("打乱公式无法解析"),
    UNPARSEABLE_MOVE("动作无法解析"),
    SEQUENCE_GAP("动作序号不连续"),
    START_STATE_MISMATCH("起始局面与打乱不一致"),
    FINAL_STATE_MISMATCH("最终局面与回放不一致"),
    CROSS_NOT_FOUND("没有找到十字完成点"),
    F2L_NOT_FOUND("没有找到 F2L 完成点"),
    OLL_NOT_FOUND("没有找到 OLL 完成点"),
    SOLVED_NOT_FOUND("没有找到还原完成点"),
    AMBIGUOUS_FINAL_ONLY("只有最终状态成立，无法证明阶段边界"),
    NON_MONOTONIC_BOUNDARIES("阶段边界顺序异常"),
    NON_CFOP_PROGRESSION("动作序列不符合可证明的 CFOP 进程"),
    CHECKPOINT_MISSING("缺少起止局面检查点"),
    TIME_NON_MONOTONIC("动作时间不单调")
}

enum class PhaseAvailability {
    AVAILABLE,
    PROVEN_SKIP,
    SAME_MOVE_COMPLETION,
    UNAVAILABLE,
    GAP_AFFECTED
}

data class ReplayValidation(
    val valid: Boolean,
    val reasonCode: AnalysisReasonCode? = null,
    val parsedMoves: List<RecordedMove> = emptyList(),
    val states: List<String> = emptyList(),
    val startMatches: Boolean = false,
    val finalMatches: Boolean = false,
    val finalSolved: Boolean = false,
    val sequenceComplete: Boolean = false,
    val timeMonotonic: Boolean = false,
    val checkpointComplete: Boolean = false,
    /** Moves reconstructed from a unique sequence/checkpoint solution. */
    val recoveredMoveCount: Int = 0,
    val recoveredOrdinals: Set<Int> = emptySet()
)

data class MetricSummary(
    val durationMs: Long,
    val moveCount: Int,
    val pauseMs: Long,
    val pauseRate: Double,
    val totalTps: Double?,
    val activeTps: Double?,
    val longestGapMs: Long?
)

data class PhaseMetric(
    val code: PhaseCode,
    val startMs: Long,
    val endMs: Long,
    val startOrdinalExclusive: Int,
    val endOrdinalInclusive: Int,
    val summary: MetricSummary,
    val confidence: Double,
    val flags: Set<String> = emptySet(),
    val availability: PhaseAvailability = PhaseAvailability.UNAVAILABLE
)

data class SolveAnalysis(
    val solveId: String,
    val analyzerVersion: String,
    val status: AnalysisStatus,
    val reason: String? = null,
    val confidence: Double,
    val total: MetricSummary,
    val phases: List<PhaseMetric>,
    val reasonCode: AnalysisReasonCode? = null,
    val replay: ReplayValidation? = null,
    val totalMetricsReliable: Boolean = false,
    val recordedMoveCount: Int = 0,
    /** Cross/bottom face selected from the observed CFOP progression. */
    val detectedCrossFace: Char? = null
)

/**
 * Returns the physical turns attributed to a CFOP phase. Phase boundaries are
 * replay-state indexes: the start is inclusive in the move list and the end is
 * exclusive, even though the persisted field names describe state ordinals.
 */
fun executedMovesForPhase(phase: PhaseMetric, moves: List<RecordedMove>): List<RecordedMove> {
    val start = phase.startOrdinalExclusive.coerceIn(0, moves.size)
    val end = phase.endOrdinalInclusive.coerceIn(start, moves.size)
    return moves.subList(start, end)
}

/**
 * An exact average keeps the integer sum and divisor until presentation. This
 * prevents a displayed tie from hiding a real one-millisecond PB difference.
 */
enum class AverageStatus { VALID, DNF, INSUFFICIENT }

data class ExactAverage(
    val status: AverageStatus,
    val sumMs: Long = 0L,
    val divisor: Int = 0
) {
    val valueMs: Long?
        get() = if (status == AverageStatus.VALID && divisor > 0) sumMs / divisor else null
}

data class RollingStats(
    val currentSingleMs: Long?,
    val bestSingleMs: Long?,
    val currentAo5: ExactAverage,
    val bestAo5: ExactAverage,
    val currentAo12: ExactAverage,
    val bestAo12: ExactAverage
)

sealed class PbThreshold {
    data class NeedMore(val count: Int) : PbThreshold()
    data object NoRecordYet : PbThreshold()
    data object ImpossibleThisWindow : PbThreshold()
    data object DnfStillBreaksPb : PbThreshold()
    data class AtMost(val rawMs: Long) : PbThreshold()
}

data class PreSolveTargets(
    val ao5: PbThreshold,
    val ao12: PbThreshold
)

private fun adjustedMs(solve: SolveRecord): Long? = when (solve.penalty) {
    Penalty.NONE -> solve.durationMs
    Penalty.PLUS_TWO -> solve.durationMs + 2_000L
    Penalty.DNF -> null
}

private fun compareAverage(left: ExactAverage, right: ExactAverage): Int {
    if (left.status != AverageStatus.VALID || right.status != AverageStatus.VALID) return 0
    val lhs = left.sumMs * right.divisor.toLong()
    val rhs = right.sumMs * left.divisor.toLong()
    return lhs.compareTo(rhs)
}

fun averageOf(records: List<SolveRecord>, n: Int = records.size): ExactAverage {
    if (records.size < n || n <= 0) return ExactAverage(AverageStatus.INSUFFICIENT)
    val window = records.takeLast(n)
    val sorted = window.sortedWith(
        compareBy<SolveRecord> { adjustedMs(it) == null }
            .thenBy { adjustedMs(it) ?: Long.MAX_VALUE }
    )
    val trim = ceil(n * 0.05).toInt()
    val kept = sorted.drop(trim).dropLast(trim)
    if (kept.any { adjustedMs(it) == null }) return ExactAverage(AverageStatus.DNF)
    val values = kept.mapNotNull(::adjustedMs)
    return if (values.isEmpty()) {
        ExactAverage(AverageStatus.DNF)
    } else {
        ExactAverage(AverageStatus.VALID, values.sum(), values.size)
    }
}

fun bestAverage(records: List<SolveRecord>, n: Int): ExactAverage {
    if (records.size < n) return ExactAverage(AverageStatus.INSUFFICIENT)
    return records.windowed(n).map { averageOf(it, n) }
        .filter { it.status == AverageStatus.VALID }
        .minWithOrNull { a, b -> compareAverage(a, b) }
        ?: ExactAverage(AverageStatus.DNF)
}

fun rollingStats(records: List<SolveRecord>): RollingStats {
    val chronological = records.sortedBy { it.startedAt }
    val validSingles = chronological.mapNotNull(::adjustedMs)
    return RollingStats(
        currentSingleMs = chronological.lastOrNull()?.let(::adjustedMs),
        bestSingleMs = validSingles.minOrNull(),
        currentAo5 = averageOf(chronological, 5),
        bestAo5 = bestAverage(chronological, 5),
        currentAo12 = averageOf(chronological, 12),
        bestAo12 = bestAverage(chronological, 12)
    )
}

fun nextPbThreshold(records: List<SolveRecord>, n: Int): PbThreshold {
    val chronological = records.sortedBy { it.startedAt }
    if (chronological.size < n - 1) return PbThreshold.NeedMore(n - 1 - chronological.size)
    val best = bestAverage(chronological, n)
    if (best.status != AverageStatus.VALID) return PbThreshold.NoRecordYet
    val recent = chronological.takeLast(n - 1)

    fun candidate(ms: Long?): ExactAverage {
        val candidate = SolveRecord(
            id = "candidate",
            sessionId = "candidate",
            sessionName = "candidate",
            scramble = "",
            durationMs = ms ?: 0L,
            startedAt = Long.MAX_VALUE,
            penalty = if (ms == null) Penalty.DNF else Penalty.NONE
        )
        return averageOf(recent + candidate, n)
    }

    fun improves(value: ExactAverage): Boolean =
        value.status == AverageStatus.VALID && compareAverage(value, best) < 0

    if (!improves(candidate(0L))) return PbThreshold.ImpossibleThisWindow
    if (improves(candidate(null))) return PbThreshold.DnfStillBreaksPb

    var low = 0L
    var high = 24L * 60L * 60L * 1_000L
    if (improves(candidate(high))) return PbThreshold.AtMost(high)
    while (low < high) {
        val middle = (low + high + 1L) / 2L
        if (improves(candidate(middle))) low = middle else high = middle - 1L
    }
    return PbThreshold.AtMost(low)
}

private data class PauseInfo(val pauseMs: Long, val longestGapMs: Long?)

private fun pauseInfo(
    moves: List<RecordedMove>,
    firstMoveIndex: Int,
    lastMoveExclusive: Int,
    startMs: Long,
    endMs: Long,
    thresholdMs: Int
): PauseInfo {
    if (firstMoveIndex >= lastMoveExclusive || firstMoveIndex !in moves.indices) {
        return PauseInfo(0L, null)
    }
    val lastIndex = (lastMoveExclusive - 1).coerceAtMost(moves.lastIndex)
    var pause = 0L
    var longest: Long? = null
    fun addGap(gapStart: Long, gapEnd: Long) {
        val gap = (gapEnd - gapStart).coerceAtLeast(0L)
        if (gap > (longest ?: 0L)) longest = gap
        if (gap > thresholdMs) {
            val excessStart = gapStart + thresholdMs
            val overlapStart = max(excessStart, startMs)
            val overlapEnd = min(gapEnd, endMs)
            if (overlapEnd > overlapStart) pause += overlapEnd - overlapStart
        }
    }
    addGap(startMs, moves[firstMoveIndex].elapsedMs)
    for (index in firstMoveIndex + 1..lastIndex) {
        addGap(moves[index - 1].elapsedMs, moves[index].elapsedMs)
    }
    addGap(moves[lastIndex].elapsedMs, endMs)
    return PauseInfo(pause, longest)
}

private fun metricSummary(
    moves: List<RecordedMove>,
    firstMoveIndex: Int,
    lastMoveExclusive: Int,
    startMs: Long,
    endMs: Long,
    thresholdMs: Int
): MetricSummary {
    val duration = (endMs - startMs).coerceAtLeast(0L)
    val count = (lastMoveExclusive - firstMoveIndex).coerceAtLeast(0)
    val pause = pauseInfo(moves, firstMoveIndex, lastMoveExclusive, startMs, endMs, thresholdMs)
    val seconds = duration / 1_000.0
    val activeSeconds = ((duration - pause.pauseMs).coerceAtLeast(1L)) / 1_000.0
    return MetricSummary(
        durationMs = duration,
        moveCount = count,
        pauseMs = pause.pauseMs,
        pauseRate = if (duration == 0L) 0.0 else pause.pauseMs.toDouble() / duration,
        totalTps = count.takeIf { seconds > 0.0 }?.div(seconds),
        activeTps = count.takeIf { activeSeconds > 0.0 }?.div(activeSeconds),
        longestGapMs = pause.longestGapMs
    )
}

private data class CfopDetection(
    val crossFace: Char?,
    val crossEnd: Int?,
    val f2lEnd: Int?,
    val ollEnd: Int?,
    val solvedEnd: Int?,
    val slotEndings: List<Int?>
)

private fun canonicalMove(move: com.cubetrace.app.core.cube.CubeMove): com.cubetrace.app.core.cube.CubeMove? {
    val mapped = moyuMoveToYellowTopBlueFront(move.normalized)
    return MoveParser.parse(mapped).moves.singleOrNull()
}

private fun parseCanonicalScramble(notation: String): List<com.cubetrace.app.core.cube.CubeMove>? {
    if (notation.isBlank()) return null
    val parsed = MoveParser.parse(notation)
    if (parsed.error != null || parsed.moves.isEmpty()) return null
    return parsed.moves.mapNotNull(::canonicalMove).takeIf { it.size == parsed.moves.size }
}

private fun parseCanonicalMove(notation: String): com.cubetrace.app.core.cube.CubeMove? {
    val parsed = MoveParser.parse(notation)
    if (parsed.error != null || parsed.moves.size != 1) return null
    return canonicalMove(parsed.moves.single())
}

private fun sequenceDistance(from: Int, to: Int): Int = (to - from + 256) % 256

private fun sequenceIsComplete(solve: SolveRecord, moves: List<RecordedMove>): Boolean {
    if (moves.isEmpty() || moves.any { it.sequence == null || it.gap }) return false
    val sequences = moves.mapNotNull { it.sequence }
    if (sequences.zipWithNext().any { (previous, current) -> sequenceDistance(previous, current) > 128 }) return false
    val start = solve.startSequence ?: return false
    val end = solve.endSequence ?: return false
    val firstDistance = sequenceDistance(start, sequences.first())
    // One A5 notification can contain several historical moves. Older app
    // versions stored the packet's final sequence on every move in that
    // burst, so the number of distinct sequence values is not the number of
    // physical turns. The device counter advances once per turn: compare the
    // start/end counter distance with the actual recorded move count instead.
    return firstDistance in 1..minOf(moves.size, 128) &&
        sequenceDistance(sequences.last(), end) == 0 &&
        sequenceDistance(start, end) == moves.size.mod(256)
}

private data class RecoveredReplay(
    val moves: List<RecordedMove>,
    val states: List<String>,
    val recoveredOrdinal: Int
)

private val recoverableFaceMoves: List<com.cubetrace.app.core.cube.CubeMove> by lazy {
    // One V10 A5 counter step is one quarter turn. A physical 180-degree
    // turn arrives as two events and therefore cannot fill one missing count.
    listOf("U", "U'", "R", "R'", "F", "F'", "D", "D'", "L", "L'", "B", "B'")
        .mapNotNull { MoveParser.parse(it).moves.singleOrNull() }
}

/**
 * Locates exactly one missing device counter value. Recovery deliberately
 * excludes legacy duplicate-sequence bursts and multi-step gaps: those do not
 * contain enough evidence to infer a physical turn safely.
 */
private fun singleMissingSequencePosition(
    solve: SolveRecord,
    moves: List<RecordedMove>
): Pair<Int, Int>? {
    if (moves.isEmpty() || moves.any { it.sequence == null || it.gap }) return null
    val start = solve.startSequence ?: return null
    val end = solve.endSequence ?: return null
    if (sequenceDistance(start, end) != (moves.size + 1).mod(256)) return null
    val sequences = moves.mapNotNull { it.sequence }
    val missing = mutableListOf<Pair<Int, Int>>()

    when (sequenceDistance(start, sequences.first())) {
        1 -> Unit
        2 -> missing += 0 to (start + 1).mod(256)
        else -> return null
    }
    sequences.zipWithNext().forEachIndexed { index, (previous, current) ->
        when (sequenceDistance(previous, current)) {
            1 -> Unit
            2 -> missing += (index + 1) to (previous + 1).mod(256)
            else -> return null
        }
    }
    when (sequenceDistance(sequences.last(), end)) {
        0 -> Unit
        1 -> missing += moves.size to end
        else -> return null
    }
    return missing.singleOrNull()
}

private fun replayCanonicalMoves(
    initial: CubeState,
    moves: List<RecordedMove>
): List<String>? {
    var state = initial
    return buildList {
        add(state.asFacelets())
        moves.forEach { recorded ->
            val move = MoveParser.parse(recorded.code).moves.singleOrNull() ?: return null
            state = state.apply(move)
            add(state.asFacelets())
        }
    }
}

/**
 * Repairs one dropped V10 move only when all 12 legal quarter turns are tested
 * and exactly one of them reproduces the saved final checkpoint. The raw
 * database remains untouched; the inferred move exists only in this replay.
 */
private fun recoverSingleMissingMove(
    solve: SolveRecord,
    initial: CubeState,
    observedMoves: List<RecordedMove>
): RecoveredReplay? {
    if (solve.source != SolveSource.V10_AI) return null
    val endFacelets = solve.endFacelets ?: return null
    val (insertAt, missingSequence) = singleMissingSequencePosition(solve, observedMoves) ?: return null
    val beforeElapsed = observedMoves.getOrNull(insertAt - 1)?.elapsedMs ?: 0L
    val afterElapsed = observedMoves.getOrNull(insertAt)?.elapsedMs ?: solve.durationMs
    val inferredElapsed = beforeElapsed + (afterElapsed - beforeElapsed).coerceAtLeast(0L) / 2L
    val beforeReceived = observedMoves.getOrNull(insertAt - 1)?.receivedAtElapsedMs
    val afterReceived = observedMoves.getOrNull(insertAt)?.receivedAtElapsedMs
    val inferredReceived = if (beforeReceived != null && afterReceived != null) {
        beforeReceived + (afterReceived - beforeReceived).coerceAtLeast(0L) / 2L
    } else null

    val matches = recoverableFaceMoves.mapNotNull { candidate ->
        val inferred = RecordedMove(
            ordinal = insertAt,
            code = candidate.normalized,
            elapsedMs = inferredElapsed,
            sequence = missingSequence,
            deviceTimeMs = null,
            receivedAtElapsedMs = inferredReceived,
            timeQuality = MoveTimeQuality.ESTIMATED
        )
        val expanded = buildList {
            addAll(observedMoves.take(insertAt))
            add(inferred)
            addAll(observedMoves.drop(insertAt))
        }.mapIndexed { index, move -> move.copy(ordinal = index) }
        if (!sequenceIsComplete(solve, expanded)) return@mapNotNull null
        val states = replayCanonicalMoves(initial, expanded) ?: return@mapNotNull null
        if (states.last() == endFacelets) RecoveredReplay(expanded, states, insertAt) else null
    }
    return matches.singleOrNull()
}

/**
 * Detects the first visible CFOP milestones in a replay.
 *
 * The solve orientation is deliberately not taken from SolveRecord.crossFace:
 * a user may solve white, yellow, red, or any other face as the cross. Every
 * face is evaluated as a candidate, then the candidate with the strongest
 * independently observable F2L -> OLL -> solved chain is selected. This keeps
 * an accidental early cross on an unrelated face from locking the analysis to
 * the wrong bottom color. Boundaries within the selected chain are always the
 * first observed state, even if a later move temporarily changes that stage.
 */
private fun detectionForFace(states: List<String>, face: Char): CfopDetection? {
    val crossEnd = states.indices.firstOrNull { isCrossSolvedOn(states[it], face) } ?: return null
    // F1/F2/F3/F4 are the first state in which each individual slot is
    // complete after the first observed cross.  Several slots may complete
    // on the same move, which is represented by equal state indexes.
    val slotEndings = (0..3).map { slot ->
        (crossEnd..states.lastIndex).firstOrNull { index ->
            slot in f2lSolvedSlotsOn(states[index], face)
        }
    }
    val f2lEnd = (crossEnd..states.lastIndex).firstOrNull { index ->
        isF2lSolvedOn(states[index], face)
    }

    val ollEnd = f2lEnd?.let { start ->
        (start..states.lastIndex).firstOrNull { index ->
            isLastLayerOrientedOn(states[index], face)
        }
    }
    val solvedEnd = ollEnd?.let { start ->
        (start..states.lastIndex).firstOrNull { index ->
            isCubeSolvedRelativeToCenters(states[index])
        }
    }

    return CfopDetection(
        crossFace = face,
        crossEnd = crossEnd,
        f2lEnd = f2lEnd,
        ollEnd = ollEnd,
        solvedEnd = solvedEnd,
        slotEndings = slotEndings
    )
}

private fun CfopDetection.distinctMilestoneCount(): Int =
    (listOfNotNull(crossEnd, f2lEnd, ollEnd, solvedEnd) + slotEndings.filterNotNull()).distinct().size

private fun detectCfop(states: List<String>): CfopDetection {
    val empty = CfopDetection(null, null, null, null, null, List(4) { null })
    if (states.isEmpty()) return empty
    val candidates = cfopCrossFaces().mapNotNull { detectionForFace(states, it) }
    return candidates.minWithOrNull(
        compareByDescending<CfopDetection> {
            it.ollEnd != null && it.solvedEnd != null && it.ollEnd < it.solvedEnd
        }.thenByDescending {
            it.f2lEnd != null && it.solvedEnd != null && it.f2lEnd < it.solvedEnd
        }.thenByDescending {
            it.distinctMilestoneCount()
        }.thenBy {
            it.ollEnd ?: Int.MAX_VALUE
        }.thenBy {
            it.f2lEnd ?: Int.MAX_VALUE
        }.thenBy {
            it.crossEnd ?: Int.MAX_VALUE
        }
    ) ?: empty
}

private fun replayValidation(solve: SolveRecord): ReplayValidation {
    if (solve.moves.isEmpty()) return ReplayValidation(false, AnalysisReasonCode.NO_MOVES)
    val scrambleMoves = parseCanonicalScramble(solve.scramble)
        ?: return ReplayValidation(false, AnalysisReasonCode.UNPARSEABLE_SCRAMBLE)
    val ordered = solve.moves.sortedBy { it.ordinal }
    if (ordered.map { it.ordinal } != ordered.indices.toList()) {
        return ReplayValidation(false, AnalysisReasonCode.SEQUENCE_GAP)
    }
    if (ordered.any { it.gap }) {
        return ReplayValidation(false, AnalysisReasonCode.SEQUENCE_GAP)
    }
    val observedMoves = mutableListOf<RecordedMove>()
    val initialState = CubeState.solved().apply(scrambleMoves)
    var state = initialState
    val observedStates = mutableListOf(state.asFacelets())
    for (recorded in ordered) {
        val parsed = parseCanonicalMove(recorded.code)
            ?: return ReplayValidation(false, AnalysisReasonCode.UNPARSEABLE_MOVE, observedMoves, observedStates)
        state = state.apply(parsed)
        observedStates += state.asFacelets()
        observedMoves += recorded.copy(code = parsed.normalized)
    }
    val timeMonotonic = observedMoves.all { it.elapsedMs in 0L..solve.durationMs } &&
        observedMoves.zipWithNext().all { (previous, current) -> current.elapsedMs >= previous.elapsedMs }
    if (!timeMonotonic) {
        return ReplayValidation(false, AnalysisReasonCode.TIME_NON_MONOTONIC, observedMoves, observedStates, timeMonotonic = false)
    }
    val startExpected = observedStates.first()
    val startMatches = solve.startFacelets != null && solve.startFacelets == startExpected
    if (solve.startFacelets == null || solve.endFacelets == null) {
        return ReplayValidation(
            false,
            AnalysisReasonCode.CHECKPOINT_MISSING,
            observedMoves,
            observedStates,
            startMatches = startMatches,
            timeMonotonic = true,
            sequenceComplete = sequenceIsComplete(solve, observedMoves)
        )
    }
    if (!startMatches) {
        return ReplayValidation(
            false,
            AnalysisReasonCode.START_STATE_MISMATCH,
            observedMoves,
            observedStates,
            startMatches = false,
            timeMonotonic = true,
            sequenceComplete = sequenceIsComplete(solve, observedMoves),
            checkpointComplete = true
        )
    }

    var parsedMoves: List<RecordedMove> = observedMoves
    var states: List<String> = observedStates
    var recoveredMoveCount = 0
    var recoveredOrdinals: Set<Int> = emptySet()
    if (!sequenceIsComplete(solve, observedMoves)) {
        val recovered = recoverSingleMissingMove(solve, initialState, observedMoves)
            ?: return ReplayValidation(
                false,
                AnalysisReasonCode.SEQUENCE_GAP,
                observedMoves,
                observedStates,
                startMatches = true,
                timeMonotonic = true,
                checkpointComplete = true
            )
        parsedMoves = recovered.moves
        states = recovered.states
        recoveredMoveCount = 1
        recoveredOrdinals = setOf(recovered.recoveredOrdinal)
    }
    val finalMatches = solve.endFacelets == states.last()
    if (!finalMatches) {
        return ReplayValidation(false, AnalysisReasonCode.FINAL_STATE_MISMATCH, parsedMoves, states, startMatches = true, finalMatches = false, finalSolved = false, timeMonotonic = true, sequenceComplete = true, checkpointComplete = true, recoveredMoveCount = recoveredMoveCount, recoveredOrdinals = recoveredOrdinals)
    }
    val finalSolved = isCubeSolvedRelativeToCenters(states.last())
    if (!finalSolved || solve.completeness != Completeness.COMPLETE || parsedMoves.any { it.gap }) {
        return ReplayValidation(false, if (!finalSolved) AnalysisReasonCode.SOLVED_NOT_FOUND else AnalysisReasonCode.SEQUENCE_GAP, parsedMoves, states, startMatches = true, finalMatches = true, finalSolved = finalSolved, timeMonotonic = true, sequenceComplete = true, checkpointComplete = true, recoveredMoveCount = recoveredMoveCount, recoveredOrdinals = recoveredOrdinals)
    }
    return ReplayValidation(true, parsedMoves = parsedMoves, states = states, startMatches = true, finalMatches = true, finalSolved = true, sequenceComplete = true, timeMonotonic = true, checkpointComplete = true, recoveredMoveCount = recoveredMoveCount, recoveredOrdinals = recoveredOrdinals)
}

private fun metricUnavailable(code: PhaseCode, availability: PhaseAvailability = PhaseAvailability.UNAVAILABLE): PhaseMetric =
    PhaseMetric(code, 0L, 0L, 0, 0, MetricSummary(0L, 0, 0L, 0.0, null, null, null), 0.0, availability = availability)

/** Rebuilds CFOP only when the saved facts prove the corresponding boundary. */
fun analyzeSolve(solve: SolveRecord, pauseThresholdMs: Int): SolveAnalysis? {
    val validation = replayValidation(solve)
    val states = validation.states
    val moves = validation.parsedMoves
    val detection = detectCfop(states)
    val boundaries = listOfNotNull(detection.crossEnd, detection.f2lEnd, detection.ollEnd, detection.solvedEnd)
    val monotonic = boundaries.zipWithNext().all { (start, end) -> end >= start }
    val fullBoundaries = detection.crossEnd != null && detection.f2lEnd != null &&
        detection.ollEnd != null && detection.solvedEnd != null && detection.slotEndings.all { it != null }
    val boundaryComplete = validation.valid && monotonic && fullBoundaries
    val totalFactsReliable = moves.isNotEmpty() && validation.timeMonotonic && validation.sequenceComplete &&
        moves.none { it.gap } && validation.recoveredMoveCount == 0
    val rawTotal = if (moves.isNotEmpty()) metricSummary(moves, 0, moves.size, 0L, solve.durationMs, pauseThresholdMs) else MetricSummary(solve.durationMs.coerceAtLeast(0L), 0, 0L, 0.0, null, null, null)
    val total = when {
        totalFactsReliable -> rawTotal
        validation.valid && validation.recoveredMoveCount > 0 -> rawTotal.copy(
            pauseMs = 0L,
            pauseRate = 0.0,
            totalTps = null,
            activeTps = null,
            longestGapMs = null
        )
        else -> rawTotal.copy(moveCount = 0, pauseMs = 0L, pauseRate = 0.0, totalTps = null, activeTps = null, longestGapMs = null)
    }

    val reasonCode = when {
        validation.reasonCode != null -> validation.reasonCode
        !monotonic -> AnalysisReasonCode.NON_MONOTONIC_BOUNDARIES
        detection.crossEnd == null -> AnalysisReasonCode.CROSS_NOT_FOUND
        detection.f2lEnd == null || detection.slotEndings.any { it == null } -> AnalysisReasonCode.F2L_NOT_FOUND
        detection.ollEnd == null -> AnalysisReasonCode.OLL_NOT_FOUND
        detection.solvedEnd == null -> AnalysisReasonCode.SOLVED_NOT_FOUND
        !fullBoundaries -> AnalysisReasonCode.NON_CFOP_PROGRESSION
        else -> null
    }

    fun stateTime(index: Int): Long = when {
        index <= 0 -> 0L
        index - 1 in moves.indices -> moves[index - 1].elapsedMs
        else -> solve.durationMs.coerceAtLeast(0L)
    }

    val evidenceConfidence = if (validation.recoveredMoveCount > 0) 0.90 else 0.97
    val phases = if (!validation.checkpointComplete || !validation.startMatches ||
        !validation.sequenceComplete || !validation.timeMonotonic || moves.any { it.gap } ||
        validation.reasonCode in setOf(
            AnalysisReasonCode.START_STATE_MISMATCH,
            AnalysisReasonCode.FINAL_STATE_MISMATCH,
            AnalysisReasonCode.CHECKPOINT_MISSING,
            AnalysisReasonCode.UNPARSEABLE_SCRAMBLE,
            AnalysisReasonCode.UNPARSEABLE_MOVE,
            AnalysisReasonCode.SEQUENCE_GAP,
            AnalysisReasonCode.TIME_NON_MONOTONIC
        )) {
        PhaseCode.entries.map(::metricUnavailable)
    } else {
        val cross = detection.crossEnd
        val slots = detection.slotEndings.filterNotNull().sorted()
        val f2l = detection.f2lEnd
        val oll = detection.ollEnd
        val solved = detection.solvedEnd
        fun availability(code: PhaseCode, start: Int, end: Int): PhaseAvailability = when {
            end != start -> PhaseAvailability.AVAILABLE
            code == PhaseCode.C -> PhaseAvailability.PROVEN_SKIP
            code == PhaseCode.F1 || code == PhaseCode.F2 || code == PhaseCode.F3 || code == PhaseCode.F4 -> PhaseAvailability.SAME_MOVE_COMPLETION
            else -> PhaseAvailability.PROVEN_SKIP
        }
        if (cross == null) {
            PhaseCode.entries.map(::metricUnavailable)
        } else {
            val phaseEnds = if (slots.size == 4 && f2l != null && oll != null && solved != null) {
                listOf(cross) + slots + listOf(oll, solved)
            } else null
            if (phaseEnds == null || phaseEnds.zipWithNext().any { (start, end) -> end < start }) {
                PhaseCode.entries.map(::metricUnavailable)
            } else {
                buildList {
                    var startState = 0
                    PhaseCode.entries.forEachIndexed { index, code ->
                        val endState = phaseEnds[index]
                        val startMs = stateTime(startState)
                        val endMs = if (code == PhaseCode.P) solve.durationMs else stateTime(endState)
                        val baseAvailability = availability(code, startState, endState)
                        val containsRecoveredMove = validation.recoveredOrdinals.any { it in startState until endState }
                        val estimatedBoundaryTime = validation.recoveredOrdinals.any {
                            it in startState until endState || it == startState - 1
                        }
                        val phaseAvailability = if (estimatedBoundaryTime) {
                            PhaseAvailability.GAP_AFFECTED
                        } else {
                            baseAvailability
                        }
                        val flags = buildSet {
                            if (baseAvailability == PhaseAvailability.PROVEN_SKIP) add("SKIP")
                            if (baseAvailability == PhaseAvailability.SAME_MOVE_COMPLETION) add("SAME_MOVE_COMPLETION")
                            if (code in setOf(PhaseCode.F1, PhaseCode.F2, PhaseCode.F3, PhaseCode.F4) && endState == cross) add("X_CROSS_EMBEDDED")
                            if (containsRecoveredMove) add("RECOVERED_MOVE")
                            if (estimatedBoundaryTime) add("ESTIMATED_TIME")
                        }
                        add(
                            PhaseMetric(
                                code = code,
                                startMs = startMs,
                                endMs = endMs,
                                startOrdinalExclusive = startState,
                                endOrdinalInclusive = endState,
                                summary = metricSummary(moves, startState, endState, startMs, endMs, pauseThresholdMs),
                                confidence = evidenceConfidence,
                                flags = flags,
                                availability = phaseAvailability
                            )
                        )
                        startState = endState
                    }
                }
            }
        }
    }
    val complete = boundaryComplete && phases.all { it.availability != PhaseAvailability.UNAVAILABLE }
    return SolveAnalysis(
        solveId = solve.id,
        analyzerVersion = ANALYZER_VERSION,
        status = if (complete) AnalysisStatus.COMPLETE else AnalysisStatus.INCOMPLETE,
        reason = reasonCode?.label,
        confidence = if (complete) {
            evidenceConfidence
        } else {
            phases.filter { it.availability != PhaseAvailability.UNAVAILABLE }.minOfOrNull { it.confidence } ?: 0.0
        },
        total = total,
        phases = phases,
        reasonCode = reasonCode,
        replay = validation,
        totalMetricsReliable = totalFactsReliable,
        recordedMoveCount = solve.moves.size,
        detectedCrossFace = detection.crossFace
    )
}

enum class SkillStatus(val label: String) {
    BUILDING("建立中"),
    PRELIMINARY("初步"),
    USABLE("可参考"),
    CALIBRATED("可靠"),
    FALLBACK("稳健基线")
}

data class IntervalForecast(
    val median: Double,
    val p50Low: Double,
    val p50High: Double,
    val p80Low: Double,
    val p80High: Double,
    val stable: Double,
    val formDelta: Double
)

data class ReproducibleInterval(
    val median: Double,
    val p50Low: Double,
    val p50High: Double
)

/**
 * A reproducible level deliberately excludes the short-term form component.
 * The uncertainty width is retained from the calibrated forecast, but centred
 * on the long-term state so one unusually fast or slow latest solve cannot
 * dominate the large number shown in Records.
 */
fun IntervalForecast.reproducibleInterval(): ReproducibleInterval {
    val safeMedian = median.coerceAtLeast(0.001)
    val lowerRatio = (p50Low / safeMedian).coerceIn(0.05, 1.0)
    val upperRatio = (p50High / safeMedian).coerceAtLeast(1.0)
    return ReproducibleInterval(
        median = stable,
        p50Low = stable * lowerRatio,
        p50High = stable * upperRatio
    )
}

data class SkillForecast(
    val timeMs: IntervalForecast,
    val moves: IntervalForecast,
    val pauseRate: IntervalForecast,
    val practicalTps: Double,
    val activeTps: Double
)

data class SkillPhaseForecast(
    val code: PhaseCode,
    val forecast: SkillForecast?,
    val sampleCount: Int,
    val status: SkillStatus
)

data class SkillEstimate(
    val modelVersion: String = MODEL_VERSION,
    val sampleCount: Int = 0,
    val reliableCount: Int = 0,
    val status: SkillStatus = SkillStatus.BUILDING,
    val total: SkillForecast? = null,
    val phases: List<SkillPhaseForecast> = emptyList(),
    val recentDeltaMs: Double? = null,
    val lastUpdatedAt: Long? = null,
    val backtestSummary: String = "样本不足，尚未回测"
) {
    val reproducibleTime: ReproducibleInterval? get() = total?.timeMs?.reproducibleInterval()
}

private enum class Transform { LOG, LOGIT }

private data class Observation(val at: Long, val value: Double, val reliability: Double)

private data class FilterResult(
    val forecast: IntervalForecast,
    val transformedMedian: Double,
    val modelMae: Double,
    val baselineMae: Double,
    val coverage80: Double
)

private fun transform(value: Double, kind: Transform): Double = when (kind) {
    Transform.LOG -> ln(value.coerceAtLeast(0.001))
    Transform.LOGIT -> {
        val p = value.coerceIn(0.001, 0.999)
        ln(p / (1.0 - p))
    }
}

private fun inverse(value: Double, kind: Transform): Double = when (kind) {
    Transform.LOG -> exp(value)
    Transform.LOGIT -> 1.0 / (1.0 + exp(-value))
}

private fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
}

private fun scalarFilter(observations: List<Observation>, kind: Transform): FilterResult? {
    if (observations.isEmpty()) return null
    val transformed = observations.map { transform(it.value, kind) }
    var stable = transformed.first()
    var form = 0.0
    var aa = 0.10
    var af = 0.0
    var ff = 0.14
    var previousAt = observations.first().at
    val predictions = mutableListOf<Double>()
    val scales = mutableListOf<Double>()

    observations.forEachIndexed { index, observation ->
        if (index > 0) {
            val gapDays = ((observation.at - previousAt).coerceAtLeast(0L) / DAY_MS.toDouble()).coerceAtMost(30.0)
            val decay = exp(-gapDays / 14.0)
            form *= decay
            af *= decay
            ff = ff * decay * decay + 0.025
            aa += 0.004
        }
        previousAt = observation.at
        val observed = transformed[index]
        val mean = stable + form
        val baseNoise = if (kind == Transform.LOGIT) 0.24 else 0.16
        val priorVariance = (aa + ff + 2.0 * af).coerceAtLeast(0.0001)
        val residual = observed - mean
        val z = residual / sqrt(priorVariance + baseNoise * baseNoise)
        val huberWeight = min(1.0, 1.25 / abs(z).coerceAtLeast(1.0))
        val robustResidual = residual * huberWeight
        val effectiveNoise = baseNoise * baseNoise / (huberWeight * huberWeight * observation.reliability.coerceIn(0.1, 1.0))
        val innovation = (aa + ff + 2.0 * af + effectiveNoise).coerceAtLeast(0.0001)
        val ka = (aa + af) / innovation
        val kf = (af + ff) / innovation
        stable += ka * robustResidual
        form += kf * robustResidual
        val oldAf = af
        aa = (aa - ka * (aa + af)).coerceAtLeast(0.000001)
        af = (af - ka * (oldAf + ff)).coerceIn(-0.49, 0.49)
        ff = (ff - kf * (oldAf + ff)).coerceAtLeast(0.000001)
        if (index > 0) {
            predictions += mean
            scales += sqrt(priorVariance + effectiveNoise)
        }
    }

    val latestMean = stable + form
    val latestScale = sqrt((aa + ff + 2.0 * af + 0.12).coerceAtLeast(0.0001))
    val z50 = 0.6745
    val z80 = 1.2816
    val forecast = IntervalForecast(
        median = inverse(latestMean, kind),
        p50Low = inverse(latestMean - z50 * latestScale, kind),
        p50High = inverse(latestMean + z50 * latestScale, kind),
        p80Low = inverse(latestMean - z80 * latestScale, kind),
        p80High = inverse(latestMean + z80 * latestScale, kind),
        stable = inverse(stable, kind),
        formDelta = inverse(latestMean, kind) - inverse(stable, kind)
    )
    val modelErrors = predictions.zip(observations.drop(1)).map { (prediction, observation) ->
        abs(prediction - transform(observation.value, kind))
    }
    val baselineErrors = observations.drop(1).mapIndexed { index, observation ->
        val previous = transformed.take(index + 1).takeLast(12)
        abs(transformed[index + 1] - (median(previous) ?: transformed[index]))
    }
    val modelMae = modelErrors.averageOrZero()
    val baselineMae = baselineErrors.averageOrZero()
    val coverage80 = observations.drop(1).mapIndexed { index, observation ->
        val prediction = predictions.getOrNull(index) ?: return@mapIndexed true
        val scale = scales.getOrNull(index) ?: latestScale
        val actual = transformed[index + 1]
        actual in (prediction - z80 * scale)..(prediction + z80 * scale)
    }.count { it }.toDouble() / max(1, observations.size - 1)
    return FilterResult(forecast, latestMean, modelMae, baselineMae, coverage80)
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

private fun reliabilityFor(solve: SolveRecord, analysis: SolveAnalysis): Double = when {
    solve.penalty == Penalty.DNF -> 0.0
    solve.completeness != Completeness.COMPLETE -> 0.35
    analysis.status != AnalysisStatus.COMPLETE -> 0.45
    (analysis.replay?.recoveredMoveCount ?: 0) > 0 -> 0.0
    solve.penalty == Penalty.PLUS_TWO -> 0.95
    else -> 1.0
}

private fun forecastFor(
    observations: List<Observation>,
    time: List<Observation>,
    moves: List<Observation>,
    pauses: List<Observation>
): SkillForecast? {
    val timeResult = scalarFilter(time, Transform.LOG) ?: return null
    val moveResult = scalarFilter(moves, Transform.LOG) ?: return null
    val pauseResult = scalarFilter(pauses, Transform.LOGIT) ?: return null
    val practical = moveResult.forecast.median / (timeResult.forecast.median / 1_000.0).coerceAtLeast(0.001)
    val activeSeconds = (timeResult.forecast.median * (1.0 - pauseResult.forecast.median)).coerceAtLeast(1.0) / 1_000.0
    val active = moveResult.forecast.median / activeSeconds
    return SkillForecast(timeResult.forecast, moveResult.forecast, pauseResult.forecast, practical, active)
}

/**
 * CTSS-1: a small deterministic robust two-timescale filter. It is rebuilt
 * from confirmed smart solves, so deleting this cache never deletes facts.
 */
object Ctss1Estimator {
    fun estimate(records: List<SolveRecord>, pauseThresholdMs: Int): SkillEstimate {
        val samples = records.asSequence()
            .filter { solve ->
                solve.source == SolveSource.V10_AI &&
                    solve.penalty != Penalty.DNF &&
                    solve.completeness == Completeness.COMPLETE
            }
            .sortedBy { it.startedAt }
            .mapNotNull { solve ->
                val analysis = analyzeSolve(solve, pauseThresholdMs) ?: return@mapNotNull null
                // CTSS is a historical model, not a repair pass.  A gap,
                // missing checkpoint or unavailable phase must not enter the
                // phase or total observations as a softened fake sample.
                if (analysis.status != AnalysisStatus.COMPLETE ||
                    !analysis.totalMetricsReliable ||
                    analysis.phases.any { it.availability == PhaseAvailability.UNAVAILABLE || it.availability == PhaseAvailability.GAP_AFFECTED }
                ) return@mapNotNull null
                val reliability = reliabilityFor(solve, analysis)
                if (reliability <= 0.0) return@mapNotNull null
                solve to analysis
            }
            .toList()
        val sampleCount = samples.size
        if (sampleCount == 0) return SkillEstimate(sampleCount = 0, reliableCount = 0)
        val reliableCount = samples.count { it.second.status == AnalysisStatus.COMPLETE && it.first.completeness == Completeness.COMPLETE }
        val totalTime = samples.map { (solve, analysis) -> Observation(solve.startedAt, analysis.total.durationMs.toDouble().coerceAtLeast(1.0), reliabilityFor(solve, analysis)) }
        val totalMoves = samples.map { (solve, analysis) -> Observation(solve.startedAt, analysis.total.moveCount.toDouble().coerceAtLeast(1.0), reliabilityFor(solve, analysis)) }
        val totalPause = samples.map { (solve, analysis) -> Observation(solve.startedAt, analysis.total.pauseRate.coerceIn(0.001, 0.999), reliabilityFor(solve, analysis)) }
        val total = forecastFor(totalTime, totalTime, totalMoves, totalPause)
        val overallTimeResult = scalarFilter(totalTime, Transform.LOG)
        val status = when {
            sampleCount < 5 -> SkillStatus.BUILDING
            sampleCount < 12 -> SkillStatus.PRELIMINARY
            sampleCount < 30 -> SkillStatus.USABLE
            overallTimeResult != null && overallTimeResult.baselineMae > 0.0 && overallTimeResult.modelMae > overallTimeResult.baselineMae * 1.03 -> SkillStatus.FALLBACK
            overallTimeResult != null && overallTimeResult.coverage80 in 0.72..0.88 -> SkillStatus.CALIBRATED
            else -> SkillStatus.USABLE
        }
        val visibleTotal = total.takeIf { sampleCount >= 5 }
        val phaseForecasts = PhaseCode.entries.map { code ->
            val phaseSamples = samples.mapNotNull { (solve, analysis) ->
                val phase = analysis.phases.firstOrNull { it.code == code } ?: return@mapNotNull null
                if (phase.summary.durationMs <= 0L) return@mapNotNull null
                Triple(solve, analysis, phase)
            }
            // Time, move count and pause rate are one observation from the
            // same solve. Apply the same reliability to all three; otherwise
            // a +2 or other downgraded result could still contribute its
            // moves/pause at full weight while its time was discounted.
            val times = phaseSamples.map { (solve, analysis, phase) ->
                val reliability = reliabilityFor(solve, analysis)
                Observation(solve.startedAt, phase.summary.durationMs.toDouble().coerceAtLeast(1.0), reliability)
            }
            val moves = phaseSamples.map { (solve, analysis, phase) ->
                Observation(solve.startedAt, phase.summary.moveCount.toDouble().coerceAtLeast(1.0), reliabilityFor(solve, analysis))
            }
            val pauses = phaseSamples.map { (solve, analysis, phase) ->
                Observation(solve.startedAt, phase.summary.pauseRate.coerceIn(0.001, 0.999), reliabilityFor(solve, analysis))
            }
            val forecast = if (phaseSamples.size >= 5) forecastFor(times, times, moves, pauses) else null
            SkillPhaseForecast(code, forecast, phaseSamples.size, statusForCount(phaseSamples.size))
        }
        return SkillEstimate(
            modelVersion = MODEL_VERSION,
            sampleCount = sampleCount,
            reliableCount = reliableCount,
            status = status,
            total = visibleTotal,
            phases = phaseForecasts,
            recentDeltaMs = overallTimeResult?.forecast?.formDelta?.takeIf { sampleCount >= 12 },
            lastUpdatedAt = samples.lastOrNull()?.first?.startedAt,
            backtestSummary = overallTimeResult?.let { "滚动回测 ${"%.0f".format(it.coverage80 * 100)}% 覆盖 · 模型与最近 12 次基线自动比较" }
                ?: "样本不足，尚未回测"
        )
    }

    private fun statusForCount(count: Int): SkillStatus = when {
        count < 5 -> SkillStatus.BUILDING
        count < 12 -> SkillStatus.PRELIMINARY
        count < 30 -> SkillStatus.USABLE
        else -> SkillStatus.CALIBRATED
    }
}
