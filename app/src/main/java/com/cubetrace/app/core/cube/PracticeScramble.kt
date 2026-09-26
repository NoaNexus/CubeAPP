// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.cube

import com.cubetrace.app.core.model.CubeCase
import com.cubetrace.app.core.model.Stage
import java.util.ArrayDeque

private val practiceCatalogCases: List<CubeCase> by lazy { PresetCatalog.all() }

/**
 * Produces a replayable setup from a solved yellow-top / blue-front cube for a
 * built-in formula case. The case's recognition state is authoritative; the
 * currently selected formula variant never changes the practice position.
 */
fun practiceScrambleForCase(item: CubeCase): String? {
    val catalogCase = practiceCatalogCases.singleOrNull { it.stableId == item.stableId }
        ?: return null
    if (item.stage != catalogCase.stage ||
        item.number != catalogCase.number ||
        item.canonicalState != catalogCase.canonicalState
    ) {
        return null
    }

    val target = CubeState.fromFacelets(catalogCase.canonicalState) ?: return null
    val moves = when (catalogCase.stage) {
        Stage.F2L ->
            setupScramble(CuratedFormulaPack.f2lSetup, catalogCase.number, target)
        Stage.OLL ->
            setupScramble(CuratedFormulaPack.ollSetup, catalogCase.number, target)
        Stage.PLL ->
            pllScramble(catalogCase.variant.notation, target)
        Stage.CROSS -> null
    } ?: return null

    val notation = moves.joinToString(" ") { it.normalized }
    if (notation.isBlank()) return null
    val parsed = MoveParser.parse(notation)
    if (parsed.error != null || parsed.moves.isEmpty()) return null
    return notation.takeIf {
        CubeState.solved().apply(parsed.moves).asFacelets() == catalogCase.canonicalState
    }
}

/** Explicit F2L/OLL setup is the source of the case state; append a legal
 * whole-cube frame correction when the canonical state normalizer did so. */
private fun setupScramble(
    setupPack: List<String>,
    caseNumber: Int,
    target: CubeState
): List<CubeMove>? {
    val setup = setupPack.getOrNull(caseNumber - 1) ?: return null
    val parsed = MoveParser.parse(setup)
    if (parsed.error != null || parsed.moves.isEmpty()) return null
    val state = CubeState.solved().apply(parsed.moves)
    return referenceFrameRotations.firstNotNullOfOrNull { correction ->
        correction.takeIf { state.apply(it).asFacelets() == target.asFacelets() }
            ?.let { parsed.moves + it }
    }
}

/**
 * PLL states are keyed to their preferred catalog algorithm. Search legal
 * starting and ending cube frames around the inverse so algorithms containing
 * x/y/z reproduce the same normalized recognition state as the catalog.
 */
private fun pllScramble(notation: String, target: CubeState): List<CubeMove>? {
    val parsed = MoveParser.parse(notation)
    if (parsed.error != null || parsed.moves.isEmpty()) return null
    val inverse = parsed.moves.inverse()
    val solved = CubeState.solved()
    for (startFrame in referenceFrameRotations) {
        val afterInverse = solved.apply(startFrame + inverse)
        for (endFrame in referenceFrameRotations) {
            if (afterInverse.apply(endFrame).asFacelets() == target.asFacelets()) {
                return startFrame + inverse + endFrame
            }
        }
    }
    return null
}

/** All 24 orientations expressed only as legal x/y/z moves. */
private val referenceFrameRotations: List<List<CubeMove>> by lazy {
    val start = CubeState.solved()
    val queue = ArrayDeque<Pair<CubeState, List<CubeMove>>>()
    val seen = mutableSetOf(start.asFacelets())
    val paths = mutableListOf<List<CubeMove>>()
    queue.addLast(start to emptyList())
    val rotations = listOf(CubeMove("x"), CubeMove("y"), CubeMove("z"))
    while (queue.isNotEmpty() && paths.size < 24) {
        val (state, path) = queue.removeFirst()
        paths += path
        rotations.forEach { move ->
            val next = state.apply(move)
            if (seen.add(next.asFacelets())) queue.addLast(next to (path + move))
        }
    }
    check(paths.size == 24) { "Expected 24 legal cube orientations, found ${paths.size}" }
    paths
}
