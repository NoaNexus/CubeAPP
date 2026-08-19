// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cubetrace.app.core.backup.BackupManager
import com.cubetrace.app.core.cube.CubeState
import com.cubetrace.app.core.cube.PresetCatalog
import com.cubetrace.app.core.cube.ScrambleGenerator
import com.cubetrace.app.core.cube.cubeStateFromFacelets
import com.cubetrace.app.core.cube.inverse
import com.cubetrace.app.core.cube.normalizedMoves
import com.cubetrace.app.core.cube.moyuMoveToYellowTopBlueFront
import com.cubetrace.app.core.cube.yellowTopBlueFrontToOfficialFacelets
import com.cubetrace.app.core.cube.yellowTopBlueFrontToOfficialMove
import com.cubetrace.app.core.cube.verifyF2lStage
import com.cubetrace.app.core.cube.verifyOllStage
import com.cubetrace.app.core.cube.verifyPllStage
import com.cubetrace.app.core.data.LocalRepository
import com.cubetrace.app.core.data.SettingsRepository
import com.cubetrace.app.core.device.DeviceLiveState
import com.cubetrace.app.core.device.DeviceStatus
import com.cubetrace.app.core.device.NearbyV10Device
import com.cubetrace.app.core.device.Quaternion
import com.cubetrace.app.core.device.V10DeviceManager
import com.cubetrace.app.core.device.V10Message
import com.cubetrace.app.core.model.AppSettings
import com.cubetrace.app.core.model.AlgorithmVariant
import com.cubetrace.app.core.model.CaseFilter
import com.cubetrace.app.core.model.Completeness
import com.cubetrace.app.core.model.CubeCase
import com.cubetrace.app.core.model.Penalty
import com.cubetrace.app.core.model.RecordedMove
import com.cubetrace.app.core.model.SolveRecord
import com.cubetrace.app.core.model.SolveSource
import com.cubetrace.app.core.model.Stage
import com.cubetrace.app.core.model.SmartCubeFrame
import com.cubetrace.app.core.model.TrainingResult
import com.cubetrace.app.core.model.calculateStats
import com.cubetrace.app.core.model.displayPenalty
import com.cubetrace.app.core.model.formatDuration
import com.cubetrace.app.core.model.penaltyAdjustedMs
import com.cubetrace.app.core.render3d.Cube3DView
import com.cubetrace.app.core.render3d.f2lFocus
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private object CubeTraceColors {
    val graphite = Color(0xFF13222D)
    val mist = Color(0xFFF5F7F8)
    val paper = Color(0xFFFFFFFF)
    val track = Color(0xFF1769D1)
    val trackSoft = Color(0xFFE7F0FB)
    val signal = Color(0xFFE18418)
    val fault = Color(0xFFC23B3B)
    val line = Color(0xFFD6E0E7)
    val muted = Color(0xFF61717B)
    val blueWash = Color(0xFFEAF3FB)
    val diagramGray = Color(0xFFD1DCE2)
    val diagramNeutral = Color(0xFF404040)
    val diagramGrayLight = Color(0xFFF5F8F9)
    val diagramBlue = Color(0xFF1675D1)
    val diagramBlueDark = Color(0xFF0D4E91)
    val diagramBlueLight = Color(0xFF8EBDEB)
    val whiteFace = Color(0xFFFFFEF8)
    val yellowFace = Color(0xFFFFE018)
    val redFace = Color(0xFFEF3340)
    val orangeFace = Color(0xFFFF8A1E)
    val blueFace = Color(0xFF0878E8)
    val greenFace = Color(0xFF08B878)
    val gap = Color(0xFF14232D)
}

enum class AppSection(val label: String, val mark: String) {
    FORMULA("公式", "ƒ"),
    TRAINING("训练", "◎"),
    TIMER("计时", "◷"),
    RECORDS("记录", "▥")
}

enum class TimerPhase(val label: String) {
    IDLE("按住准备"),
    READY("松手开始"),
    INSPECTION("观察中"),
    WAITING_CUBE("等待魔方转动"),
    RUNNING("正在计时"),
    STOPPED("等待确认")
}

enum class SmartScramblePhase {
    READY_TO_SCRAMBLE,
    SCRAMBLING,
    ERROR,
    READY_TO_INSPECT,
    INSPECTION,
    SOLVING,
    SOLVED
}

data class TimerSnapshot(
    val phase: TimerPhase = TimerPhase.IDLE,
    val elapsedMs: Long = 0L,
    val scramble: String = ScrambleGenerator.generate(),
    val pendingPenalty: Penalty = Penalty.NONE,
    val smartScrambleProgress: Int = 0,
    val inspectionRemainingMs: Long = 0L,
    val smartAuto: Boolean = false,
    val smartPhase: SmartScramblePhase = SmartScramblePhase.READY_TO_SCRAMBLE,
    val smartError: String? = null,
    val smartCorrection: String? = null
)

data class TimerClockState(
    val phase: TimerPhase,
    val elapsedMs: Long,
    val inspectionRemainingMs: Long
)

data class FormulaVerificationResult(
    val success: Boolean,
    val message: String
)

class CubeTraceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val backupManager = BackupManager(application, repository)
    private val deviceManager = V10DeviceManager(application)

    private val _section = MutableStateFlow(AppSection.FORMULA)
    val section: StateFlow<AppSection> = _section.asStateFlow()
    private val _cases = MutableStateFlow<List<CubeCase>>(emptyList())
    val cases: StateFlow<List<CubeCase>> = _cases.asStateFlow()
    private val _solves = MutableStateFlow<List<SolveRecord>>(emptyList())
    val solves: StateFlow<List<SolveRecord>> = _solves.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _stage = MutableStateFlow<Stage?>(null)
    val stage: StateFlow<Stage?> = _stage.asStateFlow()
    private val _filter = MutableStateFlow(CaseFilter.ALL)
    val filter: StateFlow<CaseFilter> = _filter.asStateFlow()
    private val _selectedCase = MutableStateFlow<CubeCase?>(null)
    val selectedCase: StateFlow<CubeCase?> = _selectedCase.asStateFlow()
    private val _selectedVariants = MutableStateFlow<List<AlgorithmVariant>>(emptyList())
    val selectedVariants: StateFlow<List<AlgorithmVariant>> = _selectedVariants.asStateFlow()
    private val _selectedSolve = MutableStateFlow<SolveRecord?>(null)
    val selectedSolve: StateFlow<SolveRecord?> = _selectedSolve.asStateFlow()
    private val _timer = MutableStateFlow(TimerSnapshot())
    val timer: StateFlow<TimerSnapshot> = _timer.asStateFlow()
    val timerPage: StateFlow<TimerSnapshot> = timer
        .map { it.copy(elapsedMs = 0L, inspectionRemainingMs = 0L) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _timer.value)
    val timerClock: StateFlow<TimerClockState> = timer
        .map { TimerClockState(it.phase, it.elapsedMs, it.inspectionRemainingMs) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TimerClockState(_timer.value.phase, _timer.value.elapsedMs, _timer.value.inspectionRemainingMs)
        )
    private val _trainingQueue = MutableStateFlow<List<CubeCase>>(emptyList())
    val trainingQueue: StateFlow<List<CubeCase>> = _trainingQueue.asStateFlow()
    private val _trainingIndex = MutableStateFlow(0)
    val trainingIndex: StateFlow<Int> = _trainingIndex.asStateFlow()
    private val _trainingRevealed = MutableStateFlow(false)
    val trainingRevealed: StateFlow<Boolean> = _trainingRevealed.asStateFlow()
    private val _deviceStatusMessage = MutableStateFlow("")
    val deviceStatusMessage: StateFlow<String> = _deviceStatusMessage.asStateFlow()
    private var timerJob: Job? = null
    private var casesJob: Job? = null
    private var solvesJob: Job? = null
    private var timerStartElapsed = 0L
    private var configuredSmartScramble = ""
    private var configuredSmartFrame = SmartCubeFrame.OFFICIAL_WHITE_GREEN
    private var activeSmartFrame = SmartCubeFrame.OFFICIAL_WHITE_GREEN
    private var smartExpectedStates: List<String> = emptyList()
    private var smartEventTokenEnds: List<Int> = emptyList()
    private var smartScrambleTokenCount = 0
    private var smartEventCursor = 0
    private var lastSmartSequence: Int? = null
    private val smartRecoveryMoves = mutableListOf<String>()
    private var lastSmartFacelets: String? = null
    /** The first face move after a solve; it must be followed by its inverse. */
    private var smartCompletionMove: String? = null

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )
    val deviceStatus = deviceManager.status
    val nearbyDevices = deviceManager.devices
    val deviceMessages = deviceManager.messages
    val deviceLiveState = deviceManager.liveState
    // Sensor packets arrive much faster than the rest of the page needs to
    // recompose. Keep a low-frequency UI snapshot for the page and expose the
    // raw orientation separately to the frame-paced 3D renderer.
    val deviceUiState: StateFlow<DeviceLiveState> = deviceLiveState
        .map { state -> state.copy(orientation = if (state.orientation == null) null else Quaternion.identity()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeviceLiveState())
    val deviceOrientation: StateFlow<Quaternion?> = deviceLiveState
        .map { it.orientation }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refreshCases()
        refreshSolves()
    }

    fun selectSection(section: AppSection) { _section.value = section }
    fun setQuery(value: String) { _query.value = value; refreshCases() }
    fun setStage(value: Stage?) { _stage.value = value; refreshCases() }
    fun setFilter(value: CaseFilter) { _filter.value = value; refreshCases() }
    fun openCase(item: CubeCase) {
        _selectedCase.value = item
        _selectedVariants.value = repository.listVariants(item.stableId)
    }
    fun closeCase() {
        _selectedCase.value = null
        _selectedVariants.value = emptyList()
    }
    fun openSolve(item: SolveRecord) { _selectedSolve.value = item }
    fun closeSolve() { _selectedSolve.value = null }
    fun goToTraining(item: CubeCase? = null) {
        _selectedCase.value = null
        item?.let { _trainingQueue.value = listOf(it) }
        _section.value = AppSection.TRAINING
    }

    fun toggleFavorite(item: CubeCase) {
        repository.setFavorite(item.stableId, !item.favorite)
        refreshCases()
        _selectedCase.value = repository.getCase(item.stableId)
    }

    fun saveCaseNotes(item: CubeCase, notes: String) {
        repository.updateNotes(item.stableId, notes)
        refreshCases()
        _selectedCase.value = repository.getCase(item.stableId)
    }

    fun addVariant(item: CubeCase, notation: String): Boolean {
        val saved = repository.addUserVariant(item.stableId, notation.trim())
        if (saved) {
            refreshCases()
            _selectedCase.value = repository.getCase(item.stableId)
            _selectedVariants.value = repository.listVariants(item.stableId)
        }
        return saved
    }

    fun selectPreferredVariant(item: CubeCase, variantId: String): Boolean {
        val selected = repository.setPreferredVariant(item.stableId, variantId)
        if (selected) {
            refreshCases()
            _selectedCase.value = repository.getCase(item.stableId)
            _selectedVariants.value = repository.listVariants(item.stableId)
        }
        return selected
    }

    fun verifyVariant(item: CubeCase, variantId: String): FormulaVerificationResult {
        val variant = repository.listVariants(item.stableId).firstOrNull { it.id == variantId }
            ?: return FormulaVerificationResult(false, "找不到这条用户公式")
        val candidateMoves = normalizedMoves(variant.notation)
        if (candidateMoves.isEmpty()) {
            return FormulaVerificationResult(false, "校验失败：公式无法解析")
        }

        val verificationSetups = buildList {
            if (CubeState.fromFacelets(item.canonicalState) != null) add(item.canonicalState)
            // OLL/PLL entries can contain a whole-cube regrip or a wide-layer
            // move in their printed formula. When the chart's display frame
            // and that notation frame differ, retry from the exact inverse
            // setup of the bundled formula. This still checks only the CFOP
            // stage goal, never equality of unrelated gray stickers.
            if (item.stage == Stage.OLL || item.stage == Stage.PLL) {
                val bundledNotation = PresetCatalog.all()
                    .firstOrNull { it.stableId == item.stableId }
                    ?.variant
                    ?.notation
                if (bundledNotation != null) {
                    add(CubeState.solved().apply(normalizedMoves(bundledNotation).inverse()).asFacelets())
                }
            }
        }.distinct()
        if (verificationSetups.isEmpty()) {
            return FormulaVerificationResult(false, "校验失败：案例初始状态无效")
        }
        val stagePassed = verificationSetups.any { setupFacelets ->
            val candidateResult = CubeState.fromFacelets(setupFacelets)!!.apply(candidateMoves)
            when (item.stage) {
                Stage.F2L -> verifyF2lStage(setupFacelets, candidateResult.asFacelets())
                Stage.OLL -> verifyOllStage(setupFacelets, candidateResult.asFacelets())
                Stage.PLL -> verifyPllStage(setupFacelets, candidateResult.asFacelets())
                Stage.CROSS -> candidateResult.isSolved()
            }
        }
        if (!stagePassed) {
            val reason = when (item.stage) {
                Stage.F2L -> "十字、目标 F2L 槽位或已还原槽位未满足条件"
                Stage.OLL -> "顶层没有全部翻色"
                Stage.PLL -> "顶层排列没有完成"
                Stage.CROSS -> "魔方没有还原"
            }
            return FormulaVerificationResult(false, "校验未通过：$reason；请检查方向、撇号或顺序")
        }
        if (!repository.setVariantVerified(item.stableId, variantId, true)) {
            return FormulaVerificationResult(false, "校验结果保存失败，请稍后重试")
        }
        refreshCases()
        _selectedCase.value = repository.getCase(item.stableId)
        _selectedVariants.value = repository.listVariants(item.stableId)
        return FormulaVerificationResult(
            true,
            when (item.stage) {
                Stage.F2L -> "校验通过：十字、目标 F2L 槽位和已还原槽位满足条件"
                Stage.OLL -> "校验通过：顶层朝向完成，不比较其他灰色块"
                Stage.PLL -> "校验通过：顶层排列完成，不比较其他灰色块"
                Stage.CROSS -> "校验通过：魔方已还原"
            }
        )
    }

    fun deleteVariant(item: CubeCase, variantId: String): Boolean {
        val deleted = repository.deleteUserVariant(item.stableId, variantId)
        if (deleted) {
            refreshCases()
            _selectedCase.value = repository.getCase(item.stableId)
            _selectedVariants.value = repository.listVariants(item.stableId)
        }
        return deleted
    }

    fun startTraining() {
        val queue = _trainingQueue.value.ifEmpty {
            _cases.value.filter { it.stage != Stage.CROSS }.shuffled().take(12).also { _trainingQueue.value = it }
        }
        if (queue.isNotEmpty()) {
            _trainingIndex.value = 0
            _trainingRevealed.value = false
        }
    }

    fun revealTraining() { _trainingRevealed.value = true }

    fun answerTraining(result: TrainingResult) {
        val current = _trainingQueue.value.getOrNull(_trainingIndex.value) ?: return
        if (result == TrainingResult.WRONG || result == TrainingResult.CORRECT) {
            repository.updateMastery(current.stableId, result.name)
        }
        val next = _trainingIndex.value + 1
        _trainingIndex.value = next.coerceAtMost(_trainingQueue.value.size)
        _trainingRevealed.value = false
        refreshCases()
    }

    fun regenerateScramble() {
        timerJob?.cancel()
        smartRecoveryMoves.clear()
        smartCompletionMove = null
        _timer.value = TimerSnapshot(scramble = ScrambleGenerator.generate())
    }

    /**
     * Builds the expected physical move path for the current scramble. The
     * device reports quarter-turn events, so a printed half turn is expanded
     * to two events while a prime move remains one inverse event.
     */
    fun configureSmartScramble(
        scramble: String,
        frame: SmartCubeFrame = settings.value.smartCubeFrame
    ) {
        if (configuredSmartScramble == scramble && configuredSmartFrame == frame) return
        configuredSmartScramble = scramble
        configuredSmartFrame = frame
        activeSmartFrame = frame
        val displayScramble = smartScrambleNotation(scramble, frame)
        val tokens = displayScramble.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        smartScrambleTokenCount = tokens.size
        val states = mutableListOf<String>()
        val tokenEnds = mutableListOf<Int>()
        var state = CubeState.solved()
        tokens.forEach { token ->
            val move = normalizedMoves(token).firstOrNull() ?: return@forEach
            val normalizedTurns = move.turns.mod(4)
            if (normalizedTurns == 0) return@forEach
            val eventMove = move.copy(turns = if (normalizedTurns == 3) 3 else 1)
            val eventCount = if (normalizedTurns == 2) 2 else 1
            repeat(eventCount) {
                state = state.apply(eventMove)
                states += state.asFacelets()
            }
            tokenEnds += states.lastIndex
        }
        smartExpectedStates = states
        smartEventTokenEnds = tokenEnds
        smartEventCursor = 0
        smartRecoveryMoves.clear()
        smartCompletionMove = null
        // Keep the current device sequence as the baseline when only the
        // scramble text changes. This prevents the first subsequent turn from
        // being mistaken for the new baseline.
        _timer.value = _timer.value.copy(
            smartScrambleProgress = 0,
            inspectionRemainingMs = 0L,
            smartAuto = false,
            smartPhase = SmartScramblePhase.READY_TO_SCRAMBLE,
            smartError = null,
            smartCorrection = null
        )
    }

    /** Returns the notation shown to the user and expected from the device. */
    fun smartScrambleNotation(scramble: String, frame: SmartCubeFrame): String =
        if (frame == SmartCubeFrame.OFFICIAL_WHITE_GREEN) {
            scramble
        } else {
            scramble.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                .joinToString(" ") { moyuMoveToYellowTopBlueFront(it) }
        }

    /** Converts the internal yellow-top/blue-front device state for display. */
    fun smartDisplayState(state: DeviceLiveState, frame: SmartCubeFrame): DeviceLiveState {
        if (frame == SmartCubeFrame.PERSONAL_YELLOW_BLUE) return state
        return state.copy(
            facelets = state.facelets?.let(::yellowTopBlueFrontToOfficialFacelets),
            lastMove = state.lastMove?.let(::yellowTopBlueFrontToOfficialMove),
            animationFromFacelets = state.animationFromFacelets?.let(::yellowTopBlueFrontToOfficialFacelets)
        )
    }

    /** Receives low-rate facelet/move updates from the connected smart cube. */
    fun onSmartCubeUpdate(state: DeviceLiveState) {
        val sequence = state.sequence
        if (!state.synced || sequence == null) {
            if (!state.synced) {
                lastSmartSequence = null
                lastSmartFacelets = null
                smartCompletionMove = null
            }
            return
        }
        state.facelets?.let { lastSmartFacelets = it }
        val previousSequence = lastSmartSequence
        if (previousSequence == null) {
            lastSmartSequence = sequence
            return
        }
        val difference = (sequence - previousSequence + 256) % 256
        if (difference == 0) return
        lastSmartSequence = sequence

        val currentTimer = _timer.value
        if (
            currentTimer.phase == TimerPhase.STOPPED &&
            currentTimer.smartAuto &&
            currentTimer.smartPhase == SmartScramblePhase.SOLVED
        ) {
            handlePostSolveFaceMove(state, difference)
            return
        }
        if (currentTimer.phase == TimerPhase.INSPECTION || currentTimer.phase == TimerPhase.WAITING_CUBE) {
            startTimer(smartAuto = true)
            return
        }
        if (currentTimer.phase == TimerPhase.RUNNING && currentTimer.smartAuto) {
            val solved = state.facelets?.let { CubeState.fromFacelets(it)?.isSolved() } == true
            if (solved) stopTimer()
            return
        }
        if (currentTimer.smartAuto || currentTimer.phase !in setOf(TimerPhase.IDLE, TimerPhase.READY)) return

        val facelets = state.facelets?.let {
            if (activeSmartFrame == SmartCubeFrame.OFFICIAL_WHITE_GREEN) {
                yellowTopBlueFrontToOfficialFacelets(it)
            } else {
                it
            }
        } ?: return
        val stateBeforeMove = smartExpectedStates.getOrNull(smartEventCursor - 1)
            ?: CubeState.solved().asFacelets()
        if (facelets == stateBeforeMove) {
            smartRecoveryMoves.clear()
            _timer.value = currentTimer.copy(
                smartPhase = if (smartEventCursor == 0) {
                    SmartScramblePhase.READY_TO_SCRAMBLE
                } else {
                    SmartScramblePhase.SCRAMBLING
                },
                smartError = null,
                smartCorrection = null
            )
            return
        }

        val remaining = (smartExpectedStates.size - smartEventCursor).coerceAtLeast(0)
        // The V10 sequence counter is the authoritative number of physical
        // moves since the previous update. Never accept a state from an
        // earlier future position: doing so would silently hide a missed or
        // extra turn and recreate the old one-step-behind bug.
        val matchedStep = if (difference in 1..remaining &&
            smartExpectedStates.getOrNull(smartEventCursor + difference - 1) == facelets
        ) {
            difference
        } else {
            null
        }
        if (matchedStep != null) {
            smartEventCursor += matchedStep
            val completedTokens = smartEventTokenEnds.count { it < smartEventCursor }
            val scrambleComplete = smartEventCursor >= smartExpectedStates.size
            _timer.value = currentTimer.copy(
                smartScrambleProgress = completedTokens,
                smartPhase = if (scrambleComplete) {
                    SmartScramblePhase.READY_TO_INSPECT
                } else {
                    SmartScramblePhase.SCRAMBLING
                },
                smartError = null,
                smartCorrection = null
            )
            if (scrambleComplete && settings.value.smartAutoInspectionEnabled) {
                startSmartInspection()
            }
        } else {
            val displayedMove = state.lastMove?.let { move ->
                if (activeSmartFrame == SmartCubeFrame.OFFICIAL_WHITE_GREEN) {
                    yellowTopBlueFrontToOfficialMove(move)
                } else {
                    move
                }
            }
            displayedMove
                ?.let { normalizedMoves(it).firstOrNull()?.normalized }
                ?.let { move ->
                    val inverse = normalizedMoves(move).firstOrNull()?.inverse()?.normalized
                    if (smartRecoveryMoves.lastOrNull() == inverse) {
                        smartRecoveryMoves.removeAt(smartRecoveryMoves.lastIndex)
                    } else {
                        smartRecoveryMoves += move
                    }
                }
            val undo = smartRecoveryMoves.lastOrNull()
                ?.let { normalizedMoves(it).firstOrNull()?.inverse()?.normalized }
            _timer.value = currentTimer.copy(
                smartPhase = SmartScramblePhase.ERROR,
                smartError = "动作与当前打乱不一致，已暂停进度",
                smartCorrection = undo?.let { "请执行 $it 撤销这一步，再继续" }
                    ?: "请回到上一步状态，再继续执行下一步"
            )
        }
    }

    fun startSmartInspection() {
        val current = _timer.value
        if (smartScrambleTokenCount == 0 || current.smartScrambleProgress < smartScrambleTokenCount) return
        if (current.smartPhase != SmartScramblePhase.READY_TO_INSPECT) return
        if (current.phase !in setOf(TimerPhase.IDLE, TimerPhase.READY)) return
        timerJob?.cancel()
        _timer.value = current.copy(
            phase = TimerPhase.INSPECTION,
            elapsedMs = 0L,
            inspectionRemainingMs = 15_000L,
            pendingPenalty = Penalty.NONE,
            smartAuto = true,
            smartPhase = SmartScramblePhase.INSPECTION,
            smartError = null,
            smartCorrection = null
        )
        timerJob = viewModelScope.launch {
            val end = SystemClock.elapsedRealtime() + 15_000L
            while (true) {
                val remaining = (end - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                _timer.value = _timer.value.copy(inspectionRemainingMs = remaining)
                if (remaining == 0L) {
                    _timer.value = _timer.value.copy(phase = TimerPhase.WAITING_CUBE)
                    break
                }
                delay(8L)
            }
        }
    }

    /**
     * A completed smart solve is confirmed by two real face turns: one move
     * followed by its inverse, for example R R' or U U'. This avoids using a
     * gyro-only gesture, and makes the next round begin only after the cube
     * has physically returned to its solved state.
     */
    private fun handlePostSolveFaceMove(state: DeviceLiveState, difference: Int) {
        val moves = state.moveHistory.ifEmpty { listOfNotNull(state.lastMove) }
        if (difference !in 1..2 || moves.size != difference) {
            smartCompletionMove = null
            _timer.value = _timer.value.copy(
                smartError = "动作包中包含多步转动",
                smartCorrection = "请转动一个面，再反向转回（例如 R R'）；完成后会自动保存"
            )
            return
        }

        var pairCompleted = false
        moves.forEach { rawMove ->
            val move = smartFaceMove(rawMove)
            if (move == null) {
                smartCompletionMove = null
                pairCompleted = false
                _timer.value = _timer.value.copy(
                    smartError = "未识别到有效的面转动",
                    smartCorrection = "请用 R、U、F、D、L 或 B 转动一次，再反向转回"
                )
                return@forEach
            }

            val expectedInverse = smartCompletionMove?.let(::inverseSmartMove)
            if (expectedInverse == null) {
                smartCompletionMove = move
                pairCompleted = false
                _timer.value = _timer.value.copy(
                    smartError = null,
                    smartCorrection = "已记录 $move，请执行 ${inverseSmartMove(move) ?: "反向动作"}"
                )
            } else if (move == expectedInverse) {
                smartCompletionMove = null
                pairCompleted = true
                _timer.value = _timer.value.copy(smartError = null, smartCorrection = null)
            } else {
                // Treat a new face move as the beginning of a fresh pair. The
                // result is never saved until the displayed inverse is received.
                smartCompletionMove = move
                pairCompleted = false
                _timer.value = _timer.value.copy(
                    smartError = "还原完成，请执行反向动作",
                    smartCorrection = "已记录 $move，请执行 ${inverseSmartMove(move) ?: "反向动作"}"
                )
            }
        }

        if (pairCompleted && smartCompletionMove == null) {
            val solved = state.facelets?.let { CubeState.fromFacelets(it)?.isSolved() } == true
            if (solved) {
                saveTimer()
                regenerateScramble()
            } else {
                _timer.value = _timer.value.copy(
                    smartError = "反向动作已收到，但局面还没有回到还原状态",
                    smartCorrection = "请先恢复还原状态，再执行一次面转动和反向转回"
                )
            }
        }
    }

    private fun smartFaceMove(move: String?): String? {
        val displayed = move?.let {
            if (activeSmartFrame == SmartCubeFrame.OFFICIAL_WHITE_GREEN) {
                yellowTopBlueFrontToOfficialMove(it)
            } else {
                it
            }
        } ?: return null
        val parsed = normalizedMoves(displayed).firstOrNull() ?: return null
        if (parsed.symbol.uppercase() !in setOf("U", "R", "F", "D", "L", "B")) return null
        return parsed.normalized
    }

    private fun inverseSmartMove(move: String): String? =
        normalizedMoves(move).firstOrNull()?.inverse()?.normalized

    fun prepareTimer() {
        if (_timer.value.phase == TimerPhase.IDLE) {
            _timer.value = _timer.value.copy(phase = TimerPhase.READY, elapsedMs = 0L)
        }
    }

    fun toggleTimer() {
        when (_timer.value.phase) {
            TimerPhase.READY -> startTimer()
            TimerPhase.RUNNING -> stopTimer()
            TimerPhase.IDLE -> prepareTimer()
            TimerPhase.INSPECTION, TimerPhase.WAITING_CUBE -> Unit
            TimerPhase.STOPPED -> Unit
        }
    }

    private fun startTimer(smartAuto: Boolean = false) {
        timerStartElapsed = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        _timer.value = _timer.value.copy(
            phase = TimerPhase.RUNNING,
            elapsedMs = 0L,
            inspectionRemainingMs = 0L,
            pendingPenalty = Penalty.NONE,
            smartAuto = smartAuto || _timer.value.smartAuto,
            smartPhase = if (smartAuto || _timer.value.smartAuto) {
                SmartScramblePhase.SOLVING
            } else {
                _timer.value.smartPhase
            }
        )
        timerJob = viewModelScope.launch {
            while (true) {
                _timer.value = _timer.value.copy(elapsedMs = SystemClock.elapsedRealtime() - timerStartElapsed)
                // Keep the timer responsive on high-refresh displays. The timer
                // state is collected only by TimerRoute, so this does not
                // invalidate the formula grid or the rest of the app tree.
                delay(8L)
            }
        }
    }

    fun stopTimer() {
        if (_timer.value.phase != TimerPhase.RUNNING) return
        timerJob?.cancel()
        val elapsed = SystemClock.elapsedRealtime() - timerStartElapsed
        val smartSolve = _timer.value.smartAuto
        _timer.value = _timer.value.copy(
            phase = TimerPhase.STOPPED,
            elapsedMs = elapsed,
            smartPhase = if (smartSolve) SmartScramblePhase.SOLVED else _timer.value.smartPhase
        )
        if (smartSolve) {
            smartCompletionMove = null
        }
    }

    fun setTimerPenalty(penalty: Penalty) { _timer.value = _timer.value.copy(pendingPenalty = penalty) }

    fun saveTimer() {
        if (_timer.value.phase != TimerPhase.STOPPED) return
        repository.saveSolve(
            SolveRecord(
                id = UUID.randomUUID().toString(),
                sessionId = "main",
                sessionName = "主 session",
                scramble = _timer.value.scramble,
                durationMs = _timer.value.elapsedMs,
                startedAt = System.currentTimeMillis(),
                penalty = _timer.value.pendingPenalty,
                source = if (_timer.value.smartAuto) SolveSource.V10_AI else SolveSource.MANUAL,
                completeness = Completeness.COMPLETE,
                moves = emptyList()
            )
        )
        refreshSolves()
        smartCompletionMove = null
        _timer.value = TimerSnapshot()
    }

    fun abandonTimer() { timerJob?.cancel(); smartCompletionMove = null; _timer.value = TimerSnapshot() }

    fun setReducedMotion(value: Boolean) { viewModelScope.launch { settingsRepository.setReducedMotion(value) } }
    fun setAssistLabels(value: Boolean) { viewModelScope.launch { settingsRepository.setAssistLabels(value) } }
    fun setVibration(value: Boolean) { viewModelScope.launch { settingsRepository.setVibration(value) } }
    fun setGyroFollow(value: Boolean) { viewModelScope.launch { settingsRepository.setGyroFollow(value) } }
    fun setSmartCubeFrame(value: SmartCubeFrame) { viewModelScope.launch { settingsRepository.setSmartCubeFrame(value) } }
    fun setSmartAutoInspection(value: Boolean) { viewModelScope.launch { settingsRepository.setSmartAutoInspection(value) } }
    fun calibrateDeviceOrientation() { deviceManager.calibrateOrientation() }
    fun setPauseThreshold(value: Int) { viewModelScope.launch { settingsRepository.setPauseThreshold(value) } }

    fun scanDevices() { deviceManager.startScan() }
    fun stopScan() { deviceManager.stopScan() }
    fun connectDevice(item: NearbyV10Device) { deviceManager.connect(item) }
    fun disconnectDevice() { deviceManager.close() }
    fun requestFacelets() = deviceManager.request(com.cubetrace.app.core.device.V10Protocol.SafeCommand.REQUEST_FACELETS)
    fun requestBattery() = deviceManager.request(com.cubetrace.app.core.device.V10Protocol.SafeCommand.REQUEST_BATTERY)
    fun exportBackup(uri: Uri, onDone: (String) -> Unit) = viewModelScope.launch {
        runCatching { backupManager.export(uri) }
            .onSuccess { onDone("已导出 ${it.solves} 条成绩、${it.cases} 个案例") }
            .onFailure { onDone("备份失败：${it.message ?: "无法写入文件"}") }
    }

    fun stats(): com.cubetrace.app.core.model.CubeStats = calculateStats(_solves.value.reversed())

    private fun refreshCases() {
        casesJob?.cancel()
        val stage = _stage.value
        val query = _query.value
        val filter = _filter.value
        casesJob = viewModelScope.launch(Dispatchers.IO) {
            _cases.value = repository.listCases(stage, query, filter)
        }
    }

    private fun refreshSolves() {
        solvesJob?.cancel()
        solvesJob = viewModelScope.launch(Dispatchers.IO) {
            _solves.value = repository.listSolves()
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        casesJob?.cancel()
        solvesJob?.cancel()
        deviceManager.close()
        repository.close()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    private val appViewModel by viewModels<CubeTraceViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CubeTraceApp(appViewModel) }
    }
}

@Composable
private fun CubeTraceApp(viewModel: CubeTraceViewModel) {
    val section by viewModel.section.collectAsStateWithLifecycle()
    val cases by viewModel.cases.collectAsStateWithLifecycle()
    val solves by viewModel.solves.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedStage by viewModel.stage.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selectedCase by viewModel.selectedCase.collectAsStateWithLifecycle()
    val selectedVariants by viewModel.selectedVariants.collectAsStateWithLifecycle()
    val selectedSolve by viewModel.selectedSolve.collectAsStateWithLifecycle()
    val trainingQueue by viewModel.trainingQueue.collectAsStateWithLifecycle()
    val trainingIndex by viewModel.trainingIndex.collectAsStateWithLifecycle()
    val trainingRevealed by viewModel.trainingRevealed.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val deviceStatus by viewModel.deviceStatus.collectAsStateWithLifecycle()
    val nearbyDevices by viewModel.nearbyDevices.collectAsStateWithLifecycle()
    val deviceMessages by viewModel.deviceMessages.collectAsStateWithLifecycle()
    val deviceLiveState by viewModel.deviceUiState.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    var deviceOpen by remember { mutableStateOf(false) }
    var formulaHeaderCompact by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var snackbar by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.scanDevices()
        else snackbar = "未授权附近设备，公式和手动计时仍可用"
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportBackup(it) { message -> snackbar = message } }
    }
    LaunchedEffect(section) {
        if (section != AppSection.FORMULA) formulaHeaderCompact = false
    }

    MaterialTheme(colorScheme = CubeTraceThemeColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = CubeTraceColors.mist) {
            Scaffold(
                containerColor = CubeTraceColors.mist,
                topBar = {
                    CubeTraceTopBar(
                        section = section,
                        deviceStatus = deviceStatus,
                        onDevice = { deviceOpen = true },
                        onSettings = { settingsOpen = true },
                        compact = section == AppSection.FORMULA && formulaHeaderCompact
                    )
                },
                bottomBar = {
                    CubeTraceNavigation(section, viewModel::selectSection)
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (section) {
                        AppSection.FORMULA -> FormulaScreen(
                            cases = cases,
                            query = query,
                            stage = selectedStage,
                            filter = filter,
                            assistLabels = settings.assistLabels,
                            onQuery = viewModel::setQuery,
                            onStage = viewModel::setStage,
                            onFilter = viewModel::setFilter,
                            onOpenCase = viewModel::openCase,
                            onHeaderCompact = { formulaHeaderCompact = it }
                        )
                        AppSection.TRAINING -> TrainingScreen(
                            queue = trainingQueue,
                            index = trainingIndex,
                            revealed = trainingRevealed,
                            assistLabels = settings.assistLabels,
                            onStart = viewModel::startTraining,
                            onReveal = viewModel::revealTraining,
                            onAnswer = viewModel::answerTraining,
                            onOpenCase = viewModel::openCase
                        )
                        AppSection.TIMER -> TimerRoute(
                            viewModel = viewModel,
                            solves = solves,
                            deviceStatus = deviceStatus,
                            deviceLiveState = deviceLiveState,
                            deviceOrientation = viewModel.deviceOrientation,
                            gyroFollowEnabled = settings.gyroFollowEnabled,
                            smartCubeFrame = settings.smartCubeFrame,
                            onDevice = { deviceOpen = true }
                        )
                        AppSection.RECORDS -> RecordsScreen(
                            solves = solves,
                            pauseThreshold = settings.pauseThresholdMs,
                            onOpenSolve = viewModel::openSolve,
                            onExport = { backupLauncher.launch("CubeTrace-backup.zip") }
                        )
                    }

                    if (selectedCase != null) {
                        CaseDetailDialog(
                            item = selectedCase!!,
                            variants = selectedVariants,
                            assistLabels = settings.assistLabels,
                            reducedMotion = settings.reducedMotion,
                            onDismiss = viewModel::closeCase,
                            onFavorite = { viewModel.toggleFavorite(selectedCase!!) },
                            onTrain = { viewModel.goToTraining(selectedCase) },
                            onNotes = { viewModel.saveCaseNotes(selectedCase!!, it) },
                            onAddVariant = { viewModel.addVariant(selectedCase!!, it) },
                            onSelectVariant = { viewModel.selectPreferredVariant(selectedCase!!, it) },
                            onVerifyVariant = { viewModel.verifyVariant(selectedCase!!, it) },
                            onDeleteVariant = { viewModel.deleteVariant(selectedCase!!, it) }
                        )
                    }
                    if (selectedSolve != null) {
                        SolveReviewDialog(selectedSolve!!, settings.pauseThresholdMs, viewModel::closeSolve)
                    }
                    if (settingsOpen) {
                        SettingsDialog(
                            settings = settings,
                            onDismiss = { settingsOpen = false },
                            onReducedMotion = viewModel::setReducedMotion,
                            onAssistLabels = viewModel::setAssistLabels,
                            onVibration = viewModel::setVibration,
                            onGyroFollow = viewModel::setGyroFollow,
                            onSmartCubeFrame = viewModel::setSmartCubeFrame,
                            onSmartAutoInspection = viewModel::setSmartAutoInspection,
                            onPauseThreshold = viewModel::setPauseThreshold,
                            onBackup = { settingsOpen = false; backupLauncher.launch("CubeTrace-backup.zip") },
                            onPrivacy = { snackbar = "不创建账号、不访问互联网、不上传成绩、公式或蓝牙数据" }
                        )
                    }
                    if (deviceOpen) {
                        DeviceDialog(
                            status = deviceStatus,
                            nearbyDevices = nearbyDevices,
                            messages = deviceMessages,
                            liveState = deviceLiveState,
                            smartCubeFrame = settings.smartCubeFrame,
                            onDismiss = { deviceOpen = false },
                            onScan = {
                                if (android.os.Build.VERSION.SDK_INT >= 31) {
                                    permissionLauncher.launch(V10DeviceManager.requiredPermissions())
                                } else {
                                    permissionLauncher.launch(V10DeviceManager.requiredPermissions())
                                }
                            },
                            onStopScan = viewModel::stopScan,
                            onConnect = viewModel::connectDevice,
                            onDisconnect = viewModel::disconnectDevice,
                            onSync = viewModel::requestFacelets,
                            onBattery = viewModel::requestBattery,
                            onCalibrate = viewModel::calibrateDeviceOrientation
                        )
                    }
                    if (snackbar.isNotBlank()) {
                        Text(
                            snackbar,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp)
                                .clip(RoundedCornerShape(6.dp)).background(CubeTraceColors.graphite).padding(horizontal = 16.dp, vertical = 12.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LaunchedEffect(snackbar) {
                            delay(3_500L)
                            snackbar = ""
                        }
                    }
                }
            }
        }
    }
}

private val CubeTraceThemeColors = androidx.compose.material3.lightColorScheme(
    primary = CubeTraceColors.track,
    onPrimary = Color.White,
    primaryContainer = CubeTraceColors.trackSoft,
    onPrimaryContainer = CubeTraceColors.graphite,
    secondary = CubeTraceColors.signal,
    background = CubeTraceColors.mist,
    surface = CubeTraceColors.paper,
    surfaceVariant = CubeTraceColors.blueWash,
    onSurface = CubeTraceColors.graphite,
    onSurfaceVariant = CubeTraceColors.muted,
    outline = CubeTraceColors.line,
    error = CubeTraceColors.fault
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CubeTraceTopBar(
    section: AppSection,
    deviceStatus: DeviceStatus,
    onDevice: () -> Unit,
    onSettings: () -> Unit,
    compact: Boolean = false
) {
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).background(CubeTraceColors.mist).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CubeTraceBrandMark(modifier = Modifier.size(28.dp))
            Text(section.label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) { SettingsGlyph() }
        }
    } else {
        TopAppBar(
            navigationIcon = { CubeTraceBrandMark(modifier = Modifier.padding(start = 16.dp)) },
            title = {
                Column {
                    Text(section.label, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
                    Text("校准工作台 · 离线", fontSize = 12.sp, color = CubeTraceColors.muted)
                }
            },
            actions = {
                if (section == AppSection.TIMER || section == AppSection.TRAINING) {
                    DeviceStatusChip(deviceStatus, onDevice)
                }
                IconButton(onClick = onSettings, modifier = Modifier.padding(end = 8.dp)) { SettingsGlyph() }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CubeTraceColors.mist, scrolledContainerColor = CubeTraceColors.mist)
        )
    }
}

@Composable
private fun CubeTraceBrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.app_icon),
        contentDescription = "方迹",
        modifier = modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
    )
}

@Composable
private fun SettingsGlyph() {
    Canvas(Modifier.size(24.dp).semantics { contentDescription = "设置" }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(CubeTraceColors.graphite, radius = 7.dp.toPx(), center = center, style = Stroke(width = 1.8.dp.toPx()))
        drawCircle(CubeTraceColors.track, radius = 2.2.dp.toPx(), center = center)
        repeat(8) { index ->
            val angle = index * (Math.PI / 4.0)
            val start = Offset(center.x + kotlin.math.cos(angle).toFloat() * 9.dp.toPx(), center.y + kotlin.math.sin(angle).toFloat() * 9.dp.toPx())
            val end = Offset(center.x + kotlin.math.cos(angle).toFloat() * 11.dp.toPx(), center.y + kotlin.math.sin(angle).toFloat() * 11.dp.toPx())
            drawLine(CubeTraceColors.graphite, start, end, strokeWidth = 1.8.dp.toPx())
        }
    }
}

@Composable
private fun DeviceStatusChip(status: DeviceStatus, onClick: () -> Unit) {
    val (text, color) = when (status) {
        DeviceStatus.Unavailable -> "蓝牙不可用" to CubeTraceColors.fault
        DeviceStatus.Idle -> "V10 AI · 未连接" to CubeTraceColors.graphite
        DeviceStatus.PermissionRequired -> "附近设备权限" to CubeTraceColors.signal
        DeviceStatus.Scanning -> "正在扫描" to CubeTraceColors.track
        is DeviceStatus.Connecting -> "连接中 · ${status.name}" to CubeTraceColors.track
        is DeviceStatus.Ready -> "魔方 · 已连接${status.battery?.let { " · $it%" } ?: ""}" to CubeTraceColors.track
        is DeviceStatus.Syncing -> "正在恢复状态" to CubeTraceColors.signal
        is DeviceStatus.Error -> "魔方异常" to CubeTraceColors.fault
    }
    AssistChip(
        onClick = onClick,
        label = { Text(text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Text("◉", color = color) },
        modifier = Modifier.widthIn(max = 148.dp)
    )
}

@Composable
private fun CubeTraceNavigation(section: AppSection, onSection: (AppSection) -> Unit) {
    NavigationBar(containerColor = CubeTraceColors.paper, tonalElevation = 0.dp, modifier = Modifier.border(1.dp, CubeTraceColors.line)) {
        AppSection.entries.forEach { item ->
            NavigationBarItem(
                selected = item == section,
                onClick = { onSection(item) },
                icon = { NavigationGlyph(item, item == section) },
                label = { Text(item.label, fontSize = 12.sp, fontWeight = if (item == section) FontWeight.SemiBold else FontWeight.Normal) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = CubeTraceColors.track,
                    selectedTextColor = CubeTraceColors.track,
                    indicatorColor = CubeTraceColors.trackSoft,
                    unselectedIconColor = CubeTraceColors.muted,
                    unselectedTextColor = CubeTraceColors.muted
                )
            )
        }
    }
}

@Composable
private fun NavigationGlyph(item: AppSection, selected: Boolean) {
    val color = if (selected) CubeTraceColors.track else CubeTraceColors.muted
    Canvas(Modifier.size(24.dp)) {
        when (item) {
            AppSection.FORMULA -> {
                drawRoundRect(color, Offset(3.dp.toPx(), 3.dp.toPx()), Size(7.dp.toPx(), 7.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(width = 1.8.dp.toPx()))
                drawRoundRect(color, Offset(14.dp.toPx(), 3.dp.toPx()), Size(7.dp.toPx(), 7.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(width = 1.8.dp.toPx()))
                drawRoundRect(color, Offset(3.dp.toPx(), 14.dp.toPx()), Size(7.dp.toPx(), 7.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(width = 1.8.dp.toPx()))
                drawRoundRect(color, Offset(14.dp.toPx(), 14.dp.toPx()), Size(7.dp.toPx(), 7.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(width = 1.8.dp.toPx()))
            }
            AppSection.TRAINING -> {
                drawCircle(color, radius = 8.dp.toPx(), center = Offset(12.dp.toPx(), 12.dp.toPx()), style = Stroke(width = 1.8.dp.toPx()))
                drawCircle(color, radius = 3.dp.toPx(), center = Offset(12.dp.toPx(), 12.dp.toPx()))
                drawLine(color, Offset(12.dp.toPx(), 1.dp.toPx()), Offset(12.dp.toPx(), 5.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppSection.TIMER -> {
                drawCircle(color, radius = 8.dp.toPx(), center = Offset(12.dp.toPx(), 12.dp.toPx()), style = Stroke(width = 1.8.dp.toPx()))
                drawLine(color, Offset(12.dp.toPx(), 12.dp.toPx()), Offset(12.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(12.dp.toPx(), 12.dp.toPx()), Offset(17.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppSection.RECORDS -> {
                drawLine(color, Offset(4.dp.toPx(), 19.dp.toPx()), Offset(4.dp.toPx(), 10.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(10.dp.toPx(), 19.dp.toPx()), Offset(10.dp.toPx(), 5.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(16.dp.toPx(), 19.dp.toPx()), Offset(16.dp.toPx(), 8.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(20.dp.toPx(), 19.dp.toPx()), Offset(20.dp.toPx(), 3.dp.toPx()), strokeWidth = 2.2.dp.toPx())
            }
        }
    }
}

@Composable
private fun FormulaScreen(
    cases: List<CubeCase>,
    query: String,
    stage: Stage?,
    filter: CaseFilter,
    assistLabels: Boolean,
    onQuery: (String) -> Unit,
    onStage: (Stage?) -> Unit,
    onFilter: (CaseFilter) -> Unit,
    onOpenCase: (CubeCase) -> Unit,
    onHeaderCompact: (Boolean) -> Unit
) {
    val gridState = rememberLazyGridState()
    val scrollScope = rememberCoroutineScope()
    val headerCompact by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 72
        }
    }
    LaunchedEffect(headerCompact) { onHeaderCompact(headerCompact) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(164.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionEyebrow("本地公式库")
                        Text("一眼识别，一条公式", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CubeTraceColors.graphite, modifier = Modifier.padding(top = 5.dp))
                        Text("先看色块，再选案例；公式和熟练度放在同一张卡片里。", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(cases.size.toString(), fontFamily = FontFamily.Monospace, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = CubeTraceColors.track)
                        Text("个案例", fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.3.sp, color = CubeTraceColors.muted)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜索案例、编号或公式", color = CubeTraceColors.muted) },
                    leadingIcon = { Text("⌕", fontSize = 24.sp, color = CubeTraceColors.track) },
                    trailingIcon = { if (query.isNotBlank()) TextButton(onClick = { onQuery("") }) { Text("清除", color = CubeTraceColors.track) } },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = CubeTraceColors.paper,
                        focusedContainerColor = CubeTraceColors.paper,
                        unfocusedBorderColor = CubeTraceColors.line,
                        focusedBorderColor = CubeTraceColors.track
                    )
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 4.dp)) {
                    item { StageChip("全部", stage == null) { onStage(null) } }
                    item { StageChip("F2L · 41组", stage == Stage.F2L) { onStage(Stage.F2L) } }
                    item { StageChip("OLL · 57组", stage == Stage.OLL) { onStage(Stage.OLL) } }
                    item { StageChip("PLL · 21组", stage == Stage.PLL) { onStage(Stage.PLL) } }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 5.dp, bottom = 12.dp)) {
                    CaseFilter.entries.forEach { filterItem ->
                        item {
                            FilterChip(
                                selected = filter == filterItem,
                                onClick = { onFilter(filterItem) },
                                label = { Text(filterItem.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CubeTraceColors.graphite,
                                    selectedLabelColor = Color.White,
                                    containerColor = CubeTraceColors.paper,
                                    labelColor = CubeTraceColors.muted
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = filter == filterItem,
                                    borderColor = CubeTraceColors.line,
                                    selectedBorderColor = CubeTraceColors.graphite
                                )
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CubeTraceColors.trackSoft)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        "筛选说明：到期 = 到了下次复习时间；生疏 = 掌握度 0–2；收藏 = 你手动点过星标。训练时“没认出”降一级，“熟练”升一级，迟疑和跳过不改变等级。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = CubeTraceColors.muted
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (stage == null) "全部阶段" else stage.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CubeTraceColors.graphite)
                    Text("${cases.size} 个案例 · 黄顶蓝前公式库", fontSize = 10.sp, color = CubeTraceColors.muted, fontFamily = FontFamily.Monospace)
                }
                }
            }
            if (cases.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("没有匹配的案例", "清除搜索或筛选条件后继续浏览。", onClick = { onQuery(""); onFilter(CaseFilter.ALL); onStage(null) })
                }
            } else {
                items(cases, key = { it.stableId }) { item ->
                    CaseCard(item, assistLabels, onClick = { onOpenCase(item) })
                }
            }
        }

        if (headerCompact) {
            FloatingActionButton(
                onClick = { scrollScope.launch { gridState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 14.dp)
                    .size(48.dp)
                    .semantics { contentDescription = "回到顶部" },
                containerColor = CubeTraceColors.track,
                contentColor = Color.White
            ) {
                Text("↑", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CubeTraceColors.track,
            selectedLabelColor = Color.White,
            containerColor = CubeTraceColors.paper,
            labelColor = CubeTraceColors.graphite
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CubeTraceColors.line,
            selectedBorderColor = CubeTraceColors.track
        )
    )
}

@Composable
private fun CaseCard(item: CubeCase, assistLabels: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CubeTraceColors.paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CubeTraceColors.line)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(12.dp)).background(CubeTraceColors.blueWash),
                contentAlignment = Alignment.Center
            ) {
                CaseStatePreview(item.stage, item.canonicalState, Modifier.fillMaxSize().padding(8.dp), assistLabels)
                Row(modifier = Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    StageBadge(item.stage)
                    Text("${item.number.toString().padStart(2, '0')}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = CubeTraceColors.muted)
                }
                if (item.favorite) Text("★", modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), color = CubeTraceColors.signal, fontSize = 18.sp)
            }
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            Text(item.alias, fontSize = 11.sp, color = CubeTraceColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            MiniNotation(item.variant.notation)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MasteryTicks(item.masteryBox)
                Text("${item.masteryBox} / 5 ${item.masteryLabel}", fontSize = 11.sp, color = CubeTraceColors.muted)
            }
        }
    }
}

@Composable
private fun StageBadge(stage: Stage) {
    val (label, color) = when (stage) {
        Stage.F2L -> "F2L" to CubeTraceColors.track
        Stage.OLL -> "OLL" to CubeTraceColors.diagramBlueDark
        Stage.PLL -> "PLL" to CubeTraceColors.signal
        Stage.CROSS -> "十字" to CubeTraceColors.graphite
    }
    Text(
        label,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        letterSpacing = 0.8.sp,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.82f)).padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun MiniNotation(notation: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 9.dp, bottom = 9.dp).horizontalScroll(rememberScrollState())) {
        notation.split(" ").filter(String::isNotBlank).take(6).forEach { token ->
            Text(
                token,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = CubeTraceColors.graphite,
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(CubeTraceColors.trackSoft).padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MasteryTicks(value: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { index ->
            Box(Modifier.size(7.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (index < value) CubeTraceColors.track else CubeTraceColors.line))
        }
    }
}

@Composable
private fun TrainingScreen(
    queue: List<CubeCase>,
    index: Int,
    revealed: Boolean,
    assistLabels: Boolean,
    onStart: () -> Unit,
    onReveal: () -> Unit,
    onAnswer: (TrainingResult) -> Unit,
    onOpenCase: (CubeCase) -> Unit
) {
    val current = queue.getOrNull(index)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionEyebrow("今日 / 训练")
        Text("把薄弱的动作，练成可复现的动作。", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("五级复习箱 · 只在本地记录", fontSize = 13.sp, color = CubeTraceColors.muted)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock("今日到期", queue.count { it.dueAt <= System.currentTimeMillis() }.toString(), "案例")
            StatBlock("本轮", if (queue.isEmpty()) "—" else "${index.coerceAtMost(queue.size)} / ${queue.size}", "进度")
            StatBlock("方式", "识别", "训练")
        }
        Spacer(Modifier.height(18.dp))
        if (current == null || index >= queue.size) {
            EmptyState("今天先从一个案例开始", "默认从生疏项抽取 12 个；也可以从公式详情直接练习。", onStart, "开始本轮训练")
        } else {
            LinearProgressIndicator(progress = { index.toFloat() / queue.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth(), color = CubeTraceColors.track)
            Spacer(Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = CubeTraceColors.paper), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("识别训练", color = CubeTraceColors.track, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(current.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(current.orientationRule, fontSize = 12.sp, color = CubeTraceColors.muted)
                    Spacer(Modifier.height(10.dp))
                    CaseStatePreview(current.stage, current.canonicalState, Modifier.size(220.dp), assistLabels)
                    Spacer(Modifier.height(12.dp))
                    if (revealed) {
                        Text("首选公式", fontSize = 12.sp, color = CubeTraceColors.muted)
                        Text(current.variant.notation, fontFamily = FontFamily.Monospace, fontSize = 17.sp, color = CubeTraceColors.graphite, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("这次识别怎么样？", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { onAnswer(TrainingResult.WRONG) }) { Text("没认出") }
                            OutlinedButton(onClick = { onAnswer(TrainingResult.HESITANT) }) { Text("迟疑") }
                            Button(onClick = { onAnswer(TrainingResult.CORRECT) }) { Text("熟练") }
                        }
                    } else {
                        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("显示答案") }
                    }
                    TextButton(onClick = { onOpenCase(current) }) { Text("打开案例详情") }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionRule("训练规则")
        Text("错误降一级；连续正确升一级。迟疑不会自动判错，跳过不改变掌握度。", fontSize = 13.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun TimerRoute(
    viewModel: CubeTraceViewModel,
    solves: List<SolveRecord>,
    deviceStatus: DeviceStatus,
    deviceLiveState: DeviceLiveState,
    deviceOrientation: StateFlow<Quaternion?>,
    gyroFollowEnabled: Boolean,
    smartCubeFrame: SmartCubeFrame,
    onDevice: () -> Unit
) {
    // The elapsed/inspection ticker runs at display speed. Do not feed those
    // two fields into the whole timer page: recomposing the cards, device
    // panel and 3D host every 8 ms is enough to make a high-refresh display
    // look like a 30/60 Hz surface. TimerClock collects the live values in a
    // small isolated subtree below.
    val timer by viewModel.timerPage.collectAsStateWithLifecycle()
    LaunchedEffect(timer.scramble, smartCubeFrame) {
        viewModel.configureSmartScramble(timer.scramble, smartCubeFrame)
    }
    LaunchedEffect(deviceLiveState.sequence, deviceLiveState.facelets, deviceLiveState.lastMove, deviceLiveState.moveHistory) {
        viewModel.onSmartCubeUpdate(deviceLiveState)
    }
    val displayLiveState = remember(deviceLiveState, smartCubeFrame) {
        viewModel.smartDisplayState(deviceLiveState, smartCubeFrame)
    }
    val displayScramble = remember(timer.scramble, smartCubeFrame) {
        viewModel.smartScrambleNotation(timer.scramble, smartCubeFrame)
    }
    TimerScreen(
        timer = timer,
        timerClock = viewModel.timerClock,
        solves = solves,
        deviceStatus = deviceStatus,
        deviceLiveState = displayLiveState,
        deviceOrientation = deviceOrientation,
        gyroFollowEnabled = gyroFollowEnabled,
        smartCubeFrame = smartCubeFrame,
        scrambleNotation = displayScramble,
        onPrepare = viewModel::prepareTimer,
        onToggle = viewModel::toggleTimer,
        onStartSmartInspection = viewModel::startSmartInspection,
        onRegenerate = viewModel::regenerateScramble,
        onPenalty = viewModel::setTimerPenalty,
        onSave = viewModel::saveTimer,
        onAbandon = viewModel::abandonTimer,
        onDevice = onDevice
    )
}

@Composable
private fun TimerScreen(
    timer: TimerSnapshot,
    timerClock: StateFlow<TimerClockState>,
    solves: List<SolveRecord>,
    deviceStatus: DeviceStatus,
    deviceLiveState: DeviceLiveState,
    deviceOrientation: StateFlow<Quaternion?>,
    gyroFollowEnabled: Boolean,
    smartCubeFrame: SmartCubeFrame,
    scrambleNotation: String,
    onPrepare: () -> Unit,
    onToggle: () -> Unit,
    onStartSmartInspection: () -> Unit,
    onRegenerate: () -> Unit,
    onPenalty: (Penalty) -> Unit,
    onSave: () -> Unit,
    onAbandon: () -> Unit,
    onDevice: () -> Unit
) {
    // The cube illustration is the inspection state for the displayed
    // scramble, not a permanently solved cube. The selected reference frame
    // drives both the colors and the notation.
    val scrambleFacelets = remember(scrambleNotation) {
        CubeState.solved().apply(normalizedMoves(scrambleNotation)).asFacelets()
    }
    val liveFacelets = deviceLiveState.facelets
    val displayFacelets = liveFacelets ?: scrambleFacelets
    val scrambleTokens = remember(scrambleNotation) {
        scrambleNotation.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    }
    val smartCubeAvailable = deviceStatus is DeviceStatus.Ready && deviceLiveState.synced
    val smartTimerActive = smartCubeAvailable && timer.smartAuto &&
        timer.phase in setOf(TimerPhase.INSPECTION, TimerPhase.WAITING_CUBE, TimerPhase.RUNNING)
    val solvingFocus = timer.smartAuto &&
        timer.phase in setOf(TimerPhase.RUNNING, TimerPhase.STOPPED) &&
        timer.smartPhase in setOf(SmartScramblePhase.SOLVING, SmartScramblePhase.SOLVED)
    val smartInspectionFocus = timer.smartAuto && timer.phase == TimerPhase.INSPECTION
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (solvingFocus) 10.dp else 16.dp)
    ) {
        if (solvingFocus) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (timer.phase == TimerPhase.RUNNING) "正在还原" else "还原完成",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "智能魔方实时状态 · ${smartCubeFrame.label}",
                        fontSize = 12.sp,
                        color = CubeTraceColors.muted
                    )
                }
                TextButton(onClick = onDevice) { Text("设备") }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("主 session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("白色十字 · ${smartCubeFrame.label} · HTM · 观察 15 秒", fontSize = 12.sp, color = CubeTraceColors.muted)
                }
                TextButton(onClick = onDevice) { Text("设备面板", maxLines = 1) }
            }
        }

        if (smartInspectionFocus) {
            SmartInspectionFocus(timerClock)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CubeTraceColors.paper),
            shape = RoundedCornerShape(if (solvingFocus) 18.dp else 12.dp),
            modifier = Modifier.padding(top = if (solvingFocus) 0.dp else 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(if (solvingFocus) 10.dp else 18.dp)) {
                if (!solvingFocus) {
                    ScrambleSequenceCard(
                        tokens = scrambleTokens,
                        smartMode = smartCubeAvailable,
                        progress = timer.smartScrambleProgress,
                        phase = timer.smartPhase,
                        onRegenerate = onRegenerate,
                        regenerateEnabled = !smartTimerActive
                    )
                    if (smartCubeAvailable) {
                        SmartScrambleStatusBanner(
                            timer = timer,
                            tokens = scrambleTokens,
                            onStartSmartInspection = onStartSmartInspection
                        )
                    }
                } else {
                    Text(
                        text = if (timer.phase == TimerPhase.RUNNING) {
                            "计时进行中 · 还原完成后自动停止"
                        } else {
                            timer.smartError ?: timer.smartCorrection
                                ?: "请转动一个面，再反向转回，例如 R R'；完成后自动保存并进入下一轮"
                        },
                        fontSize = 13.sp,
                        color = if (timer.smartError == null) CubeTraceColors.track else CubeTraceColors.fault,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                if (solvingFocus) {
                    TimerClock(
                        timerClock = timerClock,
                        smartTimerActive = smartTimerActive,
                        focusMode = true,
                        onPrepare = onPrepare,
                        onToggle = onToggle
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    text = if (liveFacelets != null) "智能魔方实时局面" else "打乱局面预览",
                    fontSize = if (solvingFocus) 13.sp else 12.sp,
                    fontWeight = if (solvingFocus) FontWeight.Medium else FontWeight.Normal,
                    color = if (liveFacelets != null) CubeTraceColors.track else CubeTraceColors.muted,
                    modifier = Modifier.padding(top = if (solvingFocus) 2.dp else 10.dp)
                )
                Cube3DView(
                    facelets = displayFacelets,
                    animationFromFacelets = deviceLiveState.animationFromFacelets,
                    animateMove = if (liveFacelets != null) deviceLiveState.lastMove else null,
                    animationKey = deviceLiveState.sequence ?: 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (solvingFocus) 300.dp else 178.dp)
                        .clipToBounds(),
                    sceneOrientation = null,
                    sceneOrientationFlow = if (liveFacelets != null && gyroFollowEnabled) deviceOrientation else null,
                    modelScale = if (solvingFocus) 0.94f else 0.74f,
                    animationSpeed = if (liveFacelets != null) 2f else 1f,
                    cubeFrame = smartCubeFrame
                )
                if (liveFacelets != null) {
                    Text(
                        text = "已同步 · 局面序号 ${deviceLiveState.sequence ?: "—"}" +
                            if (gyroFollowEnabled && deviceLiveState.orientation != null) " · 陀螺仪跟随" else "",
                        fontSize = 11.sp,
                        color = CubeTraceColors.muted
                    )
                }
                if (!solvingFocus && !smartInspectionFocus) {
                    Spacer(Modifier.height(22.dp))
                    TimerClock(
                        timerClock = timerClock,
                        smartTimerActive = smartTimerActive,
                        onPrepare = onPrepare,
                        onToggle = onToggle
                    )
                }
                if (timer.phase == TimerPhase.STOPPED) {
                    Spacer(Modifier.height(if (solvingFocus) 10.dp else 16.dp))
                    Text("成绩确认", fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Penalty.entries.forEach { penalty ->
                            FilterChip(
                                selected = timer.pendingPenalty == penalty,
                                onClick = { onPenalty(penalty) },
                                label = { Text(penalty.label) }
                            )
                        }
                    }
                    if (solvingFocus && timer.smartAuto) {
                        Text(
                            "完成一次面转动和反向转回后自动保存",
                            fontSize = 12.sp,
                            color = CubeTraceColors.muted,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TextButton(onClick = onAbandon, modifier = Modifier.fillMaxWidth()) { Text("放弃本次") }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("保留成绩") }
                            TextButton(onClick = onAbandon) { Text("放弃") }
                        }
                    }
                }
            }
        }

        if (!solvingFocus) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    SectionEyebrow("最近 / 5 次")
                    Text("最近成绩", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                val stats = calculateStats(solves.reversed())
                Text("ao5 ${formatDuration(stats.ao5Ms)}", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = CubeTraceColors.muted)
            }
            Spacer(Modifier.height(8.dp))
            solves.take(5).forEach { solve ->
                SolveRow(solve, onClick = {})
            }
            if (solves.isEmpty()) Text("完成第一次计时后，这里会出现你的动作轨迹和成绩。", fontSize = 13.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(vertical = 12.dp))
            val statusText = when (deviceStatus) {
                DeviceStatus.Idle, DeviceStatus.PermissionRequired -> "手动模式已就绪 · 无需连接设备"
                is DeviceStatus.Ready -> if (deviceLiveState.synced) {
                    "V10 AI 已连接 · 魔方局面已同步"
                } else {
                    "V10 AI 已连接 · 等待局面同步"
                }
                is DeviceStatus.Error -> deviceStatus.message
                else -> "设备连接状态：${deviceStatus::class.simpleName}"
            }
            Text(statusText, fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun TimerClock(
    timerClock: StateFlow<TimerClockState>,
    smartTimerActive: Boolean,
    focusMode: Boolean = false,
    onPrepare: () -> Unit,
    onToggle: () -> Unit
) {
    val clock by timerClock.collectAsStateWithLifecycle()
    val timerInteraction = if (smartTimerActive) {
        Modifier
    } else {
        Modifier.pointerInput(clock.phase) {
            detectTapGestures(onLongPress = { onPrepare() }, onTap = { onToggle() })
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (focusMode) 232.dp else 190.dp)
            .clip(RoundedCornerShape(if (focusMode) 16.dp else 8.dp))
            .background(CubeTraceColors.mist)
            .then(timerInteraction)
            .semantics { contentDescription = "计时区，${clock.phase.label}，轻触开始或停止，长按准备" },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val timerText = when (clock.phase) {
                TimerPhase.INSPECTION -> "${((clock.inspectionRemainingMs + 999L) / 1000L).coerceAtLeast(0L)}"
                else -> formatDuration(clock.elapsedMs)
            }
            Text(
                timerText,
                fontFamily = FontFamily.Monospace,
                fontSize = if (focusMode) 72.sp else 56.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (clock.phase == TimerPhase.RUNNING) CubeTraceColors.track else CubeTraceColors.graphite
            )
            Text(
                clock.phase.label,
                color = when (clock.phase) {
                    TimerPhase.RUNNING -> CubeTraceColors.track
                    TimerPhase.STOPPED -> CubeTraceColors.signal
                    else -> CubeTraceColors.muted
                },
                fontSize = if (focusMode) 16.sp else 14.sp
            )
            if (clock.phase == TimerPhase.IDLE) {
                Text("长按准备 · 松手后轻触开始", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 8.dp))
            }
            if (clock.phase == TimerPhase.INSPECTION) {
                Text("观察倒计时 · 转动魔方自动开始", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 8.dp))
            }
            if (clock.phase == TimerPhase.WAITING_CUBE) {
                Text("观察结束 · 转动魔方自动开始", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 8.dp))
            }
            if (clock.phase == TimerPhase.RUNNING && smartTimerActive) {
                Text("智能魔方检测中 · 还原后自动停止", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SmartInspectionFocus(timerClock: StateFlow<TimerClockState>) {
    val clock by timerClock.collectAsStateWithLifecycle()
    val remainingMs = clock.inspectionRemainingMs.coerceIn(0L, 15_000L)
    val remainingSeconds = ((remainingMs + 999L) / 1_000L).coerceAtLeast(0L)
    val elapsedFraction = 1f - remainingMs.toFloat() / 15_000f

    Card(
        colors = CardDefaults.cardColors(containerColor = CubeTraceColors.signal.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CubeTraceColors.signal.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    SectionEyebrow("观察 / 15 秒")
                    Text(
                        "先看清魔方，再开始还原",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        "观察期间无需操作手机；倒计时结束后，转动魔方会自动开始计时。",
                        fontSize = 12.sp,
                        color = CubeTraceColors.muted,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        remainingSeconds.toString(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 52.sp,
                        lineHeight = 52.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CubeTraceColors.signal
                    )
                    Text("秒", fontSize = 12.sp, color = CubeTraceColors.signal)
                }
            }
            LinearProgressIndicator(
                progress = { elapsedFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 13.dp).height(6.dp),
                color = CubeTraceColors.signal,
                trackColor = CubeTraceColors.signal.copy(alpha = 0.16f)
            )
            Text(
                "观察已开始",
                fontSize = 11.sp,
                color = CubeTraceColors.signal,
                modifier = Modifier.padding(top = 7.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScrambleSequenceCard(
    tokens: List<String>,
    smartMode: Boolean,
    progress: Int,
    phase: SmartScramblePhase,
    onRegenerate: () -> Unit,
    regenerateEnabled: Boolean
) {
    val completedCount = if (smartMode) progress.coerceIn(0, tokens.size) else 0
    val progressFraction = if (tokens.isEmpty()) 0f else completedCount.toFloat() / tokens.size
    val currentColor = if (phase == SmartScramblePhase.ERROR) CubeTraceColors.fault else CubeTraceColors.track
    val finished = smartMode && completedCount == tokens.size && tokens.isNotEmpty()

    Card(
        colors = CardDefaults.cardColors(containerColor = CubeTraceColors.mist),
        shape = RoundedCornerShape(13.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CubeTraceColors.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    SectionEyebrow(if (smartMode) "智能打乱 / 3×3" else "本轮打乱 / 3×3")
                    Text(
                        if (smartMode) "跟着魔方逐步执行" else "按 WCA 顺序完成本轮打乱",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                TextButton(onClick = onRegenerate, enabled = regenerateEnabled) {
                    Text("重新生成", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(35.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (finished) CubeTraceColors.signal else currentColor)
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (smartMode) {
                                if (finished) "打乱动作已全部完成" else "执行进度"
                            } else {
                                "公式已生成"
                            },
                            fontSize = 12.sp,
                            color = CubeTraceColors.muted
                        )
                        Text(
                            if (smartMode) "$completedCount / ${tokens.size} 步" else "${tokens.size} 个动作",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (finished) CubeTraceColors.signal else CubeTraceColors.graphite
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(4.dp),
                        color = if (finished) CubeTraceColors.signal else currentColor,
                        trackColor = CubeTraceColors.line
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tokens.forEachIndexed { index, token ->
                    val completed = smartMode && index < completedCount
                    val current = smartMode && index == completedCount && completedCount < tokens.size
                    val chipColor = when {
                        current -> currentColor
                        completed -> CubeTraceColors.trackSoft
                        else -> CubeTraceColors.paper
                    }
                    val textColor = when {
                        current -> Color.White
                        completed -> CubeTraceColors.track
                        else -> CubeTraceColors.graphite
                    }
                    val borderColor = when {
                        current -> currentColor
                        completed -> CubeTraceColors.track.copy(alpha = 0.28f)
                        else -> CubeTraceColors.line
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(chipColor)
                            .border(1.dp, borderColor, RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = token,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = textColor
                        )
                    }
                }
            }
            if (tokens.isEmpty()) {
                Text("暂无打乱公式", fontSize = 13.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun SmartScrambleStatusBanner(
    timer: TimerSnapshot,
    tokens: List<String>,
    onStartSmartInspection: () -> Unit
) {
    val nextToken = tokens.getOrNull(timer.smartScrambleProgress)
    val (title, body, color) = when (timer.smartPhase) {
        SmartScramblePhase.READY_TO_SCRAMBLE -> Triple(
            "可以开始打乱",
            "请按顺序执行下方公式；此时不要自由转动魔方。下一步：${nextToken ?: "—"}",
            CubeTraceColors.track
        )
        SmartScramblePhase.SCRAMBLING -> Triple(
            "正在打乱 · ${timer.smartScrambleProgress} / ${tokens.size}",
            "只执行高亮的下一步：${nextToken ?: "—"}",
            CubeTraceColors.track
        )
        SmartScramblePhase.ERROR -> Triple(
            "动作不匹配",
            listOfNotNull(timer.smartError, timer.smartCorrection).joinToString("\n"),
            CubeTraceColors.fault
        )
        SmartScramblePhase.READY_TO_INSPECT -> Triple(
            "打乱完成，可以开始观察",
            "确认魔方状态后开始 15 秒观察，观察期间不需要操作屏幕。",
            CubeTraceColors.track
        )
        SmartScramblePhase.INSPECTION -> Triple(
            "15 秒观察中",
            "观察已经开始；倒计时结束后，第一次转动会自动开始计时。",
            CubeTraceColors.signal
        )
        SmartScramblePhase.SOLVING -> Triple(
            "正在还原",
            "智能魔方检测中，还原完成后会自动停止计时。",
            CubeTraceColors.track
        )
        SmartScramblePhase.SOLVED -> if (timer.smartError == null) {
            Triple(
                "还原完成",
                timer.smartCorrection
                    ?: "请转动一个面，再反向转回，例如 R R'；完成后自动保存并进入新一轮打乱。",
                CubeTraceColors.signal
            )
        } else {
            Triple(
                "还不能开始下一轮",
                listOfNotNull(timer.smartError, timer.smartCorrection).joinToString("\n"),
                CubeTraceColors.fault
            )
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f)),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = color)
            Text(body, fontSize = 12.sp, color = CubeTraceColors.graphite, modifier = Modifier.padding(top = 4.dp))
            if (timer.smartPhase == SmartScramblePhase.READY_TO_INSPECT &&
                timer.phase in setOf(TimerPhase.IDLE, TimerPhase.READY)
            ) {
                Button(
                    onClick = onStartSmartInspection,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text("开始 15 秒观察")
                }
            }
        }
    }
}

@Composable
private fun RecordsScreen(
    solves: List<SolveRecord>,
    pauseThreshold: Int,
    onOpenSolve: (SolveRecord) -> Unit,
    onExport: () -> Unit
) {
    val stats = calculateStats(solves.reversed())
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { SectionEyebrow("主会话") ; Text("成绩与复盘", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onExport) { Text("导出备份") }
        }
        Text("原始时间、惩罚和动作分开保存；分析结果可重新生成。", fontSize = 13.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock("次数", stats.count.toString(), "次还原")
            StatBlock("最佳", formatDuration(stats.bestMs), "保留成绩")
            StatBlock("平均", formatDuration(stats.averageMs), "原始口径")
        }
        Spacer(Modifier.height(18.dp))
        SectionRule("趋势 / 最近 12 次")
        TrendChart(solves.take(12).reversed())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            MetricText("ao5", formatDuration(stats.ao5Ms))
            MetricText("ao12", formatDuration(stats.ao12Ms))
            MetricText("停顿阈值", "$pauseThreshold ms")
        }
        Spacer(Modifier.height(20.dp))
        SectionRule("最近还原")
        if (solves.isEmpty()) {
            EmptyState("还没有成绩", "去计时页完成一次手动还原。", {})
        } else {
            solves.forEach { solve -> SolveRow(solve, onClick = { onOpenSolve(solve) }) }
        }
    }
}

@Composable
private fun TrendChart(solves: List<SolveRecord>) {
    val values = solves.mapNotNull(::penaltyAdjustedMs)
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp)) {
        drawLine(CubeTraceColors.line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
        if (values.isEmpty()) return@Canvas
        val min = values.minOrNull() ?: 0L
        val max = values.maxOrNull()?.coerceAtLeast(min + 1) ?: 1L
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = if (values.size == 1) size.width / 2 else index.toFloat() / (values.size - 1) * size.width
            val y = (value - min).toFloat() / (max - min).toFloat() * (size.height - 22.dp.toPx()) + 10.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(CubeTraceColors.track, radius = 4.dp.toPx(), center = Offset(x, y))
        }
        drawPath(path, color = CubeTraceColors.track, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun SolveRow(solve: SolveRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(formatDuration(penaltyAdjustedMs(solve)), fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(92.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${solve.sessionName} · ${solve.source.label}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(solve.scramble, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CubeTraceColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (solve.penalty != Penalty.NONE) Text(displayPenalty(solve), color = if (solve.penalty == Penalty.DNF) CubeTraceColors.fault else CubeTraceColors.signal, fontSize = 12.sp)
            Text(if (solve.completeness == Completeness.COMPLETE) "完整" else "不完整", fontSize = 11.sp, color = CubeTraceColors.muted)
        }
    }
    HorizontalDivider(color = CubeTraceColors.line)
}

private data class DemoFrame(
    val facelets: String,
    val step: Int,
    val activeMove: String? = null,
    val animationFromFacelets: String? = null
)

@Composable
private fun CaseDetailDialog(
    item: CubeCase,
    variants: List<AlgorithmVariant>,
    assistLabels: Boolean,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onTrain: () -> Unit,
    onNotes: (String) -> Unit,
    onAddVariant: (String) -> Boolean,
    onSelectVariant: (String) -> Boolean,
    onVerifyVariant: (String) -> FormulaVerificationResult,
    onDeleteVariant: (String) -> Boolean
) {
    var notes by remember(item.stableId, item.notes) { mutableStateOf(item.notes) }
    var newVariant by remember(item.stableId, item.variant.id) { mutableStateOf("") }
    var variantMessage by remember(item.stableId, item.variant.id) { mutableStateOf("") }
    var variantMessageIsError by remember(item.stableId, item.variant.id) { mutableStateOf(false) }
    var variantToDelete by remember(item.stableId) { mutableStateOf<AlgorithmVariant?>(null) }
    var demoOpen by remember(item.stableId, item.variant.id) { mutableStateOf(false) }
    var demoPlaying by remember(item.stableId, item.variant.id) { mutableStateOf(false) }
    var demoSpeed by remember(item.stableId) { mutableStateOf(1f) }
    var demoFrame by remember(item.stableId, item.variant.id, item.canonicalState) {
        mutableStateOf(DemoFrame(facelets = item.canonicalState, step = 0))
    }
    val demoFacelets = demoFrame.facelets
    val demoStep = demoFrame.step
    val activeMove = demoFrame.activeMove
    val demoMoves = remember(item.stableId, item.variant.id, item.variant.notation) { normalizedMoves(item.variant.notation) }
    val demoStates = remember(item.stableId, item.variant.id, item.variant.notation, item.canonicalState) {
        buildList {
            var state = cubeStateFromFacelets(item.canonicalState)
            add(state.asFacelets())
            demoMoves.forEach { move ->
                state = state.apply(move)
                add(state.asFacelets())
            }
        }
    }
    LaunchedEffect(item.stableId, item.variant.id, demoPlaying, demoSpeed) {
        if (!demoPlaying) {
            if (demoFrame.activeMove != null) demoFrame = demoFrame.copy(activeMove = null)
            return@LaunchedEffect
        }
        var step = demoFrame.step
        while (step < demoMoves.size) {
            val index = step
            // The renderer needs both ends of the turn in the same frame:
            // facelets is the post-move target while animationFromFacelets is
            // the pre-move source. If the target is left at the old state,
            // the final animation frame briefly repaints the moving stickers
            // with their old colors, which looks like a color flash.
            demoFrame = DemoFrame(
                facelets = demoStates[index + 1],
                step = index,
                activeMove = demoMoves[index].normalized,
                animationFromFacelets = demoStates[index]
            )
            // Keep the state commit just after the renderer's continuous
            // quarter-turn (360 ms normal / 120 ms reduced motion).
            val baseDelay = if (reducedMotion) 140L else 380L
            delay((baseDelay / demoSpeed).toLong())
            step = index + 1
            demoFrame = DemoFrame(
                facelets = demoStates[step],
                step = step,
                activeMove = null
            )
        }
        demoPlaying = false
    }
    DialogSurface(onDismiss) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column { SectionEyebrow(item.stage.label) ; Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold) ; Text(item.alias, fontSize = 13.sp, color = CubeTraceColors.muted) }
                Row { IconButton(onClick = onFavorite) { Text(if (item.favorite) "★" else "☆", color = CubeTraceColors.signal, fontSize = 24.sp) }; TextButton(onClick = onDismiss) { Text("关闭") } }
            }
            Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                CaseStatePreview(item.stage, item.canonicalState, Modifier.size(245.dp), assistLabels)
            }
            SectionRule("首选公式")
            MoveTokenRow(item.variant.notation, wrap = true)
            OutlinedButton(
                onClick = {
                    demoOpen = true
                    if (demoStep >= demoMoves.size) {
                        demoFrame = DemoFrame(facelets = item.canonicalState, step = 0)
                    }
                    demoPlaying = !demoPlaying
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text(if (demoPlaying) "暂停三维演示" else "播放三维演示公式")
            }
            if (demoOpen) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CubeTraceColors.paper),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Cube3DView(
                             facelets = demoFacelets,
                             animationFromFacelets = demoFrame.animationFromFacelets,
                             modifier = Modifier.fillMaxWidth().height(220.dp),
                            animateMove = activeMove,
                            animationKey = demoStep,
                            reducedMotion = reducedMotion,
                            focusF2L = item.stage == Stage.F2L,
                            focusFacelets = if (item.stage == Stage.F2L) item.canonicalState else null,
                            animationSpeed = demoSpeed
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("播放速度", fontSize = 12.sp, color = CubeTraceColors.muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0.5f, 1f).forEach { speed ->
                                    FilterChip(
                                        selected = demoSpeed == speed,
                                        onClick = { demoSpeed = speed },
                                        label = { Text(if (speed == 0.5f) "0.5×" else "1×", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CubeTraceColors.track,
                                            selectedLabelColor = Color.White,
                                            containerColor = CubeTraceColors.trackSoft,
                                            labelColor = CubeTraceColors.graphite
                                        )
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "第 ${demoStep.coerceAtMost(demoMoves.size)} / ${demoMoves.size} 步",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = CubeTraceColors.muted
                            )
                            Row {
                                TextButton(onClick = {
                                    demoPlaying = false
                                    demoFrame = DemoFrame(facelets = item.canonicalState, step = 0)
                                }) { Text("重置") }
                                TextButton(onClick = {
                                    if (demoStep >= demoMoves.size) {
                                        demoFrame = DemoFrame(facelets = item.canonicalState, step = 0)
                                    }
                                    demoPlaying = !demoPlaying
                                }) { Text(if (demoPlaying) "暂停" else "播放") }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(if (item.variant.verified) "已校验" else "未校验") })
                AssistChip(onClick = {}, label = { Text(item.variant.sourceType) })
            }
            Spacer(Modifier.height(14.dp))
            SectionRule("公式变体")
            Text(
                "首选公式会显示在公式卡片，并用于下面的三维演示；用户公式保存后默认不会替换首选。",
                fontSize = 12.sp,
                color = CubeTraceColors.muted,
                modifier = Modifier.padding(top = 8.dp)
            )
            variants.forEach { variant ->
                val isPreferred = variant.id == item.variant.id
                val isUserVariant = variant.sourceType == "用户自建"
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPreferred) CubeTraceColors.trackSoft else CubeTraceColors.mist
                    ),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    if (isPreferred) "当前首选" else variant.sourceType,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isPreferred) CubeTraceColors.track else CubeTraceColors.graphite
                                )
                                Text(
                                    if (variant.verified) "已校验" else "未校验",
                                    fontSize = 11.sp,
                                    color = if (variant.verified) CubeTraceColors.track else CubeTraceColors.signal
                                )
                            }
                        }
                        MoveTokenRow(variant.notation, wrap = true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isPreferred) {
                                TextButton(onClick = { onSelectVariant(variant.id) }) {
                                    Text("设为首选")
                                }
                            }
                            if (isUserVariant) {
                                TextButton(onClick = {
                                    val result = onVerifyVariant(variant.id)
                                    variantMessage = result.message
                                    variantMessageIsError = !result.success
                                }) {
                                    Text(if (variant.verified) "重新校验" else "校验")
                                }
                                TextButton(onClick = { variantToDelete = variant }) {
                                    Text("删除", color = CubeTraceColors.fault)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = newVariant,
                onValueChange = {
                    newVariant = it
                    variantMessage = ""
                },
                label = { Text("新增一条用户公式") },
                placeholder = { Text("例如：F' U F U U R U R'") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = false,
                minLines = 2
            )
            Button(
                onClick = {
                    val candidate = newVariant.trim()
                    when {
                        candidate.isBlank() -> {
                            variantMessage = "请先输入公式"
                            variantMessageIsError = true
                        }
                        normalizedMoves(candidate).isEmpty() -> {
                            variantMessage = "公式无法解析，请检查动作符号和撇号"
                            variantMessageIsError = true
                        }
                        !onAddVariant(candidate) -> {
                            variantMessage = "保存失败，请稍后重试"
                            variantMessageIsError = true
                        }
                        else -> {
                            newVariant = ""
                            variantMessage = "已保存为用户公式；点击上方“设为首选”后即可用于三维演示"
                            variantMessageIsError = false
                        }
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("保存未校验变体") }
            if (variantMessage.isNotBlank()) {
                Text(
                    variantMessage,
                    fontSize = 12.sp,
                    color = if (variantMessageIsError) CubeTraceColors.fault else CubeTraceColors.track,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            variantToDelete?.let { target ->
                AlertDialog(
                    onDismissRequest = { variantToDelete = null },
                    title = { Text("删除用户公式？") },
                    text = {
                        Text("删除后这条用户公式将从当前案例移除；内置预设公式不会受到影响。")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val deleted = onDeleteVariant(target.id)
                            variantMessage = if (deleted) "用户公式已删除" else "删除失败，请稍后重试"
                            variantMessageIsError = !deleted
                            variantToDelete = null
                        }) {
                            Text("删除", color = CubeTraceColors.fault)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { variantToDelete = null }) { Text("取消") }
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
            SectionRule("笔记")
            OutlinedTextField(notes, onValueChange = { notes = it }, label = { Text("手法、视线或易错点") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3)
            TextButton(onClick = { onNotes(notes) }) { Text("保存笔记") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onTrain, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("练这个案例") }
        }
    }
}

@Composable
private fun SolveReviewDialog(solve: SolveRecord, pauseThreshold: Int, onDismiss: () -> Unit) {
    DialogSurface(onDismiss) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { SectionEyebrow("复盘 / ${solve.source.label}") ; Text("还原复盘", fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            Text(formatDuration(penaltyAdjustedMs(solve)), fontFamily = FontFamily.Monospace, fontSize = 42.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Text(solve.scramble, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(18.dp))
            SectionRule("动作轨迹")
            MoveRail(solve.moves, pauseThreshold)
            Spacer(Modifier.height(18.dp))
            SectionRule("阶段摘要")
            PhaseSummary(solve)
            Spacer(Modifier.height(18.dp))
            Text("当前记录为 ${if (solve.completeness == Completeness.COMPLETE) "完整" else "不完整"}。没有设备动作事件时，不伪造精确阶段指标。", fontSize = 13.sp, color = CubeTraceColors.muted)
        }
    }
}

@Composable
private fun MoveRail(moves: List<RecordedMove>, pauseThreshold: Int) {
    if (moves.isEmpty()) {
        EmptyState("这次没有动作事件", "手动计时仍保留成绩；连接 V10 AI 后会在这里记录动作轨迹。", {})
        return
    }
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            moves.forEachIndexed { index, move ->
                if (move.gap || (index > 0 && move.elapsedMs - moves[index - 1].elapsedMs > pauseThreshold)) {
                    Text("⋯", color = CubeTraceColors.signal, modifier = Modifier.padding(horizontal = 3.dp))
                }
                Text(move.code, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (index % 4 == 0) CubeTraceColors.trackSoft else Color.Transparent).padding(horizontal = 6.dp, vertical = 5.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(CubeTraceColors.graphite).padding(top = 8.dp))
    }
}

@Composable
private fun PhaseSummary(solve: SolveRecord) {
    val phases = listOf("十字", "F2L", "OLL", "PLL")
    Column(modifier = Modifier.padding(top = 8.dp)) {
        phases.forEachIndexed { index, phase ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(phase, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                Text(if (solve.moves.isEmpty()) "—" else "待分析", fontFamily = FontFamily.Monospace, color = CubeTraceColors.muted)
                Text(if (index == 0) "客观指标" else "自动分段", fontSize = 12.sp, color = CubeTraceColors.muted)
            }
            HorizontalDivider(color = CubeTraceColors.line)
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    onAssistLabels: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onGyroFollow: (Boolean) -> Unit,
    onSmartCubeFrame: (SmartCubeFrame) -> Unit,
    onSmartAutoInspection: (Boolean) -> Unit,
    onPauseThreshold: (Int) -> Unit,
    onBackup: () -> Unit,
    onPrivacy: () -> Unit
) {
    DialogSurface(onDismiss) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("设置", fontSize = 24.sp, fontWeight = FontWeight.SemiBold); TextButton(onClick = onDismiss) { Text("关闭") } }
            Text("计时与记号", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
            SettingRow("公式复原朝向", "黄顶蓝前；白色十字置于 D 面", false, showSwitch = false) {}
            SettingRow("记号", "WCA · HTM", false, showSwitch = false) {}
            Text("智能魔方打乱朝向", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text("官方打乱按白顶绿前生成；也可以切换为你的黄顶蓝前习惯。", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 3.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmartCubeFrame.entries.forEach { frame ->
                    FilterChip(
                        selected = settings.smartCubeFrame == frame,
                        onClick = { onSmartCubeFrame(frame) },
                        label = { Text(frame.label) }
                    )
                }
            }
            SettingRow(
                "打乱完成后自动观察",
                if (settings.smartAutoInspectionEnabled) {
                    "最后一步完成后自动进入 15 秒观察，不需要操作手机。"
                } else {
                    "打乱完成后停在确认页，由你决定开始观察或结束本轮。"
                },
                settings.smartAutoInspectionEnabled
            ) { onSmartAutoInspection(!settings.smartAutoInspectionEnabled) }
            Text("停顿阈值 · ${settings.pauseThresholdMs} ms", fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            Slider(value = settings.pauseThresholdMs.toFloat(), onValueChange = { onPauseThreshold(it.toInt()) }, valueRange = 100f..600f, steps = 4)
            Text(
                "相邻动作间隔超过这个时间，会在还原复盘中标记为一次停顿；不影响计时和智能魔方同步。",
                fontSize = 12.sp,
                color = CubeTraceColors.muted
            )
            Text("显示与反馈", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            SettingRow("色觉辅助标签", "在贴片中央显示 W / Y / R / O / B / G", settings.assistLabels) { onAssistLabels(!settings.assistLabels) }
            SettingRow("低动态模式", "关闭大幅旋转与插值动画", settings.reducedMotion) { onReducedMotion(!settings.reducedMotion) }
            SettingRow("振动反馈", "Ready、完成和训练结果提示", settings.vibrationEnabled) { onVibration(!settings.vibrationEnabled) }
            SettingRow("陀螺仪跟随", "仅影响三维展示，不改变魔方状态", settings.gyroFollowEnabled) { onGyroFollow(!settings.gyroFollowEnabled) }
            Text("数据与合规", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("导出离线备份 .cubetrace.zip") }
            OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("隐私说明") }
            Text("方迹 CubeTrace 0.1.0 · GPL-3.0-only", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 18.dp))
            Text("本版本内置 119 条标准 CFOP 公式，公式页面按黄顶蓝前展示；智能魔方打乱默认按白顶绿前，可在上方切换。V10 AI 连接后会同步固件、电量、局面和陀螺仪。", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, showSwitch: Boolean = true, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) { Text(title, fontSize = 15.sp); Text(subtitle, fontSize = 12.sp, color = CubeTraceColors.muted) }
        if (showSwitch) Switch(checked = checked, onCheckedChange = { onClick() })
    }
}

@Composable
private fun DeviceDialog(
    status: DeviceStatus,
    nearbyDevices: List<NearbyV10Device>,
    messages: List<V10Message>,
    liveState: DeviceLiveState,
    smartCubeFrame: SmartCubeFrame,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (NearbyV10Device) -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
    onBattery: () -> Unit,
    onCalibrate: () -> Unit
) {
    DialogSurface(onDismiss) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { SectionEyebrow("DEVICE / V10 AI") ; Text("设备面板", fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }; TextButton(onClick = onDismiss) { Text("关闭") } }
            Spacer(Modifier.height(8.dp))
            StatusBanner(status, liveState)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                Button(onClick = onScan) { Text("扫描 V10 AI") }
                if (status == DeviceStatus.Scanning) OutlinedButton(onClick = onStopScan) { Text("停止") }
                if (status is DeviceStatus.Ready || status is DeviceStatus.Error) OutlinedButton(onClick = onDisconnect) { Text("断开") }
            }
            if (nearbyDevices.isEmpty()) {
                Text("显示名称、目标服务或魔域地址匹配的设备；连接后还会验证服务、特征和协议。", fontSize = 13.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 12.dp))
            } else {
                SectionRule("扫描结果")
                nearbyDevices.forEach { device ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onConnect(device) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(device.name, fontWeight = FontWeight.SemiBold); Text("RSSI ${device.rssi} · 地址提示 ${device.addressHint}", fontSize = 12.sp, color = CubeTraceColors.muted) }
                        Text(if (device.verified) "已验证" else "待验证", color = if (device.verified) CubeTraceColors.track else CubeTraceColors.signal, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = CubeTraceColors.line)
                }
            }
            if (status is DeviceStatus.Ready) {
                SectionRule("只读操作")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) { OutlinedButton(onClick = onSync) { Text("同步局面") }; OutlinedButton(onClick = onBattery) { Text("读取电量") } }
                OutlinedButton(onClick = onCalibrate, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("校准当前视角") }
                Text("不会提供任意十六进制写入；仅允许设备信息、完整局面、电量和 gyro 白名单指令。", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 10.dp))
                val calibrationPose = if (smartCubeFrame == SmartCubeFrame.OFFICIAL_WHITE_GREEN) "白顶、绿前、红右" else "黄顶、蓝前、红右"
                Text("校准前请把魔方按${calibrationPose}平放；校准后投影会以这个姿态为基准。", fontSize = 12.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "实时局面：${if (liveState.synced) "已同步" else "等待同步"} · 陀螺仪：${if (liveState.orientation != null) "已接收" else "等待数据"}",
                    fontSize = 12.sp,
                    color = CubeTraceColors.muted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            SectionRule("最近消息")
            Text(if (messages.isEmpty()) "等待设备数据" else messages.takeLast(5).joinToString(" · ") { it::class.simpleName ?: "event" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 10.dp))
            Text(
                when (status) {
                    is DeviceStatus.Ready -> "协议状态：已完成握手；实时局面和陀螺仪会同步到计时页"
                    is DeviceStatus.Connecting -> "协议状态：正在验证服务和加密握手"
                    is DeviceStatus.Syncing -> "协议状态：正在同步设备状态"
                    is DeviceStatus.Error -> "协议状态：${status.message}"
                    else -> "协议状态：等待连接"
                },
                color = if (status is DeviceStatus.Error) CubeTraceColors.fault else CubeTraceColors.track,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun StatusBanner(status: DeviceStatus, liveState: DeviceLiveState) {
    val (title, body, color) = when (status) {
        DeviceStatus.Unavailable -> Triple("蓝牙不可用", "请打开系统蓝牙；公式和手动计时仍可用。", CubeTraceColors.fault)
        DeviceStatus.Idle -> Triple("未连接 V10 AI", "点击扫描，或继续使用离线功能。", CubeTraceColors.graphite)
        DeviceStatus.PermissionRequired -> Triple("需要附近设备权限", "只用于发现和连接你选择的智能魔方，不读取位置。", CubeTraceColors.signal)
        DeviceStatus.Scanning -> Triple("正在扫描", "保持 V10 AI 唤醒并靠近手机。", CubeTraceColors.track)
        is DeviceStatus.Connecting -> Triple("正在连接", status.name, CubeTraceColors.track)
        is DeviceStatus.Ready -> Triple(
            if (liveState.synced) "已连接 · 局面已同步" else "已连接 · 等待局面",
            "固件 ${status.firmware ?: "未知"} · 电量 ${status.battery?.let { "$it%" } ?: "待读取"}",
            CubeTraceColors.track
        )
        is DeviceStatus.Syncing -> Triple("正在恢复魔方状态", status.reason, CubeTraceColors.signal)
        is DeviceStatus.Error -> Triple("设备识别失败", status.message, CubeTraceColors.fault)
    }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.09f)).padding(14.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = color); Text(body, fontSize = 13.sp, color = CubeTraceColors.graphite, modifier = Modifier.padding(top = 3.dp)) }
}

@Composable
private fun DialogSurface(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxSize(0.94f), shape = RoundedCornerShape(16.dp), color = CubeTraceColors.mist, tonalElevation = 3.dp, content = content)
    }
}

@Composable
private fun CaseStatePreview(
    stage: Stage,
    facelets: String,
    modifier: Modifier = Modifier,
    assistLabels: Boolean = false
) {
    when (stage) {
        Stage.F2L, Stage.CROSS -> F2LReferencePreview(facelets, modifier, assistLabels)
        Stage.OLL -> OLLReferencePreview(facelets, modifier, assistLabels)
        Stage.PLL -> PLLReferencePreview(facelets, modifier, assistLabels)
    }
}

/**
 * The case thumbnails follow SpeedCubeDB's compact jcube language: F2L is a
 * three-face perspective cube and only the pair/solved lower-layer stickers
 * retain their color. Everything else is a neutral gray so the pair is easy
 * to identify in a fast-scrolling list.
 */
@Composable
private fun F2LReferencePreview(
    facelets: String,
    modifier: Modifier = Modifier,
    assistLabels: Boolean = false
) {
    val focus = remember(facelets) { f2lFocus(facelets) }
    Box(
        modifier = modifier.semantics {
            contentDescription = if (assistLabels) {
                "F2L current state, highlighted pair and solved lower-layer pieces, yellow top and blue front"
            } else {
                "F2L current state diagram"
            }
        }.drawWithCache {
            val geometry = buildF2LReferenceGeometry(size)
            onDrawBehind {
                drawF2LReferenceCube(
                    facelets,
                    focus.target + focus.solved + focus.crossSolved + focus.referenceCenters,
                    geometry
                )
            }
        }
    )
}

/** OLL is a current-state-only jcube diagram, including the four side strips. */
@Composable
private fun OLLReferencePreview(
    facelets: String,
    modifier: Modifier = Modifier,
    assistLabels: Boolean = false
) {
    JCubePatternPreview(
        facelets = facelets,
        mode = JCubePatternMode.OLL,
        modifier = modifier.semantics {
            contentDescription = if (assistLabels) {
                "OLL current yellow orientation pattern, yellow top and blue front"
            } else {
                "OLL current state diagram"
            }
        }
    )
}

/** PLL uses the same current-state layout, with real side colors for swaps. */
@Composable
private fun PLLReferencePreview(
    facelets: String,
    modifier: Modifier = Modifier,
    assistLabels: Boolean = false
) {
    JCubePatternPreview(
        facelets = facelets,
        mode = JCubePatternMode.PLL,
        modifier = modifier.semantics {
            contentDescription = if (assistLabels) {
                "PLL current permutation pattern, yellow top and blue front"
            } else {
                "PLL current state diagram"
            }
        }
    )
}

private enum class JCubePatternMode { OLL, PLL }

private val jCubeRailIndexes = arrayOf(
    intArrayOf(47, 46, 45), // B: far edge, left to right in the chart.
    intArrayOf(11, 10, 9),   // R: right rail, top to bottom in the chart.
    intArrayOf(18, 19, 20),  // F: near edge, left to right in the chart.
    intArrayOf(36, 37, 38)   // L: left rail, bottom to top in the chart.
)

@Composable
private fun JCubePatternPreview(
    facelets: String,
    mode: JCubePatternMode,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawJCubePattern(facelets, mode)
    }
}

/**
 * Draws the 75x75 SpeedCubeDB-style jcube at any card size. OLL keeps the
 * yellow/neutral current-state view. PLL deliberately switches to the chart
 * language from the reference image: a yellow U face, neutral side rails, and
 * straight arrows that show where each last-layer piece moves.
 */
private fun DrawScope.drawJCubePattern(facelets: String, mode: JCubePatternMode) {
    val scale = (kotlin.math.min(size.width, size.height) / 75f).coerceAtLeast(0.1f)
    val origin = Offset(
        (size.width - 75f * scale) / 2f,
        (size.height - 75f * scale) / 2f
    )
    fun point(x: Float, y: Float): Offset = Offset(origin.x + x * scale, origin.y + y * scale)
    fun cellColor(value: Char): Color = when (mode) {
        JCubePatternMode.OLL -> if (value == 'U') CubeTraceColors.yellowFace else CubeTraceColors.diagramGray
        // PLL's color is intentionally schematic. The arrows carry the
        // permutation information; painting the rail stickers red/green/etc.
        // made the chart much harder to scan at thumbnail size.
        JCubePatternMode.PLL -> CubeTraceColors.yellowFace
    }
    val pllMovement = if (mode == JCubePatternMode.PLL) pllMovementFor(facelets) else null
    fun drawCell(x: Float, y: Float, width: Float, height: Float, color: Color, radius: Float = 1.1f) {
        drawRoundRect(
            color = color,
            topLeft = point(x, y),
            size = Size(width * scale, height * scale),
            cornerRadius = CornerRadius(radius * scale)
        )
        drawRoundRect(
            color = CubeTraceColors.gap.copy(alpha = 0.72f),
            topLeft = point(x, y),
            size = Size(width * scale, height * scale),
            cornerRadius = CornerRadius(radius * scale),
            style = Stroke(width = (0.55f * scale).coerceAtLeast(0.35f))
        )
    }

    drawRect(
        color = if (mode == JCubePatternMode.PLL) CubeTraceColors.diagramGrayLight else CubeTraceColors.gap,
        topLeft = point(0f, 0f),
        size = Size(75f * scale, 75f * scale)
    )

    val mainLeft = 8.333f
    val mainTop = 8.333f
    val mainCell = 19.444f
    repeat(3) { row ->
        repeat(3) { col ->
            val value = facelets.getOrNull(row * 3 + col) ?: 'U'
            drawCell(
                x = mainLeft + col * mainCell + 0.95f,
                y = mainTop + row * mainCell + 0.95f,
                width = mainCell - 1.9f,
                height = mainCell - 1.9f,
                color = cellColor(value),
                radius = 2.3f
            )
        }
    }

    val railPositions = floatArrayOf(9.305f, 28.75f, 48.194f)
    val railSize = 17.5f
    val railThickness = 6.389f
    repeat(3) { slot ->
        val top = facelets.getOrNull(jCubeRailIndexes[0][slot]) ?: 'B'
        val right = facelets.getOrNull(jCubeRailIndexes[1][slot]) ?: 'R'
        val bottom = facelets.getOrNull(jCubeRailIndexes[2][slot]) ?: 'F'
        val left = facelets.getOrNull(jCubeRailIndexes[3][slot]) ?: 'L'
        fun railColor(index: Int, value: Char): Color = if (mode == JCubePatternMode.PLL) {
            if (index in (pllMovement?.movedStickerIndexes ?: emptySet())) {
                CubeTraceColors.diagramBlue
            } else {
                CubeTraceColors.diagramGray
            }
        } else {
            cellColor(value)
        }
        drawCell(
            x = railPositions[slot], y = 0.972f,
            width = railSize, height = railThickness,
            color = if (mode == JCubePatternMode.OLL) cellColor(if (top != 'U') 'X' else top)
            else railColor(jCubeRailIndexes[0][slot], top),
            radius = 1.0f
        )
        drawCell(
            x = railPositions[slot], y = 67.639f,
            width = railSize, height = railThickness,
            color = if (mode == JCubePatternMode.OLL) cellColor(if (bottom != 'U') 'X' else bottom)
            else railColor(jCubeRailIndexes[2][slot], bottom),
            radius = 1.0f
        )
        drawCell(
            x = 0.972f, y = railPositions[slot],
            width = railThickness, height = railSize,
            color = if (mode == JCubePatternMode.OLL) cellColor(if (left != 'U') 'X' else left)
            else railColor(jCubeRailIndexes[3][slot], left),
            radius = 1.0f
        )
        drawCell(
            x = 67.639f, y = railPositions[slot],
            width = railThickness, height = railSize,
            color = if (mode == JCubePatternMode.OLL) cellColor(if (right != 'U') 'X' else right)
            else railColor(jCubeRailIndexes[1][slot], right),
            radius = 1.0f
        )
    }

    pllMovement?.let { movement ->
        drawPllMovementArrows(
            movement = movement,
            point = ::point,
            scale = scale,
            mainLeft = mainLeft,
            mainTop = mainTop,
            mainCell = mainCell
        )
    }
}

private data class F2LReferenceFaceGeometry(val startIndex: Int, val cells: List<Path>)

private data class F2LReferenceGeometry(
    val shell: List<Path>,
    val faces: List<F2LReferenceFaceGeometry>
)

/**
 * CubeRoot/VisualCube-style perspective. The outer shell is deliberately
 * black; the gaps between the 3x3 faces create the thick, readable seams from
 * the reference instead of drawing a flat isometric net.
 */
private fun buildF2LReferenceGeometry(canvasSize: Size): F2LReferenceGeometry {
    val scale = (kotlin.math.min(canvasSize.width, canvasSize.height) / 1.8f).coerceAtLeast(1f)
    val origin = Offset(
        canvasSize.width / 2f - 0.9f * scale,
        canvasSize.height / 2f - 0.9f * scale
    )
    fun point(x: Float, y: Float): Offset = Offset(
        origin.x + (x + 0.9f) * scale,
        origin.y + (y + 0.9f) * scale
    )
    fun polygon(points: List<Offset>): Path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    fun lerp(a: Offset, b: Offset, amount: Float): Offset = Offset(
        a.x + (b.x - a.x) * amount,
        a.y + (b.y - a.y) * amount
    )
    fun bilinear(tl: Offset, tr: Offset, br: Offset, bl: Offset, u: Float, v: Float): Offset = lerp(
        lerp(tl, tr, u),
        lerp(bl, br, u),
        v
    )
    fun cellPath(
        tl: Offset,
        tr: Offset,
        br: Offset,
        bl: Offset,
        col: Int,
        row: Int
    ): Path {
        // A small UV inset leaves the black shell visible between every cell.
        val inset = 0.022f
        val u0 = col / 3f + inset
        val u1 = (col + 1) / 3f - inset
        val v0 = row / 3f + inset
        val v1 = (row + 1) / 3f - inset
        return polygon(
            listOf(
                bilinear(tl, tr, br, bl, u0, v0),
                bilinear(tl, tr, br, bl, u1, v0),
                bilinear(tl, tr, br, bl, u1, v1),
                bilinear(tl, tr, br, bl, u0, v1)
            ).map { point(it.x, it.y) }
        )
    }
    fun face(startIndex: Int, tl: Offset, tr: Offset, br: Offset, bl: Offset) =
        F2LReferenceFaceGeometry(
            startIndex = startIndex,
            cells = buildList {
                repeat(3) { row ->
                    repeat(3) { col -> add(cellPath(tl, tr, br, bl, col, row)) }
                }
            }
        )

    // These are the fixed VisualCube corners in a yellow-top/blue-front view.
    val frontTopLeft = Offset(-0.69915175f, -0.34957588f)
    val frontTopRight = Offset(0.20684406f, -0.10342203f)
    val backTopRight = Offset(0.65400099f, -0.50223953f)
    val backTopLeft = Offset(-0.16103317f, -0.68150056f)
    val frontBottomRight = Offset(0.18464331f, 0.7814199f)
    val frontBottomLeft = Offset(-0.6304931f, 0.48418668f)
    val backBottomRight = Offset(0.59354044f, 0.29677022f)

    val shell = listOf(
        polygon(listOf(frontTopRight, backTopRight, backBottomRight, frontBottomRight).map { point(it.x, it.y) }),
        polygon(listOf(backTopLeft, backTopRight, frontTopRight, frontTopLeft).map { point(it.x, it.y) }),
        polygon(listOf(frontTopLeft, frontTopRight, frontBottomRight, frontBottomLeft).map { point(it.x, it.y) })
    )
    return F2LReferenceGeometry(
        shell = shell,
        faces = listOf(
            face(9, frontTopRight, backTopRight, backBottomRight, frontBottomRight),
            face(0, backTopLeft, backTopRight, frontTopRight, frontTopLeft),
            face(18, frontTopLeft, frontTopRight, frontBottomRight, frontBottomLeft)
        )
    )
}

/** The current F2L state, with only the pair and solved lower-layer pieces colored. */
private fun DrawScope.drawF2LReferenceCube(
    facelets: String,
    important: Set<Int>,
    geometry: F2LReferenceGeometry
) {
    geometry.shell.forEach { drawPath(it, color = Color.Black) }
    geometry.faces.forEach { face ->
        face.cells.forEachIndexed { cellIndex, path ->
            val index = face.startIndex + cellIndex
            drawPath(
                path,
                color = if (index in important) {
                    cubeStickerColor(facelets.getOrNull(index) ?: 'X')
                } else {
                    CubeTraceColors.diagramNeutral
                }
            )
        }
    }
}

private data class DiagramSticker(val index: Int, val face: Char, val x: Int, val y: Int, val z: Int)

private val diagramStickers: List<DiagramSticker> = buildList {
    var index = 0
    for (face in "URFDLB") {
        repeat(3) { row ->
            repeat(3) { col ->
                val coordinate = when (face) {
                    'U' -> intArrayOf(col - 1, 1, row - 1)
                    'R' -> intArrayOf(1, 1 - row, 1 - col)
                    'F' -> intArrayOf(col - 1, 1 - row, 1)
                    'D' -> intArrayOf(col - 1, -1, 1 - row)
                    'L' -> intArrayOf(-1, 1 - row, col - 1)
                    else -> intArrayOf(1 - col, 1 - row, -1)
                }
                add(DiagramSticker(index++, face, coordinate[0], coordinate[1], coordinate[2]))
            }
        }
    }
}

private data class PllPieceSlot(
    val position: Triple<Int, Int, Int>,
    val stickerIndexes: List<Int>,
    val solvedColors: Set<Char>
)

private data class PllMovement(
    val arrows: List<Pair<PllPieceSlot, PllPieceSlot>>,
    val movedStickerIndexes: Set<Int>
)

/**
 * The eight y=1 edge/corner cubies are the pieces PLL permutes. Matching the
 * current piece color set to the solved slot gives a real source -> target
 * permutation for the arrows; the drawing is therefore derived from the
 * actual case state instead of a generic arrow decoration.
 */
private val pllPieceSlots: List<PllPieceSlot> by lazy {
    diagramStickers
        .filter { it.y == 1 }
        .groupBy { Triple(it.x, it.y, it.z) }
        .values
        .filter { it.size >= 2 }
        .map { stickers ->
            PllPieceSlot(
                position = Triple(stickers.first().x, stickers.first().y, stickers.first().z),
                stickerIndexes = stickers.map { it.index }.sorted(),
                solvedColors = stickers.map { it.face }.toSet()
            )
        }
}

private fun pllMovementFor(facelets: String): PllMovement {
    val arrows = pllPieceSlots.mapNotNull { source ->
        val currentColors = source.stickerIndexes.mapNotNull(facelets::getOrNull).toSet()
        val destination = pllPieceSlots.firstOrNull { it.solvedColors == currentColors }
        if (destination == null || destination.position == source.position) null else source to destination
    }
    return PllMovement(
        arrows = arrows,
        movedStickerIndexes = arrows.flatMap { it.first.stickerIndexes }.toSet()
    )
}

private fun DrawScope.drawPllMovementArrows(
    movement: PllMovement,
    point: (Float, Float) -> Offset,
    scale: Float,
    mainLeft: Float,
    mainTop: Float,
    mainCell: Float
) {
    fun anchor(position: Triple<Int, Int, Int>): Offset {
        // z=-1 is the far/top edge of the U face and z=1 is the near/bottom
        // edge. This makes the arrow layout read like a top-down PLL chart.
        val col = position.first + 1
        val row = position.third + 1
        return point(
            mainLeft + (col + 0.5f) * mainCell,
            mainTop + (row + 0.5f) * mainCell
        )
    }

    // Convert the source -> target pairs into cycles. A two-cycle is drawn as
    // one double-headed line, so the two directions never sit on top of each
    // other. Longer cycles use one straight segment per link; each segment is
    // unique and meets the next one only at its endpoint.
    val destinationBySource = movement.arrows.associate { it.first.position to it.second.position }
    val slotByPosition = movement.arrows
        .flatMap { listOf(it.first, it.second) }
        .associateBy { it.position }
    val visited = mutableSetOf<Triple<Int, Int, Int>>()
    val cycles = buildList {
        movement.arrows.forEach { (source, _) ->
            if (source.position in visited) return@forEach
            val cycle = mutableListOf<Triple<Int, Int, Int>>()
            var current = source.position
            while (current !in visited && destinationBySource.containsKey(current)) {
                visited += current
                cycle += current
                current = destinationBySource.getValue(current)
            }
            if (cycle.size > 1) add(cycle)
        }
    }

    fun drawStraightArrow(start: Offset, end: Offset, arrowAtStart: Boolean, arrowAtEnd: Boolean) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val unitX = dx / length
        val unitY = dy / length
        val lineWidth = (1.15f * scale).coerceAtLeast(0.75f)
        val headLength = (4.4f * scale).coerceAtLeast(3f)
        val headWidth = (1.8f * scale).coerceAtLeast(1.2f)
        val arrowColor = CubeTraceColors.diagramBlueDark

        // The dark keyline keeps the yellow face readable on small cards.
        drawLine(
            Color.White.copy(alpha = 0.82f),
            start,
            end,
            strokeWidth = lineWidth * 2.1f
        )
        drawLine(arrowColor, start, end, strokeWidth = lineWidth)

        fun drawHead(tip: Offset, directionX: Float, directionY: Float) {
            val base = Offset(
                tip.x - directionX * headLength,
                tip.y - directionY * headLength
            )
            val left = Offset(
                base.x - directionY * headWidth,
                base.y + directionX * headWidth
            )
            val right = Offset(
                base.x + directionY * headWidth,
                base.y - directionX * headWidth
            )
            drawLine(arrowColor, tip, left, strokeWidth = lineWidth)
            drawLine(arrowColor, tip, right, strokeWidth = lineWidth)
        }

        if (arrowAtEnd) drawHead(end, unitX, unitY)
        if (arrowAtStart) drawHead(start, -unitX, -unitY)
    }

    cycles.forEach { cycle ->
        if (cycle.size == 2) {
            drawStraightArrow(
                start = anchor(cycle[0]),
                end = anchor(cycle[1]),
                arrowAtStart = true,
                arrowAtEnd = true
            )
        } else {
            cycle.forEachIndexed { index, sourcePosition ->
                val destinationPosition = cycle[(index + 1) % cycle.size]
                // Corner and edge cycles use their own endpoints, keeping
                // straight lines visually separated even when both cycles
                // occupy the same top-layer frame.
                val source = slotByPosition.getValue(sourcePosition)
                val destination = slotByPosition.getValue(destinationPosition)
                drawStraightArrow(
                    start = anchor(source.position),
                    end = anchor(destination.position),
                    arrowAtStart = false,
                    arrowAtEnd = true
                )
            }
        }
    }
}

private fun f2lPieceIsUnsolved(facelets: String, face: Char, index: Int): Boolean {
    val descriptor = diagramStickers.firstOrNull { it.face == face && it.index == index } ?: return true
    return diagramStickers.any { sticker ->
        sticker.x == descriptor.x && sticker.y == descriptor.y && sticker.z == descriptor.z &&
            facelets.getOrNull(sticker.index) != sticker.face
    }
}

@Composable
private fun IsometricCasePreview(facelets: String, modifier: Modifier = Modifier, assistLabels: Boolean = false) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (assistLabels) "F2L 立体三面状态图，颜色标签已开启" else "F2L 立体三面状态图"
        }
    ) {
        val unit = (size.minDimension / 6.1f).coerceAtLeast(1f)
        val across = Offset(unit * 0.82f, unit * 0.43f)
        val down = Offset(-unit * 0.82f, unit * 0.43f)
        val vertical = Offset(0f, unit * 0.86f)
        val cubeHeight = across.y * 6f + vertical.y * 3f
        val origin = Offset(size.width / 2f, (size.height - cubeHeight) / 2f)
        val gap = unit * 0.075f
        val outline = CubeTraceColors.graphite.copy(alpha = 0.30f)

        fun plus(base: Offset, vector: Offset, count: Int): Offset =
            Offset(base.x + vector.x * count, base.y + vector.y * count)

        fun stickerPath(topLeft: Offset, horizontal: Offset, verticalStep: Offset): Path = Path().apply {
            moveTo(topLeft.x + gap / 2f, topLeft.y + gap / 2f)
            lineTo(topLeft.x + horizontal.x - gap / 2f, topLeft.y + horizontal.y + gap / 2f)
            lineTo(
                topLeft.x + horizontal.x + verticalStep.x - gap / 2f,
                topLeft.y + horizontal.y + verticalStep.y - gap / 2f
            )
            lineTo(topLeft.x + verticalStep.x + gap / 2f, topLeft.y + verticalStep.y - gap / 2f)
            close()
        }

        fun drawFace(start: Int, anchor: Offset, horizontal: Offset, verticalStep: Offset) {
            repeat(3) { row ->
                repeat(3) { col ->
                    val cellTopLeft = plus(plus(anchor, horizontal, col), verticalStep, row)
                    val value = facelets.getOrNull(start + row * 3 + col) ?: 'U'
                    val path = stickerPath(cellTopLeft, horizontal, verticalStep)
                    drawPath(path, color = cubeStickerColor(value))
                    drawPath(path, color = outline, style = Stroke(width = 0.8.dp.toPx()))
                }
            }
        }

        // CubeEngine's canonical order is URFDLB: U on top, F on the left,
        // and R on the right. Keeping this mapping explicit avoids a misleading
        // diagram even when the algorithm data changes.
        drawFace(18, plus(origin, down, 3), across, vertical)
        drawFace(9, plus(origin, across, 3), down, vertical)
        drawFace(0, origin, across, down)
    }
}

@Composable
private fun FlatCasePreview(stage: Stage, facelets: String, modifier: Modifier = Modifier, assistLabels: Boolean = false) {
    val stageName = stage.label
    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (assistLabels) "$stageName 九宫格状态图，颜色标签已开启" else "$stageName 九宫格状态图"
        }
    ) {
        val cell = (size.minDimension / 5.6f).coerceAtLeast(1f)
        val grid = cell * 3f
        val railCell = cell * 0.64f
        val railGap = cell * 0.42f
        val mainLeft = (size.width - grid) / 2f
        val mainTop = (size.height - grid) / 2f
        val cellRadius = CornerRadius((cell * 0.08f).coerceAtLeast(1f))
        val railRadius = CornerRadius((railCell * 0.08f).coerceAtLeast(1f))

        fun mainColor(value: Char): Color {
            // OLL and PLL are both read from the yellow U face in the
            // yellow-top/blue-front convention. The side rails show the four
            // adjacent top rows so the same diagram also communicates the
            // front/side state without turning the cube into a net.
            val neutralFace = if (stage == Stage.OLL) 'U' else 'F'
            val neutral = value == neutralFace
            if (neutral) return CubeTraceColors.diagramGrayLight
            return when (value) {
                'F', 'B' -> CubeTraceColors.diagramBlueDark
                'L', 'D' -> CubeTraceColors.diagramBlueLight
                else -> CubeTraceColors.diagramBlue
            }
        }

        fun railColor(value: Char): Color = when (value) {
            'U', 'R' -> CubeTraceColors.diagramGrayLight
            'F', 'B' -> CubeTraceColors.diagramBlue
            else -> CubeTraceColors.diagramGray
        }

        fun drawCell(left: Float, top: Float, sizePx: Float, color: Color, radius: CornerRadius) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(sizePx, sizePx),
                cornerRadius = radius
            )
            drawRoundRect(
                color = CubeTraceColors.paper.copy(alpha = 0.78f),
                topLeft = Offset(left, top),
                size = Size(sizePx, sizePx),
                cornerRadius = radius,
                style = Stroke(width = 0.8.dp.toPx())
            )
        }

        val mainStart = if (stage == Stage.OLL) 0 else 18
        repeat(3) { row ->
            repeat(3) { col ->
                val value = facelets.getOrNull(mainStart + row * 3 + col) ?: 'U'
                drawCell(mainLeft + col * cell, mainTop + row * cell, cell - cell * 0.06f, mainColor(value), cellRadius)
            }
        }
        drawRoundRect(
            color = CubeTraceColors.diagramBlueDark,
            topLeft = Offset(mainLeft - cell * 0.18f, mainTop - cell * 0.18f),
            size = Size(grid + cell * 0.36f, grid + cell * 0.36f),
            cornerRadius = CornerRadius(cell * 0.13f),
            style = Stroke(width = 1.4.dp.toPx())
        )

        val contextStarts = if (stage == Stage.OLL) {
            intArrayOf(36, 9, 45, 18) // L, R, B, F around the yellow U face
        } else {
            intArrayOf(36, 9, 0, 27) // L, R, U, D around the blue F face
        }
        val leftX = mainLeft - railGap - railCell
        val rightX = mainLeft + grid + railGap
        val verticalRailTop = mainTop + (grid - railCell * 3f) / 2f
        repeat(3) { index ->
            val leftValue = facelets.getOrNull(contextStarts[0] + index) ?: 'U'
            val rightValue = facelets.getOrNull(contextStarts[1] + index) ?: 'U'
            drawCell(leftX, verticalRailTop + index * railCell, railCell - 0.8f, railColor(leftValue), railRadius)
            drawCell(rightX, verticalRailTop + index * railCell, railCell - 0.8f, railColor(rightValue), railRadius)
        }
        val horizontalRailLeft = mainLeft + (grid - railCell * 3f) / 2f
        val topY = mainTop - railGap - railCell
        val bottomY = mainTop + grid + railGap
        repeat(3) { index ->
            val topValue = facelets.getOrNull(contextStarts[2] + index) ?: 'U'
            val bottomValue = facelets.getOrNull(contextStarts[3] + index) ?: 'U'
            drawCell(horizontalRailLeft + index * railCell, topY, railCell - 0.8f, railColor(topValue), railRadius)
            drawCell(horizontalRailLeft + index * railCell, bottomY, railCell - 0.8f, railColor(bottomValue), railRadius)
        }
    }
}

private fun cubeStickerColor(value: Char): Color = when (value) {
    // The canonical URFDLB positions are now displayed as yellow top and
    // blue front; the face letters remain positional, not paint names.
    'U' -> CubeTraceColors.yellowFace
    'R' -> CubeTraceColors.redFace
    'F' -> CubeTraceColors.blueFace
    'D' -> CubeTraceColors.whiteFace
    'L' -> CubeTraceColors.orangeFace
    'B' -> CubeTraceColors.greenFace
    else -> CubeTraceColors.paper
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveTokenRow(notation: String, wrap: Boolean = false) {
    val tokens = notation.split(" ").filter(String::isNotBlank)
    if (wrap) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tokens.forEach { token ->
                Text(
                    token,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(CubeTraceColors.trackSoft)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState())
        ) {
            tokens.forEach { token ->
                Text(
                    token,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(CubeTraceColors.trackSoft)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, onClick: () -> Unit, action: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("□", fontSize = 34.sp, color = CubeTraceColors.track)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(body, fontSize = 13.sp, color = CubeTraceColors.muted, modifier = Modifier.padding(top = 5.dp))
        if (action != null) Button(onClick = onClick, modifier = Modifier.padding(top = 14.dp)) { Text(action) }
    }
}

@Composable
private fun SectionEyebrow(text: String) { Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.1.sp, color = CubeTraceColors.track, fontWeight = FontWeight.SemiBold) }

@Composable
private fun SectionRule(text: String) { Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); HorizontalDivider(modifier = Modifier.weight(1f), color = CubeTraceColors.line) } }

@Composable
private fun StatBlock(label: String, value: String, caption: String) { Column(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(CubeTraceColors.paper).padding(12.dp).widthIn(min = 92.dp)) { Text(label, fontSize = 12.sp, color = CubeTraceColors.muted); Text(value, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)); Text(caption, fontSize = 11.sp, color = CubeTraceColors.muted) } }

@Composable
private fun MetricText(label: String, value: String) { Column { Text(label, fontSize = 12.sp, color = CubeTraceColors.muted); Text(value, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp)) } }
