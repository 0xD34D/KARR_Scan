package com.scheffsblend.karrscan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object BleDeviceRepository {
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _devices = MutableStateFlow<List<ParsedBleDevice>>(emptyList())
    val devices: StateFlow<List<ParsedBleDevice>> = _devices.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, ParsedBleDevice>()

    fun setScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }

    fun addDevice(device: ParsedBleDevice) {
        deviceMap[device.macAddress] = device
        _devices.value = deviceMap.values.toList().sortedByDescending { it.timestamp }
    }

    fun clearDevices() {
        deviceMap.clear()
        _devices.value = emptyList()
    }
}
