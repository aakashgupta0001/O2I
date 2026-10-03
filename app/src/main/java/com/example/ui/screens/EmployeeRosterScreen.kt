package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bluetooth.DiscoveredBluetoothSignal
import com.example.data.local.EmployeeEntity
import com.example.ui.theme.CyberCyan400
import com.example.ui.theme.EmeraldGreen400
import com.example.ui.theme.RoseAlert400
import com.example.ui.theme.SlateNavy900
import com.example.ui.theme.SlateNavy950
import java.util.Locale

val O2I_EMPLOYEE_CATEGORIES = listOf("Drone", "TDF", "Simulator", "Robotics", "PCB")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmployeeRosterScreen(
    employees: List<EmployeeEntity>,
    discoveredSignals: List<DiscoveredBluetoothSignal>,
    bondedDevices: List<DiscoveredBluetoothSignal>,
    openAddDialogInitially: Boolean = false,
    onConsumedInitialAddDialog: () -> Unit = {},
    onSaveEmployee: (EmployeeEntity) -> Unit,
    onDeleteEmployee: (Long, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var editingEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openAddDialogInitially) {
        if (openAddDialogInitially) {
            showAddDialog = true
            onConsumedInitialAddDialog()
        }
    }

    val filterCategories = remember { listOf("All") + O2I_EMPLOYEE_CATEGORIES }
    val filteredEmployees = remember(employees, searchQuery, selectedCategory) {
        employees.filter { emp ->
            val matchesCat = selectedCategory == "All" ||
                emp.department.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                emp.fullName.contains(searchQuery, ignoreCase = true) ||
                emp.employeeCode.contains(searchQuery, ignoreCase = true) ||
                emp.department.contains(searchQuery, ignoreCase = true) ||
                emp.bluetoothDeviceName.contains(searchQuery, ignoreCase = true) ||
                emp.bluetoothAddress.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEmployee = null
                    showAddDialog = true
                },
                containerColor = CyberCyan400,
                contentColor = SlateNavy950,
                modifier = Modifier.testTag("fab_add_employee")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Register Employee")
                    Text("Register Employee", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("employee_roster_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Card
            item {
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
                        Text(
                            text = "O2I Permanent Employee Roster (1-Time Setup)",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Register each employee only ONCE. Their Bluetooth MAC/Pass ID is saved permanently in O2I — every morning and evening when Admin Bluetooth is ON, their arrival and exit are logged automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "ACTIVE O2I STAFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan400
                                )
                                Text(
                                    text = "${employees.size} Employees",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "CATEGORIES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldGreen400
                                )
                                Text(
                                    text = "Drone • TDF • Simulator • Robotics • PCB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Search & Category Filter
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search name, Emp ID, category, Bluetooth MAC...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_employee")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filterCategories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) },
                                modifier = Modifier.testTag("filter_cat_$cat")
                            )
                        }
                    }
                }
            }

            if (filteredEmployees.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "No matching O2I employees in roster",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap 'Register Employee' to add staff in Drone, TDF, Simulator, Robotics, or PCB categories with their Bluetooth identifier.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    editingEmployee = null
                                    showAddDialog = true
                                }
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Register Employee")
                            }
                        }
                    }
                }
            } else {
                items(filteredEmployees, key = { it.id }) { emp ->
                    EmployeeRosterItemCard(
                        employee = emp,
                        onEdit = {
                            editingEmployee = emp
                            showAddDialog = true
                        },
                        onDelete = { onDeleteEmployee(emp.id, emp.fullName) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        val nextCode = remember(employees) {
            val nextNum = employees.size + 101
            "EMP-$nextNum"
        }
        EmployeeFormDialog(
            initialEmployee = editingEmployee,
            suggestedCode = nextCode,
            availableSignals = (discoveredSignals + bondedDevices).distinctBy { it.address.ifBlank { it.deviceName } },
            onDismiss = {
                showAddDialog = false
                editingEmployee = null
            },
            onSave = { savedEmp ->
                onSaveEmployee(savedEmp)
                showAddDialog = false
                editingEmployee = null
            }
        )
    }
}

@Composable
private fun EmployeeRosterItemCard(
    employee: EmployeeEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("roster_card_${employee.employeeCode}"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = employee.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = employee.employeeCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldGreen400.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = employee.department,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldGreen400,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${employee.designation} • Category: ${employee.department} • Permanent Daily Auto-Track",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("btn_edit_${employee.employeeCode}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Employee")
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("btn_delete_${employee.employeeCode}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Employee",
                            tint = RoseAlert400
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = CyberCyan400,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = buildString {
                            append(employee.bluetoothDeviceName.ifBlank { "O2I-${employee.employeeCode}" })
                            if (employee.bluetoothAddress.isNotBlank()) {
                                append(" [${employee.bluetoothAddress}]")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = EmeraldGreen400,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%02d:%02d - %02d:%02d",
                            employee.shiftStartHour,
                            employee.shiftStartMinute,
                            employee.shiftEndHour,
                            employee.shiftEndMinute
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldGreen400
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmployeeFormDialog(
    initialEmployee: EmployeeEntity?,
    suggestedCode: String,
    availableSignals: List<DiscoveredBluetoothSignal>,
    onDismiss: () -> Unit,
    onSave: (EmployeeEntity) -> Unit
) {
    var code by remember { mutableStateOf(initialEmployee?.employeeCode ?: suggestedCode) }
    var fullName by remember { mutableStateOf(initialEmployee?.fullName ?: "") }
    var category by remember { mutableStateOf(initialEmployee?.department ?: "Drone") }
    var designation by remember { mutableStateOf(initialEmployee?.designation ?: "Engineer") }
    var btAddress by remember { mutableStateOf(initialEmployee?.bluetoothAddress ?: "") }
    var btDeviceName by remember {
        mutableStateOf(initialEmployee?.bluetoothDeviceName ?: "O2I-$code")
    }
    var shiftStartHour by remember {
        mutableStateOf((initialEmployee?.shiftStartHour ?: 9).toString())
    }
    var shiftEndHour by remember {
        mutableStateOf((initialEmployee?.shiftEndHour ?: 18).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialEmployee == null) "Register O2I Employee" else "Edit O2I Employee")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text("Employee ID") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.42f)
                            .testTag("input_emp_code")
                    )
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.58f)
                            .testTag("input_emp_name")
                    )
                }

                Text("Employee Category:", style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    O2I_EMPLOYEE_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat },
                            label = { Text(cat) },
                            modifier = Modifier.testTag("select_cat_$cat")
                        )
                    }
                }

                OutlinedTextField(
                    value = designation,
                    onValueChange = { designation = it },
                    label = { Text("Role / Designation") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_emp_role")
                )

                OutlinedTextField(
                    value = btDeviceName,
                    onValueChange = { btDeviceName = it },
                    label = { Text("Bluetooth Device Name / BLE Pass ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_emp_bt_name")
                )

                OutlinedTextField(
                    value = btAddress,
                    onValueChange = { btAddress = it.uppercase() },
                    label = { Text("Bluetooth MAC (e.g. AA:BB:CC:11:22:33)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_emp_bt_mac")
                )

                if (availableSignals.isNotEmpty()) {
                    Text(
                        text = "Tap a detected/paired Bluetooth device to autofill:",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan400
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableSignals.take(5).forEach { sig ->
                            AssistChip(
                                onClick = {
                                    btAddress = sig.address
                                    btDeviceName = sig.deviceName
                                },
                                label = { Text("${sig.deviceName} (${sig.address.takeLast(8)})") }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = shiftStartHour,
                        onValueChange = { shiftStartHour = it },
                        label = { Text("Shift In Hour (0-23)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = shiftEndHour,
                        onValueChange = { shiftEndHour = it },
                        label = { Text("Shift Out Hour (0-23)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = fullName.trim().ifBlank { "Employee $code" }
                    val inHour = (shiftStartHour.toIntOrNull() ?: 9).coerceIn(0, 23)
                    val outHour = (shiftEndHour.toIntOrNull() ?: 18).coerceIn(0, 23)
                    onSave(
                        EmployeeEntity(
                            id = initialEmployee?.id ?: 0L,
                            employeeCode = code.trim().ifBlank { suggestedCode },
                            fullName = cleanName,
                            department = category.trim().ifBlank { "Drone" },
                            designation = designation.trim().ifBlank { "Engineer" },
                            bluetoothAddress = btAddress.trim(),
                            bluetoothDeviceName = btDeviceName.trim().ifBlank { "O2I-${code.trim()}" },
                            monthlyBaseSalary = 0.0,
                            shiftStartHour = inHour,
                            shiftStartMinute = 0,
                            shiftEndHour = outHour,
                            shiftEndMinute = 0
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen400,
                    contentColor = SlateNavy950
                ),
                modifier = Modifier.testTag("btn_confirm_save_employee")
            ) {
                Text("Save Employee", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
