// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app.core.device

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.hypot

object V10Protocol {
    const val DEVICE_PREFIX = "WCU_MY32_"
    const val SERVICE_UUID = "0783b03e-7735-b5a0-1760-a305d2795cb0"
    const val NOTIFY_UUID = "0783b03e-7735-b5a0-1760-a305d2795cb1"
    const val WRITE_UUID = "0783b03e-7735-b5a0-1760-a305d2795cb2"

    private val baseKey = byteArrayOf(
        21, 119, 58, 92, 103, 14, 45, 31, 23, 103, 42, 19, 155.toByte(), 103, 82, 87
    )
    private val baseIv = byteArrayOf(
        17, 35, 38, 37, 134.toByte(), 42, 44, 59, 85, 6, 127, 49, 126, 103, 33, 87
    )

    fun normalizeMac(address: String): String? {
        val compact = address.replace(":", "").replace("-", "").replace(" ", "").uppercase()
        if (compact.length != 12 || compact.any { it !in "0123456789ABCDEF" }) return null
        return compact.chunked(2).joinToString(":")
    }

    fun macBytes(address: String): ByteArray? = normalizeMac(address)
        ?.split(":")
        ?.map { it.toInt(16).toByte() }
        ?.toByteArray()

    fun macHintFromName(deviceName: String?): String? {
        val name = deviceName?.trim()?.uppercase() ?: return null
        val suffix = name.removePrefix(DEVICE_PREFIX)
        if (name.startsWith(DEVICE_PREFIX) && suffix.length == 4 && suffix.all { it in "0123456789ABCDEF" }) {
            return "CF:30:16:00:${suffix.substring(0, 2)}:${suffix.substring(2, 4)}"
        }
        return null
    }

    fun macFromManufacturerData(data: ByteArray): String? {
        if (data.size < 6) return null
        // Manufacturer data normally starts with a two-byte company id. The
        // remaining six bytes contain the cube address in LSB-first order.
        // This offset is important: using the final six bytes also consumes
        // one byte of the address when an Android scan record includes a
        // vendor prefix after the company id.
        val offset = if (data.size >= 8) 2 else 0
        if (data.size < offset + 6) return null
        return (offset until offset + 6).map { data[offset + 5 - (it - offset)].toInt() and 0xff }
            .joinToString(":") { "%02X".format(it) }
    }

    fun deriveKeyIv(address: String): KeyMaterial? {
        val mac = macBytes(address) ?: return null
        val key = baseKey.copyOf()
        val iv = baseIv.copyOf()
        for (i in 0 until 6) {
            key[i] = ((key[i].toInt() and 0xff) + (mac[5 - i].toInt() and 0xff)).mod(255).toByte()
            iv[i] = ((iv[i].toInt() and 0xff) + (mac[5 - i].toInt() and 0xff)).mod(255).toByte()
        }
        return KeyMaterial(key, iv)
    }

    data class KeyMaterial(val key: ByteArray, val iv: ByteArray)

    class PacketCipher(private val material: KeyMaterial) {
        private val key = SecretKeySpec(material.key, "AES")

        fun decrypt(data: ByteArray): ByteArray = transform(data, Cipher.DECRYPT_MODE)
        fun encrypt(data: ByteArray): ByteArray = transform(data, Cipher.ENCRYPT_MODE)

        private fun transform(input: ByteArray, mode: Int): ByteArray {
            require(input.size >= 16) { "V10 packet must be at least one AES block" }
            val result = input.copyOf()
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(mode, key)
            if (mode == Cipher.DECRYPT_MODE) {
                if (result.size > 16) {
                    val offset = result.size - 16
                    val last = cipher.doFinal(result.copyOfRange(offset, result.size))
                    for (i in 0 until 16) result[offset + i] = (last[i].toInt() xor material.iv[i].toInt()).toByte()
                }
                val first = cipher.doFinal(result.copyOfRange(0, 16))
                for (i in 0 until 16) result[i] = (first[i].toInt() xor material.iv[i].toInt()).toByte()
            } else {
                for (i in 0 until 16) result[i] = (result[i].toInt() xor material.iv[i].toInt()).toByte()
                val first = cipher.doFinal(result.copyOfRange(0, 16))
                first.copyInto(result, 0)
                if (result.size > 16) {
                    val offset = result.size - 16
                    for (i in 0 until 16) result[offset + i] = (result[offset + i].toInt() xor material.iv[i].toInt()).toByte()
                    val last = cipher.doFinal(result.copyOfRange(offset, offset + 16))
                    last.copyInto(result, offset)
                }
            }
            return result
        }
    }

    enum class SafeCommand(
        private val opcode: Int,
        private val payloadIndex: Int = 1,
        private val payloadValue: Int = 0
    ) {
        REQUEST_DEVICE_INFO(0xA1),
        REQUEST_FACELETS(0xA3),
        REQUEST_BATTERY(0xA4),
        ENABLE_GYRO(0xAC, 2, 1),
        DISABLE_GYRO(0xAC, 2, 0);

        fun plainPayload(): ByteArray = ByteArray(20).also {
            it[0] = opcode.toByte()
            it[payloadIndex] = payloadValue.toByte()
        }
    }

    fun bitString(bytes: ByteArray): String = bytes.joinToString("") {
        (it.toInt() and 0xff).toString(2).padStart(8, '0')
    }

    fun readBits(bits: String, start: Int, length: Int): Int =
        bits.substring(start, start + length).toInt(2)
}

sealed interface V10Message {
    data class Hardware(
        val deviceName: String,
        val softwareVersion: String,
        val hardwareVersion: String
    ) : V10Message

    data class Facelets(val facelets: String, val sequence: Int) : V10Message
    data class Battery(val percent: Int) : V10Message
    data class Move(
        val move: String,
        val sequence: Int,
        val deviceOffsetMs: Int,
        // A5 contains a fixed five-slot history. Keep null placeholders so
        // the slot index remains aligned with the sequence delta; compressing
        // the list would make a packet with an empty slot apply the wrong move.
        val moves: List<String?> = listOf(move)
    ) : V10Message
    data class Gyro(val quaternion: Quaternion) : V10Message
    data class GyroStatus(val functional: Boolean, val enabled: Boolean) : V10Message
    data class Unknown(val type: Int, val length: Int) : V10Message
}

object V10MessageDecoder {
    private const val FACE_ORDER = "FBUDLR"
    private const val OUTPUT_ORDER = "URFDLB"

    fun decode(packet: ByteArray): V10Message {
        if (packet.isEmpty()) return V10Message.Unknown(-1, 0)
        val type = packet[0].toInt() and 0xff
        if (type == 0xAB) return decodeGyro(packet)
        val bits = V10Protocol.bitString(packet)
        return when (type) {
            0xA1 -> decodeHardware(bits)
            0xA3 -> decodeFacelets(bits)
            0xA4 -> V10Message.Battery(V10Protocol.readBits(bits, 8, 8).coerceIn(0, 100))
            0xA5 -> decodeMove(bits)
            0xAC -> V10Message.GyroStatus(
                functional = V10Protocol.readBits(bits, 8, 8) != 0,
                enabled = V10Protocol.readBits(bits, 16, 8) != 0
            )
            else -> V10Message.Unknown(type, packet.size)
        }
    }

    private fun decodeHardware(bits: String): V10Message.Hardware {
        val name = buildString {
            repeat(8) { append(V10Protocol.readBits(bits, 8 + it * 8, 8).toChar()) }
        }.trim('\u0000', ' ', '\uFFFD')
        // MoYu32 firmware reports software first in the A1 payload. Keep the
        // hardware value separately for diagnostics even though the compact
        // UI displays the software/firmware version.
        val hardware = "${V10Protocol.readBits(bits, 72, 8)}.${V10Protocol.readBits(bits, 80, 8)}"
        val software = "${V10Protocol.readBits(bits, 88, 8)}.${V10Protocol.readBits(bits, 96, 8)}"
        return V10Message.Hardware(name, software, hardware)
    }

    private fun decodeFacelets(bits: String): V10Message.Facelets {
        require(bits.length >= 152) { "facelet packet is truncated" }
        val faces = intArrayOf(2, 5, 0, 3, 4, 1)
        val output = StringBuilder(54)
        for (faceIndex in 0 until 6) {
            val face = faces[faceIndex]
            // The protocol's facelet payload starts after the one-byte type.
            // Skipping this byte is essential: otherwise every 24-bit block
            // is shifted by one byte and a valid A3 response looks malformed.
            val payloadStart = 8 + face * 24
            val faceBits = bits.substring(payloadStart, payloadStart + 24)
            repeat(8) { position ->
                output.append(FACE_ORDER[V10Protocol.readBits(faceBits, position * 3, 3)])
                if (position == 3) output.append(FACE_ORDER[face])
            }
        }
        val sequence = if (bits.length >= 160) V10Protocol.readBits(bits, 152, 8) else 0
        return V10Message.Facelets(output.toString(), sequence)
    }

    private fun decodeMove(bits: String): V10Message.Move {
        require(bits.length >= 160) { "move packet is truncated" }
        val sequence = V10Protocol.readBits(bits, 88, 8)
        val moves = (0 until 5).map { index ->
            val moveCode = V10Protocol.readBits(bits, 96 + index * 5, 5)
            if (moveCode >= 12) {
                null
            } else {
                val face = FACE_ORDER[moveCode shr 1]
                val suffix = if ((moveCode and 1) == 1) "'" else ""
                "$face$suffix"
            }
        }
        return V10Message.Move(
            move = moves.firstOrNull() ?: "?",
            sequence = sequence,
            deviceOffsetMs = V10Protocol.readBits(bits, 8, 16),
            moves = moves
        )
    }

    private fun decodeGyro(packet: ByteArray): V10Message.Gyro {
        require(packet.size >= 17) { "gyro packet is truncated" }
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val w = buffer.getInt(1) / 1_073_741_824.0
        val x = buffer.getInt(5) / 1_073_741_824.0
        val y = buffer.getInt(9) / 1_073_741_824.0
        val z = buffer.getInt(13) / 1_073_741_824.0
        return V10Message.Gyro(Quaternion.normalized(x, y, z, w))
    }
}

data class Quaternion(val x: Double, val y: Double, val z: Double, val w: Double) {
    fun dot(other: Quaternion): Double = x * other.x + y * other.y + z * other.z + w * other.w
    fun negated(): Quaternion = Quaternion(-x, -y, -z, -w)
    fun conjugated(): Quaternion = Quaternion(-x, -y, -z, w)

    fun multiplied(other: Quaternion): Quaternion = normalized(
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
        w * other.w - x * other.x - y * other.y - z * other.z
    )

    fun relativeTo(reference: Quaternion): Quaternion = reference.conjugated().multiplied(this)

    companion object {
        fun normalized(x: Double, y: Double, z: Double, w: Double): Quaternion {
            val length = hypot(hypot(x, y), hypot(z, w))
            require(length.isFinite() && length > 0.0001) { "invalid quaternion length" }
            return Quaternion(x / length, y / length, z / length, w / length)
        }

        fun identity() = Quaternion(0.0, 0.0, 0.0, 1.0)

        fun slerp(from: Quaternion, toInput: Quaternion, progress: Double): Quaternion {
            var to = toInput
            var cosine = from.dot(to)
            if (cosine < 0) {
                to = to.negated()
                cosine = -cosine
            }
            if (cosine > 0.9995) {
                val t = progress.coerceIn(0.0, 1.0)
                return normalized(
                    from.x + t * (to.x - from.x),
                    from.y + t * (to.y - from.y),
                    from.z + t * (to.z - from.z),
                    from.w + t * (to.w - from.w)
                )
            }
            val angle = kotlin.math.acos(cosine.coerceIn(-1.0, 1.0))
            val sinAngle = kotlin.math.sin(angle)
            val a = kotlin.math.sin((1 - progress) * angle) / sinAngle
            val b = kotlin.math.sin(progress * angle) / sinAngle
            return normalized(
                a * from.x + b * to.x,
                a * from.y + b * to.y,
                a * from.z + b * to.z,
                a * from.w + b * to.w
            )
        }
    }
}

class QuaternionContinuity {
    private var previous: Quaternion? = null

    fun accept(next: Quaternion): Quaternion {
        val stable = previous?.let { if (it.dot(next) < 0) next.negated() else next } ?: next
        previous = stable
        return stable
    }

    fun reset() {
        previous = null
    }
}
