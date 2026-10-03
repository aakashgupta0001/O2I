package com.example.excel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.AttendanceLogEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.PayrollSettingsEntity
import com.example.domain.MonthlyAttendanceReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExcelSheetTab(
    val sheetName: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val summaryFooter: List<String> = emptyList()
)

data class ExcelWorkbookPreview(
    val fileName: String,
    val filePath: String,
    val lastSyncedFormatted: String,
    val fileSizeKb: Double,
    val sheets: List<ExcelSheetTab>
)

object ExcelReportGenerator {

    /**
     * Generates and saves the multi-sheet Microsoft Excel Workbook (.xls SpreadsheetML)
     * and CSV companion file inside the app's persistent exports folder.
     */
    suspend fun syncAndSaveMonthlyExcelWorkbook(
        context: Context,
        yearMonth: String,
        employees: List<EmployeeEntity>,
        logs: List<AttendanceLogEntity>,
        attendanceReport: MonthlyAttendanceReport,
        settings: PayrollSettingsEntity
    ): Pair<File, ExcelWorkbookPreview> = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "exports").apply {
            if (!exists()) mkdirs()
        }
        val safeMonth = yearMonth.replace("[^0-9A-Za-z_-]".toRegex(), "_")
        val excelFile = File(exportDir, "O2I_Attendance_Report_$safeMonth.xls")
        val xmlContent = buildExcelXmlWorkbook(
            yearMonth = yearMonth,
            employees = employees,
            logs = logs,
            attendanceReport = attendanceReport,
            settings = settings
        )
        excelFile.writeText(xmlContent, Charsets.UTF_8)

        // Companion CSV file
        val csvFile = File(exportDir, "O2I_Attendance_$safeMonth.csv")
        csvFile.writeText(
            buildCsvContent(yearMonth, logs, attendanceReport),
            Charsets.UTF_8
        )

        val preview = buildWorkbookPreview(
            excelFile = excelFile,
            employees = employees,
            logs = logs,
            attendanceReport = attendanceReport
        )
        Pair(excelFile, preview)
    }

    /**
     * Writes the multi-sheet Microsoft Excel (.xls) workbook directly to a user-selected SAF Uri.
     */
    suspend fun exportExcelToUri(
        context: Context,
        targetUri: Uri,
        yearMonth: String,
        employees: List<EmployeeEntity>,
        logs: List<AttendanceLogEntity>,
        attendanceReport: MonthlyAttendanceReport,
        settings: PayrollSettingsEntity
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val xmlContent = buildExcelXmlWorkbook(
                yearMonth = yearMonth,
                employees = employees,
                logs = logs,
                attendanceReport = attendanceReport,
                settings = settings
            )
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(xmlContent.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Writes the CSV report to a user-selected SAF Uri.
     */
    suspend fun exportCsvToUri(
        context: Context,
        targetUri: Uri,
        yearMonth: String,
        logs: List<AttendanceLogEntity>,
        attendanceReport: MonthlyAttendanceReport
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val csvContent = buildCsvContent(yearMonth, logs, attendanceReport)
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(csvContent.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Creates a Share Intent via FileProvider so Admin can open the .xls workbook directly
     * in Microsoft Excel, Google Sheets, Gmail, Drive, or WhatsApp.
     */
    fun createShareExcelIntent(context: Context, excelFile: File, yearMonth: String): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, excelFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "O2I Monthly Bluetooth Attendance Excel Report ($yearMonth)"
            )
            putExtra(
                Intent.EXTRA_TEXT,
                "Attached is the O2I automated Bluetooth Gate Attendance & Monthly Summary Excel Workbook for $yearMonth."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Export / Share O2I Excel Workbook ($yearMonth)")
    }

    fun buildWorkbookPreview(
        excelFile: File?,
        employees: List<EmployeeEntity>,
        logs: List<AttendanceLogEntity>,
        attendanceReport: MonthlyAttendanceReport
    ): ExcelWorkbookPreview {
        val attendanceSheet = ExcelSheetTab(
            sheetName = "1_Daily_Attendance",
            columns = listOf(
                "Date",
                "Emp ID",
                "Employee Name",
                "Category",
                "Arrival (In)",
                "Exit (Out)",
                "Worked Hrs",
                "Overtime Hrs",
                "Late Mins",
                "Status",
                "BT RSSI",
                "Gate Method"
            ),
            rows = logs.map { log ->
                listOf(
                    log.dateString,
                    log.employeeCode,
                    log.employeeName,
                    log.department,
                    log.checkInTimeFormatted,
                    log.checkOutTimeFormatted ?: "ACTIVE (IN OFFICE)",
                    String.format(Locale.US, "%.2f", log.workedHours),
                    String.format(Locale.US, "%.2f", log.overtimeHours),
                    log.lateMinutes.toString(),
                    log.status,
                    log.arrivalRssi?.let { "${it}dBm" } ?: "N/A",
                    log.detectionMethod
                )
            },
            summaryFooter = listOf(
                "TOTAL RECORDS: ${logs.size}",
                "",
                "",
                "",
                "",
                "TOTAL HRS:",
                String.format(Locale.US, "%.2f", logs.sumOf { it.workedHours }),
                String.format(Locale.US, "%.2f", logs.sumOf { it.overtimeHours }),
                "${logs.sumOf { it.lateMinutes }}m",
                "",
                "",
                ""
            )
        )

        val monthlySummarySheet = ExcelSheetTab(
            sheetName = "2_Monthly_Report_${attendanceReport.yearMonth}",
            columns = listOf(
                "Emp ID",
                "Employee Name",
                "Category",
                "Role",
                "Working Days",
                "Present Days",
                "Absent Days",
                "Late Days",
                "Worked Hrs",
                "Regular Hrs",
                "Overtime Hrs",
                "Attendance %"
            ),
            rows = attendanceReport.items.map { item ->
                listOf(
                    item.employee.employeeCode,
                    item.employee.fullName,
                    item.employee.department,
                    item.employee.designation,
                    item.standardWorkingDays.toString(),
                    item.daysPresent.toString(),
                    item.daysAbsent.toString(),
                    item.daysLate.toString(),
                    String.format(Locale.US, "%.2f", item.totalWorkedHours),
                    String.format(Locale.US, "%.2f", item.totalRegularHours),
                    String.format(Locale.US, "%.2f", item.totalOvertimeHours),
                    "${String.format(Locale.US, "%.1f", item.attendancePercentage)}%"
                )
            },
            summaryFooter = listOf(
                "TOTAL (${attendanceReport.totalEmployees})",
                "",
                "",
                "",
                "",
                "${attendanceReport.totalPresentDays}d",
                "",
                "${attendanceReport.totalLateArrivals}d",
                String.format(Locale.US, "%.2f", attendanceReport.totalHoursLogged),
                "",
                String.format(Locale.US, "%.2f", attendanceReport.totalOvertimeHours),
                ""
            )
        )

        val rosterSheet = ExcelSheetTab(
            sheetName = "3_O2I_Roster",
            columns = listOf(
                "Emp ID",
                "Full Name",
                "Category",
                "Role",
                "Bluetooth MAC",
                "BT Device / Beacon ID",
                "Shift Timing"
            ),
            rows = employees.map { emp ->
                listOf(
                    emp.employeeCode,
                    emp.fullName,
                    emp.department,
                    emp.designation,
                    emp.bluetoothAddress.ifBlank { "Dynamic BLE" },
                    emp.bluetoothDeviceName.ifBlank { "O2I-${emp.employeeCode}" },
                    String.format(
                        Locale.US,
                        "%02d:%02d - %02d:%02d",
                        emp.shiftStartHour,
                        emp.shiftStartMinute,
                        emp.shiftEndHour,
                        emp.shiftEndMinute
                    )
                )
            }
        )

        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US)
        val fileExists = excelFile != null && excelFile.exists()
        val sizeKb = if (fileExists && excelFile != null) {
            ((excelFile.length() / 1024.0) * 10.0).toInt() / 10.0
        } else {
            0.0
        }

        return ExcelWorkbookPreview(
            fileName = excelFile?.name ?: "O2I_Attendance_Report_${attendanceReport.yearMonth}.xls",
            filePath = excelFile?.absolutePath ?: "/files/exports/O2I_Attendance_Report_${attendanceReport.yearMonth}.xls",
            lastSyncedFormatted = sdf.format(Date(excelFile?.lastModified() ?: System.currentTimeMillis())),
            fileSizeKb = sizeKb,
            sheets = listOf(attendanceSheet, monthlySummarySheet, rosterSheet)
        )
    }

    /**
     * Builds a Microsoft Office Excel 2003 XML Spreadsheet (.xls) containing 3 styled worksheets:
     * 1. Daily Attendance Log
     * 2. Monthly Attendance Summary
     * 3. O2I Employee Bluetooth Roster
     */
    fun buildExcelXmlWorkbook(
        yearMonth: String,
        employees: List<EmployeeEntity>,
        logs: List<AttendanceLogEntity>,
        attendanceReport: MonthlyAttendanceReport,
        settings: PayrollSettingsEntity
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<?mso-application progid="Excel.Sheet"?>""")
        sb.appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")

        // Styles
        sb.appendLine("""  <Styles>""")
        sb.appendLine("""    <Style ss:ID="Default" ss:Name="Normal">""")
        sb.appendLine("""      <Alignment ss:Vertical="Center"/>""")
        sb.appendLine("""      <Font ss:FontName="Calibri" ss:Size="11" ss:Color="#0F172A"/>""")
        sb.appendLine("""    </Style>""")
        sb.appendLine("""    <Style ss:ID="TitleBanner">""")
        sb.appendLine("""      <Alignment ss:Vertical="Center" ss:Horizontal="Left"/>""")
        sb.appendLine("""      <Font ss:FontName="Calibri" ss:Size="14" ss:Bold="1" ss:Color="#FFFFFF"/>""")
        sb.appendLine("""      <Interior ss:Color="#0F172A" ss:Pattern="Solid"/>""")
        sb.appendLine("""    </Style>""")
        sb.appendLine("""    <Style ss:ID="HeaderCell">""")
        sb.appendLine("""      <Alignment ss:Vertical="Center" ss:Horizontal="Center"/>""")
        sb.appendLine("""      <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#FFFFFF"/>""")
        sb.appendLine("""      <Interior ss:Color="#0891B2" ss:Pattern="Solid"/>""")
        sb.appendLine("""    </Style>""")
        sb.appendLine("""    <Style ss:ID="DataCell">""")
        sb.appendLine("""      <Alignment ss:Vertical="Center"/>""")
        sb.appendLine("""      <Font ss:FontName="Calibri" ss:Size="11" ss:Color="#1E293B"/>""")
        sb.appendLine("""    </Style>""")
        sb.appendLine("""    <Style ss:ID="NumberCell">""")
        sb.appendLine("""      <Alignment ss:Vertical="Center" ss:Horizontal="Right"/>""")
        sb.appendLine("""      <Font ss:FontName="Consolas" ss:Size="11" ss:Color="#0F172A"/>""")
        sb.appendLine("""    </Style>""")
        sb.appendLine("""    <Style ss:ID="TotalRow">""")
        sb.appendLine("""      <Alignment ss:Vertical="Center"/>""")
        sb.appendLine("""      <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#064E3B"/>""")
        sb.appendLine("""      <Interior ss:Color="#D1FAE5" ss:Pattern="Solid"/>""")
        sb.appendLine("""    </Style>""")
        sb.appendLine("""  </Styles>""")

        // Worksheet 1: Daily Attendance Log
        sb.appendLine("""  <Worksheet ss:Name="Daily_Attendance_$yearMonth">""")
        sb.appendLine("""    <Table>""")
        sb.appendLine("""      <Row ss:Height="26">""")
        sb.appendLine("""        <Cell ss:StyleID="TitleBanner" ss:MergeAcross="11"><Data ss:Type="String">${escapeXml(settings.companyName)} - Bluetooth Gate Attendance Log ($yearMonth)</Data></Cell>""")
        sb.appendLine("""      </Row>""")
        val attHeaders = listOf(
            "Date", "Employee ID", "Employee Name", "Category",
            "Morning Arrival", "Evening Exit", "Worked Hours", "Regular Hours",
            "Overtime Hours", "Late Minutes", "Attendance Status", "Bluetooth Gate Signal"
        )
        sb.appendLine("""      <Row ss:Height="22">""")
        attHeaders.forEach { h ->
            sb.appendLine("""        <Cell ss:StyleID="HeaderCell"><Data ss:Type="String">${escapeXml(h)}</Data></Cell>""")
        }
        sb.appendLine("""      </Row>""")

        logs.forEach { log ->
            sb.appendLine("""      <Row ss:Height="19">""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(log.dateString)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(log.employeeCode)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(log.employeeName)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(log.department)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(log.checkInTimeFormatted)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(log.checkOutTimeFormatted ?: "IN_OFFICE")}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${log.workedHours}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${log.regularHours}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${log.overtimeHours}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${log.lateMinutes}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(log.status)}</Data></Cell>""")
            val sigInfo = "${log.detectionMethod} (In:${log.arrivalRssi ?: "-"}dBm / Out:${log.exitRssi ?: "-"}dBm)"
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(sigInfo)}</Data></Cell>""")
            sb.appendLine("""      </Row>""")
        }
        sb.appendLine("""    </Table>""")
        sb.appendLine("""  </Worksheet>""")

        // Worksheet 2: Monthly Attendance Summary Report
        sb.appendLine("""  <Worksheet ss:Name="Monthly_Report_$yearMonth">""")
        sb.appendLine("""    <Table>""")
        sb.appendLine("""      <Row ss:Height="26">""")
        sb.appendLine("""        <Cell ss:StyleID="TitleBanner" ss:MergeAcross="11"><Data ss:Type="String">${escapeXml(settings.companyName)} - Monthly Attendance Report ($yearMonth)</Data></Cell>""")
        sb.appendLine("""      </Row>""")
        val sumHeaders = listOf(
            "Employee ID", "Employee Name", "Category", "Designation",
            "Working Days", "Present Days", "Absent Days", "Late Days",
            "Total Worked Hrs", "Regular Hrs", "Overtime Hrs", "Attendance %"
        )
        sb.appendLine("""      <Row ss:Height="22">""")
        sumHeaders.forEach { h ->
            sb.appendLine("""        <Cell ss:StyleID="HeaderCell"><Data ss:Type="String">${escapeXml(h)}</Data></Cell>""")
        }
        sb.appendLine("""      </Row>""")

        attendanceReport.items.forEach { item ->
            sb.appendLine("""      <Row ss:Height="20">""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(item.employee.employeeCode)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(item.employee.fullName)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(item.employee.department)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(item.employee.designation)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${item.standardWorkingDays}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${item.daysPresent}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${item.daysAbsent}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${item.daysLate}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${item.totalWorkedHours}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${item.totalRegularHours}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${item.totalOvertimeHours}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="TotalRow"><Data ss:Type="Number">${item.attendancePercentage}</Data></Cell>""")
            sb.appendLine("""      </Row>""")
        }
        sb.appendLine("""    </Table>""")
        sb.appendLine("""  </Worksheet>""")

        // Worksheet 3: Employee Roster & Bluetooth Bindings
        sb.appendLine("""  <Worksheet ss:Name="O2I_Bluetooth_Roster">""")
        sb.appendLine("""    <Table>""")
        val rosterHeaders = listOf(
            "Employee Code", "Full Name", "Category", "Designation",
            "Bluetooth MAC Address", "Bluetooth Device / Beacon Name",
            "Shift Start", "Shift End"
        )
        sb.appendLine("""      <Row ss:Height="22">""")
        rosterHeaders.forEach { h ->
            sb.appendLine("""        <Cell ss:StyleID="HeaderCell"><Data ss:Type="String">${escapeXml(h)}</Data></Cell>""")
        }
        sb.appendLine("""      </Row>""")
        employees.forEach { emp ->
            sb.appendLine("""      <Row ss:Height="19">""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(emp.employeeCode)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(emp.fullName)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(emp.department)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(emp.designation)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(emp.bluetoothAddress)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${escapeXml(emp.bluetoothDeviceName)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${String.format(Locale.US, "%02d:%02d", emp.shiftStartHour, emp.shiftStartMinute)}</Data></Cell>""")
            sb.appendLine("""        <Cell ss:StyleID="DataCell"><Data ss:Type="String">${String.format(Locale.US, "%02d:%02d", emp.shiftEndHour, emp.shiftEndMinute)}</Data></Cell>""")
            sb.appendLine("""      </Row>""")
        }
        sb.appendLine("""    </Table>""")
        sb.appendLine("""  </Worksheet>""")

        sb.appendLine("""</Workbook>""")
        return sb.toString()
    }

    fun buildCsvContent(
        yearMonth: String,
        logs: List<AttendanceLogEntity>,
        attendanceReport: MonthlyAttendanceReport
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# O2I MONTHLY ATTENDANCE SUMMARY ($yearMonth)")
        sb.appendLine("Employee ID,Employee Name,Category,Designation,Working Days,Present Days,Absent Days,Late Days,Worked Hours,Regular Hours,Overtime Hours,Attendance %")
        attendanceReport.items.forEach { item ->
            sb.appendLine(
                listOf(
                    csvEscape(item.employee.employeeCode),
                    csvEscape(item.employee.fullName),
                    csvEscape(item.employee.department),
                    csvEscape(item.employee.designation),
                    item.standardWorkingDays.toString(),
                    item.daysPresent.toString(),
                    item.daysAbsent.toString(),
                    item.daysLate.toString(),
                    item.totalWorkedHours.toString(),
                    item.totalRegularHours.toString(),
                    item.totalOvertimeHours.toString(),
                    item.attendancePercentage.toString()
                ).joinToString(",")
            )
        }
        sb.appendLine()
        sb.appendLine("# DAILY BLUETOOTH GATE ATTENDANCE LOGS ($yearMonth)")
        sb.appendLine("Date,Employee ID,Employee Name,Category,Arrival Time,Exit Time,Worked Hours,Overtime Hours,Late Minutes,Status,Arrival RSSI (dBm),Exit RSSI (dBm),Detection Method")
        logs.forEach { log ->
            sb.appendLine(
                listOf(
                    csvEscape(log.dateString),
                    csvEscape(log.employeeCode),
                    csvEscape(log.employeeName),
                    csvEscape(log.department),
                    csvEscape(log.checkInTimeFormatted),
                    csvEscape(log.checkOutTimeFormatted ?: "IN_OFFICE"),
                    log.workedHours.toString(),
                    log.overtimeHours.toString(),
                    log.lateMinutes.toString(),
                    csvEscape(log.status),
                    (log.arrivalRssi ?: "").toString(),
                    (log.exitRssi ?: "").toString(),
                    csvEscape(log.detectionMethod)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun escapeXml(input: String): String =
        input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun csvEscape(input: String): String {
        val cleaned = input.replace("\"", "\"\"")
        return if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("\n")) {
            "\"$cleaned\""
        } else {
            cleaned
        }
    }
}
