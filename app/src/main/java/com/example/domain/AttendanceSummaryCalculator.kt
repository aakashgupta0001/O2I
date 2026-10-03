package com.example.domain

import com.example.data.local.AttendanceLogEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.PayrollSettingsEntity
import kotlin.math.max
import kotlin.math.roundToInt

data class EmployeeMonthlyAttendanceItem(
    val employee: EmployeeEntity,
    val yearMonth: String,
    val standardWorkingDays: Int,
    val daysPresent: Int,
    val daysLate: Int,
    val daysHalfDay: Int,
    val daysOvertime: Int,
    val daysAbsent: Int,
    val attendancePercentage: Double,
    val totalWorkedHours: Double,
    val totalRegularHours: Double,
    val totalOvertimeHours: Double,
    val attendanceLogs: List<AttendanceLogEntity>
)

data class MonthlyAttendanceReport(
    val yearMonth: String,
    val companyName: String,
    val standardWorkingDays: Int,
    val totalEmployees: Int,
    val totalPresentDays: Int,
    val totalLateArrivals: Int,
    val totalHoursLogged: Double,
    val totalOvertimeHours: Double,
    val items: List<EmployeeMonthlyAttendanceItem>,
    val categoryHoursTotals: Map<String, Double>
)

object AttendanceSummaryCalculator {

    fun calculateMonthlyAttendance(
        yearMonth: String,
        employees: List<EmployeeEntity>,
        monthLogs: List<AttendanceLogEntity>,
        settings: PayrollSettingsEntity
    ): MonthlyAttendanceReport {
        val workingDays = max(1, settings.standardWorkingDaysPerMonth)
        val logsByEmployee = monthLogs.groupBy { it.employeeId }

        val summaryItems = employees.map { emp ->
            val empLogs = logsByEmployee[emp.id].orEmpty().sortedBy { it.dateString }
            val daysPresent = empLogs.size
            val daysLate = empLogs.count { it.lateMinutes > 0 || it.status == "LATE" }
            val daysHalfDay = empLogs.count { it.status == "HALF_DAY" }
            val daysOvertime = empLogs.count { it.overtimeHours > 0.0 || it.status == "OVERTIME" }
            val daysAbsent = max(0, workingDays - daysPresent)

            val attendancePct = round2((daysPresent.toDouble() / workingDays.toDouble()) * 100.0)
                .coerceIn(0.0, 100.0)

            val totalWorkedHours = round2(empLogs.sumOf { it.workedHours })
            val totalRegularHours = round2(empLogs.sumOf { it.regularHours })
            val totalOvertimeHours = round2(empLogs.sumOf { it.overtimeHours })

            EmployeeMonthlyAttendanceItem(
                employee = emp,
                yearMonth = yearMonth,
                standardWorkingDays = workingDays,
                daysPresent = daysPresent,
                daysLate = daysLate,
                daysHalfDay = daysHalfDay,
                daysOvertime = daysOvertime,
                daysAbsent = daysAbsent,
                attendancePercentage = attendancePct,
                totalWorkedHours = totalWorkedHours,
                totalRegularHours = totalRegularHours,
                totalOvertimeHours = totalOvertimeHours,
                attendanceLogs = empLogs
            )
        }

        val catTotals = summaryItems
            .groupBy { it.employee.department.ifBlank { "Drone" } }
            .mapValues { (_, list) -> round2(list.sumOf { it.totalWorkedHours }) }

        return MonthlyAttendanceReport(
            yearMonth = yearMonth,
            companyName = settings.companyName,
            standardWorkingDays = workingDays,
            totalEmployees = employees.size,
            totalPresentDays = summaryItems.sumOf { it.daysPresent },
            totalLateArrivals = summaryItems.sumOf { it.daysLate },
            totalHoursLogged = round2(summaryItems.sumOf { it.totalWorkedHours }),
            totalOvertimeHours = round2(summaryItems.sumOf { it.totalOvertimeHours }),
            items = summaryItems,
            categoryHoursTotals = catTotals
        )
    }

    private fun round2(value: Double): Double =
        (value * 100.0).roundToInt() / 100.0
}
