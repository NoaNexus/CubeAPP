// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.analysis

import com.cubetrace.app.core.cube.cfopCrossFaces
import com.cubetrace.app.core.cube.f2lSolvedSlotsOn
import com.cubetrace.app.core.cube.isCrossSolvedOn
import com.cubetrace.app.core.cube.isF2lSolvedOn
import com.cubetrace.app.core.cube.isLastLayerOrientedOn
import com.cubetrace.app.core.model.Completeness
import com.cubetrace.app.core.model.MoveTimeQuality
import com.cubetrace.app.core.model.Penalty
import com.cubetrace.app.core.model.RecordedMove
import com.cubetrace.app.core.model.SolveRecord
import com.cubetrace.app.core.model.SolveSource

/** Local-only helper for replaying records pulled from a connected test phone. */
object LiveAnalysisDiagnostic {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: <sqlite3 executable> <database>" }
        val query = """
            SELECT s.id,
                   s.duration_ms,
                   s.started_at,
                   s.penalty,
                   s.source,
                   s.completeness,
                   s.scramble,
                   ifnull(s.start_facelets, ''),
                   ifnull(s.end_facelets, ''),
                   ifnull(s.start_sequence, ''),
                   ifnull(s.end_sequence, ''),
                   ifnull(s.cross_face, 'D'),
                   ifnull((
                       SELECT group_concat(item, '~') FROM (
                           SELECT m.ordinal || ',' || m.move_code || ',' || m.elapsed_ms || ',' ||
                                  ifnull(m.sequence, '') || ',' || m.gap || ',' ||
                                  ifnull(m.device_time_ms, '') || ',' ||
                                  ifnull(m.received_at_elapsed_ms, '') || ',' ||
                                  ifnull(m.time_quality, 'UNKNOWN') AS item
                           FROM move_event m
                           WHERE m.solve_id = s.id
                           ORDER BY m.ordinal
                       )
                   ), '')
            FROM solve s
            WHERE s.source = 'V10_AI' AND s.completeness = 'COMPLETE'
            ORDER BY s.started_at DESC
            LIMIT 20;
        """.trimIndent()
        val process = ProcessBuilder(args[0], "-separator", "\t", args[1], query)
            .redirectErrorStream(true)
            .start()
        val lines = process.inputStream.bufferedReader().readLines()
        check(process.waitFor() == 0) { lines.joinToString("\n") }

        lines.filter { it.isNotBlank() }.forEach { line ->
            val fields = line.split('\t')
            check(fields.size == 13) { "unexpected sqlite row (${fields.size}): $line" }
            val moves = fields[12].takeIf { it.isNotBlank() }
                ?.split('~')
                ?.map { encoded ->
                    val move = encoded.split(',', limit = 8)
                    RecordedMove(
                        ordinal = move[0].toInt(),
                        code = move[1],
                        elapsedMs = move[2].toLong(),
                        sequence = move[3].toIntOrNull(),
                        gap = move[4] == "1",
                        deviceTimeMs = move[5].toLongOrNull(),
                        receivedAtElapsedMs = move[6].toLongOrNull(),
                        timeQuality = MoveTimeQuality.valueOf(move[7])
                    )
                }
                .orEmpty()
            val solve = SolveRecord(
                id = fields[0],
                sessionId = "diagnostic",
                sessionName = "diagnostic",
                scramble = fields[6],
                durationMs = fields[1].toLong(),
                startedAt = fields[2].toLong(),
                penalty = Penalty.valueOf(fields[3]),
                source = SolveSource.valueOf(fields[4]),
                completeness = Completeness.valueOf(fields[5]),
                moves = moves,
                startFacelets = fields[7].ifBlank { null },
                endFacelets = fields[8].ifBlank { null },
                startSequence = fields[9].toIntOrNull(),
                endSequence = fields[10].toIntOrNull(),
                crossFace = fields[11].first()
            )
            val analysis = analyzeSolve(solve, 250) ?: return@forEach
            val states = analysis.replay?.states.orEmpty()
            val candidates = buildList {
                for (face in cfopCrossFaces()) {
                    val cross = states.indices.firstOrNull { isCrossSolvedOn(states[it], face) } ?: continue
                    val f2l = (cross..states.lastIndex).firstOrNull { isF2lSolvedOn(states[it], face) }
                    val oll = f2l?.let { start ->
                        (start..states.lastIndex).firstOrNull { isLastLayerOrientedOn(states[it], face) }
                    }
                    val slots = (0..3).map { slot ->
                        (cross..states.lastIndex).firstOrNull { slot in f2lSolvedSlotsOn(states[it], face) }
                    }
                    add("$face:C$cross/F${f2l ?: "-"}/O${oll ?: "-"}/S${slots.joinToString(".") { it?.toString() ?: "-" }}")
                }
            }
            val phaseTimes = analysis.phases.joinToString("/") { "${it.code.name}:${it.summary.durationMs}" }
            println("${solve.id}\t${analysis.status}\t${analysis.reasonCode}\t$phaseTimes\t${candidates.joinToString(" ")}")
        }
    }
}
