package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_logs",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["employeeId", "dateString"], unique = true),
        Index(value = ["yearMonth"]),
        Index(value = ["dateString"])
    ]
)
data class AttendanceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val employeeCode: String,
    val employeeName: String,
    val department: String,
    val dateString: String, // "YYYY-MM-DD"
    val yearMonth: String, // "YYYY-MM"
    val checkInTimestamp: Long,
    val checkOutTimestamp: Long? = null,
    val checkInTimeFormatted: String, // "HH:mm:ss"
    val checkOutTimeFormatted: String? = null, // "HH:mm:ss"
    val arrivalRssi: Int? = null, // Bluetooth signal strength in dBm at arrival
    val exitRssi: Int? = null, // Bluetooth signal strength in dBm at exit
    val bluetoothMacUsed: String = "",
    val detectionMethod: String = "BLUETOOTH_AUTO", // BLUETOOTH_AUTO, BLE_BEACON_AUTO, ADMIN_GATE_MANUAL
    val status: String = "ON_TIME", // ON_TIME, LATE, HALF_DAY, OVERTIME
    val lateMinutes: Int = 0,
    val workedHours: Double = 0.0,
    val regularHours: Double = 0.0,
    val overtimeHours: Double = 0.0,
    val notes: String = ""
)
