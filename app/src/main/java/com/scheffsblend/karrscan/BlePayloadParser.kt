package com.scheffsblend.karrscan

import android.util.Log
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

data class ParsedBleDevice(
    val macAddress: String,
    val name: String,
    val rssi: Int,
    val unitTypeIndex: Int,
    val modeIndex: Int,
    val isArmed: Boolean,
    val isSnowMode: Boolean,
    val isEliteMode: Boolean,
    val vin: String,
    val batteryVoltage: Double,
    val latitude: Double?,
    val longitude: Double?,
    val isDebugMode: Boolean,
    val isEncrypted: Boolean,
    val timestamp: Long
)

object BlePayloadParser {

    private val REGEX_ALPHA_NUM = Pattern.compile("^[A-F0-9]$")
    private val REGEX_NAME_BASE32 = Pattern.compile("^[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{13}$")
    private val REGEX_PREFIX_NAME = Pattern.compile("^(DR|QT) [A-F0-9]{7,8}$")

    fun parse(macAddress: String, rssi: Int, rawData: ByteArray): ParsedBleDevice? {
        if (rawData.size < 5) {
            Log.d("BlePayloadParser", "Payload too short (${rawData.size} bytes) to be valid for MAC: $macAddress")
            return null
        }

        var name = ""
        val offset: Int

        // 26-Byte Payload Format vs Standard Format
        if (rawData.size == 26) {
            offset = 13
            name = String(rawData, 0, 13, StandardCharsets.UTF_8)

            if (name.startsWith("DR ") || name.startsWith("QT ")) {
                if (name.length > 11) name = name.substring(0, 11)
                val lastChar = name.last().toString()
                if (!REGEX_ALPHA_NUM.matcher(lastChar).matches() && name.length > 10) {
                    name = name.substring(0, 10)
                }
            }

            if (!REGEX_NAME_BASE32.matcher(name).matches() && !REGEX_PREFIX_NAME.matcher(name).matches()) {
                Log.d("BlePayloadParser", "Regex mismatch for name: '$name' for MAC: $macAddress")
                return null // Invalid device
            }
        } else {
            offset = 0
        }

        // Convert signed bytes to unsigned ints for safe bitwise math
        val b0 = rawData[offset].toInt() and 0xFF
        val b1 = rawData[offset + 1].toInt() and 0xFF
        val b2 = rawData[offset + 2].toInt() and 0xFF
        val b3 = rawData[offset + 3].toInt() and 0xFF
        val b4 = rawData[offset + 4].toInt() and 0xFF

        // 1. Extract UnitType
        val unitTypeCalc = ((b0 and 0xC0) shr 6) + ((b1 and 0xC0) shr 4)
        if (unitTypeCalc == 0) {
            Log.d("BlePayloadParser", "Calculated Unit Type is 0 for MAC: $macAddress")
            return null
        }
        val unitTypeIndex = unitTypeCalc - 1

        // 2. Extract Mode & Status Flags
        var modeIndex = -1
        var isArmed = false
        var isSnowMode = false
        var isEliteMode = false

        val modeCalc = ((b2 and 0xC0) shr 6) + ((b3 and 0x40) shr 4)
        if (modeCalc != 0) {
            modeIndex = modeCalc - 1
            isArmed = ((b3 and 0x80) shr 7) == 1
            isSnowMode = ((b4 and 0x40) shr 6) == 1
            isEliteMode = ((b4 and 0x80) shr 7) == 1
        }

        // 3. Extract VIN (masking off the top 2 bits of the first 5 payload bytes)
        val vinBytes = byteArrayOf(
            (b0 and 0x3F).toByte(),
            (b1 and 0x3F).toByte(),
            (b2 and 0x3F).toByte(),
            (b3 and 0x3F).toByte(),
            (b4 and 0x3F).toByte()
        )
        for (b in vinBytes) {
            if (b < '0'.code.toByte() || b > '9'.code.toByte()) {
                Log.d("BlePayloadParser", "Invalid VIN character byte: $b for MAC: $macAddress")
                return null
            }
        }
        val vin = String(vinBytes, StandardCharsets.UTF_8)

        // 4. Extract Battery Voltage
        var batteryVoltage = 0.0
        if (rawData.size > offset + 5) {
            val b5 = rawData[offset + 5].toInt() and 0xFF
            batteryVoltage = (b5 * 60.0) / 1000.0
        }

        // 5. Extract GPS Latitude & Longitude
        var latitude: Double? = null
        var longitude: Double? = null
        if (rawData.size >= offset + 12) {
            val b6 = rawData[offset + 6].toInt() and 0xFF
            val b7 = rawData[offset + 7].toInt() and 0xFF
            val b8 = rawData[offset + 8].toInt() and 0xFF

            val b9 = rawData[offset + 9].toInt() and 0xFF
            val b10 = rawData[offset + 10].toInt() and 0xFF
            val b11 = rawData[offset + 11].toInt() and 0xFF

            var num5 = ((b8 shl 16) or (b7 shl 8) or b6).toLong() * 100
            var num6 = ((b11 shl 16) or (b10 shl 8) or b9).toLong() * 100

            if (num5 != 0L && num6 != 0L) {
                latitude = (num5.toDouble() / 1_000_000.0) - 90.0
                longitude = (num6.toDouble() / 1_000_000.0) - 180.0
            }
        }

        // 6. Extract Debug & Encrypted Flags
        var isDebugMode = false
        var isEncrypted = false
        if (rawData.size == offset + 13) {
            val b12 = rawData[offset + 12].toInt() and 0xFF
            isDebugMode = (b12 and 1) == 1
            isEncrypted = (b12 and 2) == 2
        }
        
        Log.d("BlePayloadParser", "Parsing successful for MAC: $macAddress, Unit Type: $unitTypeIndex, Mode: $modeIndex")

        return ParsedBleDevice(
            macAddress = macAddress,
            name = name,
            rssi = rssi,
            unitTypeIndex = unitTypeIndex,
            modeIndex = modeIndex,
            isArmed = isArmed,
            isSnowMode = isSnowMode,
            isEliteMode = isEliteMode,
            vin = vin,
            batteryVoltage = batteryVoltage,
            latitude = latitude,
            longitude = longitude,
            isDebugMode = isDebugMode,
            isEncrypted = isEncrypted,
            timestamp = System.currentTimeMillis()
        )
    }
}