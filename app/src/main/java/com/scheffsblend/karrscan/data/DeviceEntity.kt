package com.scheffsblend.karrscan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val macAddress: String,
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
    val firstSeen: Long,
    val lastSeen: Long
)
