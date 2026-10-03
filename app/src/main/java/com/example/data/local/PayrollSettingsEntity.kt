package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payroll_settings")
data class PayrollSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val companyName: String = "O2I",
    val officeGateName: String = "O2I Main Bluetooth Gate",
    val gateMode: String = "SMART_AUTO", // SMART_AUTO, MORNING_ARRIVAL, EVENING_EXIT
    val rssiThresholdDbm: Int = -85, // Minimum RSSI (e.g. -85 dBm) to auto-trigger check-in/out
    val gracePeriodMinutes: Int = 15, // Minutes after shiftStart before marking LATE
    val minMinutesBeforeExit: Int = 1, // Minimum minutes after arrival before Smart Auto logs an exit
    val standardWorkingDaysPerMonth: Int = 26,
    val standardDailyHours: Double = 9.0,
    val pfTaxDeductionPercent: Double = 10.0,
    val latePenaltyPerThreeLatesDays: Double = 0.5, // 0.5 day salary deduction per 3 late arrivals
    val currencySymbol: String = "₹",
    val autoSyncExcelOnEvent: Boolean = true,
    val lastExcelSyncTimestamp: Long? = null,
    val lastExcelFilePath: String = ""
)
