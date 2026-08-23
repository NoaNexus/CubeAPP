// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

import kotlin.math.max

private const val COACHING_RULE_VERSION = "COACH-1.0.0"

enum class CoachingScope { SOLVE, PHASE, TREND }

enum class CoachingPriority { PRIMARY, SECONDARY, INFO }

enum class CoachingRule {
    PAUSE_DOMINANT,
    EXECUTION_SLOW,
    MOVE_COUNT_HIGH,
    PHASE_TIME_HIGH,
    SINGLE_PAUSE_PEAK,
    PHASE_TIME_SHARE
}

data class CoachingEvidence(
    val phase: PhaseCode,
    val startMs: Long,
    val endMs: Long,
    val startOrdinalExclusive: Int,
    val endOrdinalInclusive: Int
)

/**
 * A structured, reproducible coaching result. UI copy is derived from [rule]
 * and the numeric parameters instead of being persisted as an opaque verdict.
 */
data class CoachingInsight(
    val insightId: String,
    val ruleVersion: String = COACHING_RULE_VERSION,
    val scope: CoachingScope,
    val priority: CoachingPriority,
    val rule: CoachingRule,
    val phase: PhaseCode,
    val observedValue: Double,
    val baselineValue: Double? = null,
    val opportunityMs: Long,
    val sampleCount: Int,
    val evidence: CoachingEvidence,
    val confidence: Double,
    val suggestedDrillId: String,
    val techniques: List<CoachingTechnique> = emptyList()
)

enum class SkillAssessmentState { BUILDING, IMPROVING, STEADY, ATTENTION }

data class SkillAssessment(
    val state: SkillAssessmentState,
    val title: String,
    val summary: String,
    val recommendation: String,
    val evidence: String,
    val focusPhase: PhaseCode? = null
)

private data class RankedInsight(val score: Double, val insight: CoachingInsight)

/**
 * Generates at most one primary and two secondary observations. A historical
 * comparison is only made when that phase has at least five reliable samples;
 * without a baseline the function emits one factual, single-solve observation.
 */
fun buildCoachingInsights(
    analysis: SolveAnalysis?,
    estimate: SkillEstimate?
): List<CoachingInsight> {
    if (analysis == null || analysis.status != AnalysisStatus.COMPLETE) return emptyList()
    val statusWeight = when (estimate?.status) {
        SkillStatus.CALIBRATED -> 1.0
        SkillStatus.USABLE, SkillStatus.FALLBACK -> 0.9
        SkillStatus.PRELIMINARY -> 0.78
        else -> 0.68
    }
    val ranked = analysis.phases.mapNotNull { phase ->
        if (phase.availability == PhaseAvailability.UNAVAILABLE ||
            phase.availability == PhaseAvailability.GAP_AFFECTED ||
            phase.summary.durationMs <= 0L
        ) {
            return@mapNotNull null
        }
        val historical = estimate?.phases?.firstOrNull { it.code == phase.code }
            ?.takeIf { it.sampleCount >= 5 && it.forecast != null }
            ?: return@mapNotNull null
        val forecast = historical.forecast ?: return@mapNotNull null
        val timeAboveRange = phase.summary.durationMs - forecast.timeMs.p50High.toLong()
        if (timeAboveRange < 150L) return@mapNotNull null

        val pauseGap = phase.summary.pauseRate - forecast.pauseRate.p50High
        val activeLowReference = forecast.moves.p50Low / (
            forecast.timeMs.p50High.coerceAtLeast(1.0) / 1_000.0 *
                (1.0 - forecast.pauseRate.p50Low).coerceAtLeast(0.05)
            )
        val activeIsLow = phase.summary.activeTps?.let { it < activeLowReference } == true
        val moveGap = phase.summary.moveCount - forecast.moves.p80High
        val rule = when {
            pauseGap >= 0.05 && !activeIsLow -> CoachingRule.PAUSE_DOMINANT
            activeIsLow && pauseGap < 0.05 -> CoachingRule.EXECUTION_SLOW
            moveGap >= 2.0 -> CoachingRule.MOVE_COUNT_HIGH
            else -> CoachingRule.PHASE_TIME_HIGH
        }
        val (observed, baseline, drill) = when (rule) {
            CoachingRule.PAUSE_DOMINANT -> Triple(phase.summary.pauseRate, forecast.pauseRate.median, "slow_turn_flow")
            CoachingRule.EXECUTION_SLOW -> Triple(phase.summary.activeTps ?: 0.0, activeLowReference, "phase_execution")
            CoachingRule.MOVE_COUNT_HIGH -> Triple(phase.summary.moveCount.toDouble(), forecast.moves.p80High, "move_efficiency")
            else -> Triple(phase.summary.durationMs.toDouble(), forecast.timeMs.median, "phase_targeted")
        }
        val scope = if (historical.sampleCount >= 12) CoachingScope.TREND else CoachingScope.PHASE
        val confidence = (phase.confidence * statusWeight).coerceIn(0.0, 1.0)
        RankedInsight(
            score = timeAboveRange * confidence,
            insight = CoachingInsight(
                insightId = "${analysis.solveId}:${phase.code.name}:${rule.name}",
                scope = scope,
                priority = CoachingPriority.INFO,
                rule = rule,
                phase = phase.code,
                observedValue = observed,
                baselineValue = baseline,
                opportunityMs = timeAboveRange,
                sampleCount = historical.sampleCount,
                evidence = phase.toEvidence(),
                confidence = confidence,
                suggestedDrillId = drill,
                techniques = buildTechniqueRecommendations(analysis, phase, estimate)
            )
        )
    }.sortedByDescending { it.score }

    if (ranked.isNotEmpty()) {
        return ranked.take(3).mapIndexed { index, item ->
            item.insight.copy(priority = if (index == 0) CoachingPriority.PRIMARY else CoachingPriority.SECONDARY)
        }
    }

    val measurable = analysis.phases.filter {
        it.availability != PhaseAvailability.UNAVAILABLE &&
            it.availability != PhaseAvailability.GAP_AFFECTED &&
            it.summary.durationMs > 0L
    }
    if (measurable.isEmpty()) return emptyList()
    val pausePhase = measurable.maxByOrNull { it.summary.longestGapMs ?: 0L }
    val pauseIsMaterial = pausePhase != null &&
        (pausePhase.summary.longestGapMs ?: 0L) >= max(250L, (analysis.total.durationMs * 0.08).toLong())
    val phase = if (pauseIsMaterial) pausePhase!! else measurable.maxBy { it.summary.durationMs }
    val rule = if (pauseIsMaterial) CoachingRule.SINGLE_PAUSE_PEAK else CoachingRule.PHASE_TIME_SHARE
    val observed = if (pauseIsMaterial) phase.summary.pauseRate else phase.summary.durationMs.toDouble()
    return listOf(
        CoachingInsight(
            insightId = "${analysis.solveId}:${phase.code.name}:${rule.name}",
            scope = CoachingScope.SOLVE,
            priority = CoachingPriority.PRIMARY,
            rule = rule,
            phase = phase.code,
            observedValue = observed,
            opportunityMs = 0L,
            sampleCount = 0,
            evidence = phase.toEvidence(),
            confidence = (phase.confidence * 0.72).coerceIn(0.0, 1.0),
            suggestedDrillId = if (pauseIsMaterial) "slow_turn_flow" else "phase_targeted",
            techniques = buildTechniqueRecommendations(analysis, phase, estimate)
        )
    )
}

private fun PhaseMetric.toEvidence() = CoachingEvidence(
    phase = code,
    startMs = startMs,
    endMs = endMs,
    startOrdinalExclusive = startOrdinalExclusive,
    endOrdinalInclusive = endOrdinalInclusive
)

/** Maps CTSS facts to cautious user-facing language without an opaque score. */
object SkillLevelPresenter {
    fun present(estimate: SkillEstimate): SkillAssessment {
        val total = estimate.total ?: return SkillAssessment(
            state = SkillAssessmentState.BUILDING,
            title = "个人基线正在建立",
            summary = "还需 ${max(0, 5 - estimate.sampleCount)} 次完整智能还原，才能评价当前可复现水平。",
            recommendation = "先按正常节奏完成还原，不需要刻意追求单次极限。",
            evidence = "当前 ${estimate.sampleCount} 个可靠样本 · ${estimate.status.label}"
        )
        if (estimate.sampleCount < 12) {
            return SkillAssessment(
                state = SkillAssessmentState.STEADY,
                title = "初步水平仍在收敛",
                summary = "当前数字采用稳健中位估计；满 12 次可靠样本后再判断近期提速或回落。",
                recommendation = "保持正常节奏继续完成智能还原，暂时不要根据一两次快慢调整训练方向。",
                evidence = "当前 ${estimate.sampleCount} 个可靠样本 · ${estimate.status.label}"
            )
        }
        val delta = estimate.recentDeltaMs ?: 0.0
        val state = when {
            delta <= -150.0 -> SkillAssessmentState.IMPROVING
            delta >= 150.0 -> SkillAssessmentState.ATTENTION
            else -> SkillAssessmentState.STEADY
        }
        val (title, summary) = when (state) {
            SkillAssessmentState.IMPROVING -> "近期状态正在提速" to
                "当前预测比长期稳定水平快约 ${deltaMagnitude(delta)}。"
            SkillAssessmentState.ATTENTION -> "近期状态有所回落" to
                "当前预测比长期稳定水平慢约 ${deltaMagnitude(delta)}，先找最集中的时间差。"
            SkillAssessmentState.STEADY -> "当前节奏接近长期水平" to
                "近期波动仍在个人常见范围内，适合针对最耗时阶段做小幅优化。"
            SkillAssessmentState.BUILDING -> error("handled above")
        }
        val focus = estimate.phases.asSequence()
            .filter { it.forecast != null && it.sampleCount >= 5 }
            .maxByOrNull { phase ->
                val forecast = phase.forecast!!
                max(0.0, forecast.timeMs.formDelta) / forecast.timeMs.median.coerceAtLeast(1.0)
            }
        val focusForecast = focus?.forecast
        val recommendation = when {
            focus == null || focusForecast == null -> "继续积累完整智能成绩，七段建议会随样本逐步出现。"
            focusForecast.pauseRate.median >= 0.20 ||
                focusForecast.pauseRate.median >= total.pauseRate.median + 0.05 ->
                "优先练 ${focus.code.label} 连贯性：做 5 分钟慢拧不断流，先减少阶段内停顿。"
            focusForecast.activeTps < total.activeTps * 0.90 ->
                "优先练 ${focus.code.label} 执行：选择常见 case 做短组重复，保持动作准确。"
            else -> "优先复盘 ${focus.code.label}：比较步数和阶段起始停顿，再选择定向练习。"
        }
        val evidence = if (focus != null && focusForecast != null) {
            "${focus.code.label} 预测 ${formatSeconds(focusForecast.timeMs.median)} · " +
                "80% ${formatSeconds(focusForecast.timeMs.p80Low)}–${formatSeconds(focusForecast.timeMs.p80High)} · " +
                "${focus.sampleCount} 个样本"
        } else {
            "总体 ${estimate.sampleCount} 个样本 · ${estimate.status.label}"
        }
        return SkillAssessment(
            state = state,
            title = title,
            summary = summary,
            recommendation = recommendation,
            evidence = evidence,
            focusPhase = focus?.code
        )
    }

    private fun deltaMagnitude(value: Double): String = formatSeconds(kotlin.math.abs(value))

    private fun formatSeconds(ms: Double): String = "%.3f 秒".format(ms.coerceAtLeast(0.0) / 1_000.0)
}
