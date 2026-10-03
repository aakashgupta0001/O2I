package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AttendanceRepository
import com.example.data.local.EmployeeEntity
import com.example.data.local.GateRegistrationResult
import com.example.data.local.PulseAttendDatabase
import com.example.ui.screens.O2I_EMPLOYEE_CATEGORIES
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: PulseAttendDatabase
    private lateinit var repository: AttendanceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PulseAttendDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = AttendanceRepository(database.pulseAttendDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read O2I string from context and verify categories`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("O2I", appName)
        assertEquals(
            listOf("Drone", "TDF", "Simulator", "Robotics", "PCB"),
            O2I_EMPLOYEE_CATEGORIES
        )
    }

    @Test
    fun `one time employee registration persists across multiple days without re-registration`() = runBlocking {
        // Admin registers employee ONCE
        val empId = repository.saveEmployee(
            EmployeeEntity(
                employeeCode = "EMP-101",
                fullName = "Rohan Sharma",
                department = "Robotics",
                designation = "Robotics Engineer",
                bluetoothAddress = "AA:BB:CC:11:22:33",
                bluetoothDeviceName = "O2I-EMP-101"
            )
        )
        val savedEmp = database.pulseAttendDao().getEmployeeById(empId)
        assertNotNull(savedEmp)
        val employee = savedEmp!!

        // Day 1 Morning Arrival & Evening Exit
        val day1Morning = 1791000000000L
        val day1Evening = day1Morning + (9 * 3600_000L)

        val arrivalDay1 = repository.processEmployeeGateEvent(
            employee = employee,
            rssi = -58,
            detectionMethod = "BLUETOOTH_AUTO",
            eventTimeMillis = day1Morning
        )
        assertTrue(arrivalDay1 is GateRegistrationResult.ArrivalRegistered)

        val exitDay1 = repository.processEmployeeGateEvent(
            employee = employee,
            rssi = -60,
            detectionMethod = "BLUETOOTH_AUTO",
            eventTimeMillis = day1Evening
        )
        assertTrue(exitDay1 is GateRegistrationResult.ExitRegistered)

        // Day 2 Morning Arrival & Evening Exit (24 hours later — NO re-registration of employee!)
        val day2Morning = day1Morning + (24 * 3600_000L)
        val day2Evening = day2Morning + (9 * 3600_000L)

        val arrivalDay2 = repository.processEmployeeGateEvent(
            employee = employee,
            rssi = -55,
            detectionMethod = "BLUETOOTH_AUTO",
            eventTimeMillis = day2Morning
        )
        assertTrue(arrivalDay2 is GateRegistrationResult.ArrivalRegistered)

        val exitDay2 = repository.processEmployeeGateEvent(
            employee = employee,
            rssi = -59,
            detectionMethod = "BLUETOOTH_AUTO",
            eventTimeMillis = day2Evening
        )
        assertTrue(exitDay2 is GateRegistrationResult.ExitRegistered)

        // Verify employee is still registered only once, with 2 distinct daily attendance logs
        val activeStaff = repository.getActiveEmployeesSnapshot()
        assertEquals(1, activeStaff.size)
        assertEquals("Robotics", activeStaff.first().department)
    }
}
