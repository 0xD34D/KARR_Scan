package com.scheffsblend.karrscan

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.scheffsblend.karrscan.R

class BleScannerService : Service(), LocationListener {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var locationManager: LocationManager? = null
    private var lastMobileLocation: Location? = null

    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ble_scanner_channel"

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val advertisedName = result.device.name ?: result.scanRecord?.deviceName ?: ""
            if (!advertisedName.startsWith("QT") && !advertisedName.startsWith("DR")) {
                return
            }

            val bytes = result.scanRecord?.bytes ?: return
            
            var index = 0
            while (index < bytes.size) {
                val length = bytes[index].toInt() and 0xFF
                if (length == 0) break
                val type = bytes[index + 1].toInt() and 0xFF
                if (type == 0xFF) {
                    val data = bytes.copyOfRange(index + 2, index + 1 + length)
                    val parsed = BlePayloadParser.parse(result.device.address, result.rssi, data)
                    if (parsed != null) {
                        @SuppressLint("MissingPermission")
                        val finalName = if (parsed.name.isEmpty()) {
                            result.device.name ?: result.scanRecord?.deviceName ?: ""
                        } else {
                            parsed.name
                        }
                        
                        var deviceToUpdate = parsed.copy(name = finalName)
                        
                        // Inject mobile location if device location is missing
                        if (deviceToUpdate.latitude == null || deviceToUpdate.longitude == null) {
                            lastMobileLocation?.let { loc ->
                                deviceToUpdate = deviceToUpdate.copy(
                                    latitude = loc.latitude,
                                    longitude = loc.longitude
                                )
                            }
                        }
                        
                        BleDeviceRepository.addDevice(deviceToUpdate)
                    }
                }
                index += length + 1
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("BleScannerService", "Service onCreate")
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("BleScannerService", "onStartCommand action: ${intent?.action}")
        if (intent?.action == ACTION_STOP_SCAN) {
            Log.i("BleScannerService", "Stopping service via ACTION_STOP_SCAN")
            stopLocationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            BleDeviceRepository.setScanning(false)
            return START_NOT_STICKY
        }

        startForegroundService()
        Log.i("BleScannerService", "Starting BLE scan and Location updates")
        startLocationUpdates()
        BleDeviceRepository.setScanning(true)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothAdapter?.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        
        return START_STICKY
    }

    private fun startForegroundService() {
        Log.i("BleScannerService", "startForegroundService called")
        createNotificationChannel()
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                if (hasLocationPermission()) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i("BleScannerService", "startForeground successful")
        } catch (e: Exception) {
            Log.e("BleScannerService", "Failed to start foreground service", e)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        Log.i("BleScannerService", "Service onDestroy")
        stopLocationUpdates()
        BleDeviceRepository.setScanning(false)
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.i("BleScannerService", "Skipping location updates: Permission not granted")
            return
        }
        try {
            // Request updates from both GPS and Network for reliability
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000L, // 5 seconds
                    10f,   // 10 meters
                    this
                )
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    10f,
                    this
                )
            }
        } catch (e: Exception) {
            Log.e("BleScannerService", "Failed to start location updates", e)
        }
    }

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        Log.i("BleScannerService", "Mobile location updated: ${location.latitude}, ${location.longitude}")
        lastMobileLocation = location
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BleScannerService::class.java)
        stopIntent.action = ACTION_STOP_SCAN
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_action_stop), stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val ACTION_STOP_SCAN = "com.scheffsblend.ACTION_STOP_SCAN"
    }
}
