// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cubetrace.app.core.cube.PresetCatalog
import com.cubetrace.app.core.cube.normalizedMoves
import com.cubetrace.app.core.model.AlgorithmVariant
import com.cubetrace.app.core.model.CaseFilter
import com.cubetrace.app.core.model.Completeness
import com.cubetrace.app.core.model.CubeCase
import com.cubetrace.app.core.model.Penalty
import com.cubetrace.app.core.model.RecordedMove
import com.cubetrace.app.core.model.SolveRecord
import com.cubetrace.app.core.model.SolveSource
import com.cubetrace.app.core.model.Stage
import java.util.UUID

private const val DATABASE_NAME = "cubetrace.db"
private const val DATABASE_VERSION = 14

private class CubeTraceDb(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cube_case (
                stable_id TEXT PRIMARY KEY NOT NULL,
                stage TEXT NOT NULL,
                case_number INTEGER NOT NULL,
                name TEXT NOT NULL,
                alias TEXT NOT NULL,
                canonical_state TEXT NOT NULL,
                orientation_rule TEXT NOT NULL,
                preset_version TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE algorithm_variant (
                id TEXT PRIMARY KEY NOT NULL,
                case_id TEXT NOT NULL,
                notation TEXT NOT NULL,
                normalized_moves TEXT NOT NULL,
                source_type TEXT NOT NULL,
                verified INTEGER NOT NULL,
                preferred INTEGER NOT NULL,
                note TEXT NOT NULL,
                FOREIGN KEY(case_id) REFERENCES cube_case(stable_id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE user_case_state (
                case_id TEXT PRIMARY KEY NOT NULL,
                mastery_box INTEGER NOT NULL DEFAULT 0,
                due_at INTEGER NOT NULL DEFAULT 0,
                favorite INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                preferred_variant_id TEXT,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(case_id) REFERENCES cube_case(stable_id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE solve (
                id TEXT PRIMARY KEY NOT NULL,
                session_id TEXT NOT NULL,
                session_name TEXT NOT NULL,
                scramble TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                started_at INTEGER NOT NULL,
                penalty TEXT NOT NULL,
                source TEXT NOT NULL,
                completeness TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE move_event (
                solve_id TEXT NOT NULL,
                ordinal INTEGER NOT NULL,
                move_code TEXT NOT NULL,
                elapsed_ms INTEGER NOT NULL,
                sequence INTEGER,
                gap INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(solve_id, ordinal),
                FOREIGN KEY(solve_id) REFERENCES solve(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE training_attempt (
                id TEXT PRIMARY KEY NOT NULL,
                case_id TEXT NOT NULL,
                result TEXT NOT NULL,
                recognition_ms INTEGER,
                execution_ms INTEGER,
                occurred_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        seedCases(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 14) migratePresetContent(db)
    }

    private fun migratePresetContent(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            PresetCatalog.all().forEach { cubeCase ->
                val caseValues = ContentValues().apply {
                    put("name", cubeCase.name)
                    put("alias", cubeCase.alias)
                    put("canonical_state", cubeCase.canonicalState)
                    put("orientation_rule", cubeCase.orientationRule)
                    put("preset_version", PresetCatalog.VERSION)
                }
                db.update(
                    "cube_case",
                    caseValues,
                    "stable_id = ?",
                    arrayOf(cubeCase.stableId)
                )

                val variant = cubeCase.variant
                val variantValues = ContentValues().apply {
                    put("notation", variant.notation)
                    put("normalized_moves", variant.normalizedMoves.joinToString(" "))
                    put("source_type", variant.sourceType)
                    put("verified", if (variant.verified) 1 else 0)
                    put("preferred", if (variant.preferred) 1 else 0)
                    put("note", variant.note)
                }
                db.update(
                    "algorithm_variant",
                    variantValues,
                    "id = ?",
                    arrayOf(variant.id)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedCases(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            PresetCatalog.all().forEachIndexed { index, cubeCase ->
                val caseValues = ContentValues().apply {
                    put("stable_id", cubeCase.stableId)
                    put("stage", cubeCase.stage.name)
                    put("case_number", cubeCase.number)
                    put("name", cubeCase.name)
                    put("alias", cubeCase.alias)
                    put("canonical_state", cubeCase.canonicalState)
                    put("orientation_rule", cubeCase.orientationRule)
                    put("preset_version", PresetCatalog.VERSION)
                }
                db.insertWithOnConflict("cube_case", null, caseValues, SQLiteDatabase.CONFLICT_IGNORE)

                val variant = cubeCase.variant
                val variantValues = ContentValues().apply {
                    put("id", variant.id)
                    put("case_id", variant.caseId)
                    put("notation", variant.notation)
                    put("normalized_moves", variant.normalizedMoves.joinToString(" "))
                    put("source_type", variant.sourceType)
                    put("verified", if (variant.verified) 1 else 0)
                    put("preferred", if (variant.preferred) 1 else 0)
                    put("note", variant.note)
                }
                db.insertWithOnConflict("algorithm_variant", null, variantValues, SQLiteDatabase.CONFLICT_IGNORE)

                val stateValues = ContentValues().apply {
                    put("case_id", cubeCase.stableId)
                    put("mastery_box", index % 6)
                    put("due_at", if (index < 12) now else now + 86_400_000L)
                    put("favorite", if (index % 13 == 0) 1 else 0)
                    put("notes", "")
                    put("preferred_variant_id", variant.id)
                    put("updated_at", now)
                }
                db.insertWithOnConflict("user_case_state", null, stateValues, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

class LocalRepository(context: Context) {
    private val helper = CubeTraceDb(context.applicationContext)

    fun listCases(
        stage: Stage? = null,
        query: String = "",
        filter: CaseFilter = CaseFilter.ALL
    ): List<CubeCase> {
        val db = helper.readableDatabase
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        stage?.let {
            clauses += "c.stage = ?"
            args += it.name
        }
        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotEmpty()) {
            clauses += "(c.name LIKE ? OR c.alias LIKE ? OR v.notation LIKE ?)"
            val pattern = "%$normalizedQuery%"
            args += pattern
            args += pattern
            args += pattern
        }
        when (filter) {
            CaseFilter.DUE -> clauses += "u.due_at <= ?".also { args += System.currentTimeMillis().toString() }
            CaseFilter.WEAK -> clauses += "u.mastery_box <= 2"
            CaseFilter.FAVORITE -> clauses += "u.favorite = 1"
            CaseFilter.ALL -> Unit
        }
        val where = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")?.let { "WHERE $it" } ?: ""
        val sql = """
            SELECT c.*, v.id AS variant_id, v.notation, v.normalized_moves, v.source_type,
                   v.verified, v.preferred, v.note, u.mastery_box, u.due_at, u.favorite,
                   u.notes AS user_notes
            FROM cube_case c
            JOIN algorithm_variant v ON v.case_id = c.stable_id AND v.preferred = 1
            LEFT JOIN user_case_state u ON u.case_id = c.stable_id
            $where
            ORDER BY c.stage, c.case_number
        """.trimIndent()
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toCubeCase())
            }
        }
    }

    fun getCase(id: String): CubeCase? = listCases().firstOrNull { it.stableId == id }

    fun listVariants(caseId: String): List<AlgorithmVariant> {
        val db = helper.readableDatabase
        return db.rawQuery(
            """
            SELECT id, case_id, notation, normalized_moves, source_type,
                   verified, preferred, note
            FROM algorithm_variant
            WHERE case_id = ?
            ORDER BY preferred DESC, id ASC
            """.trimIndent(),
            arrayOf(caseId)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toAlgorithmVariant())
            }
        }
    }

    /** Makes exactly one formula the case's preferred formula. */
    fun setPreferredVariant(caseId: String, variantId: String): Boolean {
        val db = helper.writableDatabase
        db.beginTransaction()
        return try {
            val belongsToCase = db.rawQuery(
                "SELECT 1 FROM algorithm_variant WHERE id = ? AND case_id = ? LIMIT 1",
                arrayOf(variantId, caseId)
            ).use { it.moveToFirst() }
            if (!belongsToCase) {
                false
            } else {
                db.update(
                    "algorithm_variant",
                    ContentValues().apply { put("preferred", 0) },
                    "case_id = ?",
                    arrayOf(caseId)
                )
                val changed = db.update(
                    "algorithm_variant",
                    ContentValues().apply { put("preferred", 1) },
                    "id = ? AND case_id = ?",
                    arrayOf(variantId, caseId)
                )
                db.update(
                    "user_case_state",
                    ContentValues().apply {
                        put("preferred_variant_id", variantId)
                        put("updated_at", System.currentTimeMillis())
                    },
                    "case_id = ?",
                    arrayOf(caseId)
                )
                db.setTransactionSuccessful()
                changed > 0
            }
        } finally {
            db.endTransaction()
        }
    }

    /** Stores the result of the formula simulation for a user-created variant. */
    fun setVariantVerified(caseId: String, variantId: String, verified: Boolean = true): Boolean {
        val values = ContentValues().apply {
            put("verified", if (verified) 1 else 0)
            put(
                "note",
                if (verified) "用户公式已通过模拟效果校验。"
                else "用户公式尚未通过模拟效果校验。"
            )
        }
        return helper.writableDatabase.update(
            "algorithm_variant",
            values,
            "id = ? AND case_id = ? AND source_type = ?",
            arrayOf(variantId, caseId, "用户自建")
        ) > 0
    }

    /** Deletes only a user-created formula. Preset formulas are intentionally protected. */
    fun deleteUserVariant(caseId: String, variantId: String): Boolean {
        val db = helper.writableDatabase
        db.beginTransaction()
        return try {
            var deleted = false
            var wasPreferred = false
            db.rawQuery(
                "SELECT source_type, preferred FROM algorithm_variant WHERE id = ? AND case_id = ? LIMIT 1",
                arrayOf(variantId, caseId)
            ).use { cursor ->
                if (cursor.moveToFirst() && cursor.getString(0) == "用户自建") {
                    wasPreferred = cursor.getInt(1) == 1
                    deleted = db.delete(
                        "algorithm_variant",
                        "id = ? AND case_id = ? AND source_type = ?",
                        arrayOf(variantId, caseId, "用户自建")
                    ) > 0
                }
            }

            if (deleted && wasPreferred) {
                // Never leave a case without a preferred formula. Prefer the bundled
                // formula when a user-selected formula is removed.
                db.update(
                    "algorithm_variant",
                    ContentValues().apply { put("preferred", 0) },
                    "case_id = ?",
                    arrayOf(caseId)
                )
                val fallbackId = db.rawQuery(
                    """
                    SELECT id
                    FROM algorithm_variant
                    WHERE case_id = ?
                    ORDER BY CASE WHEN source_type = '用户自建' THEN 1 ELSE 0 END, id ASC
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(caseId)
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                if (fallbackId != null) {
                    db.update(
                        "algorithm_variant",
                        ContentValues().apply { put("preferred", 1) },
                        "id = ? AND case_id = ?",
                        arrayOf(fallbackId, caseId)
                    )
                    db.update(
                        "user_case_state",
                        ContentValues().apply {
                            put("preferred_variant_id", fallbackId)
                            put("updated_at", System.currentTimeMillis())
                        },
                        "case_id = ?",
                        arrayOf(caseId)
                    )
                } else {
                    db.update(
                        "user_case_state",
                        ContentValues().apply {
                            putNull("preferred_variant_id")
                            put("updated_at", System.currentTimeMillis())
                        },
                        "case_id = ?",
                        arrayOf(caseId)
                    )
                }
            }

            if (deleted) db.setTransactionSuccessful()
            deleted
        } finally {
            db.endTransaction()
        }
    }

    fun saveSolve(record: SolveRecord): String {
        val id = record.id.ifBlank { UUID.randomUUID().toString() }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("id", id)
                put("session_id", record.sessionId)
                put("session_name", record.sessionName)
                put("scramble", record.scramble)
                put("duration_ms", record.durationMs)
                put("started_at", record.startedAt)
                put("penalty", record.penalty.name)
                put("source", record.source.name)
                put("completeness", record.completeness.name)
                put("notes", record.notes)
            }
            db.insertOrThrow("solve", null, values)
            record.moves.forEach { move ->
                val moveValues = ContentValues().apply {
                    put("solve_id", id)
                    put("ordinal", move.ordinal)
                    put("move_code", move.code)
                    put("elapsed_ms", move.elapsedMs)
                    move.sequence?.let { put("sequence", it) }
                    put("gap", if (move.gap) 1 else 0)
                }
                db.insertOrThrow("move_event", null, moveValues)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return id
    }

    fun listSolves(limit: Int = 200): List<SolveRecord> {
        val db = helper.readableDatabase
        return db.rawQuery(
            "SELECT * FROM solve ORDER BY started_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val solveId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    add(
                        SolveRecord(
                            id = solveId,
                            sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id")),
                            sessionName = cursor.getString(cursor.getColumnIndexOrThrow("session_name")),
                            scramble = cursor.getString(cursor.getColumnIndexOrThrow("scramble")),
                            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")),
                            startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                            penalty = enumValue<Penalty>(cursor.getString(cursor.getColumnIndexOrThrow("penalty")), Penalty.NONE),
                            source = enumValue<SolveSource>(cursor.getString(cursor.getColumnIndexOrThrow("source")), SolveSource.MANUAL),
                            completeness = enumValue<Completeness>(cursor.getString(cursor.getColumnIndexOrThrow("completeness")), Completeness.COMPLETE),
                            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
                            moves = loadMoves(db, solveId)
                        )
                    )
                }
            }
        }
    }

    fun updatePenalty(id: String, penalty: Penalty) {
        helper.writableDatabase.update(
            "solve",
            ContentValues().apply { put("penalty", penalty.name) },
            "id = ?",
            arrayOf(id)
        )
    }

    fun setFavorite(caseId: String, favorite: Boolean) {
        updateUserCase(caseId) { put("favorite", if (favorite) 1 else 0) }
    }

    fun updateNotes(caseId: String, notes: String) {
        updateUserCase(caseId) { put("notes", notes) }
    }

    fun updateMastery(caseId: String, result: String) {
        val db = helper.writableDatabase
        val current = db.rawQuery(
            "SELECT mastery_box FROM user_case_state WHERE case_id = ?",
            arrayOf(caseId)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        val next = when (result) {
            "WRONG" -> (current - 1).coerceAtLeast(0)
            "CORRECT" -> (current + 1).coerceAtMost(5)
            else -> current
        }
        val due = when (next) {
            0 -> System.currentTimeMillis()
            1 -> System.currentTimeMillis() + 86_400_000L
            2 -> System.currentTimeMillis() + 3 * 86_400_000L
            3 -> System.currentTimeMillis() + 7 * 86_400_000L
            4 -> System.currentTimeMillis() + 14 * 86_400_000L
            else -> System.currentTimeMillis() + 30 * 86_400_000L
        }
        updateUserCase(caseId) {
            put("mastery_box", next)
            put("due_at", due)
        }
    }

    fun addUserVariant(caseId: String, notation: String): Boolean {
        val normalizedNotation = normalizedMoves(notation.trim())
            .joinToString(" ") { it.normalized }
        if (normalizedNotation.isBlank()) return false
        val variantId = "$caseId.user.${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put("id", variantId)
            put("case_id", caseId)
            put("notation", normalizedNotation)
            put("normalized_moves", normalizedNotation)
            put("source_type", "用户自建")
            put("verified", 0)
            put("preferred", 0)
            put("note", "用户公式默认未校验；请在真实魔方上核对后再标记。")
        }
        return helper.writableDatabase.insert("algorithm_variant", null, values) != -1L
    }

    fun exportRows(): Pair<List<CubeCase>, List<SolveRecord>> = listCases() to listSolves(10_000)

    fun close() = helper.close()

    private fun updateUserCase(caseId: String, block: ContentValues.() -> Unit) {
        val values = ContentValues().apply {
            block()
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update("user_case_state", values, "case_id = ?", arrayOf(caseId))
    }

    private fun loadMoves(db: SQLiteDatabase, solveId: String): List<RecordedMove> = db.rawQuery(
        "SELECT ordinal, move_code, elapsed_ms, sequence, gap FROM move_event WHERE solve_id = ? ORDER BY ordinal",
        arrayOf(solveId)
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    RecordedMove(
                        ordinal = cursor.getInt(0),
                        code = cursor.getString(1),
                        elapsedMs = cursor.getLong(2),
                        sequence = if (cursor.isNull(3)) null else cursor.getInt(3),
                        gap = cursor.getInt(4) == 1
                    )
                )
            }
        }
    }

    private fun android.database.Cursor.toAlgorithmVariant(): AlgorithmVariant = AlgorithmVariant(
        id = getString(getColumnIndexOrThrow("id")),
        caseId = getString(getColumnIndexOrThrow("case_id")),
        notation = getString(getColumnIndexOrThrow("notation")),
        normalizedMoves = getString(getColumnIndexOrThrow("normalized_moves"))
            .split(" ").filter(String::isNotBlank),
        sourceType = getString(getColumnIndexOrThrow("source_type")),
        verified = getInt(getColumnIndexOrThrow("verified")) == 1,
        preferred = getInt(getColumnIndexOrThrow("preferred")) == 1,
        note = getString(getColumnIndexOrThrow("note"))
    )

    private fun android.database.Cursor.toCubeCase(): CubeCase {
        val caseId = getString(getColumnIndexOrThrow("stable_id"))
        val variant = AlgorithmVariant(
            id = getString(getColumnIndexOrThrow("variant_id")),
            caseId = caseId,
            notation = getString(getColumnIndexOrThrow("notation")),
            normalizedMoves = getString(getColumnIndexOrThrow("normalized_moves"))
                .split(" ").filter(String::isNotBlank),
            sourceType = getString(getColumnIndexOrThrow("source_type")),
            verified = getInt(getColumnIndexOrThrow("verified")) == 1,
            preferred = getInt(getColumnIndexOrThrow("preferred")) == 1,
            note = getString(getColumnIndexOrThrow("note"))
        )
        return CubeCase(
            stableId = caseId,
            stage = enumValue(getString(getColumnIndexOrThrow("stage")), Stage.F2L),
            number = getInt(getColumnIndexOrThrow("case_number")),
            name = getString(getColumnIndexOrThrow("name")),
            alias = getString(getColumnIndexOrThrow("alias")),
            canonicalState = getString(getColumnIndexOrThrow("canonical_state")),
            orientationRule = getString(getColumnIndexOrThrow("orientation_rule")),
            variant = variant,
            masteryBox = getInt(getColumnIndexOrThrow("mastery_box")),
            dueAt = getLong(getColumnIndexOrThrow("due_at")),
            favorite = getInt(getColumnIndexOrThrow("favorite")) == 1,
            notes = getString(getColumnIndexOrThrow("user_notes"))
        )
    }
}

private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
