// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cubetrace.app.core.model.AppSettings
import com.cubetrace.app.core.model.SmartCubeFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cubeTraceDataStore by preferencesDataStore(name = "cubetrace_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val crossColor = stringPreferencesKey("cross_color")
        val notation = stringPreferencesKey("notation")
        val pauseThreshold = intPreferencesKey("pause_threshold")
        val inspection = booleanPreferencesKey("inspection")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val assistLabels = booleanPreferencesKey("assist_labels")
        val vibration = booleanPreferencesKey("vibration")
        val sound = booleanPreferencesKey("sound")
        val gyroFollow = booleanPreferencesKey("gyro_follow")
        val smartCubeFrame = stringPreferencesKey("smart_cube_frame")
        val smartAutoInspection = booleanPreferencesKey("smart_auto_inspection")
        val recordChaseHints = booleanPreferencesKey("record_chase_hints")
    }

    val settings: Flow<AppSettings> = context.cubeTraceDataStore.data.map { prefs ->
        AppSettings(
            crossColor = prefs[Keys.crossColor] ?: "白",
            notation = prefs[Keys.notation] ?: "WCA",
            pauseThresholdMs = prefs[Keys.pauseThreshold] ?: 250,
            inspectionEnabled = prefs[Keys.inspection] ?: true,
            reducedMotion = prefs[Keys.reducedMotion] ?: false,
            assistLabels = prefs[Keys.assistLabels] ?: false,
            vibrationEnabled = prefs[Keys.vibration] ?: true,
            soundEnabled = prefs[Keys.sound] ?: false,
            gyroFollowEnabled = prefs[Keys.gyroFollow] ?: true,
            smartCubeFrame = prefs[Keys.smartCubeFrame]
                ?.let { saved -> SmartCubeFrame.entries.firstOrNull { it.name == saved } }
                ?: SmartCubeFrame.OFFICIAL_WHITE_GREEN,
            smartAutoInspectionEnabled = prefs[Keys.smartAutoInspection] ?: false,
            recordChaseHintsEnabled = prefs[Keys.recordChaseHints] ?: true
        )
    }

    suspend fun setReducedMotion(value: Boolean) = context.cubeTraceDataStore.edit { it[Keys.reducedMotion] = value }
    suspend fun setAssistLabels(value: Boolean) = context.cubeTraceDataStore.edit { it[Keys.assistLabels] = value }
    suspend fun setVibration(value: Boolean) = context.cubeTraceDataStore.edit { it[Keys.vibration] = value }
    suspend fun setSound(value: Boolean) = context.cubeTraceDataStore.edit { it[Keys.sound] = value }
    suspend fun setGyroFollow(value: Boolean) = context.cubeTraceDataStore.edit { it[Keys.gyroFollow] = value }
    suspend fun setSmartCubeFrame(value: SmartCubeFrame) = context.cubeTraceDataStore.edit { it[Keys.smartCubeFrame] = value.name }
    suspend fun setSmartAutoInspection(value: Boolean) = context.cubeTraceDataStore.edit { it[Keys.smartAutoInspection] = value }
    suspend fun setRecordChaseHints(value: Boolean) = context.cubeTraceDataStore.edit { it[Keys.recordChaseHints] = value }
    suspend fun setPauseThreshold(value: Int) = context.cubeTraceDataStore.edit { it[Keys.pauseThreshold] = value }
    suspend fun setCrossColor(value: String) = context.cubeTraceDataStore.edit { it[Keys.crossColor] = value }
}
