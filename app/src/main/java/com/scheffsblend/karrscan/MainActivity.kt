package com.scheffsblend.karrscan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.StateFlow
import androidx.activity.enableEdgeToEdge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.scheffsblend.karrscan.ui.theme.Amber400
import com.scheffsblend.karrscan.ui.theme.BgDark
import com.scheffsblend.karrscan.ui.theme.Emerald400
import com.scheffsblend.karrscan.ui.theme.MyApplicationTheme
import com.scheffsblend.karrscan.ui.theme.PrimaryDark
import com.scheffsblend.karrscan.ui.theme.PrimaryLight
import com.scheffsblend.karrscan.ui.theme.Red400
import com.scheffsblend.karrscan.ui.theme.SurfaceDark
import com.scheffsblend.karrscan.ui.theme.TextSecondary

class ScannerViewModel : ViewModel() {
    val isScanning: StateFlow<Boolean> = BleDeviceRepository.isScanning

    val devices: StateFlow<List<ParsedBleDevice>> = BleDeviceRepository.devices

    val history = BleDeviceRepository.history

    var locationInfoDismissed by mutableStateOf(false)

    fun clearHistory() {
        BleDeviceRepository.clearHistory()
    }
}

class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scannerViewModel: ScannerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setContent {
            MyApplicationTheme {
                scannerViewModel = viewModel()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ScannerScreen(
                        viewModel = scannerViewModel!!,
                        modifier = Modifier.padding(innerPadding),
                        onStartScan = { startScanning() },
                        onStopScan = { stopScanning() }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
            return
        }
        val intent = Intent(this, BleScannerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        val intent = Intent(this, BleScannerService::class.java)
        stopService(intent)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val history by viewModel.history.collectAsState(initial = emptyList())

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.tab_scanner), stringResource(R.string.tab_history))

    val mandatoryPermissions = remember {
        mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val mandatoryPermissionState = rememberMultiplePermissionsState(mandatoryPermissions)

    val locationPermissionState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    val isLocationGranted = locationPermissionState.permissions.any { it.status.isGranted }

    val backgroundPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        null
    }

    var showBackgroundRationale by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!mandatoryPermissionState.allPermissionsGranted) {
            mandatoryPermissionState.launchMultiplePermissionRequest()
        }
    }

    if (showBackgroundRationale) {
        AlertDialog(
            onDismissRequest = { 
                showBackgroundRationale = false
                onStartScan()
            },
            title = { Text(stringResource(R.string.location_permission_request_title)) },
            text = { Text(stringResource(R.string.location_permission_request_text))  },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundRationale = false
                    backgroundPermissionState?.launchPermissionRequest()
                }) {
                    Text(stringResource(R.string.permission_confirm_button_text))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showBackgroundRationale = false
                    onStartScan()
                }) {
                    Text(stringResource(R.string.permission_dismiss_button_text))
                }
            },
            shape = RectangleShape,
            containerColor = SurfaceDark,
            titleContentColor = PrimaryLight,
            textContentColor = TextSecondary
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_history_title)) },
            text = { Text(stringResource(R.string.dialog_clear_history_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                }) {
                    Text(stringResource(R.string.permission_confirm_button_text), color = Red400)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.permission_dismiss_button_text))
                }
            },
            shape = RectangleShape,
            containerColor = SurfaceDark,
            titleContentColor = PrimaryLight,
            textContentColor = TextSecondary
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = BgDark,
            contentColor = PrimaryLight,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = PrimaryLight
                )
            },
            divider = {
                HorizontalDivider(color = PrimaryDark, thickness = 1.dp)
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> ScannerTabContent(
                isScanning = isScanning,
                devices = devices,
                isLocationGranted = isLocationGranted,
                locationInfoDismissed = viewModel.locationInfoDismissed,
                onDismissLocationInfo = { viewModel.locationInfoDismissed = true },
                onGrantLocation = { locationPermissionState.launchMultiplePermissionRequest() },
                onStartScan = {
                    if (isLocationGranted &&
                        backgroundPermissionState != null &&
                        !backgroundPermissionState.status.isGranted) {
                        showBackgroundRationale = true
                    } else {
                        onStartScan()
                    }
                },
                onStopScan = onStopScan
            )
            1 -> HistoryTabContent(
                history = history,
                onClearHistory = { showClearHistoryDialog = true }
            )
        }
    }
}

@Composable
fun ScannerTabContent(
    isScanning: Boolean,
    devices: List<ParsedBleDevice>,
    isLocationGranted: Boolean,
    locationInfoDismissed: Boolean,
    onDismissLocationInfo: () -> Unit,
    onGrantLocation: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 24.dp, 16.dp, 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.system_header_title),
                    color = PrimaryLight,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Emerald400)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.scanning_status_active),
                            color = PrimaryLight,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(TextSecondary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.scanning_status_idle),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SurfaceDark)
                    .border(1.dp, PrimaryDark, RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) {
                    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
                    val frame by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "frame"
                    )
                    val spinnerChars = listOf("|", "/", "—", "\\")
                    Text(
                        text = spinnerChars[(frame.toInt() % 4).coerceIn(0, 3)],
                        color = PrimaryLight,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Scanner Summary Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceDark, RectangleShape)
                    .border(1.dp, PrimaryDark, RectangleShape)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.label_detected),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pluralStringResource(R.plurals.device_count, devices.size, devices.size),
                        color = PrimaryLight,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceDark, RectangleShape)
                    .border(1.dp, PrimaryDark, RectangleShape)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.label_signal),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (devices.isNotEmpty()) stringResource(R.string.rssi_format, devices.first().rssi) else stringResource(
                            R.string.rssi_unknown),
                        color = PrimaryLight,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Location Info Banner
        if (!isLocationGranted && !locationInfoDismissed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(SurfaceDark, RectangleShape)
                    .border(1.dp, Amber400.copy(alpha = 0.5f), RectangleShape)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "LOCATION_INFO",
                        color = Amber400,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.location_explanation_text),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismissLocationInfo,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "[ DISMISS ]",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onGrantLocation,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "[ GRANT_PERMISSION ]",
                                color = PrimaryLight,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(devices, key = { it.macAddress }) { device ->
                DeviceItem(device)
            }
        }

        // Footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgDark)
                .border(1.dp, Color.White.copy(alpha = 0.05f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { if (isScanning) onStopScan() else onStartScan() },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp)
                    .border(1.dp, PrimaryLight, RectangleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = PrimaryLight
                ),
                shape = RectangleShape
            ) {
                Text(
                    text = if (isScanning) stringResource(R.string.button_stop_scanner) else stringResource(
                        R.string.button_start_scanner),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun HistoryTabContent(
    history: List<ParsedBleDevice>,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pluralStringResource(R.plurals.device_count, history.size, history.size),
                color = PrimaryLight,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            TextButton(
                onClick = onClearHistory,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.button_clear_history),
                    color = Red400,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history, key = { it.macAddress }) { device ->
                DeviceItem(device)
            }
        }
    }
}

@Composable
fun DeviceItem(device: ParsedBleDevice) {
    val timestampFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RectangleShape)
            .border(1.dp, PrimaryDark, RectangleShape)
            .padding(12.dp)
    ) {
        // Header Row: Name/MAC + Armed Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (device.isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (device.isEncrypted) "Encrypted" else "Not Encrypted",
                    modifier = Modifier.size(14.dp),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (device.name.isNotEmpty()) device.name else device.macAddress,
                    color = PrimaryLight,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            val statusColor = if (device.isArmed) PrimaryLight else TextSecondary
            val bgColor = if (device.isArmed) PrimaryDark.copy(alpha = 0.5f) else BgDark

            Box(
                modifier = Modifier
                    .background(bgColor, RectangleShape)
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RectangleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (device.isArmed) stringResource(R.string.status_armed) else stringResource(
                        R.string.status_disarmed),
                    color = statusColor,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Stats Row: VIN | Voltage | Type | RSSI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.vin_label, device.vin),
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f, fill = false)
            )

            Text(text = "|", color = PrimaryDark, fontSize = 10.sp)

            Text(
                text = stringResource(R.string.voltage_format, device.batteryVoltage),
                color = if (device.batteryVoltage < 12.0) Red400 else PrimaryLight,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(text = "|", color = PrimaryDark, fontSize = 10.sp)

            Text(
                text = if (device.isEliteMode) "ELITE" else "STD",
                color = PrimaryLight,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            if (device.isSnowMode) {
                Text(text = "|", color = PrimaryDark, fontSize = 10.sp)
                Text(
                    text = "SNOW",
                    color = Amber400,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.rssi_format, device.rssi),
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Footer Row: Metadata
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timestampFormatter.format(Date(device.lastSeen)),
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            if (device.latitude != null && device.longitude != null) {
                Text(
                    text = String.format("%.4f, %.4f", device.latitude, device.longitude),
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
