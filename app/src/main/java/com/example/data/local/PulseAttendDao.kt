package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PulseAttendDao {

    // Employees
    @Query("SELECT * FROM employees WHERE isActive = 1 ORDER BY fullName ASC")
    fun observeActiveEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE isActive = 1 ORDER BY fullName ASC")
    suspend fun getActiveEmployeesSnapshot(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getEmployeeById(id: Long): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE UPPER(employeeCode) = UPPER(:code) LIMIT 1")
    suspend fun getEmployeeByCode(code: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: EmployeeEntity): Long

    @Update
    suspend fun updateEmployee(employee: EmployeeEntity)

    @Query("DELETE FROM employees WHERE id = :employeeId")
    suspend fun deleteEmployeeById(employeeId: Long)

    // Attendance Logs
    @Query("SELECT * FROM attendance_logs WHERE dateString = :dateString ORDER BY checkInTimestamp DESC")
    fun observeLogsForDate(dateString: String): Flow<List<AttendanceLogEntity>>

    @Query("SELECT * FROM attendance_logs WHERE yearMonth = :yearMonth ORDER BY dateString DESC, checkInTimestamp DESC")
    fun observeLogsForMonth(yearMonth: String): Flow<List<AttendanceLogEntity>>

    @Query("SELECT * FROM attendance_logs WHERE yearMonth = :yearMonth ORDER BY dateString ASC, checkInTimestamp ASC")
    suspend fun getLogsForMonthSnapshot(yearMonth: String): List<AttendanceLogEntity>

    @Query("SELECT * FROM attendance_logs ORDER BY checkInTimestamp DESC")
    fun observeAllLogs(): Flow<List<AttendanceLogEntity>>

    @Query("SELECT * FROM attendance_logs WHERE employeeId = :employeeId AND dateString = :dateString LIMIT 1")
    suspend fun getEmployeeLogForDate(employeeId: Long, dateString: String): AttendanceLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceLog(log: AttendanceLogEntity): Long

    @Update
    suspend fun updateAttendanceLog(log: AttendanceLogEntity)

    @Query("DELETE FROM attendance_logs WHERE id = :logId")
    suspend fun deleteAttendanceLogById(logId: Long)

    // Settings
    @Query("SELECT * FROM payroll_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<PayrollSettingsEntity?>

    @Query("SELECT * FROM payroll_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSnapshot(): PayrollSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: PayrollSettingsEntity)
}
