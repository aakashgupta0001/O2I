package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.bluetooth.DiscoveredBluetoothSignal
import com.example.data.local.AttendanceLogEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.PayrollSettingsEntity
import com.example.ui.GateEventBanner
import com.example.ui.theme.AmberWarning400
import com.example.ui.theme.CyberCyan400
import com.example.ui.theme.EmeraldGreen400
import com.example.ui.theme.RoseAlert400
import com.example.ui.theme.SlateNavy900
import com.example.ui.theme.SlateNavy950
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GateRadarScreen(
    employees: List<EmployeeEntity>,
    todayLogs: List<AttendanceLogEntity>,
    settings: PayrollSettingsEntity,
    isHardwareSupported: Boolean,
    isBluetoothEnabled: Boolean,
    isScanning: Boolean,
    isAdvertisingBeacon: Boolean,
    advertisingEmployeeCode: String?,
    bluetoothStatusMessage: String,
    discoveredSignals: List<DiscoveredBluetoothSignal>,
    bondedDevices: List<DiscoveredBluetoothSignal>,
    recentGateAlerts: List<GateEventBanner>,
    onRequestEnableBluetooth: () -> Unit,
    onToggleGateRadar: () -> Unit,
    onSelectGateMode: (String) -> Unit,
    onStartEmployeeBeacon: (String) -> Unit,
    onStopEmployeeBeacon: () -> Unit,
    onManualCheckInOrOut: (EmployeeEntity, String) -> Unit,
    onBindSignalToEmployee: (DiscoveredBluetoothSignal, EmployeeEntity) -> Unit,
    onSaveSettings: (PayrollSettingsEntity) -> Unit,
    onNavigateToAddEmployee: () -> Unit,
    onNavigateToExcelSheet: () -> Unit
) {
    var signalToBind by remember { mutableStateOf<DiscoveredBluetoothSignal?>(null) }
    var showBeaconDialog by remember { mutableStateOf(false) }
    var showGateSettingsDialog by remember { mutableStateOf(false) }
    var beaconCodeInput by remember(advertisingEmployeeCode, employees) {
        mutableStateOf(
            advertisingEmployeeCode
                ?: employees.firstOrNull()?.employeeCode
                ?: "EMP-101"
        )
    }

    val logsByEmpId = remember(todayLogs) { todayLogs.associateBy { it.employeeId } }
    val arrivedCount = todayLogs.size
    val currentlyInOfficeCount = todayLogs.count { it.checkOutTimestamp == null }
    val exitedCount = todayLogs.count { it.checkOutTimestamp != null }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("gate_radar_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Gate Terminal Card with Generated Visual Asset + Radar Visualizer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SlateNavy900),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_gate_hero_1791039420785),
                        contentDescription = "Smart Bluetooth Office Gate Banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        SlateNavy950.copy(alpha = 0.55f),
                                        SlateNavy950.copy(alpha = 0.88f),
                                        SlateNavy900
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settings.officeGateName.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyberCyan400
                                )
                                Text(
                                    text = "Admin Bluetooth Gate Hub",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Live Bluetooth Status Pill
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = when {
                                    isScanning -> EmeraldGreen400.copy(alpha = 0.18f)
                                    isBluetoothEnabled -> CyberCyan400.copy(alpha = 0.18f)
                                    else -> RoseAlert400.copy(alpha = 0.2f)
                                },
                                modifier = Modifier.border(
                                    width = 1.dp,
                                    color = when {
                                        isScanning -> EmeraldGreen400
                                        isBluetoothEnabled -> CyberCyan400
                                        else -> RoseAlert400
                                    },
                                    shape = RoundedCornerShape(50)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isScanning -> Icons.Default.BluetoothSearching
                                            isBluetoothEnabled -> Icons.Default.BluetoothConnected
                                            else -> Icons.Default.BluetoothDisabled
                                        },
                                        contentDescription = "Bluetooth Status",
                                        tint = when {
                                            isScanning -> EmeraldGreen400
                                            isBluetoothEnabled -> CyberCyan400
                                            else -> RoseAlert400
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = when {
                                            isScanning -> "RADAR LIVE"
                                            isBluetoothEnabled -> "BT READY"
                                            else -> "BT OFF"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showGateSettingsDialog = true },
                                modifier = Modifier.testTag("btn_gate_settings")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "O2I Gate Settings",
                                    tint = CyberCyan400
                                )
                            }
                        }

                        // Radar + Live Telemetry Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BluetoothRadarCanvas(
                                isScanning = isScanning,
                                signalCount = discoveredSignals.size,
                                modifier = Modifier.size(84.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = bluetoothStatusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "1-Time Staff Registration • Auto-Detects Daily • Threshold: >= ${settings.rssiThresholdDbm} dBm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan400
                                )
                            }
                        }

                        // Primary Gate Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!isBluetoothEnabled && isHardwareSupported) {
                                Button(
                                    onClick = onRequestEnableBluetooth,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_enable_bluetooth"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberCyan400,
                                        contentColor = SlateNavy950
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Turn On Admin Bluetooth", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = onToggleGateRadar,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_toggle_radar"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isScanning) RoseAlert400 else EmeraldGreen400,
                                        contentColor = SlateNavy950
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isScanning) Icons.Default.StopCircle else Icons.Default.Radar,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isScanning) "Stop Gate Radar" else "Start Auto Gate Radar",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showBeaconDialog = true },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("btn_employee_beacon_pass"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Default.WifiTethering,
                                    contentDescription = "Broadcast Employee Pass",
                                    tint = if (isAdvertisingBeacon) EmeraldGreen400 else CyberCyan400,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isAdvertisingBeacon) {
                                        "Pass: $advertisingEmployeeCode"
                                    } else {
                                        "BLE Pass"
                                    },
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Gate Shift Mode Selector (Smart Auto vs Morning Arrival vs Evening Exit)
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bluetooth Gate Detection Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        AssistChip(
                            onClick = onNavigateToExcelSheet,
                            label = { Text("View Live Excel") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.TableView,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("chip_view_live_excel")
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modes = listOf(
                            "SMART_AUTO" to "Smart Auto (In & Out)",
                            "MORNING_ARRIVAL" to "Morning Arrival Only",
                            "EVENING_EXIT" to "Evening Exit Only"
                        )
                        modes.forEach { (modeKey, label) ->
                            val selected = settings.gateMode == modeKey
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectGateMode(modeKey) },
                                label = { Text(label) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("gate_mode_$modeKey")
                            )
                        }
                    }
                }
            }
        }

        // 3. Today's Live Office Attendance KPI Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GateStatMiniCard(
                    title = "Total Roster",
                    value = "${employees.size}",
                    subtitle = "Registered",
                    accentColor = CyberCyan400,
                    modifier = Modifier.weight(1f)
                )
                GateStatMiniCard(
                    title = "Arrived Today",
                    value = "$arrivedCount",
                    subtitle = "$currentlyInOfficeCount In Office",
                    accentColor = EmeraldGreen400,
                    modifier = Modifier.weight(1f)
                )
                GateStatMiniCard(
                    title = "Exited Today",
                    value = "$exitedCount",
                    subtitle = "Checked Out",
                    accentColor = AmberWarning400,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Live Auto-Registered Bluetooth Gate Events Banner Feed
        if (recentGateAlerts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Live Gate Registration Stream",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    recentGateAlerts.take(3).forEach { alert ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (alert.isArrival) {
                                EmeraldGreen400.copy(alpha = 0.14f)
                            } else {
                                CyberCyan400.copy(alpha = 0.14f)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (alert.isArrival) EmeraldGreen400 else CyberCyan400
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (alert.isArrival) {
                                        Icons.AutoMirrored.Filled.Login
                                    } else {
                                        Icons.AutoMirrored.Filled.Logout
                                    },
                                    contentDescription = null,
                                    tint = if (alert.isArrival) EmeraldGreen400 else CyberCyan400,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alert.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = alert.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = alert.timestampFormatted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Nearby & Paired Bluetooth Signals Section (Real Hardware Scanner Feed)
        val combinedSignals = (discoveredSignals + bondedDevices.filter { bonded ->
            discoveredSignals.none { it.address.equals(bonded.address, ignoreCase = true) }
        }).take(8)

        if (combinedSignals.isNotEmpty()) {
            item {
                Text(
                    text = "Detected & Paired Bluetooth Signals (${combinedSignals.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(combinedSignals, key = { it.address.ifBlank { it.deviceName } }) { sig ->
                val matchedEmp = employees.firstOrNull { emp ->
                    (emp.bluetoothAddress.isNotBlank() && emp.bluetoothAddress.equals(sig.address, ignoreCase = true)) ||
                        (!sig.extractedEmployeeCode.isNullOrBlank() && emp.employeeCode.equals(sig.extractedEmployeeCode, ignoreCase = true)) ||
                        (emp.bluetoothDeviceName.isNotBlank() && sig.deviceName.contains(emp.bluetoothDeviceName, ignoreCase = true))
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sig.deviceName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "MAC: ${sig.address.ifBlank { "Dynamic BLE" }} • RSSI: ${sig.rssi} dBm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (matchedEmp != null) {
                                Text(
                                    text = "Bound to: ${matchedEmp.fullName} (${matchedEmp.employeeCode})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldGreen400
                                )
                            }
                        }

                        if (matchedEmp == null && employees.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { signalToBind = sig },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = "Link Bluetooth Device",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Link Staff", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // 6. Office Gate Roster Desk (Live Employee Arrival & Exit Control)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Permanently Registered O2I Staff (${employees.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Registered once by Admin • Auto-logs arrival & exit every day via Bluetooth",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onNavigateToAddEmployee,
                    modifier = Modifier.testTag("btn_gate_add_employee")
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Add Staff")
                }
            }
        }

        if (employees.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "No O2I Employees Registered at Gate Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Register your employees across Drone, TDF, Simulator, Robotics, and PCB categories with their Bluetooth device name/MAC. When Admin Bluetooth is ON, their morning arrival and evening exit will be logged automatically into the monthly Excel report.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateToAddEmployee,
                            modifier = Modifier.testTag("btn_empty_register_employee")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Register First Employee")
                        }
                    }
                }
            }
        } else {
            items(employees, key = { it.id }) { emp ->
                val todayLog = logsByEmpId[emp.id]
                EmployeeGateDeskCard(
                    employee = emp,
                    todayLog = todayLog,
                    onLogArrival = { onManualCheckInOrOut(emp, "ARRIVAL") },
                    onLogExit = { onManualCheckInOrOut(emp, "EXIT") }
                )
            }
        }
    }

    // Bind Bluetooth Signal to Employee Dialog
    signalToBind?.let { sig ->
        AlertDialog(
            onDismissRequest = { signalToBind = null },
            title = { Text("Bind Bluetooth Device to Employee") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select which employee carries '${sig.deviceName}' (${sig.address}):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    employees.forEach { emp ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onBindSignalToEmployee(sig, emp)
                                    signalToBind = null
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(emp.fullName, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${emp.employeeCode} • ${emp.department}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Icon(Icons.Default.Link, contentDescription = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { signalToBind = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Employee BLE Pass Transmitter Dialog
    if (showBeaconDialog) {
        AlertDialog(
            onDismissRequest = { showBeaconDialog = false },
            title = { Text("Employee BLE Gate Pass") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Broadcast your Employee ID over Bluetooth Low Energy (BLE) so the Admin's Gate Radar phone automatically registers your morning arrival and evening exit.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = beaconCodeInput,
                        onValueChange = { beaconCodeInput = it.uppercase() },
                        label = { Text("Employee ID Code (e.g. EMP-101)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (employees.isNotEmpty()) {
                        Text(
                            text = "Or tap a registered employee code:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            employees.take(6).forEach { emp ->
                                AssistChip(
                                    onClick = { beaconCodeInput = emp.employeeCode },
                                    label = { Text("${emp.employeeCode} (${emp.fullName.substringBefore(" ")})") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isAdvertisingBeacon) {
                    Button(
                        onClick = {
                            onStopEmployeeBeacon()
                            showBeaconDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseAlert400)
                    ) {
                        Text("Stop BLE Broadcast")
                    }
                } else {
                    Button(
                        onClick = {
                            if (beaconCodeInput.isNotBlank()) {
                                onStartEmployeeBeacon(beaconCodeInput)
                                showBeaconDialog = false
                            }
                        }
                    ) {
                        Text("Start BLE Broadcast")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBeaconDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showGateSettingsDialog) {
        var gateName by remember { mutableStateOf(settings.officeGateName) }
        var rssiThreshold by remember { mutableStateOf(settings.rssiThresholdDbm.toString()) }
        var graceMins by remember { mutableStateOf(settings.gracePeriodMinutes.toString()) }
        var exitCooldownMins by remember { mutableStateOf(settings.minMinutesBeforeExit.toString()) }
        var workingDays by remember { mutableStateOf(settings.standardWorkingDaysPerMonth.toString()) }
        var dailyHours by remember { mutableStateOf(settings.standardDailyHours.toString()) }

        AlertDialog(
            onDismissRequest = { showGateSettingsDialog = false },
            title = { Text("O2I Bluetooth Gate & Shift Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = gateName,
                        onValueChange = { gateName = it },
                        label = { Text("Office Gate Terminal Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rssiThreshold,
                            onValueChange = { rssiThreshold = it },
                            label = { Text("Min RSSI (e.g. -85)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = graceMins,
                            onValueChange = { graceMins = it },
                            label = { Text("Grace Mins (Late)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exitCooldownMins,
                            onValueChange = { exitCooldownMins = it },
                            label = { Text("Auto-Exit Cooldown (m)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = workingDays,
                            onValueChange = { workingDays = it },
                            label = { Text("Working Days/Mo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = dailyHours,
                        onValueChange = { dailyHours = it },
                        label = { Text("Standard Shift Hours / Day") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSettings(
                            settings.copy(
                                companyName = "O2I",
                                officeGateName = gateName.trim().ifBlank { "O2I Main Bluetooth Gate" },
                                rssiThresholdDbm = (rssiThreshold.toIntOrNull() ?: -85).coerceIn(-115, -20),
                                gracePeriodMinutes = (graceMins.toIntOrNull() ?: 15).coerceIn(0, 180),
                                minMinutesBeforeExit = (exitCooldownMins.toIntOrNull() ?: 1).coerceIn(1, 480),
                                standardWorkingDaysPerMonth = (workingDays.toIntOrNull() ?: 26).coerceIn(1, 31),
                                standardDailyHours = (dailyHours.toDoubleOrNull() ?: 9.0).coerceIn(1.0, 24.0)
                            )
                        )
                        showGateSettingsDialog = false
                    }
                ) {
                    Text("Save Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGateSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BluetoothRadarCanvas(
    isScanning: Boolean,
    signalCount: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f

        // Concentric radar rings
        for (i in 1..3) {
            drawCircle(
                color = CyberCyan400.copy(alpha = 0.28f),
                radius = maxRadius * (i / 3f),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        if (isScanning) {
            val rad = Math.toRadians(sweepAngle.toDouble())
            val endX = center.x + (maxRadius * cos(rad)).toFloat()
            val endY = center.y + (maxRadius * sin(rad)).toFloat()
            drawLine(
                color = EmeraldGreen400,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Center Bluetooth node
        drawCircle(
            color = if (isScanning) EmeraldGreen400 else CyberCyan400,
            radius = 5.dp.toPx(),
            center = center
        )

        // Plot live detected signals
        for (idx in 0 until minOf(signalCount, 5)) {
            val angleRad = Math.toRadians((idx * 72 + 35).toDouble())
            val dist = maxRadius * (0.45f + (idx % 3) * 0.18f)
            val dotPos = Offset(
                center.x + (dist * cos(angleRad)).toFloat(),
                center.y + (dist * sin(angleRad)).toFloat()
            )
            drawCircle(
                color = EmeraldGreen400,
                radius = 3.5.dp.toPx(),
                center = dotPos
            )
        }
    }
}

@Composable
private fun GateStatMiniCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmployeeGateDeskCard(
    employee: EmployeeEntity,
    todayLog: AttendanceLogEntity?,
    onLogArrival: () -> Unit,
    onLogExit: () -> Unit
) {
    val hasArrived = todayLog != null
    val hasExited = todayLog?.checkOutTimestamp != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gate_emp_card_${employee.employeeCode}"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = employee.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = employee.employeeCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${employee.department} • Shift ${
                            String.format(
                                Locale.US,
                                "%02d:%02d-%02d:%02d",
                                employee.shiftStartHour,
                                employee.shiftStartMinute,
                                employee.shiftEndHour,
                                employee.shiftEndMinute
                            )
                        } • BT: ${employee.bluetoothDeviceName.ifBlank { employee.bluetoothAddress.ifBlank { "Auto BLE" } }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Today's Status Badge
                val badgeColor = when {
                    hasExited -> CyberCyan400
                    hasArrived -> EmeraldGreen400
                    else -> AmberWarning400
                }
                val badgeText = when {
                    hasExited -> "EXITED (${todayLog?.workedHours}h)"
                    hasArrived -> "IN OFFICE"
                    else -> "AWAITING"
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            AnimatedVisibility(visible = todayLog != null) {
                if (todayLog != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Arrival: ${todayLog.checkInTimeFormatted} (${todayLog.status})",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Exit: ${todayLog.checkOutTimeFormatted ?: "--:--:--"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onLogArrival,
                    enabled = !hasArrived,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_arrival_${employee.employeeCode}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen400,
                        contentColor = SlateNavy950
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (hasArrived) "Arrived ${todayLog?.checkInTimeFormatted}" else "Log Morning Arrival",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onLogExit,
                    enabled = hasArrived,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_exit_${employee.employeeCode}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (hasExited) "Update Exit" else "Log Evening Exit",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
