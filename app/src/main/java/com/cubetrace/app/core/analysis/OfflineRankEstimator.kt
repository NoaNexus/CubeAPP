// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

data class OfflineRankRange(
    val bestRank: Int,
    val worstRank: Int
)

data class OfflineRankEstimate(
    val world: OfflineRankRange,
    val china: OfflineRankRange,
    val asOf: String = OfflineWcaRankEstimator.AS_OF,
    val sourceLabel: String = "WCA 三阶平均成绩离线对照"
)

/**
 * Versioned, offline rank bands derived from WCA ranks_average for event 333.
 * The app never sends a solve to WCA and never presents this as an official
 * personal rank: CTSS reproducible-time intervals are only compared with the
 * published average-time distribution.
 */
object OfflineWcaRankEstimator {
    const val AS_OF = "2026-08-24"
    const val SOURCE_VERSION = "WCA-export-v2.236"

    private data class Anchor(val timeMs: Int, val worldRank: Int, val chinaRank: Int)

    private val anchors = listOf(
        Anchor(4_000, 2, 2),
        Anchor(5_000, 17, 11),
        Anchor(6_000, 120, 25),
        Anchor(7_000, 588, 114),
        Anchor(8_000, 1_879, 283),
        Anchor(9_000, 4_416, 612),
        Anchor(10_000, 8_411, 1_175),
        Anchor(11_000, 13_590, 1_934),
        Anchor(12_000, 19_923, 2_824),
        Anchor(13_000, 27_174, 3_854),
        Anchor(15_000, 43_287, 6_114),
        Anchor(18_000, 68_191, 9_233),
        Anchor(20_000, 83_542, 10_864),
        Anchor(25_000, 118_266, 14_116),
        Anchor(30_000, 147_023, 16_448),
        Anchor(40_000, 190_218, 19_870),
        Anchor(60_000, 239_963, 23_880),
        Anchor(90_000, 266_621, 26_359),
        Anchor(120_000, 273_883, 27_078)
    )

    fun estimate(p50LowMs: Double, p50HighMs: Double): OfflineRankEstimate? {
        if (!p50LowMs.isFinite() || !p50HighMs.isFinite() || p50HighMs <= 0.0) return null
        val low = minOf(p50LowMs, p50HighMs).coerceAtLeast(1.0)
        val high = maxOf(p50LowMs, p50HighMs).coerceAtLeast(low)
        return OfflineRankEstimate(
            world = OfflineRankRange(
                bestRank = roundedRank(interpolate(low) { it.worldRank }),
                worstRank = roundedRank(interpolate(high) { it.worldRank })
            ),
            china = OfflineRankRange(
                bestRank = roundedRank(interpolate(low) { it.chinaRank }),
                worstRank = roundedRank(interpolate(high) { it.chinaRank })
            )
        )
    }

    private fun interpolate(timeMs: Double, rank: (Anchor) -> Int): Double {
        if (timeMs <= anchors.first().timeMs) return rank(anchors.first()).toDouble()
        if (timeMs >= anchors.last().timeMs) return rank(anchors.last()).toDouble()
        val upperIndex = anchors.indexOfFirst { timeMs <= it.timeMs }.coerceAtLeast(1)
        val lower = anchors[upperIndex - 1]
        val upper = anchors[upperIndex]
        val fraction = ((timeMs - lower.timeMs) / (upper.timeMs - lower.timeMs)).coerceIn(0.0, 1.0)
        val lowerLog = ln(rank(lower).coerceAtLeast(1).toDouble())
        val upperLog = ln(rank(upper).coerceAtLeast(1).toDouble())
        return exp(lowerLog + (upperLog - lowerLog) * fraction)
    }

    private fun roundedRank(value: Double): Int {
        val step = when {
            value < 1_000 -> 10
            value < 10_000 -> 100
            value < 100_000 -> 1_000
            else -> 5_000
        }
        return ((value / step).roundToInt() * step).coerceAtLeast(1)
    }
}
