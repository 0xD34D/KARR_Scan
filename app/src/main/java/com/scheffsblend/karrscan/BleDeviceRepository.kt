package com.scheffsblend.karrscan

import com.scheffsblend.karrscan.data.DeviceDao
import com.scheffsblend.karrscan.data.DeviceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object BleDeviceRepository {
    private var deviceDao: DeviceDao? = null
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    fun initialize(dao: DeviceDao) {
        deviceDao = dao
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _devices = MutableStateFlow<List<ParsedBleDevice>>(emptyList())
    val devices: StateFlow<List<ParsedBleDevice>> = _devices.asStateFlow()

    val history: Flow<List<ParsedBleDevice>>
        get() = deviceDao?.getAllDevices()?.map { entities ->
            entities.map { it.toParsedBleDevice() }
        } ?: flowOf(emptyList())

    private val deviceMap = ConcurrentHashMap<String, ParsedBleDevice>()

    fun setScanning(scanning: Boolean) {
        _isScanning.value = scanning
        if (scanning) {
            clearDevices()
        }
    }

    fun addDevice(device: ParsedBleDevice) {
        repositoryScope.launch {
            val existing = deviceDao?.getDevice(device.macAddress)
            val updatedDevice = if (existing != null) {
                device.copy(firstSeen = existing.firstSeen)
            } else {
                device
            }

            deviceDao?.insertDevice(updatedDevice.toDeviceEntity())

            deviceMap[device.macAddress] = updatedDevice
            _devices.value = deviceMap.values.toList().sortedByDescending { it.lastSeen }
        }
    }

    fun clearDevices() {
        deviceMap.clear()
        _devices.value = emptyList()
    }

    fun clearHistory() {
        repositoryScope.launch {
            deviceDao?.deleteAll()
        }
    }
}

fun DeviceEntity.toParsedBleDevice() = ParsedBleDevice(
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
    firstSeen = firstSeen,
    lastSeen = lastSeen
)

fun ParsedBleDevice.toDeviceEntity() = DeviceEntity(
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
    firstSeen = firstSeen,
    lastSeen = lastSeen
)
