// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.model

enum class Stage(val label: String) {
    CROSS("十字"),
    F2L("F2L"),
    OLL("OLL"),
    PLL("PLL")
}

enum class CaseFilter(val label: String) {
    ALL("全部"),
    DUE("到期"),
    WEAK("生疏"),
    FAVORITE("收藏")
}

enum class Penalty(val label: String) {
    NONE("保留"),
    PLUS_TWO("+2"),
    DNF("DNF")
}

enum class SolveSource(val label: String) {
    MANUAL("手动计时"),
    V10_AI("V10 AI")
}

enum class Completeness(val label: String) {
    COMPLETE("完整"),
    INCOMPLETE("不完整")
}

/** The source quality of a recorded move timestamp. */
enum class MoveTimeQuality {
    DEVICE,
    RECEIVE,
    ESTIMATED,
    UNKNOWN
}

enum class TrainingResult(val label: String) {
    WRONG("没认出"),
    HESITANT("迟疑"),
    CORRECT("熟练"),
    SKIPPED("跳过"),
    INCOMPLETE("不完整")
}

data class AlgorithmVariant(
    val id: String,
    val caseId: String,
    val notation: String,
    val normalizedMoves: List<String>,
    val sourceType: String = "预设",
    val verified: Boolean = true,
    val preferred: Boolean = false,
    val note: String = ""
)

data class CubeCase(
    val stableId: String,
    val stage: Stage,
    val number: Int,
    val name: String,
    val alias: String,
    val canonicalState: String,
    val orientationRule: String,
    val variant: AlgorithmVariant,
    val masteryBox: Int = 0,
    val dueAt: Long = 0L,
    val favorite: Boolean = false,
    val notes: String = ""
) {
    val masteryLabel: String
        get() = when (masteryBox) {
            0 -> "待熟悉"
            1 -> "初见"
            2 -> "生疏"
            3 -> "熟悉"
            4 -> "稳定"
            else -> "掌握"
        }
}

data class RecordedMove(
    val ordinal: Int,
    val code: String,
    val elapsedMs: Long,
    val sequence: Int? = null,
    val gap: Boolean = false,
    /** Device-side timestamp/offset when the protocol exposes one. */
    val deviceTimeMs: Long? = null,
    /** Monotonic receive timestamp, kept separate from solve-relative elapsedMs. */
    val receivedAtElapsedMs: Long? = null,
    val timeQuality: MoveTimeQuality = MoveTimeQuality.UNKNOWN
)

enum class CubeRotationAxis { X, Y, Z }

data class CubeRotationEvent(
    val ordinal: Int,
    val axis: CubeRotationAxis,
    /** Quarter-turn amount: 1, -1 or 2. */
    val amount: Int,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val confidence: Double
)

data class SolveRecord(
    val id: String,
    val sessionId: String,
    val sessionName: String,
    val scramble: String,
    val durationMs: Long,
    val startedAt: Long,
    val penalty: Penalty = Penalty.NONE,
    val source: SolveSource = SolveSource.MANUAL,
    val completeness: Completeness = Completeness.COMPLETE,
    val notes: String = "",
    val moves: List<RecordedMove> = emptyList(),
    /** Canonical app-frame facelets at the first physical move. */
    val startFacelets: String? = null,
    /** Canonical app-frame facelets at solve completion. */
    val endFacelets: String? = null,
    val startSequence: Int? = null,
    val endSequence: Int? = null,
    /** Cross face in the canonical app frame; white is D by default. */
    val crossFace: Char = 'D',
    /** True when gyro samples covered this solve, even if no rotation was detected. */
    val orientationTracked: Boolean = false,
    val rotationEvents: List<CubeRotationEvent> = emptyList()
)

data class AppSettings(
    val crossColor: String = "白",
    val notation: String = "WCA",
    val pauseThresholdMs: Int = 250,
    val inspectionEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val assistLabels: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val gyroFollowEnabled: Boolean = true,
    val smartCubeFrame: SmartCubeFrame = SmartCubeFrame.OFFICIAL_WHITE_GREEN,
    val smartAutoInspectionEnabled: Boolean = false,
    val recordChaseHintsEnabled: Boolean = true
)

enum class SmartCubeFrame(val label: String, val description: String) {
    OFFICIAL_WHITE_GREEN("白顶绿前", "官方打乱朝向"),
    PERSONAL_YELLOW_BLUE("黄顶蓝前", "个人复原朝向")
}

data class CubeStats(
    val count: Int,
    val bestMs: Long?,
    val averageMs: Long?,
    val ao5Ms: Long?,
    val ao12Ms: Long?
)

fun penaltyAdjustedMs(solve: SolveRecord): Long? = when (solve.penalty) {
    Penalty.NONE -> solve.durationMs
    Penalty.PLUS_TWO -> solve.durationMs + 2_000L
    Penalty.DNF -> null
}

fun displayPenalty(solve: SolveRecord): String = when (solve.penalty) {
    Penalty.NONE -> ""
    Penalty.PLUS_TWO -> "+2"
    Penalty.DNF -> "DNF"
}

fun formatDuration(ms: Long?): String {
    if (ms == null) return "DNF"
    val safe = ms.coerceAtLeast(0L)
    val minutes = safe / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    val millis = safe % 1_000L
    return if (minutes > 0) {
        "%d:%02d.%03d".format(minutes, seconds, millis)
    } else {
        "%d.%03d".format(seconds, millis)
    }
}

fun calculateAo(records: List<SolveRecord>, n: Int): Long? {
    if (records.size < n) return null
    // Callers provide chronological records. The current average is always
    // the window ending at the newest solve, not the oldest window in memory.
    val window = records.takeLast(n)
    val sorted = window.sortedWith(compareBy<SolveRecord> { penaltyAdjustedMs(it) == null }
        .thenBy { penaltyAdjustedMs(it) ?: Long.MAX_VALUE })
    val trim = kotlin.math.ceil(n * 0.05).toInt()
    val kept = sorted.drop(trim).dropLast(trim)
    if (kept.any { it.penalty == Penalty.DNF }) return null
    return kept.mapNotNull(::penaltyAdjustedMs).average().toLong()
}

fun calculateStats(records: List<SolveRecord>): CubeStats {
    val valid = records.mapNotNull(::penaltyAdjustedMs)
    return CubeStats(
        count = records.size,
        bestMs = valid.minOrNull(),
        averageMs = valid.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        ao5Ms = calculateAo(records, 5),
        ao12Ms = calculateAo(records, 12)
    )
}
