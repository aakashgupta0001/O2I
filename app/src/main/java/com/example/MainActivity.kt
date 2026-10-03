package com.example

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluetooth.BluetoothGateController
import com.example.ui.AttendanceViewModel
import com.example.ui.screens.EmployeeRosterScreen
import com.example.ui.screens.ExcelSheetScreen
import com.example.ui.screens.GateRadarScreen
import com.example.ui.theme.MyApplicationTheme

enum class O2IDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    GATE_RADAR(
        route = "gate_radar",
        label = "Gate Radar",
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar
    ),
    EXCEL_LOG(
        route = "excel_log",
        label = "Excel Sheet",
        selectedIcon = Icons.Filled.TableView,
        unselectedIcon = Icons.Outlined.TableView
    ),
    ROSTER(
        route = "roster",
        label = "Roster",
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                O2IAttendanceApp()
            }
        }
    }
}

@Composable
fun O2IAttendanceApp(
    viewModel: AttendanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by rememberSaveable { mutableStateOf(O2IDestination.GATE_RADAR) }
    var openAddEmployeeModalOnRoster by rememberSaveable { mutableStateOf(false) }
    var pendingBluetoothAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val todayLogs by viewModel.todayLogs.collectAsStateWithLifecycle()
    val monthLogs by viewModel.monthLogs.collectAsStateWithLifecycle()
    val selectedYearMonth by viewModel.selectedYearMonth.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val workbookPreview by viewModel.excelWorkbookPreview.collectAsStateWithLifecycle()
    val recentGateAlerts by viewModel.recentGateAlerts.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val isHardwareSupported by viewModel.bluetoothController.isHardwareSupported.collectAsStateWithLifecycle()
    val isBluetoothEnabled by viewModel.bluetoothController.isBluetoothEnabled.collectAsStateWithLifecycle()
    val isScanning by viewModel.bluetoothController.isScanning.collectAsStateWithLifecycle()
    val isAdvertisingBeacon by viewModel.bluetoothController.isAdvertisingBeacon.collectAsStateWithLifecycle()
    val advertisingEmployeeCode by viewModel.bluetoothController.advertisingEmployeeCode.collectAsStateWithLifecycle()
    val bluetoothStatusMessage by viewModel.bluetoothController.statusMessage.collectAsStateWithLifecycle()
    val discoveredSignals by viewModel.bluetoothController.discoveredSignals.collectAsStateWithLifecycle()
    val bondedDevices by viewModel.bluetoothController.bondedDevices.collectAsStateWithLifecycle()

    BackHandler(enabled = currentTab != O2IDestination.GATE_RADAR) {
        currentTab = O2IDestination.GATE_RADAR
    }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToastMessage()
        }
    }

    val bluetoothPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.bluetoothController.refreshAdapterState()
        pendingBluetoothAction?.invoke()
        pendingBluetoothAction = null
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.bluetoothController.refreshAdapterState()
        if (viewModel.bluetoothController.isBluetoothEnabled.value) {
            viewModel.bluetoothController.startGateRadar()
        }
    }

    val saveExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.ms-excel")
    ) { uri ->
        if (uri != null) {
            viewModel.exportExcelWorkbookToUri(uri)
        }
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsvReportToUri(uri)
        }
    }

    val ensurePermissionsAndRun: (() -> Unit) -> Unit = { action ->
        if (viewModel.bluetoothController.hasScanPermission() &&
            viewModel.bluetoothController.hasConnectPermission()
        ) {
            action()
        } else {
            pendingBluetoothAction = action
            bluetoothPermissionsLauncher.launch(BluetoothGateController.requiredPermissions())
        }
    }

    // Automatically start Gate Radar whenever Admin opens the app with Bluetooth ON
    LaunchedEffect(isBluetoothEnabled) {
        if (isBluetoothEnabled) {
            if (viewModel.bluetoothController.hasScanPermission() &&
                viewModel.bluetoothController.hasConnectPermission()
            ) {
                viewModel.bluetoothController.startGateRadar()
            } else {
                bluetoothPermissionsLauncher.launch(BluetoothGateController.requiredPermissions())
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar {
                        O2IDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (dest == O2IDestination.EXCEL_LOG && monthLogs.isNotEmpty()) {
                                                Badge { Text("${monthLogs.size}") }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        O2IDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentTab) {
                        O2IDestination.GATE_RADAR -> {
                            GateRadarScreen(
                                employees = employees,
                                todayLogs = todayLogs,
                                settings = settings,
                                isHardwareSupported = isHardwareSupported,
                                isBluetoothEnabled = isBluetoothEnabled,
                                isScanning = isScanning,
                                isAdvertisingBeacon = isAdvertisingBeacon,
                                advertisingEmployeeCode = advertisingEmployeeCode,
                                bluetoothStatusMessage = bluetoothStatusMessage,
                                discoveredSignals = discoveredSignals,
                                bondedDevices = bondedDevices,
                                recentGateAlerts = recentGateAlerts,
                                onRequestEnableBluetooth = {
                                    ensurePermissionsAndRun {
                                        runCatching {
                                            enableBluetoothLauncher.launch(
                                                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                            )
                                        }
                                    }
                                },
                                onToggleGateRadar = {
                                    ensurePermissionsAndRun {
                                        if (isScanning) {
                                            viewModel.bluetoothController.stopGateRadar()
                                        } else {
                                            viewModel.bluetoothController.startGateRadar()
                                        }
                                    }
                                },
                                onSelectGateMode = { mode -> viewModel.setGateMode(mode) },
                                onStartEmployeeBeacon = { empCode ->
                                    ensurePermissionsAndRun {
                                        viewModel.bluetoothController.startEmployeeBeaconPass(empCode)
                                    }
                                },
                                onStopEmployeeBeacon = {
                                    viewModel.bluetoothController.stopEmployeeBeaconPass()
                                },
                                onManualCheckInOrOut = { emp, action ->
                                    viewModel.triggerGateCheckInOrOut(
                                        employee = emp,
                                        overrideAction = action
                                    )
                                },
                                onBindSignalToEmployee = { sig, emp ->
                                    viewModel.bindSignalToEmployee(sig, emp)
                                },
                                onSaveSettings = { cfg ->
                                    viewModel.saveSettings(cfg)
                                },
                                onNavigateToAddEmployee = {
                                    openAddEmployeeModalOnRoster = true
                                    currentTab = O2IDestination.ROSTER
                                },
                                onNavigateToExcelSheet = {
                                    currentTab = O2IDestination.EXCEL_LOG
                                }
                            )
                        }

                        O2IDestination.EXCEL_LOG -> {
                            ExcelSheetScreen(
                                selectedYearMonth = selectedYearMonth,
                                workbookPreview = workbookPreview,
                                employees = employees,
                                monthLogs = monthLogs,
                                onChangeMonth = { delta -> viewModel.changeSelectedMonth(delta) },
                                onForceSyncExcel = { viewModel.forceSyncExcelNow() },
                                onSaveExcelFile = {
                                    saveExcelLauncher.launch("O2I_Attendance_Report_$selectedYearMonth.xls")
                                },
                                onShareExcelFile = {
                                    viewModel.shareCurrentExcelWorkbook(context)
                                },
                                onExportCsvFile = {
                                    saveCsvLauncher.launch("O2I_Attendance_$selectedYearMonth.csv")
                                },
                                onUpsertManualLog = { log -> viewModel.upsertManualAttendanceLog(log) },
                                onDeleteLog = { id -> viewModel.deleteAttendanceLog(id) }
                            )
                        }

                        O2IDestination.ROSTER -> {
                            EmployeeRosterScreen(
                                employees = employees,
                                discoveredSignals = discoveredSignals,
                                bondedDevices = bondedDevices,
                                openAddDialogInitially = openAddEmployeeModalOnRoster,
                                onConsumedInitialAddDialog = { openAddEmployeeModalOnRoster = false },
                                onSaveEmployee = { emp -> viewModel.saveEmployee(emp) },
                                onDeleteEmployee = { id, name -> viewModel.deleteEmployee(id, name) }
                            )
                        }
                    }
                }
            }
        }
    }
}
