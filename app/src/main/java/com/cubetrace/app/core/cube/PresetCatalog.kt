// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.cube

import com.cubetrace.app.core.model.AlgorithmVariant
import com.cubetrace.app.core.model.CubeCase
import com.cubetrace.app.core.model.Stage

/**
 * The built-in CFOP catalog. Formula text is kept separate from the explicit
 * recognition setup where the source library provides one; the canonical
 * state, flat diagram, and 3D playback all consume that same setup state.
 */
object PresetCatalog {
    const val VERSION = "yellow-top-blue-front-1.0.0-cuberoot-setup-states"
    const val SOURCE = "CubeRoot F2L/OLL setup states and primary algorithms; yellow top / blue front"

    fun all(): List<CubeCase> = buildList {
        addStage(Stage.F2L, CuratedFormulaPack.f2l, CuratedFormulaPack.f2lSetup, expectedCount = 41)
        addStage(Stage.OLL, CuratedFormulaPack.oll, CuratedFormulaPack.ollSetup, expectedCount = 57)
        addStage(Stage.PLL, CuratedFormulaPack.pll, expectedCount = 21)
    }

    private fun MutableList<CubeCase>.addStage(
        stage: Stage,
        formulas: List<String>,
        setups: List<String>? = null,
        expectedCount: Int
    ) {
        require(formulas.size == expectedCount) {
            "${stage.name} formula pack must contain $expectedCount cases"
        }
        require(setups == null || setups.size == expectedCount) {
            "${stage.name} setup pack must contain $expectedCount cases"
        }

        formulas.forEachIndexed { index, rawNotation ->
            val notation = normalizedMoves(rawNotation).joinToString(" ") { it.normalized }
            val number = index + 1
            val id = "cfop.${stage.name.lowercase()}.$number"
            val variant = AlgorithmVariant(
                id = "$id.v1",
                caseId = id,
                notation = notation,
                normalizedMoves = normalizedMoves(notation).map { it.normalized },
                sourceType = "标准 CFOP · 黄顶蓝前",
                verified = true,
                preferred = true,
                note = "黄顶蓝前持握；局面由 CubeEngine 对公式求逆生成，九宫格与三维演示共用该局面。"
            )
            add(
                CubeCase(
                    stableId = id,
                    stage = stage,
                    number = number,
                    name = "${stage.label} ${number.toString().padStart(2, '0')}",
                    alias = when (stage) {
                        Stage.F2L -> "F2L 基础案例 $number"
                        Stage.OLL -> "OLL 标准案例 $number"
                        Stage.PLL -> "PLL 标准案例 $number"
                        Stage.CROSS -> "十字基础"
                    },
                    canonicalState = setups?.get(index)?.let(::canonicalStateForSetup)
                        ?: if (stage == Stage.PLL) canonicalStateForPllAlgorithm(notation)
                        else canonicalStateForAlgorithm(notation),
                    orientationRule = "黄顶 / 蓝前；白色十字置于 D 面，可用 U / y 做观察调整",
                    variant = variant
                )
            )
        }
    }
}
