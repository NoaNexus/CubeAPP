// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.backup

import android.content.Context
import android.net.Uri
import com.cubetrace.app.core.cube.PresetCatalog
import com.cubetrace.app.core.data.LocalRepository
import com.cubetrace.app.core.model.CubeCase
import com.cubetrace.app.core.model.SolveRecord
import com.cubetrace.app.core.model.displayPenalty
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BackupSummary(val cases: Int, val solves: Int, val bytes: Long)

class BackupManager(private val context: Context, private val repository: LocalRepository) {
    fun export(uri: Uri): BackupSummary {
        val (cases, solves) = repository.exportRows()
        val files = linkedMapOf<String, ByteArray>()
        val userData = buildString {
            cases.forEach { append(caseJson(it)).append('\n') }
            solves.forEach { append(solveJson(it)).append('\n') }
        }.toByteArray()
        files["manifest.json"] = """
            {"formatVersion":"1","appVersion":"0.1.0","schemaVersion":1,"presetVersion":"${PresetCatalog.VERSION}","counts":{"cases":${cases.size},"solves":${solves.size}},"optionalFeatures":["move-events"]}
        """.trimIndent().toByteArray()
        files["user-data.jsonl"] = userData
        files["licenses/NOTICE.md"] = "CubeTrace is GPL-3.0-only. Third-party provenance is shipped in the source tree.\n".toByteArray()
        val checksums = files.entries.joinToString("\n") { "${sha256(it.value)}  ${it.key}" } + "\n"
        files["checksums.sha256"] = checksums.toByteArray()

        var bytesWritten = 0L
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                files.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                    bytesWritten += bytes.size
                }
            }
        } ?: error("无法写入备份文件")
        return BackupSummary(cases.size, solves.size, bytesWritten)
    }

    private fun caseJson(item: CubeCase): String = """
        {"type":"case","stableId":"${escape(item.stableId)}","stage":"${item.stage.name}","number":${item.number},"name":"${escape(item.name)}","canonicalState":"${item.canonicalState}","notation":"${escape(item.variant.notation)}","favorite":${item.favorite},"masteryBox":${item.masteryBox},"notes":"${escape(item.notes)}"}
    """.trimIndent()

    private fun solveJson(item: SolveRecord): String = """
        {"type":"solve","id":"${escape(item.id)}","session":"${escape(item.sessionName)}","scramble":"${escape(item.scramble)}","durationMs":${item.durationMs},"startedAt":${item.startedAt},"penalty":"${displayPenalty(item)}","source":"${item.source.name}","complete":${item.completeness == com.cubetrace.app.core.model.Completeness.COMPLETE},"moves":${item.moves.size}}
    """.trimIndent()

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
