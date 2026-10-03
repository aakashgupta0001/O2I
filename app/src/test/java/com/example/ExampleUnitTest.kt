package com.example

import com.example.data.local.AttendanceLogEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.PayrollSettingsEntity
import com.example.domain.AttendanceSummaryCalculator
import com.example.excel.ExcelReportGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun attendanceAndExcelWorkbook_generatesAccurateCalculations() {
        val emp = EmployeeEntity(
            id = 1L,
            employeeCode = "EMP-101",
            fullName = "Aarav Verma",
            department = "Drone",
            designation = "Drone Engineer",
            bluetoothAddress = "AA:BB:CC:11:22:33",
            bluetoothDeviceName = "O2I-EMP-101"
        )
        val log = AttendanceLogEntity(
            id = 10L,
            employeeId = 1L,
            employeeCode = "EMP-101",
            employeeName = "Aarav Verma",
            department = "Drone",
            dateString = "2026-10-03",
            yearMonth = "2026-10",
            checkInTimestamp = 1791000000000L,
            checkOutTimestamp = 1791036000000L,
            checkInTimeFormatted = "09:00:00",
            checkOutTimeFormatted = "19:00:00",
            arrivalRssi = -58,
            exitRssi = -60,
            bluetoothMacUsed = "AA:BB:CC:11:22:33",
            detectionMethod = "BLUETOOTH_AUTO",
            status = "OVERTIME",
            lateMinutes = 0,
            workedHours = 10.0,
            regularHours = 9.0,
            overtimeHours = 1.0
        )
        val settings = PayrollSettingsEntity(
            standardWorkingDaysPerMonth = 26,
            standardDailyHours = 9.0
        )

        val report = AttendanceSummaryCalculator.calculateMonthlyAttendance(
            yearMonth = "2026-10",
            employees = listOf(emp),
            monthLogs = listOf(log),
            settings = settings
        )

        assertEquals(1, report.totalEmployees)
        val item = report.items.first()
        assertEquals(1, item.daysPresent)
        assertEquals(10.0, item.totalWorkedHours, 0.01)

        val xml = ExcelReportGenerator.buildExcelXmlWorkbook(
            yearMonth = "2026-10",
            employees = listOf(emp),
            logs = listOf(log),
            attendanceReport = report,
            settings = settings
        )
        assertTrue(xml.contains("<Workbook"))
        assertTrue(xml.contains("Daily_Attendance_2026-10"))
        assertTrue(xml.contains("Monthly_Report_2026-10"))
        assertTrue(xml.contains("Drone"))
    }
}
