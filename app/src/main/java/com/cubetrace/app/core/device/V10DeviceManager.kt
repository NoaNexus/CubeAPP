// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.device

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.cubetrace.app.core.cube.CubeState
import com.cubetrace.app.core.cube.MoveParser
import com.cubetrace.app.core.cube.moyuFaceletsToYellowTopBlueFront
import com.cubetrace.app.core.cube.moyuMoveToYellowTopBlueFront
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.sqrt

sealed interface DeviceStatus {
    data object Unavailable : DeviceStatus
    data object Idle : DeviceStatus
    data object PermissionRequired : DeviceStatus
    data object Scanning : DeviceStatus
    data class Connecting(val name: String) : DeviceStatus
    data class Ready(val name: String, val battery: Int? = null, val firmware: String? = null) : DeviceStatus
    data class Syncing(val reason: String) : DeviceStatus
    data class Error(val message: String) : DeviceStatus
}

data class NearbyV10Device(
    val device: BluetoothDevice,
    val name: String,
    val addressHint: String,
    val rssi: Int,
    val verified: Boolean = false,
    val macCandidates: List<String> = emptyList()
)

/** State used by the timer cube after a smart cube has been connected. */
data class DeviceLiveState(
    val facelets: String? = null,
    val sequence: Int? = null,
    val orientation: Quaternion? = null,
    val lastMove: String? = null,
    /** All physical moves represented by the latest event packet, oldest first. */
    val moveHistory: List<String> = emptyList(),
    val animationFromFacelets: String? = null,
    val synced: Boolean = false
)

/**
 * An accepted A5 packet, or an explicit gap that was refused and is awaiting
 * an authoritative A3 snapshot.  This stream is the source of truth for
 * solve recording; Compose may skip UI snapshots, but it must never skip one
 * of these events.
 */
data class DeviceMoveEvent(
    val sequence: Int,
    val previousSequence: Int?,
    val moves: List<String>,
    val deviceTimeMs: Long?,
    val receivedAtElapsedMs: Long,
    val beforeFacelets: String?,
    val afterFacelets: String?,
    val gap: Boolean = false,
    val gapReason: String? = null
)

class V10DeviceManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private val _status = MutableStateFlow<DeviceStatus>(DeviceStatus.Idle)
    val status: StateFlow<DeviceStatus> = _status
    private val _devices = MutableStateFlow<List<NearbyV10Device>>(emptyList())
    val devices: StateFlow<List<NearbyV10Device>> = _devices
    private val _messages = MutableStateFlow<List<V10Message>>(emptyList())
    val messages: StateFlow<List<V10Message>> = _messages
    private val _liveState = MutableStateFlow(DeviceLiveState())
    val liveState: StateFlow<DeviceLiveState> = _liveState
    private val moveEventChannel = Channel<DeviceMoveEvent>(Channel.UNLIMITED)
    val moveEvents: Flow<DeviceMoveEvent> = moveEventChannel.receiveAsFlow()

    private var currentGatt: BluetoothGatt? = null
    private var currentDevice: BluetoothDevice? = null
    private var connectedItem: NearbyV10Device? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var packetCipher: V10Protocol.PacketCipher? = null
    private var cipherCandidates: List<CipherCandidate> = emptyList()
    private var activeCipherIndex = 0
    private var awaitingDeviceInfo = false
    private var followUpRequestsQueued = false

    private val operations = ArrayDeque<GattOperation>()
    private var operationInFlight = false
    private val continuity = QuaternionContinuity()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val faceletReconcileRunnable = Runnable {
        if (currentGatt != null && writeCharacteristic != null &&
            !faceletResyncPending && !faceletVerificationPending
        ) {
            // This is a routine post-move consistency check. Do not expose a
            // transient Syncing status to Compose: Ready -> Syncing -> Ready
            // was causing the entire timer scaffold to flash on every move.
            requestFaceletsForVerification()
        }
    }

    private var cubeState: CubeState? = null
    private var lastSequence: Int? = null
    // Once a sequence gap or malformed move window is detected, do not apply
    // any later A5 packets to the stale state. Wait for the requested A3
    // snapshot and resume only from that authoritative facelet state.
    private var faceletResyncPending = false
    // Routine verification must not block newer A5 move notifications while
    // its A3 response is in flight.
    private var faceletVerificationPending = false
    private var orientationReference: Quaternion? = null
    private var latestRawOrientation: Quaternion? = null
    private var emittedOrientation: Quaternion? = null
    private var readyMetadata: DeviceStatus.Ready? = null
    private var handshakeToken = 0
    private var malformedPacketStreak = 0

    private data class CipherCandidate(
        val address: String,
        val cipher: V10Protocol.PacketCipher
    )

    private data class GattOperation(
        val kind: Kind,
        val command: V10Protocol.SafeCommand? = null,
        val descriptor: BluetoothGattDescriptor? = null
    ) {
        enum class Kind { COMMAND, DESCRIPTOR }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "未命名设备"
            val normalizedName = name.uppercase()
            val address = V10Protocol.normalizeMac(device.address).orEmpty()
            val advertisesTargetService = result.scanRecord?.serviceUuids?.any {
                it.uuid.toString().equals(V10Protocol.SERVICE_UUID, ignoreCase = true)
            } == true
            val hasKnownMoyuAddressPrefix = address.startsWith("CF:30:")
            if (!normalizedName.startsWith(V10Protocol.DEVICE_PREFIX) &&
                !normalizedName.startsWith("WCU_") &&
                !normalizedName.startsWith("WCU_MY3") &&
                !normalizedName.startsWith("^S") &&
                !advertisesTargetService &&
                !hasKnownMoyuAddressPrefix
            ) return

            val existing = _devices.value.firstOrNull { it.device.address == device.address }
            val candidates = buildList {
                V10Protocol.normalizeMac(device.address)?.let(::add)
                result.scanRecord?.manufacturerSpecificData?.let { data ->
                    repeat(data.size()) {
                        V10Protocol.macFromManufacturerData(data.valueAt(it))?.let(::add)
                    }
                }
                V10Protocol.macHintFromName(name)?.let(::add)
            }.distinct()
            val addressHint = candidates.firstOrNull() ?: device.address
            val item = NearbyV10Device(
                device = device,
                name = name,
                addressHint = addressHint,
                rssi = result.rssi,
                verified = existing?.verified ?: false,
                macCandidates = candidates
            )
            _devices.value = (_devices.value.filterNot { it.device.address == device.address } + item)
                .sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _status.value = DeviceStatus.Error("扫描失败（$errorCode），请确认蓝牙和附近设备权限")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                _status.value = DeviceStatus.Connecting(currentDevice?.safeName() ?: "V10 AI")
                if (hasConnectPermission()) gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                operations.clear()
                operationInFlight = false
                resetLiveState()
                if (currentGatt === gatt) {
                    // Android can leave a failed GATT client object around
                    // after status 133. Close it here so the next scan can
                    // create a fresh connection instead of being blocked by
                    // a stale handle.
                    closeGatt()
                } else {
                    gatt.close()
                }
                _status.value = DeviceStatus.Error("魔方已断开；公式和手动计时仍可用")
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                if (currentGatt === gatt) failConnection("无法连接魔方，请确认它没有连接到其他 APP") else gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("没有找到 V10 AI 服务，请导出诊断")
                return
            }
            val service = gatt.getService(UUID.fromString(V10Protocol.SERVICE_UUID))
            notifyCharacteristic = service?.getCharacteristic(UUID.fromString(V10Protocol.NOTIFY_UUID))
            writeCharacteristic = service?.getCharacteristic(UUID.fromString(V10Protocol.WRITE_UUID))
            val notify = notifyCharacteristic
            if (service == null || notify == null || writeCharacteristic == null) {
                failConnection("设备名称匹配，但协议服务未验证")
                return
            }
            if (!hasConnectPermission()) {
                failConnection("需要附近设备权限才能读取魔方")
                return
            }

            operations.clear()
            operationInFlight = false
            awaitingDeviceInfo = true
            followUpRequestsQueued = false
            gatt.setCharacteristicNotification(notify, true)
            val descriptor = notify.getDescriptor(UUID.fromString(CLIENT_CONFIG_UUID))
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                operations.addLast(GattOperation(GattOperation.Kind.DESCRIPTOR, descriptor = descriptor))
            }
            // Do not send every command before the encrypted handshake has
            // selected a valid MAC/key. A wrong key used to make all later
            // responses look like unknown firmware and battery packets.
            queueCommand(V10Protocol.SafeCommand.REQUEST_DEVICE_INFO)
            pumpOperations()
            armHandshakeTimeout()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            completeOperation(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            completeOperation(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("API 33 callback retained for Android 8–12 compatibility")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            consumePacket(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            consumePacket(value)
        }
    }

    fun startScan() {
        // A connected cube is already streaming through the same GATT link.
        // Starting a second scan must not overwrite Ready with Scanning and
        // make the UI look disconnected while packets are still arriving.
        if (currentGatt != null) {
            markReadyKeepingMetadata()
            return
        }
        if (adapter == null) {
            _status.value = DeviceStatus.Unavailable
            return
        }
        if (!hasScanPermission()) {
            _status.value = DeviceStatus.PermissionRequired
            return
        }
        _devices.value = emptyList()
        scanner?.startScan(scanCallback)
        _status.value = DeviceStatus.Scanning
    }

    fun stopScan() {
        if (hasScanPermission()) scanner?.stopScan(scanCallback)
        if (_status.value is DeviceStatus.Scanning) {
            if (currentGatt != null && !awaitingDeviceInfo) markReadyKeepingMetadata()
            else _status.value = DeviceStatus.Idle
        }
    }

    fun connect(item: NearbyV10Device) {
        stopScan()
        if (!hasConnectPermission()) {
            _status.value = DeviceStatus.PermissionRequired
            return
        }
        closeGatt()
        currentDevice = item.device
        connectedItem = item
        val addresses = buildList {
            addAll(item.macCandidates)
            add(item.addressHint)
            add(item.device.address)
        }.mapNotNull(V10Protocol::normalizeMac).distinct()
        cipherCandidates = addresses.mapNotNull { address ->
            V10Protocol.deriveKeyIv(address)?.let { CipherCandidate(address, V10Protocol.PacketCipher(it)) }
        }
        activeCipherIndex = 0
        packetCipher = cipherCandidates.firstOrNull()?.cipher
        awaitingDeviceInfo = true
        followUpRequestsQueued = false
        readyMetadata = null
        resetLiveState()
        if (cipherCandidates.isEmpty()) {
            _status.value = DeviceStatus.Error("无法取得设备地址，无法完成智能魔方加密握手")
            return
        }
        _status.value = DeviceStatus.Connecting(item.name)
        currentGatt = item.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun request(command: V10Protocol.SafeCommand) {
        if (currentGatt == null || writeCharacteristic == null) return
        if (command == V10Protocol.SafeCommand.REQUEST_FACELETS) {
            requestFaceletsForResync("正在读取魔方局面")
            return
        }
        queueCommand(command)
        pumpOperations()
    }

    fun close() {
        stopScan()
        closeGatt()
        _status.value = DeviceStatus.Idle
    }

    private fun queueCommand(command: V10Protocol.SafeCommand) {
        operations.addLast(GattOperation(GattOperation.Kind.COMMAND, command = command))
    }

    private fun pumpOperations() {
        if (operationInFlight) return
        if (operations.isEmpty()) {
            if (!awaitingDeviceInfo) markReadyKeepingMetadata()
            return
        }
        val gatt = currentGatt ?: return
        if (!hasConnectPermission()) return
        val operation = operations.removeFirst()
        operationInFlight = true
        val started = when (operation.kind) {
            GattOperation.Kind.DESCRIPTOR -> operation.descriptor?.let {
                gatt.writeDescriptor(it)
            } ?: false
            GattOperation.Kind.COMMAND -> {
                val characteristic = writeCharacteristic ?: return
                val plain = operation.command?.plainPayload() ?: return
                val cipher = packetCipher ?: return
                characteristic.writeType = when {
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else -> characteristic.writeType
                }
                characteristic.value = cipher.encrypt(plain)
                gatt.writeCharacteristic(characteristic)
            }
        }
        if (!started) completeOperation(false)
    }

    private fun completeOperation(success: Boolean) {
        operationInFlight = false
        if (!success) {
            operations.clear()
            failConnection("V10 AI 指令未完成，已停止继续写入")
            return
        }
        pumpOperations()
    }

    private fun consumePacket(encrypted: ByteArray) {
        val decoded = decodeWithCandidates(encrypted)
        if (decoded == null) {
            if (awaitingDeviceInfo && advanceCipherCandidate()) return
            if (awaitingDeviceInfo) {
                operations.clear()
                operationInFlight = false
                failConnection("加密握手失败：未能验证设备地址，请重新扫描后连接")
                return
            }
            // Some firmware revisions emit a short vendor/status packet or a
            // packet type outside the public move/gyro set. One such packet
            // must not tear down an already verified connection; only a
            // sustained parse failure is surfaced as a device error.
            malformedPacketStreak++
            if (malformedPacketStreak >= MAX_MALFORMED_PACKETS) {
                _status.value = DeviceStatus.Syncing("设备数据暂时无法解析，正在重新读取局面")
                requestFaceletsForResync("设备数据暂时无法解析，正在重新读取局面")
                malformedPacketStreak = 0
            }
            return
        }

        malformedPacketStreak = 0
        val message = decoded.second
        if (message is V10Message.Gyro) {
            val stable = continuity.accept(message.quaternion)
            updateOrientation(stable)
        } else {
            // Gyro packets can arrive many times per second. Keeping them out
            // of this diagnostic list prevents the Compose UI from redrawing
            // the whole connection dialog at sensor rate.
            _messages.value = (_messages.value + message).takeLast(50)
        }

        when (message) {
            is V10Message.Hardware -> handleHardware(message)
            is V10Message.Battery -> handleBattery(message)
            is V10Message.Facelets -> handleFacelets(message)
            is V10Message.Move -> handleMove(message)
            is V10Message.Gyro -> Unit
            is V10Message.GyroStatus -> Unit
            is V10Message.Unknown -> Unit
        }
    }

    private fun decodeWithCandidates(encrypted: ByteArray): Pair<Int, V10Message>? {
        val indexes = if (awaitingDeviceInfo) {
            buildList {
                add(activeCipherIndex)
                addAll(cipherCandidates.indices)
            }.distinct()
        } else {
            listOf(activeCipherIndex)
        }
        for (index in indexes) {
            val candidate = cipherCandidates.getOrNull(index) ?: continue
            val message = runCatching {
                V10MessageDecoder.decode(candidate.cipher.decrypt(encrypted))
            }.getOrNull()
            if (message != null && (isPlausible(message) || (!awaitingDeviceInfo && message is V10Message.Unknown))) {
                activeCipherIndex = index
                packetCipher = candidate.cipher
                return index to message
            }
        }
        return null
    }

    private fun isPlausible(message: V10Message): Boolean = when (message) {
        is V10Message.Hardware -> message.deviceName.isBlank() || (
            message.deviceName.all { it in '\u0020'..'\u007E' } &&
                message.deviceName.any(Char::isLetterOrDigit)
            )
        is V10Message.Facelets -> message.facelets.length == 54 &&
            "URFDLB".all { face -> message.facelets.count { it == face } == 9 }
        is V10Message.Battery -> message.percent in 0..100
        is V10Message.Move -> message.moves.size == 5 && message.moves.any { it != null }
        is V10Message.Gyro -> {
            val q = message.quaternion
            val length = sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w)
            length.isFinite() && abs(length - 1.0) < 0.05
        }
        is V10Message.GyroStatus -> true
        is V10Message.Unknown -> false
    }

    private fun advanceCipherCandidate(): Boolean {
        val next = (activeCipherIndex + 1 until cipherCandidates.size).firstOrNull() ?: return false
        activeCipherIndex = next
        packetCipher = cipherCandidates[next].cipher
        operations.clear()
        operationInFlight = false
        queueCommand(V10Protocol.SafeCommand.REQUEST_DEVICE_INFO)
        pumpOperations()
        armHandshakeTimeout()
        return true
    }

    private fun handleHardware(message: V10Message.Hardware) {
        awaitingDeviceInfo = false
        cancelHandshakeTimeout()
        val name = message.deviceName.ifBlank { currentDevice?.safeName() ?: "V10 AI" }
        val current = (_status.value as? DeviceStatus.Ready) ?: readyMetadata
        val ready = (current ?: DeviceStatus.Ready(name)).copy(
            name = name,
            firmware = message.softwareVersion
        )
        readyMetadata = ready
        _status.value = ready
        connectedItem?.let { item ->
            _devices.value = _devices.value.map {
                if (it.device.address == item.device.address) it.copy(verified = true) else it
            }
        }
        if (!followUpRequestsQueued) {
            followUpRequestsQueued = true
            queueCommand(V10Protocol.SafeCommand.REQUEST_FACELETS)
            queueCommand(V10Protocol.SafeCommand.REQUEST_BATTERY)
            // A few WCU firmware revisions need a second request burst before
            // they start sending stable status/gyro packets.
            queueCommand(V10Protocol.SafeCommand.REQUEST_DEVICE_INFO)
            queueCommand(V10Protocol.SafeCommand.REQUEST_FACELETS)
            queueCommand(V10Protocol.SafeCommand.REQUEST_BATTERY)
            queueCommand(V10Protocol.SafeCommand.ENABLE_GYRO)
            queueCommand(V10Protocol.SafeCommand.REQUEST_FACELETS)
        }
        pumpOperations()
    }

    private fun handleBattery(message: V10Message.Battery) {
        val current = (_status.value as? DeviceStatus.Ready) ?: readyMetadata ?: return
        val ready = current.copy(battery = message.percent)
        readyMetadata = ready
        _status.value = ready
    }

    private fun handleFacelets(message: V10Message.Facelets) {
        // Convert before sequence checks as cubeState is stored in the app's
        // yellow-top / blue-front frame, not the raw V10 factory frame.
        val appFacelets = moyuFaceletsToYellowTopBlueFront(message.facelets)
        val previousSequence = lastSequence
        Log.d(LOG_TAG, "A3 seq=${message.sequence} prev=$previousSequence pending=$faceletResyncPending raw=${message.facelets} app=$appFacelets")
        if (previousSequence != null &&
            message.sequence == previousSequence &&
            cubeState != null &&
            appFacelets != cubeState?.asFacelets() &&
            !faceletResyncPending &&
            !faceletVerificationPending
        ) {
            // A delayed response captured before the last move can have the
            // same counter on a few firmware revisions. It must not roll back
            // the state reconstructed from the move stream. A response to an
            // explicit recovery or routine verification request is
            // authoritative and is allowed to correct an incomplete replay.
            return
        }
        if (previousSequence != null && message.sequence != previousSequence) {
            val forward = (message.sequence - previousSequence + 256) % 256
            // A3 responses can be delayed behind a burst of A5 packets. Do
            // not let an older snapshot roll the visible cube backwards. A
            // A recovery request can itself have an older A3 response queued
            // behind it. Never let that response roll the reconstructed state
            // backwards; a real current snapshot has the current counter or
            // a small forward distance (including the normal byte wrap).
            if (forward > 128) return
        }
        mainHandler.removeCallbacks(faceletReconcileRunnable)
        // V10 reports its physical factory frame (white U / green F). The
        // rest of the app is intentionally yellow U / blue F, so convert the
        // complete sticker geometry before it enters CubeState. This also
        // keeps the move stream and the A3 snapshots in one coordinate frame.
        val state = CubeState.fromFacelets(appFacelets)
        if (state == null) {
            requestFaceletsForResync("收到的局面数据无效，正在重新读取")
            return
        }
        if (orientationReference == null && latestRawOrientation != null) {
            orientationReference = latestRawOrientation
            emittedOrientation = Quaternion.identity()
            _liveState.value = _liveState.value.copy(orientation = Quaternion.identity())
        }
        val sameState = lastSequence == message.sequence && cubeState?.asFacelets() == state.asFacelets()
        cubeState = state
        lastSequence = message.sequence
        faceletResyncPending = false
        faceletVerificationPending = false
        if (sameState) {
            // A routine A3 verification is often identical to the state just
            // reconstructed from A5. Do not clear lastMove or replace the
            // animation snapshot in that case: that extra emission was the
            // visible page flash at the end of every turn.
            markReadyKeepingMetadata()
            return
        }
        _liveState.value = _liveState.value.copy(
            facelets = state.asFacelets(),
            sequence = message.sequence,
            lastMove = null,
            moveHistory = emptyList(),
            animationFromFacelets = null,
            synced = true
        )
        markReadyKeepingMetadata()
    }

    private fun handleMove(message: V10Message.Move) {
        val receivedAtElapsedMs = SystemClock.elapsedRealtime()
        if (faceletResyncPending) return
        val previousSequence = lastSequence
        if (previousSequence == null || cubeState == null) {
            requestFaceletsForResync("正在先读取当前局面")
            return
        }
        val state = cubeState ?: return
        val previousFacelets = state.asFacelets()
        val difference = (message.sequence - previousSequence + 256) % 256
        // A delayed notification from before the current A3 snapshot is not
        // a missing 255-move burst. Ignore it instead of needlessly replacing
        // the valid state with another recovery cycle.
        if (difference == 0 || difference > 128) return
        if (difference !in 1..message.moves.size) {
            emitMoveGap(
                sequence = message.sequence,
                previousSequence = previousSequence,
                deviceTimeMs = message.deviceOffsetMs.toLong(),
                receivedAtElapsedMs = receivedAtElapsedMs,
                beforeFacelets = previousFacelets,
                reason = "动作序号中断"
            )
            requestFaceletsForResync("动作序号中断，正在重新同步")
            return
        }
        val window = message.moves.take(difference)
        // The protocol reports the newest move in slot 0, then older moves.
        // Apply the window backwards, but only after confirming every required
        // slot is present. A missing slot means the authoritative A3 snapshot
        // is safer than guessing.
        if (window.any { it == null }) {
            emitMoveGap(
                sequence = message.sequence,
                previousSequence = previousSequence,
                deviceTimeMs = message.deviceOffsetMs.toLong(),
                receivedAtElapsedMs = receivedAtElapsedMs,
                beforeFacelets = previousFacelets,
                reason = "动作历史不完整"
            )
            requestFaceletsForResync("动作历史不完整，正在重新同步")
            return
        }
        // A newer move makes any in-flight routine verification stale. Keep
        // consuming the move stream and verify again after the new quiet
        // period instead of dropping this move behind the request.
        faceletVerificationPending = false
        val appliedMoves = window.filterNotNull()
            .asReversed()
            .map(::moyuMoveToYellowTopBlueFront)
        Log.d(LOG_TAG, "A5 seq=${message.sequence} prev=$previousSequence rawMoves=${message.moves} appMoves=$appliedMoves")
        var nextState = state
        for (notation in appliedMoves) {
            val parsed = MoveParser.parseOrEmpty(notation)
            if (parsed.size != 1) {
                emitMoveGap(
                    sequence = message.sequence,
                    previousSequence = previousSequence,
                    deviceTimeMs = message.deviceOffsetMs.toLong(),
                    receivedAtElapsedMs = receivedAtElapsedMs,
                    beforeFacelets = previousFacelets,
                    reason = "动作无法解析"
                )
                requestFaceletsForResync("动作数据无法识别，正在重新同步")
                return
            }
            nextState = nextState.apply(parsed.first())
        }
        cubeState = nextState
        lastSequence = message.sequence
        val animatedMove = appliedMoves.singleOrNull()
        _liveState.value = _liveState.value.copy(
            facelets = nextState.asFacelets(),
            sequence = message.sequence,
            lastMove = animatedMove,
            moveHistory = appliedMoves,
            animationFromFacelets = if (animatedMove != null) previousFacelets else null,
            synced = true
        )
        moveEventChannel.trySend(
            DeviceMoveEvent(
                sequence = message.sequence,
                previousSequence = previousSequence,
                moves = appliedMoves,
                deviceTimeMs = message.deviceOffsetMs.toLong(),
                receivedAtElapsedMs = receivedAtElapsedMs,
                beforeFacelets = previousFacelets,
                afterFacelets = nextState.asFacelets()
            )
        )
        scheduleFaceletReconciliation()
    }

    private fun emitMoveGap(
        sequence: Int,
        previousSequence: Int?,
        deviceTimeMs: Long?,
        receivedAtElapsedMs: Long,
        beforeFacelets: String?,
        reason: String
    ) {
        moveEventChannel.trySend(
            DeviceMoveEvent(
                sequence = sequence,
                previousSequence = previousSequence,
                moves = emptyList(),
                deviceTimeMs = deviceTimeMs,
                receivedAtElapsedMs = receivedAtElapsedMs,
                beforeFacelets = beforeFacelets,
                afterFacelets = null,
                gap = true,
                gapReason = reason
            )
        )
    }

    private fun updateOrientation(raw: Quaternion) {
        latestRawOrientation = raw
        if (orientationReference == null) {
            // Do not establish the neutral scene before the first trusted
            // facelet snapshot. This lets the initial physical pose (for
            // example white top / green front / red right) become the model's
            // U/F/R pose instead of inheriting a handshake pose.
            if (cubeState == null) {
                _liveState.value = _liveState.value.copy(orientation = Quaternion.identity())
                return
            }
            orientationReference = raw
            emittedOrientation = Quaternion.identity()
            _liveState.value = _liveState.value.copy(orientation = Quaternion.identity())
            return
        }
        val relative = raw.relativeTo(orientationReference ?: raw)
        val previous = emittedOrientation
        if (previous != null && quaternionAngle(previous, relative) < ORIENTATION_NOISE_RAD) return
        emittedOrientation = relative
        // Keep the newest sensor sample here. Display-rate interpolation is
        // handled by Cube3DView, so the BLE path does not add a second layer
        // of latency before the cube reaches the screen.
        _liveState.value = _liveState.value.copy(orientation = relative)
    }

    /** Re-anchors the current physical pose as the neutral U/F/R scene. */
    fun calibrateOrientation() {
        val raw = latestRawOrientation ?: return
        orientationReference = raw
        emittedOrientation = Quaternion.identity()
        _liveState.value = _liveState.value.copy(orientation = Quaternion.identity())
    }

    private fun quaternionAngle(from: Quaternion, to: Quaternion): Double =
        2.0 * acos(abs(from.dot(to)).coerceIn(0.0, 1.0))

    private fun requestFaceletsForResync(reason: String, showStatus: Boolean = true) {
        mainHandler.removeCallbacks(faceletReconcileRunnable)
        if (showStatus) _status.value = DeviceStatus.Syncing(reason)
        faceletResyncPending = true
        faceletVerificationPending = false
        if (operations.none { it.command == V10Protocol.SafeCommand.REQUEST_FACELETS }) {
            queueCommand(V10Protocol.SafeCommand.REQUEST_FACELETS)
        }
        pumpOperations()
    }

    private fun requestFaceletsForVerification() {
        mainHandler.removeCallbacks(faceletReconcileRunnable)
        if (faceletResyncPending || faceletVerificationPending) return
        faceletVerificationPending = true
        if (operations.none { it.command == V10Protocol.SafeCommand.REQUEST_FACELETS }) {
            queueCommand(V10Protocol.SafeCommand.REQUEST_FACELETS)
        }
        pumpOperations()
    }

    private fun scheduleFaceletReconciliation() {
        // A5 is an event stream without an acknowledgement. If the final
        // packet of a fast burst is lost, there is no later sequence number to
        // expose the gap. Read the authoritative A3 state after a quiet
        // period, after the 3D turn animation has completed.
        mainHandler.removeCallbacks(faceletReconcileRunnable)
        mainHandler.postDelayed(faceletReconcileRunnable, FACELET_RECONCILE_DELAY_MS)
    }

    private fun markReadyKeepingMetadata() {
        if (awaitingDeviceInfo) return
        val item = currentDevice ?: return
        val current = (_status.value as? DeviceStatus.Ready) ?: readyMetadata
        _status.value = current ?: DeviceStatus.Ready(item.safeName())
    }

    private fun armHandshakeTimeout() {
        val token = ++handshakeToken
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!awaitingDeviceInfo || token != handshakeToken) return@postDelayed
            if (!advanceCipherCandidate()) {
                awaitingDeviceInfo = false
                operations.clear()
                operationInFlight = false
                failConnection("加密握手超时：未能读取设备信息，请确认魔方未被其他 APP 占用")
            }
        }, HANDSHAKE_TIMEOUT_MS)
    }

    private fun cancelHandshakeTimeout() {
        handshakeToken++
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun resetLiveState() {
        mainHandler.removeCallbacks(faceletReconcileRunnable)
        continuity.reset()
        cubeState = null
        lastSequence = null
        faceletResyncPending = false
        faceletVerificationPending = false
        orientationReference = null
        latestRawOrientation = null
        emittedOrientation = null
        _liveState.value = DeviceLiveState()
        _messages.value = emptyList()
        malformedPacketStreak = 0
    }

    private fun closeGatt() {
        val gatt = currentGatt
        currentGatt = null
        currentDevice = null
        connectedItem = null
        notifyCharacteristic = null
        writeCharacteristic = null
        packetCipher = null
        cipherCandidates = emptyList()
        activeCipherIndex = 0
        awaitingDeviceInfo = false
        followUpRequestsQueued = false
        readyMetadata = null
        cancelHandshakeTimeout()
        operations.clear()
        operationInFlight = false
        resetLiveState()
        if (gatt != null && hasConnectPermission()) gatt.close()
    }

    private fun failConnection(message: String) {
        closeGatt()
        _status.value = DeviceStatus.Error(message)
    }

    private fun hasScanPermission(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun BluetoothDevice.safeName(): String = runCatching { name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "V10 AI"

    companion object {
        private const val LOG_TAG = "CubeTraceV10"
        private const val CLIENT_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        private const val FACELET_RECONCILE_DELAY_MS = 650L
        private const val ORIENTATION_NOISE_RAD = 0.008
        private const val HANDSHAKE_TIMEOUT_MS = 6_000L
        private const val MAX_MALFORMED_PACKETS = 8

        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
