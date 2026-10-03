package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AttendanceLogEntity
import com.example.data.local.EmployeeEntity
import com.example.excel.ExcelWorkbookPreview
import com.example.ui.theme.AmberWarning400
import com.example.ui.theme.CyberCyan400
import com.example.ui.theme.EmeraldGreen400
import com.example.ui.theme.EmeraldGreen900
import com.example.ui.theme.RoseAlert400
import com.example.ui.theme.SlateNavy800
import com.example.ui.theme.SlateNavy900
import com.example.ui.theme.SlateNavy950
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExcelSheetScreen(
    selectedYearMonth: String,
    workbookPreview: ExcelWorkbookPreview,
    employees: List<EmployeeEntity>,
    monthLogs: List<AttendanceLogEntity>,
    onChangeMonth: (Int) -> Unit,
    onForceSyncExcel: () -> Unit,
    onSaveExcelFile: () -> Unit,
    onShareExcelFile: () -> Unit,
    onExportCsvFile: () -> Unit,
    onUpsertManualLog: (AttendanceLogEntity) -> Unit,
    onDeleteLog: (Long) -> Unit
) {
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var showAddLogDialog by remember { mutableStateOf(false) }
    var selectedLogForAction by remember { mutableStateOf<AttendanceLogEntity?>(null) }

    val sheets = workbookPreview.sheets
    val activeSheet = sheets.getOrNull(selectedSheetIndex) ?: sheets.firstOrNull()
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Live Excel Workbook Header & Export Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SlateNavy900)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldGreen900
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = "Excel Workbook",
                                tint = EmeraldGreen400,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = workbookPreview.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = EmeraldGreen400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Live Excel (.xls) • ${workbookPreview.fileSizeKb} KB • Synced ${workbookPreview.lastSyncedFormatted}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onForceSyncExcel,
                        modifier = Modifier.testTag("btn_force_sync_excel")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Excel Workbook Now",
                            tint = CyberCyan400
                        )
                    }
                }

                // Month Selector + Export Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SlateNavy800
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            IconButton(
                                onClick = { onChangeMonth(-1) },
                                modifier = Modifier.testTag("btn_excel_prev_month")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Month",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = selectedYearMonth,
                                style = MaterialTheme.typography.labelLarge,
                                color = CyberCyan400,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onChangeMonth(1) },
                                modifier = Modifier.testTag("btn_excel_next_month")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Month",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showAddLogDialog = true },
                        enabled = employees.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan400,
                            contentColor = SlateNavy950
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_add_manual_log")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Row", fontWeight = FontWeight.Bold)
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSaveExcelFile,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen400,
                            contentColor = SlateNavy950
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_save_excel_file")
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Save Excel (.xls)", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onShareExcelFile,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_share_excel_file")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = CyberCyan400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Share Workbook", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onExportCsvFile,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_export_csv_file")
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = AmberWarning400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("CSV", color = Color.White)
                    }
                }
            }
        }

        // 2. Excel Worksheet Tabs Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sheets.forEachIndexed { idx, tab ->
                val isSelected = selectedSheetIndex == idx
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedSheetIndex = idx },
                    label = {
                        Text(
                            text = "${tab.sheetName} (${tab.rows.size})",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGreen900,
                        selectedLabelColor = EmeraldGreen400
                    ),
                    modifier = Modifier.testTag("excel_sheet_tab_$idx")
                )
            }
        }

        // 3. Interactive Multi-Column Excel Spreadsheet Grid
        if (activeSheet != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Excel Column Letters Bar (Row 0: #, A, B, C, D...)
                        item {
                            Row(
                                modifier = Modifier
                                    .background(SlateNavy950)
                                    .border(0.5.dp, SlateNavy800)
                            ) {
                                ExcelHeaderCell(text = "#", widthDp = 44, isIndexCol = true)
                                activeSheet.columns.forEachIndexed { colIdx, _ ->
                                    val colLetter = ('A' + colIdx).toString()
                                    ExcelHeaderCell(
                                        text = colLetter,
                                        widthDp = columnWidthForIndex(colIdx),
                                        isIndexCol = true
                                    )
                                }
                            }
                        }

                        // Sheet Column Titles Row (Row 1)
                        item {
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFF0891B2))
                                    .border(0.5.dp, SlateNavy800)
                            ) {
                                ExcelHeaderCell(text = "1", widthDp = 44, isIndexCol = true)
                                activeSheet.columns.forEachIndexed { colIdx, colTitle ->
                                    ExcelHeaderCell(
                                        text = colTitle,
                                        widthDp = columnWidthForIndex(colIdx),
                                        isIndexCol = false
                                    )
                                }
                            }
                        }

                        if (activeSheet.rows.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .width(720.dp)
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No rows recorded in ${activeSheet.sheetName} for $selectedYearMonth yet. Log an arrival at the Gate Radar or tap '+ Add Row' above.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(activeSheet.rows) { rowIdx, rowCells ->
                                val excelRowNumber = rowIdx + 2
                                val bg = if (rowIdx % 2 == 0) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                }
                                Row(
                                    modifier = Modifier
                                        .background(bg)
                                        .clickable(enabled = selectedSheetIndex == 0 && rowIdx < monthLogs.size) {
                                            selectedLogForAction = monthLogs.getOrNull(rowIdx)
                                        }
                                ) {
                                    ExcelDataCell(
                                        text = excelRowNumber.toString(),
                                        widthDp = 44,
                                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        isMono = true
                                    )
                                    rowCells.forEachIndexed { colIdx, cellValue ->
                                        val cellColor = when (cellValue) {
                                            "ON_TIME" -> EmeraldGreen400
                                            "LATE", "HALF_DAY" -> AmberWarning400
                                            "OVERTIME" -> CyberCyan400
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                        ExcelDataCell(
                                            text = cellValue,
                                            widthDp = columnWidthForIndex(colIdx),
                                            textColor = cellColor,
                                            isMono = colIdx >= 4 || colIdx <= 1
                                        )
                                    }
                                }
                            }
                        }

                        // Summary / Totals Footer Row
                        if (activeSheet.summaryFooter.isNotEmpty() && activeSheet.rows.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .background(EmeraldGreen900.copy(alpha = 0.45f))
                                        .border(1.dp, EmeraldGreen400.copy(alpha = 0.5f))
                                ) {
                                    ExcelDataCell(
                                        text = "SUM",
                                        widthDp = 44,
                                        textColor = EmeraldGreen400,
                                        isMono = true,
                                        isBold = true
                                    )
                                    activeSheet.summaryFooter.forEachIndexed { colIdx, footerVal ->
                                        ExcelDataCell(
                                            text = footerVal,
                                            widthDp = columnWidthForIndex(colIdx),
                                            textColor = EmeraldGreen400,
                                            isMono = true,
                                            isBold = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog to Inspect / Delete an Attendance Row
    selectedLogForAction?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLogForAction = null },
            title = { Text("Excel Row: ${log.employeeName} (${log.dateString})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Employee Code: ${log.employeeCode} (${log.department})")
                    Text("Morning Arrival: ${log.checkInTimeFormatted}")
                    Text("Evening Exit: ${log.checkOutTimeFormatted ?: "Still in Office"}")
                    Text("Worked Hours: ${log.workedHours}h (Overtime: ${log.overtimeHours}h)")
                    Text("Status: ${log.status} (Late: ${log.lateMinutes} mins)")
                    Text("Gate Detection: ${log.detectionMethod} (RSSI: ${log.arrivalRssi ?: "-"} dBm)")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteLog(log.id)
                        selectedLogForAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAlert400)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Row")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLogForAction = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Dialog to Add or Backdate an Attendance Row into the Excel Sheet
    if (showAddLogDialog && employees.isNotEmpty()) {
        ManualAttendanceRowDialog(
            employees = employees,
            defaultYearMonth = selectedYearMonth,
            onDismiss = { showAddLogDialog = false },
            onSave = { newLog ->
                onUpsertManualLog(newLog)
                showAddLogDialog = false
            }
        )
    }
}

private fun columnWidthForIndex(colIdx: Int): Int {
    return when (colIdx) {
        0 -> 108
        1 -> 95
        2 -> 148
        3 -> 118
        else -> 112
    }
}

@Composable
private fun ExcelHeaderCell(
    text: String,
    widthDp: Int,
    isIndexCol: Boolean
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(if (isIndexCol) 28.dp else 38.dp)
            .border(0.5.dp, Color(0xFF334155))
            .padding(horizontal = 8.dp),
        contentAlignment = if (isIndexCol) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = if (isIndexCol) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = if (isIndexCol) Color(0xFF94A3B8) else Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExcelDataCell(
    text: String,
    widthDp: Int,
    textColor: Color,
    isMono: Boolean,
    isBold: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(38.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = if (isMono) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualAttendanceRowDialog(
    employees: List<EmployeeEntity>,
    defaultYearMonth: String,
    onDismiss: () -> Unit,
    onSave: (AttendanceLogEntity) -> Unit
) {
    var selectedEmployee by remember { mutableStateOf(employees.first()) }
    var expandedEmpDropdown by remember { mutableStateOf(false) }

    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var dateInput by remember {
        mutableStateOf(
            if (todayStr.startsWith(defaultYearMonth)) todayStr else "$defaultYearMonth-01"
        )
    }
    var arrivalTimeInput by remember { mutableStateOf("09:00:00") }
    var exitTimeInput by remember { mutableStateOf("18:00:00") }
    var workedHoursInput by remember { mutableStateOf("9.0") }
    var lateMinsInput by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Attendance Row to Excel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expandedEmpDropdown,
                    onExpandedChange = { expandedEmpDropdown = !expandedEmpDropdown }
                ) {
                    OutlinedTextField(
                        value = "${selectedEmployee.fullName} (${selectedEmployee.employeeCode})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Employee") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEmpDropdown)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedEmpDropdown,
                        onDismissRequest = { expandedEmpDropdown = false }
                    ) {
                        employees.forEach { emp ->
                            DropdownMenuItem(
                                text = { Text("${emp.fullName} (${emp.employeeCode})") },
                                onClick = {
                                    selectedEmployee = emp
                                    expandedEmpDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = arrivalTimeInput,
                        onValueChange = { arrivalTimeInput = it },
                        label = { Text("Arrival (HH:mm:ss)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = exitTimeInput,
                        onValueChange = { exitTimeInput = it },
                        label = { Text("Exit (HH:mm:ss)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = workedHoursInput,
                        onValueChange = { workedHoursInput = it },
                        label = { Text("Worked Hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lateMinsInput,
                        onValueChange = { lateMinsInput = it },
                        label = { Text("Late Mins") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val worked = workedHoursInput.toDoubleOrNull() ?: 9.0
                    val regular = minOf(9.0, worked)
                    val ot = max(0.0, ((worked - 9.0) * 100.0).roundToInt() / 100.0)
                    val lateMins = lateMinsInput.toIntOrNull() ?: 0
                    val status = when {
                        ot >= 0.5 -> "OVERTIME"
                        lateMins > 15 -> "LATE"
                        worked < 5.0 -> "HALF_DAY"
                        else -> "ON_TIME"
                    }
                    val ym = if (dateInput.length >= 7) dateInput.substring(0, 7) else defaultYearMonth
                    val now = System.currentTimeMillis()
                    onSave(
                        AttendanceLogEntity(
                            employeeId = selectedEmployee.id,
                            employeeCode = selectedEmployee.employeeCode,
                            employeeName = selectedEmployee.fullName,
                            department = selectedEmployee.department,
                            dateString = dateInput.trim(),
                            yearMonth = ym,
                            checkInTimestamp = now - (worked * 3600_000).toLong(),
                            checkOutTimestamp = now,
                            checkInTimeFormatted = arrivalTimeInput.trim(),
                            checkOutTimeFormatted = exitTimeInput.trim().ifBlank { null },
                            arrivalRssi = -60,
                            exitRssi = -62,
                            bluetoothMacUsed = selectedEmployee.bluetoothAddress,
                            detectionMethod = "ADMIN_EXCEL_ENTRY",
                            status = status,
                            lateMinutes = lateMins,
                            workedHours = worked,
                            regularHours = regular,
                            overtimeHours = ot,
                            notes = "Logged in Excel Sheet by HR/Admin"
                        )
                    )
                }
            ) {
                Text("Save to Excel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
