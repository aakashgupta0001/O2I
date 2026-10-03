package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employees",
    indices = [
        Index(value = ["employeeCode"], unique = true),
        Index(value = ["bluetoothAddress"])
    ]
)
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeCode: String,
    val fullName: String,
    val department: String,
    val designation: String,
    val bluetoothAddress: String, // e.g., "AA:BB:CC:11:22:33" or empty if matched by BLE Beacon / Device Name
    val bluetoothDeviceName: String, // e.g., "O2I-EMP-101"
    val monthlyBaseSalary: Double = 0.0,
    val shiftStartHour: Int = 9,
    val shiftStartMinute: Int = 0,
    val shiftEndHour: Int = 18,
    val shiftEndMinute: Int = 0,
    val overtimeMultiplier: Double = 1.5,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
