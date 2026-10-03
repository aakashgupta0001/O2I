package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothGateController
import com.example.bluetooth.DiscoveredBluetoothSignal
import com.example.data.local.AttendanceLogEntity
import com.example.data.local.AttendanceRepository
import com.example.data.local.EmployeeEntity
import com.example.data.local.GateRegistrationResult
import com.example.data.local.PayrollSettingsEntity
import com.example.data.local.PulseAttendDatabase
import com.example.domain.AttendanceSummaryCalculator
import com.example.domain.MonthlyAttendanceReport
import com.example.excel.ExcelReportGenerator
import com.example.excel.ExcelWorkbookPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class GateEventBanner(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val subtitle: String,
    val isArrival: Boolean,
    val timestampFormatted: String,
    val rssi: Int?
)

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val database = PulseAttendDatabase.getInstance(appContext)
    val repository = AttendanceRepository(database.pulseAttendDao())
    val bluetoothController = BluetoothGateController(appContext)

    private val todayDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _selectedDate = MutableStateFlow(todayDateFormat.format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _selectedYearMonth = MutableStateFlow(monthFormat.format(Date()))
    val selectedYearMonth: StateFlow<String> = _selectedYearMonth.asStateFlow()

    private val _recentGateAlerts = MutableStateFlow<List<GateEventBanner>>(emptyList())
    val recentGateAlerts: StateFlow<List<GateEventBanner>> = _recentGateAlerts.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _lastSavedExcelFile = MutableStateFlow<File?>(null)
    val lastSavedExcelFile: StateFlow<File?> = _lastSavedExcelFile.asStateFlow()

    private val lastSignalProcessMap = mutableMapOf<Long, Long>()

    val employees: StateFlow<List<EmployeeEntity>> = repository.activeEmployees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<PayrollSettingsEntity> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PayrollSettingsEntity())

    val todayLogs: StateFlow<List<AttendanceLogEntity>> = _selectedDate
        .flatMapLatest { date -> repository.observeLogsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthLogs: StateFlow<List<AttendanceLogEntity>> = _selectedYearMonth
        .flatMapLatest { ym -> repository.observeLogsForMonth(ym) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyAttendanceReport: StateFlow<MonthlyAttendanceReport> = combine(
        _selectedYearMonth,
        employees,
        monthLogs,
        settings
    ) { ym, emps, logs, cfg ->
        AttendanceSummaryCalculator.calculateMonthlyAttendance(
            yearMonth = ym,
            employees = emps,
            monthLogs = logs,
            settings = cfg
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AttendanceSummaryCalculator.calculateMonthlyAttendance(
            yearMonth = _selectedYearMonth.value,
            employees = emptyList(),
            monthLogs = emptyList(),
            settings = PayrollSettingsEntity()
        )
    )

    val excelWorkbookPreview: StateFlow<ExcelWorkbookPreview> = combine(
        _lastSavedExcelFile,
        employees,
        monthLogs,
        monthlyAttendanceReport
    ) { savedFile, emps, logs, report ->
        ExcelReportGenerator.buildWorkbookPreview(
            excelFile = savedFile,
            employees = emps,
            logs = logs,
            attendanceReport = report
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ExcelReportGenerator.buildWorkbookPreview(
            excelFile = null,
            employees = emptyList(),
            logs = emptyList(),
            attendanceReport = monthlyAttendanceReport.value
        )
    )

    init {
        // Automatic daily date rollover ticker so every new morning resets daily gate status
        // while keeping all registered employees permanently in Room
        viewModelScope.launch {
            while (true) {
                syncCurrentDayAndMonthIfNeeded()
                kotlinx.coroutines.delay(30_000L)
            }
        }

        viewModelScope.launch {
            bluetoothController.liveSignalEvents.collect { signal ->
                syncCurrentDayAndMonthIfNeeded()
                handleIncomingBluetoothSignal(signal)
            }
        }

        viewModelScope.launch {
            combine(
                _selectedYearMonth,
                employees,
                monthLogs,
                monthlyAttendanceReport,
                settings
            ) { ym, emps, logs, report, cfg ->
                Quintuple(ym, emps, logs, report, cfg)
            }.collect { (ym, emps, logs, report, cfg) ->
                if (cfg.autoSyncExcelOnEvent) {
                    val (file, _) = ExcelReportGenerator.syncAndSaveMonthlyExcelWorkbook(
                        context = appContext,
                        yearMonth = ym,
                        employees = emps,
                        logs = logs,
                        attendanceReport = report,
                        settings = cfg
                    )
                    _lastSavedExcelFile.value = file
                }
            }
        }
    }

    private suspend fun handleIncomingBluetoothSignal(signal: DiscoveredBluetoothSignal) {
        val currentSettings = settings.value
        if (signal.rssi < currentSettings.rssiThresholdDbm) {
            return
        }

        val roster = employees.value
        if (roster.isEmpty()) return

        val matchedEmployee = roster.firstOrNull { emp ->
            val macMatches = emp.bluetoothAddress.isNotBlank() &&
                emp.bluetoothAddress.equals(signal.address, ignoreCase = true)
            val codeMatches = !signal.extractedEmployeeCode.isNullOrBlank() &&
                emp.employeeCode.equals(signal.extractedEmployeeCode, ignoreCase = true)
            val nameMatches = emp.bluetoothDeviceName.isNotBlank() &&
                signal.deviceName.isNotBlank() &&
                (signal.deviceName.equals(emp.bluetoothDeviceName, ignoreCase = true) ||
                    signal.deviceName.contains(emp.bluetoothDeviceName, ignoreCase = true) ||
                    signal.deviceName.contains(emp.employeeCode, ignoreCase = true))
            macMatches || codeMatches || nameMatches
        } ?: return

        val now = System.currentTimeMillis()
        val lastProcessed = lastSignalProcessMap[matchedEmployee.id] ?: 0L
        if (now - lastProcessed < 12_000L) {
            return
        }
        lastSignalProcessMap[matchedEmployee.id] = now

        val method = if (signal.isBleBeacon) "BLE_BEACON_AUTO" else "BLUETOOTH_AUTO"
        val result = repository.processEmployeeGateEvent(
            employee = matchedEmployee,
            rssi = signal.rssi,
            detectionMethod = method,
            overrideAction = null,
            eventTimeMillis = now
        )
        handleGateResult(result, signal.rssi)
    }

    private fun handleGateResult(result: GateRegistrationResult, rssi: Int?) {
        val nowFormatted = timeFormat.format(Date())
        when (result) {
            is GateRegistrationResult.ArrivalRegistered -> {
                triggerHapticConfirmation()
                val banner = GateEventBanner(
                    title = "ARRIVAL REGISTERED • ${result.employee.fullName} (${result.employee.employeeCode})",
                    subtitle = "Category: ${result.employee.department} • Check-In at ${result.log.checkInTimeFormatted} (${result.log.status}) • Synced to Excel",
                    isArrival = true,
                    timestampFormatted = nowFormatted,
                    rssi = rssi
                )
                _recentGateAlerts.value = (listOf(banner) + _recentGateAlerts.value).take(15)
                _toastMessage.value = "Arrival registered for ${result.employee.fullName}"
            }

            is GateRegistrationResult.ExitRegistered -> {
                triggerHapticConfirmation()
                val banner = GateEventBanner(
                    title = "EXIT REGISTERED • ${result.employee.fullName} (${result.employee.employeeCode})",
                    subtitle = "Category: ${result.employee.department} • Check-Out at ${result.log.checkOutTimeFormatted} • Worked ${result.log.workedHours}h • Synced to Excel",
                    isArrival = false,
                    timestampFormatted = nowFormatted,
                    rssi = rssi
                )
                _recentGateAlerts.value = (listOf(banner) + _recentGateAlerts.value).take(15)
                _toastMessage.value = "Exit registered for ${result.employee.fullName} (${result.log.workedHours}h)"
            }

            is GateRegistrationResult.AlreadyCompletedToday -> {}
            is GateRegistrationResult.CooldownActive -> {}
        }
    }

    fun triggerGateCheckInOrOut(
        employee: EmployeeEntity,
        overrideAction: String?,
        rssi: Int? = null,
        detectionMethod: String = "ADMIN_GATE_MANUAL"
    ) {
        viewModelScope.launch {
            val result = repository.processEmployeeGateEvent(
                employee = employee,
                rssi = rssi,
                detectionMethod = detectionMethod,
                overrideAction = overrideAction
            )
            when (result) {
                is GateRegistrationResult.ArrivalRegistered,
                is GateRegistrationResult.ExitRegistered -> {
                    handleGateResult(result, rssi)
                }

                is GateRegistrationResult.AlreadyCompletedToday -> {
                    _toastMessage.value = "${employee.fullName} already completed arrival & exit today"
                }

                is GateRegistrationResult.CooldownActive -> {
                    _toastMessage.value =
                        "Arrival logged recently for ${employee.fullName}. Use 'Log Exit' to check out now."
                }
            }
        }
    }

    fun bindSignalToEmployee(signal: DiscoveredBluetoothSignal, employee: EmployeeEntity) {
        viewModelScope.launch {
            val updated = employee.copy(
                bluetoothAddress = signal.address.ifBlank { employee.bluetoothAddress },
                bluetoothDeviceName = signal.deviceName.ifBlank { employee.bluetoothDeviceName }
            )
            repository.saveEmployee(updated)
            _toastMessage.value = "Linked ${signal.deviceName} (${signal.address}) to ${employee.fullName}"
            handleIncomingBluetoothSignal(signal)
        }
    }

    fun setGateMode(mode: String) {
        viewModelScope.launch {
            val updated = settings.value.copy(gateMode = mode)
            repository.saveSettings(updated)
            val label = when (mode) {
                "MORNING_ARRIVAL" -> "Morning Arrival Mode"
                "EVENING_EXIT" -> "Evening Exit Mode"
                else -> "Smart Auto (Arrival & Exit) Mode"
            }
            _toastMessage.value = "Gate switched to $label"
        }
    }

    fun saveSettings(updatedSettings: PayrollSettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(updatedSettings)
            _toastMessage.value = "O2I Gate settings saved"
        }
    }

    fun saveEmployee(employee: EmployeeEntity) {
        viewModelScope.launch {
            repository.saveEmployee(employee)
            _toastMessage.value = "Saved employee ${employee.fullName} (${employee.department})"
        }
    }

    fun deleteEmployee(employeeId: Long, name: String) {
        viewModelScope.launch {
            repository.deleteEmployee(employeeId)
            _toastMessage.value = "Removed $name from O2I roster"
        }
    }

    fun upsertManualAttendanceLog(log: AttendanceLogEntity) {
        viewModelScope.launch {
            repository.upsertManualAttendanceLog(log)
            _toastMessage.value = "Saved attendance log for ${log.employeeName} (${log.dateString})"
        }
    }

    fun deleteAttendanceLog(logId: Long) {
        viewModelScope.launch {
            repository.deleteAttendanceLog(logId)
            _toastMessage.value = "Attendance record deleted"
        }
    }

    fun changeSelectedMonth(offsetMonths: Int) {
        runCatching {
            val parts = _selectedYearMonth.value.split("-")
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, offsetMonths)
            }
            val newYm = monthFormat.format(cal.time)
            _selectedYearMonth.value = newYm
        }
    }

    fun forceSyncExcelNow() {
        viewModelScope.launch {
            val (file, _) = ExcelReportGenerator.syncAndSaveMonthlyExcelWorkbook(
                context = appContext,
                yearMonth = _selectedYearMonth.value,
                employees = employees.value,
                logs = monthLogs.value,
                attendanceReport = monthlyAttendanceReport.value,
                settings = settings.value
            )
            _lastSavedExcelFile.value = file
            _toastMessage.value = "O2I Excel workbook synced: ${file.name}"
        }
    }

    fun exportExcelWorkbookToUri(targetUri: Uri) {
        viewModelScope.launch {
            val ok = ExcelReportGenerator.exportExcelToUri(
                context = appContext,
                targetUri = targetUri,
                yearMonth = _selectedYearMonth.value,
                employees = employees.value,
                logs = monthLogs.value,
                attendanceReport = monthlyAttendanceReport.value,
                settings = settings.value
            )
            _toastMessage.value = if (ok) {
                "O2I Excel Workbook (.xls) saved to device!"
            } else {
                "Failed to write Excel file"
            }
        }
    }

    fun exportCsvReportToUri(targetUri: Uri) {
        viewModelScope.launch {
            val ok = ExcelReportGenerator.exportCsvToUri(
                context = appContext,
                targetUri = targetUri,
                yearMonth = _selectedYearMonth.value,
                logs = monthLogs.value,
                attendanceReport = monthlyAttendanceReport.value
            )
            _toastMessage.value = if (ok) {
                "O2I CSV Attendance report exported!"
            } else {
                "Failed to export CSV file"
            }
        }
    }

    fun shareCurrentExcelWorkbook(activityContext: Context) {
        viewModelScope.launch {
            val (file, _) = ExcelReportGenerator.syncAndSaveMonthlyExcelWorkbook(
                context = appContext,
                yearMonth = _selectedYearMonth.value,
                employees = employees.value,
                logs = monthLogs.value,
                attendanceReport = monthlyAttendanceReport.value,
                settings = settings.value
            )
            _lastSavedExcelFile.value = file
            runCatching {
                val shareIntent = ExcelReportGenerator.createShareExcelIntent(
                    context = activityContext,
                    excelFile = file,
                    yearMonth = _selectedYearMonth.value
                )
                activityContext.startActivity(shareIntent)
            }.onFailure {
                _toastMessage.value = "Excel saved at ${file.name} (Use 'Save Excel (.xls)' to download)"
            }
        }
    }

    private var lastKnownTodayStr: String = todayDateFormat.format(Date())

    private fun syncCurrentDayAndMonthIfNeeded() {
        val now = Date()
        val currentToday = todayDateFormat.format(now)
        if (currentToday != lastKnownTodayStr) {
            lastKnownTodayStr = currentToday
            _selectedDate.value = currentToday
            _selectedYearMonth.value = monthFormat.format(now)
        }
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    private fun triggerHapticConfirmation() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothController.cleanup()
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
