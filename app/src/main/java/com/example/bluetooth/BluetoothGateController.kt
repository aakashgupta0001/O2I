package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

data class DiscoveredBluetoothSignal(
    val address: String,
    val deviceName: String,
    val rssi: Int,
    val lastSeenTimestamp: Long,
    val isBleBeacon: Boolean,
    val extractedEmployeeCode: String? = null,
    val isBonded: Boolean = false
)

class BluetoothGateController(private val context: Context) {

    companion object {
        // Dedicated Service UUID for PulseAttend Employee BLE Pass broadcasting
        val PULSE_SERVICE_UUID: UUID = UUID.fromString("0000F1A0-0000-1000-8000-00805F9B34FB")

        fun requiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var classicDiscoveryLoopJob: Job? = null
    private var isReceiverRegistered = false

    private val _isHardwareSupported = MutableStateFlow(bluetoothAdapter != null)
    val isHardwareSupported: StateFlow<Boolean> = _isHardwareSupported.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val prefs = context.getSharedPreferences("o2i_bluetooth_prefs", Context.MODE_PRIVATE)

    private val _isAdvertisingBeacon = MutableStateFlow(false)
    val isAdvertisingBeacon: StateFlow<Boolean> = _isAdvertisingBeacon.asStateFlow()

    private val _advertisingEmployeeCode = MutableStateFlow<String?>(
        prefs.getString("saved_employee_beacon_code", null)
    )
    val advertisingEmployeeCode: StateFlow<String?> = _advertisingEmployeeCode.asStateFlow()

    private val _statusMessage = MutableStateFlow(
        if (bluetoothAdapter == null) {
            "Bluetooth hardware adapter not detected on this device/emulator"
        } else if (bluetoothAdapter.isEnabled) {
            "Admin Bluetooth ON — Ready for automatic gate detection"
        } else {
            "Admin Bluetooth is OFF — Turn on Bluetooth to start gate radar"
        }
    )
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _discoveredSignals = MutableStateFlow<List<DiscoveredBluetoothSignal>>(emptyList())
    val discoveredSignals: StateFlow<List<DiscoveredBluetoothSignal>> = _discoveredSignals.asStateFlow()

    private val _bondedDevices = MutableStateFlow<List<DiscoveredBluetoothSignal>>(emptyList())
    val bondedDevices: StateFlow<List<DiscoveredBluetoothSignal>> = _bondedDevices.asStateFlow()

    private val _liveSignalEvents = MutableSharedFlow<DiscoveredBluetoothSignal>(extraBufferCapacity = 32)
    val liveSignalEvents: SharedFlow<DiscoveredBluetoothSignal> = _liveSignalEvents.asSharedFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val enabled = state == BluetoothAdapter.STATE_ON
                    _isBluetoothEnabled.value = enabled
                    if (enabled) {
                        _statusMessage.value = "Admin Bluetooth ON — Auto-starting Gate Radar for registered staff..."
                        refreshBondedDevices()
                        if (hasScanPermission() && hasConnectPermission()) {
                            startGateRadar()
                        }
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        _isScanning.value = false
                        _isAdvertisingBeacon.value = false
                        _statusMessage.value = "Admin Bluetooth turned OFF — Turn ON in morning/evening for auto-attendance"
                    }
                }

                BluetoothDevice.ACTION_FOUND -> {
                    if (!hasScanPermission()) return
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    if (device != null) {
                        val name = runCatching {
                            if (hasConnectPermission()) device.name.orEmpty() else ""
                        }.getOrDefault("")
                        val addr = runCatching { device.address.orEmpty() }.getOrDefault("")
                        val isBonded = runCatching {
                            hasConnectPermission() && device.bondState == BluetoothDevice.BOND_BONDED
                        }.getOrDefault(false)
                        val validRssi = if (rssi in -120..-1) rssi else -65
                        recordSignal(
                            address = addr,
                            deviceName = name.ifBlank { "BT Device ($addr)" },
                            rssi = validRssi,
                            isBleBeacon = false,
                            extractedEmployeeCode = parseEmployeeCodeFromName(name),
                            isBonded = isBonded
                        )
                    }
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = runCatching { device.address.orEmpty() }.getOrDefault("")
            val scanRecord = result.scanRecord
            val rawName = scanRecord?.deviceName
                ?: runCatching { if (hasConnectPermission()) device.name else null }.getOrNull()
                ?: ""

            // Check if service data has PulseAttend Employee Code payload
            val serviceBytes = scanRecord?.getServiceData(ParcelUuid(PULSE_SERVICE_UUID))
            val servicePayload = serviceBytes?.let { String(it, StandardCharsets.UTF_8).trim() }
            val extractedCode = servicePayload?.takeIf { it.isNotBlank() }
                ?: parseEmployeeCodeFromName(rawName)

            val displayName = when {
                !servicePayload.isNullOrBlank() && rawName.isBlank() -> "PulsePass ($servicePayload)"
                rawName.isNotBlank() -> rawName
                else -> "BLE Signal (${address.takeLast(8)})"
            }

            recordSignal(
                address = address,
                deviceName = displayName,
                rssi = result.rssi,
                isBleBeacon = !servicePayload.isNullOrBlank(),
                extractedEmployeeCode = extractedCode,
                isBonded = false
            )
        }

        override fun onScanFailed(errorCode: Int) {
            _statusMessage.value = "BLE Radar active (Classic fallback enabled, code $errorCode)"
        }
    }

    private val bleAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertisingBeacon.value = true
            _statusMessage.value = "Broadcasting Employee BLE Pass: ${_advertisingEmployeeCode.value}"
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertisingBeacon.value = false
            _statusMessage.value = "BLE Pass broadcast unavailable on this hardware (code $errorCode)"
        }
    }

    init {
        registerReceiverIfNeeded()
        refreshAdapterState()
        if (bluetoothAdapter?.isEnabled == true && hasScanPermission() && hasConnectPermission()) {
            startGateRadar()
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun refreshAdapterState() {
        _isHardwareSupported.value = bluetoothAdapter != null
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
        if (bluetoothAdapter?.isEnabled == true) {
            refreshBondedDevices()
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasConnectPermission()) return
        runCatching {
            val list = adapter.bondedDevices.orEmpty().map { device ->
                val name = device.name.orEmpty().ifBlank { "Paired Device (${device.address})" }
                DiscoveredBluetoothSignal(
                    address = device.address.orEmpty(),
                    deviceName = name,
                    rssi = -55,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isBleBeacon = false,
                    extractedEmployeeCode = parseEmployeeCodeFromName(name),
                    isBonded = true
                )
            }
            _bondedDevices.value = list
        }
    }

    @SuppressLint("MissingPermission")
    fun startGateRadar() {
        refreshAdapterState()
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _statusMessage.value = "No physical Bluetooth radio on this emulator/device — Use Gate Desk quick actions below"
            return
        }
        if (!adapter.isEnabled) {
            _statusMessage.value = "Please turn ON Admin Bluetooth first"
            return
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            _statusMessage.value = "Bluetooth Scan & Connect permissions required"
            return
        }

        if (_isScanning.value) return
        _isScanning.value = true
        refreshBondedDevices()

        // 1. Start BLE Low-Latency Scanner
        runCatching {
            bleScanner = adapter.bluetoothLeScanner
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bleScanner?.startScan(null, scanSettings, bleScanCallback)
        }

        // 2. Start periodic Classic Bluetooth Discovery + Bonded check
        classicDiscoveryLoopJob?.cancel()
        classicDiscoveryLoopJob = scope.launch {
            while (isActive && _isScanning.value && adapter.isEnabled) {
                runCatching {
                    if (adapter.isDiscovering) {
                        adapter.cancelDiscovery()
                    }
                    adapter.startDiscovery()
                }
                delay(14_000L)
            }
        }

        _statusMessage.value = "Live Gate Radar Scanning (BLE + Classic Bluetooth)..."
    }

    @SuppressLint("MissingPermission")
    fun stopGateRadar() {
        _isScanning.value = false
        classicDiscoveryLoopJob?.cancel()
        classicDiscoveryLoopJob = null
        runCatching {
            if (hasScanPermission()) {
                bleScanner?.stopScan(bleScanCallback)
                bluetoothAdapter?.takeIf { it.isDiscovering }?.cancelDiscovery()
            }
        }
        _statusMessage.value = "Gate Radar paused"
    }

    /**
     * Allows an Employee's phone to broadcast their Employee Code over BLE (`PULSE_SERVICE_UUID`)
     * so the Admin phone's Gate Radar automatically detects their arrival/exit!
     */
    @SuppressLint("MissingPermission")
    fun startEmployeeBeaconPass(employeeCode: String) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _statusMessage.value = "Turn on Bluetooth to broadcast Employee BLE Pass"
            return
        }
        if (!hasAdvertisePermission() || !hasConnectPermission()) {
            _statusMessage.value = "Bluetooth Advertise permission required"
            return
        }
        stopEmployeeBeaconPass()

        runCatching {
            bleAdvertiser = adapter.bluetoothLeAdvertiser
            if (bleAdvertiser == null) {
                _statusMessage.value = "BLE Advertising not supported by this hardware chipset"
                return
            }
            val cleanCode = employeeCode.trim().uppercase().take(12)
            _advertisingEmployeeCode.value = cleanCode
            prefs.edit().putString("saved_employee_beacon_code", cleanCode).apply()

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(PULSE_SERVICE_UUID))
                .addServiceData(
                    ParcelUuid(PULSE_SERVICE_UUID),
                    cleanCode.toByteArray(StandardCharsets.UTF_8)
                )
                .build()

            bleAdvertiser?.startAdvertising(settings, data, bleAdvertiseCallback)
        }.onFailure {
            _statusMessage.value = "Could not start BLE pass broadcast: ${it.localizedMessage}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopEmployeeBeaconPass() {
        runCatching {
            if (_isAdvertisingBeacon.value && hasAdvertisePermission()) {
                bleAdvertiser?.stopAdvertising(bleAdvertiseCallback)
            }
        }
        _isAdvertisingBeacon.value = false
        _advertisingEmployeeCode.value = prefs.getString("saved_employee_beacon_code", null)
    }

    private fun recordSignal(
        address: String,
        deviceName: String,
        rssi: Int,
        isBleBeacon: Boolean,
        extractedEmployeeCode: String?,
        isBonded: Boolean
    ) {
        val now = System.currentTimeMillis()
        val signal = DiscoveredBluetoothSignal(
            address = address,
            deviceName = deviceName,
            rssi = rssi,
            lastSeenTimestamp = now,
            isBleBeacon = isBleBeacon,
            extractedEmployeeCode = extractedEmployeeCode,
            isBonded = isBonded
        )

        val current = _discoveredSignals.value.toMutableList()
        val existingIdx = current.indexOfFirst {
            (it.address.isNotBlank() && it.address.equals(address, ignoreCase = true)) ||
                (it.extractedEmployeeCode != null && it.extractedEmployeeCode.equals(extractedEmployeeCode, ignoreCase = true))
        }
        if (existingIdx >= 0) {
            current[existingIdx] = signal
        } else {
            current.add(0, signal)
        }
        // Keep fresh signals from last 2 minutes sorted by strongest RSSI
        _discoveredSignals.value = current
            .filter { now - it.lastSeenTimestamp < 120_000L }
            .sortedByDescending { it.rssi }

        _liveSignalEvents.tryEmit(signal)
    }

    private fun parseEmployeeCodeFromName(name: String): String? {
        if (name.isBlank()) return null
        // Match patterns like "PULSE-EMP-101" or "EMP-101" or "EMP101"
        val regex = Regex("(EMP[-_]?\\d+)", RegexOption.IGNORE_CASE)
        return regex.find(name)?.value?.uppercase()
    }

    private fun registerReceiverIfNeeded() {
        if (isReceiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_FOUND)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(bluetoothReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    fun cleanup() {
        stopGateRadar()
        stopEmployeeBeaconPass()
        if (isReceiverRegistered) {
            runCatching { context.unregisterReceiver(bluetoothReceiver) }
            isReceiverRegistered = false
        }
    }
}
