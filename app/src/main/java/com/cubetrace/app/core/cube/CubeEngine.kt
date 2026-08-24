// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.cube

import java.security.MessageDigest
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.random.Random

private data class Vec3(val x: Int, val y: Int, val z: Int) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun times(value: Int) = Vec3(x * value, y * value, z * value)
    fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z
}

private data class StickerDescriptor(val position: Vec3, val normal: Vec3)

private object StickerGeometry {
    val faces = "URFDLB"
    val descriptors: List<StickerDescriptor> = buildList {
        for (face in faces) {
            when (face) {
                'U' -> for (row in 0..2) for (col in 0..2) add(
                    StickerDescriptor(Vec3(col - 1, 1, row - 1), Vec3(0, 1, 0))
                )
                'R' -> for (row in 0..2) for (col in 0..2) add(
                    StickerDescriptor(Vec3(1, 1 - row, 1 - col), Vec3(1, 0, 0))
                )
                'F' -> for (row in 0..2) for (col in 0..2) add(
                    StickerDescriptor(Vec3(col - 1, 1 - row, 1), Vec3(0, 0, 1))
                )
                'D' -> for (row in 0..2) for (col in 0..2) add(
                    StickerDescriptor(Vec3(col - 1, -1, 1 - row), Vec3(0, -1, 0))
                )
                'L' -> for (row in 0..2) for (col in 0..2) add(
                    StickerDescriptor(Vec3(-1, 1 - row, col - 1), Vec3(-1, 0, 0))
                )
                'B' -> for (row in 0..2) for (col in 0..2) add(
                    StickerDescriptor(Vec3(1 - col, 1 - row, -1), Vec3(0, 0, -1))
                )
            }
        }
    }
    val indexes = descriptors.mapIndexed { index, descriptor ->
        key(descriptor.position, descriptor.normal) to index
    }.toMap()

    fun key(position: Vec3, normal: Vec3): String =
        "${position.x},${position.y},${position.z}|${normal.x},${normal.y},${normal.z}"
}

data class CubeMove(
    val symbol: String,
    val turns: Int = 1,
    val wide: Boolean = false
) {
    val normalized: String
        get() {
            // Lowercase face notation is the standard compact spelling for a
            // two-layer turn: r, f, d... Keep the parser permissive for Rw,
            // but never expose the W suffix in the UI or database.
            val base = if (wide) symbol.lowercase() else symbol
            return base + when (turns.mod(4)) {
                2 -> "2"
                3 -> "'"
                else -> ""
            }
        }

    fun inverse(): CubeMove = copy(turns = (4 - turns.mod(4)).mod(4).let { if (it == 0) 4 else it })
}

data class MoveParseError(val position: Int, val message: String)
data class MoveParseResult(val moves: List<CubeMove>, val error: MoveParseError? = null)

data class ScrambleValidationResult(
    val notation: String? = null,
    val error: String? = null
) {
    val valid: Boolean get() = notation != null && error == null
}

object MoveParser {
    fun parse(input: String): MoveParseResult {
        return try {
            val parser = Parser(input.replace('’', '\'').replace('′', '\''))
            MoveParseResult(parser.sequence())
        } catch (error: ParserError) {
            MoveParseResult(emptyList(), MoveParseError(error.position, error.message ?: "公式无法解析"))
        }
    }

    fun parseOrEmpty(input: String): List<CubeMove> = parse(input).moves

    private class Parser(private val source: String) {
        private var index = 0

        fun sequence(end: Char? = null): List<CubeMove> {
            val result = mutableListOf<CubeMove>()
            while (index < source.length) {
                skipWhitespace()
                if (index >= source.length) break
                if (end != null && source[index] == end) {
                    index++
                    return result
                }
                if (source[index] == '(') {
                    index++
                    val group = sequence(')')
                    val count = readRepeat()
                    repeat(count) { result += group }
                } else {
                    result += readMove()
                }
            }
            if (end != null) fail("缺少右括号")
            return result
        }

        private fun readMove(): CubeMove {
            val start = index
            if (index >= source.length) fail("缺少动作", index)
            val raw = source[index]
            val lowerWideAlias = raw in "urfdlb"
            val symbol = when {
                raw in "URFDLBMESxyz" -> raw.toString()
                raw in "urfdlb" -> raw.uppercase()
                else -> fail("未知符号：$raw", index)
            }
            index++
            // A lowercase face is already the compact two-layer spelling
            // (r/f/d...). Only an explicit Rw/Fw suffix consumes another
            // character. The old code advanced the cursor for every
            // lowercase move, so r' and f' lost their prime and generated
            // invalid OLL/F2L recognition states.
            val explicitWide = index < source.length && (source[index] == 'w' || source[index] == 'W')
            val wide = lowerWideAlias || explicitWide
            if (explicitWide) index++
            var turns = 1
            if (index < source.length && source[index] == '2') {
                turns = 2
                index++
            }
            if (turns == 2 && index < source.length && source[index] == '\'') {
                // Accept the harmless printed spelling U2'. A half turn is
                // its own inverse, so it still normalizes to U2.
                index++
                return CubeMove(symbol, turns, wide)
            }
            if (index < source.length && source[index] == '\'') {
                turns = if (turns == 2) fail("R2 后不能继续添加 prime", index) else 3
                index++
            }
            if (index < source.length && source[index] == '2') {
                fail("重复的 2 后缀", index)
            }
            if (symbol in listOf("M", "E", "S", "x", "y", "z") && wide) {
                fail("夹层或转体不使用宽层后缀", start)
            }
            return CubeMove(symbol, turns, wide)
        }

        private fun readRepeat(): Int {
            skipWhitespace()
            if (index >= source.length || !source[index].isDigit()) return 1
            val start = index
            while (index < source.length && source[index].isDigit()) index++
            val repeat = source.substring(start, index).toIntOrNull() ?: 0
            if (repeat !in 1..99) fail("重复次数必须在 1 到 99 之间", start)
            return repeat
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun fail(message: String, position: Int = index): Nothing =
            throw ParserError(message, position)
    }

    private class ParserError(message: String, val position: Int) : IllegalArgumentException(message)
}

/**
 * Validates a timer scramble without silently accepting formula-only moves.
 * Parenthesized input is allowed and flattened, but a 3x3 timer scramble is
 * deliberately limited to outer U/R/F/D/L/B turns.
 */
fun validateTimerScramble(input: String, maxMoves: Int = 100): ScrambleValidationResult {
    val parsed = MoveParser.parse(input.trim())
    parsed.error?.let { error ->
        return ScrambleValidationResult(error = "第 ${error.position + 1} 个字符附近：${error.message}")
    }
    if (parsed.moves.isEmpty()) {
        return ScrambleValidationResult(error = "请输入至少一个打乱动作")
    }
    if (parsed.moves.size > maxMoves) {
        return ScrambleValidationResult(error = "打乱最多支持 $maxMoves 个动作")
    }
    if (parsed.moves.any { it.wide || it.symbol !in setOf("U", "R", "F", "D", "L", "B") }) {
        return ScrambleValidationResult(error = "自定义打乱只支持 U、R、F、D、L、B 外层转动")
    }
    return ScrambleValidationResult(
        notation = parsed.moves.joinToString(" ") { it.normalized }
    )
}

class CubeState private constructor(private val facelets: CharArray) {
    fun asFacelets(): String = facelets.concatToString()

    fun isSolved(): Boolean = facelets.indices.all { facelets[it] == "URFDLB"[it / 9] }

    fun stateHash(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(asFacelets().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    fun apply(move: CubeMove): CubeState {
        val spec = when (move.symbol.uppercase()) {
            "U" -> TurnSpec(Vec3(0, 1, 0), setOf(1))
            "D" -> TurnSpec(Vec3(0, -1, 0), setOf(1))
            "R" -> TurnSpec(Vec3(1, 0, 0), setOf(1))
            "L" -> TurnSpec(Vec3(-1, 0, 0), setOf(1))
            "F" -> TurnSpec(Vec3(0, 0, 1), setOf(1))
            "B" -> TurnSpec(Vec3(0, 0, -1), setOf(1))
            "M" -> TurnSpec(Vec3(1, 0, 0), setOf(0), direction = 1)
            "E" -> TurnSpec(Vec3(0, 1, 0), setOf(0), direction = 1)
            "S" -> TurnSpec(Vec3(0, 0, 1), setOf(0), direction = -1)
            "X" -> TurnSpec(Vec3(1, 0, 0), setOf(-1, 0, 1))
            "Y" -> TurnSpec(Vec3(0, 1, 0), setOf(-1, 0, 1))
            "Z" -> TurnSpec(Vec3(0, 0, 1), setOf(-1, 0, 1))
            else -> return this
        }
        val layers = if (move.wide) spec.layers + 0 else spec.layers
        val amount = (spec.direction * move.turns).mod(4)
        return rotate(spec.axis, layers, amount)
    }

    fun apply(moves: List<CubeMove>): CubeState = moves.fold(this) { state, move -> state.apply(move) }

    fun copy(): CubeState = CubeState(facelets.copyOf())

    private fun rotate(axis: Vec3, layers: Set<Int>, amount: Int): CubeState {
        if (amount == 0) return this
        val result = facelets.copyOf()
        for ((index, descriptor) in StickerGeometry.descriptors.withIndex()) {
            if (descriptor.position.dot(axis) !in layers) continue
            var position = descriptor.position
            var normal = descriptor.normal
            repeat(amount) {
                position = rotateQuarter(position, axis)
                normal = rotateQuarter(normal, axis)
            }
            val target = StickerGeometry.indexes[StickerGeometry.key(position, normal)] ?: return this
            result[target] = facelets[index]
        }
        return CubeState(result)
    }

    companion object {
        fun solved(): CubeState = CubeState(CharArray(54) { "URFDLB"[it / 9] })

        fun fromFacelets(facelets: String): CubeState? {
            if (facelets.length != 54 || facelets.any { it !in "URFDLB" }) return null
            return CubeState(facelets.toCharArray())
        }
    }

    private data class TurnSpec(
        val axis: Vec3,
        val layers: Set<Int>,
        val direction: Int = -1
    )
}

private fun rotateQuarter(value: Vec3, axis: Vec3): Vec3 {
    return when {
        axis.x != 0 -> if (axis.x > 0) Vec3(value.x, -value.z, value.y) else Vec3(value.x, value.z, -value.y)
        axis.y != 0 -> if (axis.y > 0) Vec3(value.z, value.y, -value.x) else Vec3(-value.z, value.y, value.x)
        else -> if (axis.z > 0) Vec3(-value.y, value.x, value.z) else Vec3(value.y, -value.x, value.z)
    }
}

fun List<CubeMove>.inverse(): List<CubeMove> = asReversed().map(CubeMove::inverse)

object ScrambleGenerator {
    private val faces = listOf("U", "D", "L", "R", "F", "B")
    private val suffixes = listOf("", "'", "2")

    fun generate(length: Int = 20, seed: Long = Random.nextLong()): String {
        val random = Random(seed)
        val moves = mutableListOf<String>()
        var previousFace = ""
        var previousAxis = ""
        repeat(length) {
            var face: String
            do {
                face = faces.random(random)
            } while (face == previousFace || axisOf(face) == previousAxis)
            previousFace = face
            previousAxis = axisOf(face)
            moves += face + suffixes.random(random)
        }
        return moves.joinToString(" ")
    }

    private fun axisOf(face: String): String = when (face) {
        "U", "D" -> "y"
        "L", "R" -> "x"
        else -> "z"
    }
}

fun normalizedMoves(notation: String): List<CubeMove> = MoveParser.parseOrEmpty(notation)

/**
 * Returns the net quarter-turn amount (0..3) when [currentFacelets] differs
 * from [checkpointFacelets] only by rotations of the face used by
 * [expectedMove]. This lets smart scramble guidance accept physically
 * equivalent input paths such as U' U' for U2 or R' R' R' for R without
 * accepting a turn on another face.
 */
fun equivalentFaceTurnAmount(
    checkpointFacelets: String,
    currentFacelets: String,
    expectedMove: CubeMove
): Int? {
    if (expectedMove.wide || expectedMove.symbol.uppercase() !in setOf("U", "R", "F", "D", "L", "B")) {
        return null
    }
    val checkpoint = CubeState.fromFacelets(checkpointFacelets) ?: return null
    if (CubeState.fromFacelets(currentFacelets) == null) return null
    return (0..3).firstOrNull { turns ->
        checkpoint.apply(expectedMove.copy(turns = turns)).asFacelets() == currentFacelets
    }
}

/**
 * Converts the Moyu V10 protocol's factory frame (white U / green F) to the
 * app's fixed yellow U / blue F frame. The protocol uses the standard
 * URFDLB labels and face grids; the two frames differ by a 180-degree whole
 * cube rotation around the R-L axis. Re-labeling the six face blocks without
 * moving their individual stickers would leave corners and edges mirrored,
 * so the conversion is done from the shared sticker geometry.
 */
fun moyuFaceletsToYellowTopBlueFront(facelets: String): String {
    if (facelets.length != 54 || facelets.any { it !in "URFDLB" }) return facelets
    val result = CharArray(54)
    StickerGeometry.descriptors.forEachIndexed { sourceIndex, descriptor ->
        val targetPosition = rotateFrameHalfTurn(descriptor.position)
        val targetNormal = rotateFrameHalfTurn(descriptor.normal)
        val targetIndex = StickerGeometry.indexes[StickerGeometry.key(targetPosition, targetNormal)]
            ?: return facelets
        result[targetIndex] = mapMoyuFacelet(facelets[sourceIndex])
    }
    return result.concatToString()
}

/** Converts a physical V10 face move into the app's yellow-top notation. */
fun moyuMoveToYellowTopBlueFront(notation: String): String {
    val parsed = MoveParser.parse(notation)
    if (parsed.error != null) return notation
    return parsed.moves.joinToString(" ") { move ->
        val mapped = when (move.symbol.uppercase()) {
            "U" -> "D"
            "D" -> "U"
            "F" -> "B"
            "B" -> "F"
            else -> move.symbol
        }
        CubeMove(mapped, move.turns, move.wide).normalized
    }
}

/**
 * The two reference frames differ by the same 180-degree whole-cube turn in
 * either direction, so the conversion is its own inverse. Keeping explicit
 * names for the reverse direction makes the smart-cube UI code harder to
 * accidentally mix with the protocol's factory-frame conversion.
 */
fun yellowTopBlueFrontToOfficialFacelets(facelets: String): String =
    moyuFaceletsToYellowTopBlueFront(facelets)

fun yellowTopBlueFrontToOfficialMove(notation: String): String =
    moyuMoveToYellowTopBlueFront(notation)

private fun rotateFrameHalfTurn(value: Vec3): Vec3 = Vec3(value.x, -value.y, -value.z)

private fun mapMoyuFacelet(value: Char): Char = when (value) {
    'U' -> 'D'
    'D' -> 'U'
    'F' -> 'B'
    'B' -> 'F'
    else -> value
}

/**
 * Re-expresses notation authored for the legacy white-top/green-front view in
 * the app's fixed yellow-top/blue-front view. The cube geometry itself stays
 * in URFDLB order; only the physical reference frame and its move notation are
 * changed together, so canonical states and diagrams remain consistent.
 */
fun yellowTopBlueFrontNotation(notation: String): String {
    val parsed = MoveParser.parse(notation)
    if (parsed.error != null) return notation
    return parsed.moves.joinToString(" ") { move ->
        val key = move.symbol.uppercase()
        val mapped = when (key) {
            "U" -> "D"
            "D" -> "U"
            "F" -> "L"
            "L" -> "F"
            "R" -> "B"
            "B" -> "R"
            "M" -> "S"
            "S" -> "M"
            "E" -> "E"
            "X" -> "z"
            "Y" -> "y"
            "Z" -> "x"
            else -> move.symbol
        }
        val turns = if (key in setOf("E", "X", "Y", "Z")) {
            when (move.turns.mod(4)) {
                1 -> 3
                3 -> 1
                else -> move.turns
            }
        } else {
            move.turns
        }
        CubeMove(mapped, turns, move.wide).normalized
    }
}

private val referenceFrameRotations: List<List<CubeMove>> by lazy {
    // A whole-cube rotation can leave a valid OLL/F2L algorithm in a rotated
    // center frame. Enumerate the 24 orientations once, then use the one that
    // puts U/R/F centers back at U/R/F. This changes only the displayed start
    // state; the user's algorithm text still contains its y/x/z moves.
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
            if (seen.add(next.asFacelets())) {
                queue.addLast(next to (path + move))
            }
        }
    }
    paths
}

private fun normalizeReferenceFrame(facelets: String): String {
    val source = CubeState.fromFacelets(facelets) ?: return facelets
    referenceFrameRotations.forEach { path ->
        var candidate = source
        path.forEach { move -> candidate = candidate.apply(move) }
        val normalized = candidate.asFacelets()
        if (normalized.getOrNull(4) == 'U' &&
            normalized.getOrNull(13) == 'R' &&
            normalized.getOrNull(22) == 'F'
        ) {
            return normalized
        }
    }
    return facelets
}

/**
 * Reorients an observed solve so its detected Cross is on D, then relabels
 * sticker colors by the centers in that view. Four frames are returned (the
 * possible fronts around the Cross axis). This is intended for recognition
 * signatures only; it does not alter the saved solve or the live cube view.
 */
fun normalizedCfopFrames(facelets: String, crossFace: Char): List<String> {
    val source = CubeState.fromFacelets(facelets) ?: return emptyList()
    val faceIndex = "URFDLB".indexOf(crossFace.uppercaseChar())
    if (faceIndex < 0) return emptyList()
    val crossCenterColor = facelets.getOrNull(faceIndex * 9 + 4) ?: return emptyList()
    return referenceFrameRotations.mapNotNull { path ->
        var candidate = source
        path.forEach { move -> candidate = candidate.apply(move) }
        val rotated = candidate.asFacelets()
        if (rotated.getOrNull(31) != crossCenterColor) return@mapNotNull null
        normalizeColorsByCenters(rotated)
    }.distinct()
}

private fun normalizeColorsByCenters(facelets: String): String? {
    if (facelets.length != 54) return null
    val faces = "URFDLB"
    val colorToFace = faces.indices.associate { index ->
        (facelets.getOrNull(index * 9 + 4) ?: return null) to faces[index]
    }
    if (colorToFace.size != 6) return null
    return buildString(54) {
        facelets.forEach { color -> append(colorToFace[color] ?: return null) }
    }
}

private val pllSolvedPieceColorSets: Set<Set<Char>> by lazy {
    StickerGeometry.descriptors
        .mapIndexed { index, descriptor -> index to descriptor }
        .filter { (_, descriptor) -> descriptor.position.y == 1 }
        .groupBy { (_, descriptor) -> descriptor.position }
        .values
        .filter { it.size >= 2 }
        .map { stickers -> stickers.map { (index, _) -> "URFDLB"[index / 9] }.toSet() }
        .toSet()
}

/**
 * Checks the recognition shape used by a PLL diagram. The preferred PLL
 * algorithms in the chart include whole-cube x rotations; depending on the
 * final holding direction, simply inverting the text against a fixed solved
 * state can leave a corner oriented on D. A valid PLL recognition state has a
 * solved U/D face and the eight U-layer cubies are still the eight U-layer
 * cubies (only their positions may differ).
 */
private fun isPllRecognitionState(facelets: String): Boolean {
    if (facelets.length != 54) return false
    if (!facelets.substring(0, 9).all { it == 'U' }) return false
    if (!facelets.substring(27, 36).all { it == 'D' }) return false

    return StickerGeometry.descriptors
        .mapIndexed { index, descriptor -> index to descriptor }
        .filter { (_, descriptor) -> descriptor.position.y == 1 }
        .groupBy { (_, descriptor) -> descriptor.position }
        .values
        .filter { it.size >= 2 }
        .all { stickers ->
            stickers.map { (index, _) -> facelets[index] }.toSet() in pllSolvedPieceColorSets
        }
}

/**
 * Builds PLL recognition states from the chart's preferred algorithms. The
 * final solved cube may be rotated by an explicit x/y/z in the notation, so
 * search the 24 legal final reference frames and keep the candidate that is
 * already yellow-top/blue-front and has a genuine PLL top layer.
 */
fun canonicalStateForPllAlgorithm(notation: String): String {
    val moves = MoveParser.parseOrEmpty(notation)
    val inverseMoves = moves.inverse()
    val direct = normalizeReferenceFrame(CubeState.solved().apply(inverseMoves).asFacelets())
    if (moves.none { it.symbol.lowercase() in setOf("x", "y", "z") }) return direct

    val solved = CubeState.solved()
    val candidates = referenceFrameRotations.map { path ->
        var target = solved
        path.forEach { target = target.apply(it) }
        target.apply(inverseMoves).asFacelets()
    }
    return candidates.firstOrNull { candidate ->
        candidate.getOrNull(4) == 'U' &&
            candidate.getOrNull(13) == 'R' &&
            candidate.getOrNull(22) == 'F' &&
            isPllRecognitionState(candidate)
    } ?: direct
}

fun canonicalStateForAlgorithm(notation: String): String {
    val moves = MoveParser.parseOrEmpty(notation)
    return normalizeReferenceFrame(CubeState.solved().apply(moves.inverse()).asFacelets())
}

/**
 * Builds a case from the site's explicit setup sequence.  This is the source
 * of truth for recognition diagrams; the displayed algorithm is a separate
 * concern and must not be used to guess the recognition state.
 */
fun canonicalStateForSetup(notation: String): String {
    val moves = MoveParser.parseOrEmpty(notation)
    return normalizeReferenceFrame(CubeState.solved().apply(moves).asFacelets())
}

private data class CubieGroup(
    val position: Vec3,
    val stickerIndexes: List<Int>,
    val solvedStickerFaces: List<Char>
)

private val cubieGroups: List<CubieGroup> by lazy {
    StickerGeometry.descriptors
        .mapIndexed { index, descriptor -> index to descriptor }
        .groupBy { (_, descriptor) -> descriptor.position }
        .map { (position, stickers) ->
            CubieGroup(
                position = position,
                stickerIndexes = stickers.map { it.first }.sorted(),
                solvedStickerFaces = stickers.map { it.first }.sorted().map { "URFDLB"[it / 9] }
            )
        }
}

private val f2lCornerPosition = Vec3(1, -1, 1)
private val f2lEdgePosition = Vec3(1, 0, 1)
private val f2lOtherSlotPositions = listOf(
    Vec3(-1, -1, 1) to Vec3(-1, 0, 1),
    Vec3(-1, -1, -1) to Vec3(-1, 0, -1),
    Vec3(1, -1, -1) to Vec3(1, 0, -1)
)
private val crossEdgePositions = listOf(
    Vec3(0, -1, 1),
    Vec3(1, -1, 0),
    Vec3(0, -1, -1),
    Vec3(-1, -1, 0)
)

private fun cubieAt(position: Vec3): CubieGroup? = cubieGroups.firstOrNull { it.position == position }

private fun isValidStageFacelets(facelets: String): Boolean =
    facelets.length == 54 && facelets.all { it in "URFDLB" }

private fun isSolvedAt(facelets: String, cubie: CubieGroup?): Boolean =
    cubie != null && cubie.stickerIndexes.indices.all { index ->
        facelets.getOrNull(cubie.stickerIndexes[index]) == cubie.solvedStickerFaces[index]
    }

private fun topFaceIsOriented(facelets: String): Boolean {
    val topCenter = facelets.getOrNull(4) ?: return false
    return facelets.take(9).all { it == topCenter }
}

private fun topLayerIsSolvedRelativeToCenters(facelets: String): Boolean {
    if (!topFaceIsOriented(facelets)) return false
    // The first row of each side face is the visible top-layer row. Compare it
    // with that face's current center rather than hard-coding U/R/F colors;
    // this keeps valid x/y/z regrips and wide-move center states valid.
    return listOf(9, 18, 36, 45).all { faceStart ->
        val center = facelets.getOrNull(faceStart + 4) ?: return@all false
        (0..2).all { offset -> facelets.getOrNull(faceStart + offset) == center }
    }
}

/** Public CFOP predicates used by the offline solve analyzer. */
fun isCrossSolved(facelets: String): Boolean {
    if (!isValidStageFacelets(facelets)) return false
    val normalized = normalizeReferenceFrame(facelets)
    return crossEdgePositions.all { isSolvedAt(normalized, cubieAt(it)) }
}

fun isF2lSlotSolved(facelets: String, slot: Int): Boolean {
    if (!isValidStageFacelets(facelets) || slot !in 0..3) return false
    val normalized = normalizeReferenceFrame(facelets)
    val positions = when (slot) {
        0 -> f2lCornerPosition to f2lEdgePosition
        1 -> f2lOtherSlotPositions[0]
        2 -> f2lOtherSlotPositions[1]
        else -> f2lOtherSlotPositions[2]
    }
    return isSolvedAt(normalized, cubieAt(positions.first)) &&
        isSolvedAt(normalized, cubieAt(positions.second))
}

fun f2lSolvedSlots(facelets: String): Set<Int> =
    (0..3).filterTo(mutableSetOf()) { isF2lSlotSolved(facelets, it) }

fun isF2lSolved(facelets: String): Boolean =
    isCrossSolved(facelets) && f2lSolvedSlots(facelets).size == 4

fun isLastLayerOriented(facelets: String): Boolean {
    if (!isValidStageFacelets(facelets)) return false
    return topFaceIsOriented(normalizeReferenceFrame(facelets))
}

/*
 * CFOP analysis must not assume that the cross is always the D face.  The
 * timer's physical frame can be white/green, yellow/blue, or another valid
 * whole-cube orientation.  These predicates compare stickers with the center
 * currently occupying each face, so they keep the cubie logic intact while
 * making the selected cross face explicit.
 */
private const val CFOP_FACE_ORDER = "URFDLB"

private fun faceNormal(face: Char): Vec3? = when (face.uppercase().firstOrNull()) {
    'U' -> Vec3(0, 1, 0)
    'R' -> Vec3(1, 0, 0)
    'F' -> Vec3(0, 0, 1)
    'D' -> Vec3(0, -1, 0)
    'L' -> Vec3(-1, 0, 0)
    'B' -> Vec3(0, 0, -1)
    else -> null
}

private fun oppositeFace(face: Char): Char = when (face.uppercase().first()) {
    'U' -> 'D'
    'D' -> 'U'
    'R' -> 'L'
    'L' -> 'R'
    'F' -> 'B'
    else -> 'F'
}

private fun isEdgePosition(position: Vec3): Boolean =
    listOf(position.x, position.y, position.z).count { abs(it) == 1 } == 2

private fun isCornerPosition(position: Vec3): Boolean =
    listOf(position.x, position.y, position.z).all { abs(it) == 1 }

private fun faceForNormal(normal: Vec3): Char? = when {
    normal.y == 1 -> 'U'
    normal.x == 1 -> 'R'
    normal.z == 1 -> 'F'
    normal.y == -1 -> 'D'
    normal.x == -1 -> 'L'
    normal.z == -1 -> 'B'
    else -> null
}

/** A solved cubie is defined by the centers, not by hard-coded U/R/F colors. */
private fun isSolvedRelativeToCenters(facelets: String, cubie: CubieGroup?): Boolean =
    cubie != null && cubie.stickerIndexes.all { stickerIndex ->
        val face = faceForNormal(StickerGeometry.descriptors[stickerIndex].normal) ?: return@all false
        val centerIndex = CFOP_FACE_ORDER.indexOf(face) * 9 + 4
        facelets.getOrNull(stickerIndex) == facelets.getOrNull(centerIndex)
    }

private fun crossEdgePositions(face: Char): List<Vec3> {
    val normal = faceNormal(face) ?: return emptyList()
    return cubieGroups.asSequence()
        .map { it.position }
        .filter { isEdgePosition(it) && it.dot(normal) == 1 }
        .sortedWith(compareBy<Vec3> { it.x }.thenBy { it.y }.thenBy { it.z })
        .toList()
}

private fun f2lSlotPositions(face: Char): List<Pair<Vec3, Vec3>> {
    val normal = faceNormal(face) ?: return emptyList()
    return cubieGroups.asSequence()
        .map { it.position }
        .filter { isCornerPosition(it) && it.dot(normal) == 1 }
        .sortedWith(compareBy<Vec3> { it.x }.thenBy { it.y }.thenBy { it.z })
        .map { corner ->
            val edge = when {
                normal.x != 0 -> Vec3(0, corner.y, corner.z)
                normal.y != 0 -> Vec3(corner.x, 0, corner.z)
                else -> Vec3(corner.x, corner.y, 0)
            }
            corner to edge
        }
        .toList()
}

/** Returns all possible cross faces for orientation-independent CFOP analysis. */
fun cfopCrossFaces(): List<Char> = CFOP_FACE_ORDER.toList()

fun isCrossSolvedOn(facelets: String, crossFace: Char): Boolean {
    if (!isValidStageFacelets(facelets) || faceNormal(crossFace) == null) return false
    return crossEdgePositions(crossFace).all { isSolvedRelativeToCenters(facelets, cubieAt(it)) }
}

fun isF2lSlotSolvedOn(facelets: String, crossFace: Char, slot: Int): Boolean {
    if (!isValidStageFacelets(facelets) || slot !in 0..3) return false
    val pair = f2lSlotPositions(crossFace).getOrNull(slot) ?: return false
    return isSolvedRelativeToCenters(facelets, cubieAt(pair.first)) &&
        isSolvedRelativeToCenters(facelets, cubieAt(pair.second))
}

fun f2lSolvedSlotsOn(facelets: String, crossFace: Char): Set<Int> =
    (0..3).filterTo(mutableSetOf()) { isF2lSlotSolvedOn(facelets, crossFace, it) }

fun isF2lSolvedOn(facelets: String, crossFace: Char): Boolean =
    isCrossSolvedOn(facelets, crossFace) && f2lSolvedSlotsOn(facelets, crossFace).size == 4

fun isLastLayerOrientedOn(facelets: String, crossFace: Char): Boolean {
    if (!isValidStageFacelets(facelets)) return false
    val lastLayerFace = oppositeFace(crossFace)
    val start = CFOP_FACE_ORDER.indexOf(lastLayerFace) * 9
    if (start < 0) return false
    val center = facelets.getOrNull(start + 4) ?: return false
    return (start until start + 9).all { facelets.getOrNull(it) == center }
}

/** Solved up to a whole-cube regrip: every face is uniform around its center. */
fun isCubeSolvedRelativeToCenters(facelets: String): Boolean {
    if (!isValidStageFacelets(facelets)) return false
    return CFOP_FACE_ORDER.indices.all { faceIndex ->
        val start = faceIndex * 9
        val center = facelets[start + 4]
        (start until start + 9).all { facelets[it] == center }
    }
}

/**
 * Validates the actual CFOP goal of an F2L case. Only the target FR pair,
 * cross, and any F2L slots already solved in the recognition state matter;
 * U-layer stickers and other unrelated gray pieces are deliberately ignored.
 */
fun verifyF2lStage(initialFacelets: String, resultFacelets: String): Boolean {
    if (!isValidStageFacelets(initialFacelets) || !isValidStageFacelets(resultFacelets)) return false
    val initial = normalizeReferenceFrame(initialFacelets)
    val result = normalizeReferenceFrame(resultFacelets)
    val targetCorner = cubieAt(f2lCornerPosition)
    val targetEdge = cubieAt(f2lEdgePosition)
    if (!isSolvedAt(result, targetCorner) || !isSolvedAt(result, targetEdge)) return false
    if (crossEdgePositions.any { !isSolvedAt(result, cubieAt(it)) }) return false

    return f2lOtherSlotPositions.all { (cornerPosition, edgePosition) ->
        val corner = cubieAt(cornerPosition)
        val edge = cubieAt(edgePosition)
        val wasSolved = isSolvedAt(initial, corner) && isSolvedAt(initial, edge)
        !wasSolved || (isSolvedAt(result, corner) && isSolvedAt(result, edge))
    }
}

/**
 * Validates OLL by requiring the top face to be completely oriented to its
 * current center. The recognition setup is allowed to contain gray or
 * otherwise out-of-focus stickers, so no side or lower-layer stickers are
 * compared here.
 */
fun verifyOllStage(initialFacelets: String, resultFacelets: String): Boolean {
    if (!isValidStageFacelets(initialFacelets) || !isValidStageFacelets(resultFacelets)) return false
    return topFaceIsOriented(resultFacelets)
}

/**
 * Validates PLL by requiring an oriented top and a solved top-layer
 * permutation relative to the current side centers. This intentionally
 * ignores the exact route taken by the arrows or unrelated gray stickers.
 */
fun verifyPllStage(initialFacelets: String, resultFacelets: String): Boolean {
    if (!isValidStageFacelets(initialFacelets) || !isValidStageFacelets(resultFacelets)) return false
    if (!topFaceIsOriented(initialFacelets)) return false
    return topLayerIsSolvedRelativeToCenters(resultFacelets)
}

fun cubeStateFromFacelets(facelets: String): CubeState =
    CubeState.fromFacelets(facelets) ?: CubeState.solved()

fun moveCount(notation: String): Int = MoveParser.parseOrEmpty(notation).count { abs(it.turns) > 0 }
