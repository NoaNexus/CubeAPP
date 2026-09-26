// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.cube

/** Dependency-free checks for case practice scrambles and catalog stability. */
object PracticeScrambleChecks {
    @JvmStatic
    fun main(args: Array<String>) {
        val cases = PresetCatalog.all()
        check(cases.size == 119) { "expected 119 cases, found ${cases.size}" }
        checkParserContract()
        val scrambles = cases.associate { cubeCase ->
            val scramble = practiceScrambleForCase(cubeCase)
                ?: error("no verified practice scramble for ${cubeCase.stableId}")
            val parsed = MoveParser.parse(scramble)
            check(parsed.error == null && parsed.moves.isNotEmpty()) {
                "${cubeCase.stableId} produced an unparsable scramble: ${parsed.error}"
            }
            check(parsed.moves.joinToString(" ") { it.normalized } == scramble) {
                "${cubeCase.stableId} scramble was not normalized: $scramble"
            }
            check(CubeState.solved().apply(parsed.moves).asFacelets() == cubeCase.canonicalState) {
                "${cubeCase.stableId} scramble did not reach its exact canonical state"
            }
            cubeCase.stableId to scramble
        }
        checkSelectedVariantDoesNotChangePosition(cases, scrambles)
        checkMismatchedCasesAreRejected(cases.first())
        println("PracticeScrambleChecks: 119 cases passed")
    }

    private fun checkParserContract() {
        val valid = MoveParser.parse("R U R'")
        check(valid.error == null)
        check(valid.moves.map { it.normalized } == listOf("R", "U", "R'"))
        check(MoveParser.parse("R ?").error != null)
    }

    private fun checkSelectedVariantDoesNotChangePosition(
        cases: List<com.cubetrace.app.core.model.CubeCase>,
        expectedScrambles: Map<String, String>
    ) {
        val alternateMoves = normalizedMoves("F R U R' U' F'").map { it.normalized }
        cases.forEach { cubeCase ->
            val selectedVariant = cubeCase.variant.copy(
                id = "${cubeCase.stableId}.selected",
                notation = alternateMoves.joinToString(" "),
                normalizedMoves = alternateMoves,
                preferred = false
            )
            val withAlternate = cubeCase.copy(variant = selectedVariant)
            check(practiceScrambleForCase(withAlternate) == expectedScrambles[cubeCase.stableId]) {
                "selected variant changed the practice position for ${cubeCase.stableId}"
            }
        }
    }

    private fun checkMismatchedCasesAreRejected(cubeCase: com.cubetrace.app.core.model.CubeCase) {
        check(practiceScrambleForCase(cubeCase.copy(stableId = "custom.case")) == null)
        check(practiceScrambleForCase(cubeCase.copy(canonicalState = CubeState.solved().asFacelets())) == null)
    }
}
