package com.example.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

sealed class GateRegistrationResult {
    data class ArrivalRegistered(
        val employee: EmployeeEntity,
        val log: AttendanceLogEntity
    ) : GateRegistrationResult()

    data class ExitRegistered(
        val employee: EmployeeEntity,
        val log: AttendanceLogEntity
    ) : GateRegistrationResult()

    data class AlreadyCompletedToday(
        val employee: EmployeeEntity,
        val log: AttendanceLogEntity
    ) : GateRegistrationResult()

    data class CooldownActive(
        val employee: EmployeeEntity,
        val remainingSeconds: Long
    ) : GateRegistrationResult()
}

class AttendanceRepository(private val dao: PulseAttendDao) {

    val activeEmployees: Flow<List<EmployeeEntity>> = dao.observeActiveEmployees()
    val allLogs: Flow<List<AttendanceLogEntity>> = dao.observeAllLogs()
    val settings: Flow<PayrollSettingsEntity> = dao.observeSettings().map {
        it ?: PayrollSettingsEntity()
    }

    fun observeLogsForDate(dateString: String): Flow<List<AttendanceLogEntity>> =
        dao.observeLogsForDate(dateString)

    fun observeLogsForMonth(yearMonth: String): Flow<List<AttendanceLogEntity>> =
        dao.observeLogsForMonth(yearMonth)

    suspend fun getActiveEmployeesSnapshot(): List<EmployeeEntity> =
        dao.getActiveEmployeesSnapshot()

    suspend fun getLogsForMonthSnapshot(yearMonth: String): List<AttendanceLogEntity> =
        dao.getLogsForMonthSnapshot(yearMonth)

    suspend fun getSettingsSnapshot(): PayrollSettingsEntity =
        dao.getSettingsSnapshot() ?: PayrollSettingsEntity().also { dao.saveSettings(it) }

    suspend fun saveSettings(settings: PayrollSettingsEntity) {
        dao.saveSettings(settings)
    }

    suspend fun saveEmployee(employee: EmployeeEntity): Long {
        return if (employee.id == 0L) {
            dao.insertEmployee(employee)
        } else {
            dao.updateEmployee(employee)
            employee.id
        }
    }

    suspend fun deleteEmployee(employeeId: Long) {
        dao.deleteEmployeeById(employeeId)
    }

    suspend fun deleteAttendanceLog(logId: Long) {
        dao.deleteAttendanceLogById(logId)
    }

    suspend fun getEmployeeLogForDate(employeeId: Long, dateString: String): AttendanceLogEntity? =
        dao.getEmployeeLogForDate(employeeId, dateString)

    /**
     * Core Automatic & Gate Attendance Engine:
     * Registers either Arrival (Morning Check-In) or Exit (Evening Check-Out) for an employee.
     * Supports gateMode:
     * - "MORNING_ARRIVAL": Forces arrival check-in if not checked in yet.
     * - "EVENING_EXIT": Forces exit check-out if already checked in.
     * - "SMART_AUTO": Automatically logs arrival on first detection of the day, and logs exit
     *   when detected again after `minMinutesBeforeExit` cooldown.
     */
    suspend fun processEmployeeGateEvent(
        employee: EmployeeEntity,
        rssi: Int?,
        detectionMethod: String,
        overrideAction: String? = null, // "ARRIVAL", "EXIT", or null for gateMode
        eventTimeMillis: Long = System.currentTimeMillis()
    ): GateRegistrationResult {
        val currentSettings = getSettingsSnapshot()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        val dateObj = Date(eventTimeMillis)
        val dateString = dateFormat.format(dateObj)
        val yearMonth = monthFormat.format(dateObj)
        val formattedTime = timeFormat.format(dateObj)

        val existingLog = dao.getEmployeeLogForDate(employee.id, dateString)
        val effectiveMode = overrideAction ?: currentSettings.gateMode

        if (existingLog == null) {
            // No attendance record for today yet -> Register Morning Arrival
            if (effectiveMode == "EVENING_EXIT") {
                // Even if gate is in Evening Exit mode, if employee has no arrival yet, create arrival first
                // so no employee is left without a record.
            }

            val cal = Calendar.getInstance().apply { timeInMillis = eventTimeMillis }
            val shiftStartCal = Calendar.getInstance().apply {
                timeInMillis = eventTimeMillis
                set(Calendar.HOUR_OF_DAY, employee.shiftStartHour)
                set(Calendar.MINUTE, employee.shiftStartMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMinutes = ((cal.timeInMillis - shiftStartCal.timeInMillis) / 60000L).toInt()
            val lateMinutes = max(0, diffMinutes)
            val isLate = diffMinutes > currentSettings.gracePeriodMinutes
            val initialStatus = if (isLate) "LATE" else "ON_TIME"

            val newLog = AttendanceLogEntity(
                employeeId = employee.id,
                employeeCode = employee.employeeCode,
                employeeName = employee.fullName,
                department = employee.department,
                dateString = dateString,
                yearMonth = yearMonth,
                checkInTimestamp = eventTimeMillis,
                checkOutTimestamp = null,
                checkInTimeFormatted = formattedTime,
                checkOutTimeFormatted = null,
                arrivalRssi = rssi,
                exitRssi = null,
                bluetoothMacUsed = employee.bluetoothAddress.ifBlank { employee.bluetoothDeviceName },
                detectionMethod = detectionMethod,
                status = initialStatus,
                lateMinutes = if (isLate) lateMinutes else 0,
                workedHours = 0.0,
                regularHours = 0.0,
                overtimeHours = 0.0,
                notes = if (isLate) {
                    "Arrived $lateMinutes min after shift (${formatHourMin(employee.shiftStartHour, employee.shiftStartMinute)})"
                } else {
                    "Auto-registered arrival on time via $detectionMethod"
                }
            )
            val id = dao.insertAttendanceLog(newLog)
            val saved = newLog.copy(id = id)
            return GateRegistrationResult.ArrivalRegistered(employee, saved)
        } else if (existingLog.checkOutTimestamp == null) {
            // Employee already checked in today and has not exited yet!
            if (effectiveMode == "MORNING_ARRIVAL" && overrideAction == null) {
                // In strict Morning Arrival mode, don't accidentally check them out
                return GateRegistrationResult.CooldownActive(employee, 0L)
            }

            val elapsedMillis = eventTimeMillis - existingLog.checkInTimestamp
            val minCooldownMillis = if (overrideAction == "EXIT" || effectiveMode == "EVENING_EXIT") {
                5_000L // 5 seconds minimum when explicitly in Exit mode or manual exit
            } else {
                currentSettings.minMinutesBeforeExit * 60_000L
            }

            if (elapsedMillis < minCooldownMillis) {
                val remainingSec = (minCooldownMillis - elapsedMillis) / 1000L
                return GateRegistrationResult.CooldownActive(employee, max(1L, remainingSec))
            }

            // Calculate worked hours, regular hours, and overtime hours
            val rawHours = elapsedMillis.toDouble() / (1000.0 * 3600.0)
            // Round to 2 decimal places
            val workedHours = (rawHours * 100.0).roundToInt() / 100.0
            val shiftStandardHours = currentSettings.standardDailyHours
            val regularHours = (minOf(workedHours, shiftStandardHours) * 100.0).roundToInt() / 100.0
            val overtimeHours = (max(0.0, workedHours - shiftStandardHours) * 100.0).roundToInt() / 100.0

            val updatedStatus = when {
                overtimeHours >= 0.5 -> "OVERTIME"
                workedHours < (shiftStandardHours * 0.55) && workedHours > 0.1 -> "HALF_DAY"
                else -> existingLog.status
            }

            val updatedLog = existingLog.copy(
                checkOutTimestamp = eventTimeMillis,
                checkOutTimeFormatted = formattedTime,
                exitRssi = rssi,
                workedHours = workedHours,
                regularHours = regularHours,
                overtimeHours = overtimeHours,
                status = updatedStatus,
                notes = buildString {
                    if (existingLog.notes.isNotBlank()) {
                        append(existingLog.notes)
                        append(" | ")
                    }
                    append("Exit registered ($formattedTime, ${String.format(Locale.US, "%.2f", workedHours)}h worked)")
                }
            )
            dao.updateAttendanceLog(updatedLog)
            return GateRegistrationResult.ExitRegistered(employee, updatedLog)
        } else {
            // If explicitly overridden with EXIT, update exit time to latest departure
            if (overrideAction == "EXIT" || effectiveMode == "EVENING_EXIT") {
                val elapsedMillis = max(0L, eventTimeMillis - existingLog.checkInTimestamp)
                val rawHours = elapsedMillis.toDouble() / (1000.0 * 3600.0)
                val workedHours = (rawHours * 100.0).roundToInt() / 100.0
                val shiftStandardHours = currentSettings.standardDailyHours
                val regularHours = (minOf(workedHours, shiftStandardHours) * 100.0).roundToInt() / 100.0
                val overtimeHours = (max(0.0, workedHours - shiftStandardHours) * 100.0).roundToInt() / 100.0

                val updatedLog = existingLog.copy(
                    checkOutTimestamp = eventTimeMillis,
                    checkOutTimeFormatted = formattedTime,
                    exitRssi = rssi,
                    workedHours = workedHours,
                    regularHours = regularHours,
                    overtimeHours = overtimeHours
                )
                dao.updateAttendanceLog(updatedLog)
                return GateRegistrationResult.ExitRegistered(employee, updatedLog)
            }
            return GateRegistrationResult.AlreadyCompletedToday(employee, existingLog)
        }
    }

    /**
     * Allows HR/Admin to manually add or edit a full attendance log row (for backdated or corrected entries).
     */
    suspend fun upsertManualAttendanceLog(log: AttendanceLogEntity) {
        if (log.id == 0L) {
            dao.insertAttendanceLog(log)
        } else {
            dao.updateAttendanceLog(log)
        }
    }

    private fun formatHourMin(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour, minute)
}
