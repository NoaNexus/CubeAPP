// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

import com.cubetrace.app.core.cube.CubeMove
import com.cubetrace.app.core.cube.CubeState
import com.cubetrace.app.core.cube.PresetCatalog
import com.cubetrace.app.core.cube.isCubeSolvedRelativeToCenters
import com.cubetrace.app.core.cube.isF2lSolvedOn
import com.cubetrace.app.core.cube.isLastLayerOrientedOn
import com.cubetrace.app.core.cube.normalizedCfopFrames
import com.cubetrace.app.core.cube.normalizedMoves
import com.cubetrace.app.core.cube.yellowTopBlueFrontToOfficialMove
import com.cubetrace.app.core.model.CubeCase
import com.cubetrace.app.core.model.Stage
import kotlin.math.max

enum class CoachingTechniqueKind { FORMULA, START_PLAN, FINGER_PRACTICE }

/**
 * A concrete practice option derived from replay evidence. Formula tips name
 * only a uniquely matched recognition state; finger tips describe how to
 * practise that verified formula and never claim which finger the user used.
 */
data class CoachingTechnique(
    val kind: CoachingTechniqueKind,
    val title: String,
    val detail: String,
    val notation: String? = null,
    val caseId: String? = null,
    val confidence: Double
)

private data class RecognitionCase(val cubeCase: CubeCase)

private fun CubeCase.preferredFormulaReachesStageGoal(): Boolean {
    val moves = normalizedMoves(variant.notation)
    if (moves.isEmpty()) return false
    return aufVariants(canonicalState).any { alignedStart ->
        val end = CubeState.fromFacelets(alignedStart)?.apply(moves)?.asFacelets() ?: return@any false
        when (stage) {
            Stage.OLL -> isF2lSolvedOn(end, 'D') && isLastLayerOrientedOn(end, 'D')
            Stage.PLL -> isCubeSolvedRelativeToCenters(end)
            else -> false
        }
    }
}

private val ollCases by lazy {
    PresetCatalog.all().filter { it.stage == Stage.OLL && it.preferredFormulaReachesStageGoal() }
}
private val pllCases by lazy {
    PresetCatalog.all().filter { it.stage == Stage.PLL && it.preferredFormulaReachesStageGoal() }
}
private val orientationStickerIndexes = buildList {
    addAll(0..8)
    listOf(9, 18, 36, 45).forEach { start -> addAll(start..start + 2) }
}
private val sideTopStickerIndexes = buildList {
    listOf(9, 18, 36, 45).forEach { start -> addAll(start..start + 2) }
}

private fun ollSignature(facelets: String): String? {
    if (facelets.length != 54) return null
    val top = facelets.getOrNull(4) ?: return null
    return orientationStickerIndexes.joinToString("") { index ->
        if (facelets.getOrNull(index) == top) "1" else "0"
    }
}

private fun pllSignature(facelets: String): String? {
    if (facelets.length != 54 || !(0..8).all { facelets[it] == facelets[4] }) return null
    return sideTopStickerIndexes.mapNotNull(facelets::getOrNull)
        .takeIf { it.size == sideTopStickerIndexes.size }
        ?.joinToString("")
}

private fun aufVariants(facelets: String): List<String> {
    val source = CubeState.fromFacelets(facelets) ?: return emptyList()
    return (0..3).map { turns ->
        if (turns == 0) source.asFacelets() else source.apply(CubeMove("U", turns)).asFacelets()
    }
}

private fun recognizeLastLayerCase(
    analysis: SolveAnalysis,
    phase: PhaseMetric
): RecognitionCase? {
    if (phase.availability != PhaseAvailability.AVAILABLE) return null
    val replay = analysis.replay ?: return null
    val crossFace = analysis.detectedCrossFace ?: return null
    val startState = replay.states.getOrNull(phase.startOrdinalExclusive) ?: return null
    val stage = when (phase.code) {
        PhaseCode.O -> Stage.OLL
        PhaseCode.P -> Stage.PLL
        else -> return null
    }
    if (!isF2lSolvedOn(startState, crossFace)) return null
    if (stage == Stage.PLL && !isLastLayerOrientedOn(startState, crossFace)) return null
    val actualSignatures = normalizedCfopFrames(startState, crossFace)
        .flatMap(::aufVariants)
        .mapNotNull { state ->
            if (stage == Stage.OLL) ollSignature(state) else pllSignature(state)
        }
        .toSet()
    if (actualSignatures.isEmpty()) return null
    val catalog = if (stage == Stage.OLL) ollCases else pllCases
    val matches = catalog.filter { cubeCase ->
        val signature = if (stage == Stage.OLL) {
            ollSignature(cubeCase.canonicalState)
        } else {
            pllSignature(cubeCase.canonicalState)
        }
        signature != null && signature in actualSignatures
    }.distinctBy { it.stableId }
    return matches.singleOrNull()?.let(::RecognitionCase)
}

private fun phaseMoves(analysis: SolveAnalysis, phase: PhaseMetric): List<String> {
    val moves = analysis.replay?.parsedMoves ?: return emptyList()
    val start = phase.startOrdinalExclusive.coerceIn(0, moves.size)
    val end = phase.endOrdinalInclusive.coerceIn(start, moves.size)
    return moves.subList(start, end).map { move ->
        yellowTopBlueFrontToOfficialMove(move.code)
    }
}

private fun phaseEntryGapMs(analysis: SolveAnalysis, phase: PhaseMetric): Long? {
    val first = analysis.replay?.parsedMoves?.getOrNull(phase.startOrdinalExclusive) ?: return null
    return (first.elapsedMs - phase.startMs).coerceAtLeast(0L)
}

private fun containsTrigger(tokens: List<String>, trigger: List<String>): Boolean =
    tokens.windowed(trigger.size).any { it == trigger }

internal fun usesWideSliceOrRotation(tokens: List<String>): Boolean = tokens.any { token ->
    token.firstOrNull() in setOf('u', 'r', 'f', 'd', 'l', 'b', 'M', 'E', 'S', 'x', 'y', 'z')
}

private fun formulaFingerPractice(cubeCase: CubeCase, confidence: Double): CoachingTechnique {
    val moves = cubeCase.variant.normalizedMoves
    val (title, detail) = when {
        containsTrigger(moves, listOf("R", "U", "R'", "U'")) ->
            "指法练习 · 四步触发不换握" to
                "推荐公式含 R U R' U'。可把四步作为一组慢练，U / U' 用食指拨层，拇指保持前侧；这是公式练法，不是对你本次手指动作的判断。"
        moves.any { it == "U2" } ->
            "指法练习 · U2 连续双拨" to
                "推荐公式包含 U2。可尝试食指与中指连续双拨，并以不换握、贴片准确到位为优先；先慢后快。"
        usesWideSliceOrRotation(moves) ->
            "握法练习 · 先处理宽层与转体" to
                "这条公式含宽层、夹层或整体转体。先按 2–4 步分组确认握法，再连成整段，避免为了追 TPS 临时换握。"
        else ->
            "指法练习 · 两到四步分组" to
                "把推荐公式按 2–4 步触发段慢练，先做到整段不临时换握，再逐步提速；APP 不会把动作记号误当成你的真实手指轨迹。"
    }
    return CoachingTechnique(
        kind = CoachingTechniqueKind.FINGER_PRACTICE,
        title = title,
        detail = detail,
        confidence = confidence
    )
}

internal fun buildTechniqueRecommendations(
    analysis: SolveAnalysis,
    phase: PhaseMetric,
    estimate: SkillEstimate?
): List<CoachingTechnique> {
    if (analysis.status != AnalysisStatus.COMPLETE ||
        phase.availability == PhaseAvailability.UNAVAILABLE ||
        phase.availability == PhaseAvailability.GAP_AFFECTED
    ) {
        return emptyList()
    }
    val confidence = phase.confidence.coerceIn(0.0, 1.0)
    val result = mutableListOf<CoachingTechnique>()
    val moves = phaseMoves(analysis, phase)
    val containsRecoveredMove = "RECOVERED_MOVE" in phase.flags
    val phaseHistory = estimate?.phases?.firstOrNull { it.code == phase.code }
    val levelCue = when {
        phaseHistory?.sampleCount ?: 0 >= 12 -> "结合 ${phaseHistory?.sampleCount} 次个人阶段样本"
        estimate?.sampleCount ?: 0 >= 5 -> "个人基线仍在收敛"
        else -> "当前可靠样本较少"
    }

    if (!containsRecoveredMove) recognizeLastLayerCase(analysis, phase)?.cubeCase?.let { cubeCase ->
        result += CoachingTechnique(
            kind = CoachingTechniqueKind.FORMULA,
            title = "参考公式 · ${cubeCase.name}",
            detail = "本阶段起始局面的识别签名只匹配到这一案例。请先按公式库示意图对齐朝向再执行；这里只推荐适用公式，不声称你本次使用了它。$levelCue。",
            notation = cubeCase.variant.notation,
            caseId = cubeCase.stableId,
            confidence = confidence
        )
        result += formulaFingerPractice(cubeCase, confidence)
    }

    if (moves.isNotEmpty()) {
        val firstGroup = moves.take(3).joinToString(" ")
        val entryGap = phaseEntryGapMs(analysis, phase)
        val entryFact = entryGap?.takeIf { it >= 250L }?.let { "本段开始后 ${it} ms 才出现第一步。" } ?: ""
        val practice = when (phase.code) {
            PhaseCode.C -> "观察时先确定第一步与下一枚十字棱，起手前只预演前三步。"
            PhaseCode.F1, PhaseCode.F2, PhaseCode.F3, PhaseCode.F4 -> "上一组收尾前先寻找下一组，起手时一次读出前三步，避免边转边重新识别。"
            PhaseCode.O, PhaseCode.P -> "识别完成后先确认起手握姿与前三步，再开始整段执行。"
        }
        result += CoachingTechnique(
            kind = CoachingTechniqueKind.START_PLAN,
            title = "起手练习 · $firstGroup",
            detail = listOf(
                entryFact,
                practice,
                if (containsRecoveredMove) "本段含一个由前后局面唯一推定的漏传动作，起手建议仅按可确认片段生成。" else "",
                levelCue
            ).filter { it.isNotBlank() }.joinToString(" "),
            confidence = confidence
        )
    }

    if (!containsRecoveredMove && result.none { it.kind == CoachingTechniqueKind.FINGER_PRACTICE } && moves.any { it == "U2" }) {
        result += CoachingTechnique(
            kind = CoachingTechniqueKind.FINGER_PRACTICE,
            title = "指法练习 · U2 双拨",
            detail = "本阶段动作中出现 U2。可单独练习连续双拨，以不换握和准确到位为优先；这是对动作片段的练习建议，不是手指识别结果。",
            confidence = confidence
        )
    }
    return result.distinctBy { it.kind to it.title }.take(3)
}
