package com.example.udparents.vista.pantallas

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.udparents.modelo.RestriccionHorario
import com.example.udparents.viewmodel.VistaModeloApps
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Función de ayuda para formatear los milisegundos del día a una cadena de tiempo (HH:mm).
 */
private fun formatMillisOfDay(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds % 3600) / 60
    return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
}

/**
 * Composable principal para la pantalla de programación de restricciones.
 * Permite a los padres configurar y gestionar restricciones de horario para sus hijos.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PantallaProgramarRestricciones(
    uidHijo: String,
    nombreHijo: String,
    vistaModelo: VistaModeloApps = viewModel(),
    onVolver: () -> Unit
) {
    val restricciones by vistaModelo.restriccionesHorario.collectAsState()
    val usosApps by vistaModelo.listaUsos.collectAsState()
    val context = LocalContext.current

    // Cargar restricciones y apps del hijo seleccionado cuando cambie
    LaunchedEffect(uidHijo) {
        vistaModelo.cargarRestriccionesHorario(uidHijo)
        val ahora = System.currentTimeMillis()
        val inicioHoy = ahora - (ahora % (24 * 60 * 60 * 1000))
        vistaModelo.cargarUsos(uidHijo, inicioHoy, ahora)
    }

    // Estado para el diálogo de añadir/editar restricción
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingRestriction by remember { mutableStateOf<RestriccionHorario?>(null) }


    // Paleta de colores
    val primaryDark = Color(0xFF1A237E)
    val primaryLight = Color(0xFF3F51B5)
    val accentColor = Color(0xFFCDDC39)
    val onPrimaryColor = Color.White
    val surfaceColor = Color(0xFF2C387F)
    val onSurfaceColor = Color(0xFFE8EAF6)
    val borderColor = Color(0xFF9FA8DA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Restricciones para $nombreHijo",
                        color = onPrimaryColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = onPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryLight,
                    titleContentColor = onPrimaryColor
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingRestriction = null
                    showAddEditDialog = true
                },
                containerColor = accentColor,
                contentColor = primaryDark
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir Restricción")
            }
        },
        containerColor = primaryDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp) // Pading mejorado
        ) {
            if (restricciones.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No hay restricciones de horario configuradas para $nombreHijo.\nPulsa '+' para añadir una.",
                        color = onSurfaceColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { // Espacio entre tarjetas
                    items(restricciones) { restriccion ->
                        RestriccionHorarioCard(
                            restriccion = restriccion,
                            onEdit = {
                                editingRestriction = it
                                showAddEditDialog = true
                            },
                            onDelete = {
                                vistaModelo.eliminarRestriccionHorario(uidHijo, it.id)
                            },
                            onToggleEnabled = {
                                vistaModelo.guardarRestriccionHorario(uidHijo, it.copy(isEnabled = !it.isEnabled))
                            },
                            primaryDark = primaryDark,
                            primaryLight = primaryLight,
                            accentColor = accentColor,
                            onPrimaryColor = onPrimaryColor,
                            surfaceColor = surfaceColor,
                            onSurfaceColor = onSurfaceColor,
                            borderColor = borderColor
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditRestriccionDialog(
            restriccion = editingRestriction,
            onDismiss = { showAddEditDialog = false },
            onSave = { newRestriction ->
                vistaModelo.guardarRestriccionHorario(uidHijo, newRestriction)
                showAddEditDialog = false
            },
            appsList = usosApps.map { it.nombreApp to it.nombrePaquete }.distinctBy { it.second }.sortedBy { it.first },
            primaryDark = primaryDark,
            primaryLight = primaryLight,
            accentColor = accentColor,
            onPrimaryColor = onPrimaryColor,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            borderColor = borderColor
        )
    }
}

/**
 * Composable para mostrar una tarjeta individual de restricción de horario.
 */
@Composable
fun RestriccionHorarioCard(
    restriccion: RestriccionHorario,
    onEdit: (RestriccionHorario) -> Unit,
    onDelete: (RestriccionHorario) -> Unit,
    onToggleEnabled: (RestriccionHorario) -> Unit,
    primaryDark: Color,
    primaryLight: Color,
    accentColor: Color,
    onPrimaryColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    borderColor: Color
) {
    // Mapeo de los enteros de Calendar a nombres de días abreviados
    val daysMap = mapOf(
        Calendar.MONDAY to "Lun", Calendar.TUESDAY to "Mar", Calendar.WEDNESDAY to "Mié",
        Calendar.THURSDAY to "Jue", Calendar.FRIDAY to "Vie", Calendar.SATURDAY to "Sáb", Calendar.SUNDAY to "Dom"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // Añade un poco de sombra
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Column(Modifier.padding(16.dp)) { // Pading interno aumentado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = restriccion.ruleName.ifEmpty { "Regla sin nombre" },
                    style = MaterialTheme.typography.titleMedium,
                    color = onPrimaryColor
                )
                Switch(
                    checked = restriccion.isEnabled,
                    onCheckedChange = { onToggleEnabled(restriccion) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accentColor,
                        checkedTrackColor = accentColor.copy(alpha = 0.5f),
                        uncheckedThumbColor = onSurfaceColor,
                        uncheckedTrackColor = onSurfaceColor.copy(alpha = 0.5f)
                    )
                )
            }
            Spacer(Modifier.height(8.dp)) // Espaciado
            Text(
                text = "App: ${restriccion.appName.ifEmpty { "Todas las Apps" }}",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceColor
            )
            val startTimeStr = formatMillisOfDay(restriccion.startTimeMillis)
            val endTimeStr = formatMillisOfDay(restriccion.endTimeMillis)
            Text(
                text = "Horario: $startTimeStr - $endTimeStr",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceColor
            )
            Text(
                text = "Días: ${restriccion.daysOfWeek.map { daysMap[it] ?: "" }.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceColor
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onEdit(restriccion) }) {
                    Text("Editar", color = accentColor)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onDelete(restriccion) }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Composable para el diálogo de añadir o editar una restricción.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditRestriccionDialog(
    restriccion: RestriccionHorario?,
    onDismiss: () -> Unit,
    onSave: (RestriccionHorario) -> Unit,
    appsList: List<Pair<String, String>>,
    primaryDark: Color,
    primaryLight: Color,
    accentColor: Color,
    onPrimaryColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    borderColor: Color
) {
    var ruleName by remember { mutableStateOf(restriccion?.ruleName ?: "") }
    var selectedAppPackage by remember { mutableStateOf(restriccion?.packageName ?: "ALL_APPS") }
    var selectedAppName by remember { mutableStateOf(restriccion?.appName ?: "Todas las Apps") }
    var startTime by remember { mutableStateOf(restriccion?.startTimeMillis ?: 0L) }
    var endTime by remember { mutableStateOf(restriccion?.endTimeMillis ?: 0L) }
    val selectedDays = remember { mutableStateListOf(*restriccion?.daysOfWeek?.toTypedArray() ?: emptyArray()) }
    var expandedAppDropdown by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val daysOfWeekOptions = listOf(
        Calendar.MONDAY to "Lunes", Calendar.TUESDAY to "Martes", Calendar.WEDNESDAY to "Miércoles",
        Calendar.THURSDAY to "Jueves", Calendar.FRIDAY to "Viernes", Calendar.SATURDAY to "Sábado", Calendar.SUNDAY to "Domingo"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (restriccion == null) "Añadir Restricción" else "Editar Restricción", color = onPrimaryColor) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Nombre de la Regla (Opcional)", color = onSurfaceColor) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = borderColor,
                        cursorColor = accentColor,
                        focusedTextColor = onPrimaryColor,
                        unfocusedTextColor = onPrimaryColor,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = onSurfaceColor
                    )
                )
                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedAppDropdown,
                    onExpandedChange = { expandedAppDropdown = !expandedAppDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedAppName,
                        onValueChange = {},
                        label = { Text("Aplicación", color = onSurfaceColor) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAppDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = borderColor,
                            cursorColor = accentColor,
                            focusedTextColor = onPrimaryColor,
                            unfocusedTextColor = onPrimaryColor,
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = onSurfaceColor
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expandedAppDropdown,
                        onDismissRequest = { expandedAppDropdown = false },
                        modifier = Modifier.background(surfaceColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Todas las Apps", color = onPrimaryColor) },
                            onClick = {
                                selectedAppPackage = "ALL_APPS"
                                selectedAppName = "Todas las Apps"
                                expandedAppDropdown = false
                            }
                        )
                        appsList.forEach { (appName, packageName) ->
                            DropdownMenuItem(
                                text = { Text(appName, color = onPrimaryColor) },
                                onClick = {
                                    selectedAppPackage = packageName
                                    selectedAppName = appName
                                    expandedAppDropdown = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Horario:", style = MaterialTheme.typography.titleSmall, color = onPrimaryColor)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, (startTime / (60 * 60 * 1000)).toInt())
                                set(Calendar.MINUTE, ((startTime % (60 * 60 * 1000)) / (60 * 1000)).toInt())
                            }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    startTime = (hour * 60 * 60 * 1000 + minute * 60 * 1000).toLong()
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                true
                            ).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = accentColor
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor))
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = "Hora inicio")
                        Spacer(Modifier.width(4.dp))
                        Text("Inicio: ${formatMillisOfDay(startTime)}")
                    }
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, (endTime / (60 * 60 * 1000)).toInt())
                                set(Calendar.MINUTE, ((endTime % (60 * 60 * 1000)) / (60 * 1000)).toInt())
                            }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    endTime = (hour * 60 * 60 * 1000 + minute * 60 * 1000).toLong()
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                true
                            ).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = accentColor
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor))
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = "Hora fin")
                        Spacer(Modifier.width(4.dp))
                        Text("Fin: ${formatMillisOfDay(endTime)}")
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Días de la Semana:", style = MaterialTheme.typography.titleSmall, color = onPrimaryColor)
                // Esta es la parte que organiza los días de forma óptima
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), // Espaciado
                    horizontalArrangement = Arrangement.spacedBy(8.dp) // Espacio entre chips
                ) {
                    daysOfWeekOptions.forEach { (dayInt, dayName) ->
                        FilterChip(
                            selected = selectedDays.contains(dayInt),
                            onClick = {
                                if (selectedDays.contains(dayInt)) {
                                    selectedDays.remove(dayInt)
                                } else {
                                    selectedDays.add(dayInt)
                                }
                            },
                            enabled = true,
                            label = { Text(dayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = primaryDark,
                                containerColor = surfaceColor,
                                labelColor = onSurfaceColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedDays.contains(dayInt),
                                borderColor = if (selectedDays.contains(dayInt)) accentColor else borderColor
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newId = restriccion?.id ?: UUID.randomUUID().toString()
                    val newRestriction = RestriccionHorario(
                        id = newId,
                        ruleName = ruleName,
                        packageName = selectedAppPackage,
                        appName = selectedAppName,
                        startTimeMillis = startTime,
                        endTimeMillis = endTime,
                        daysOfWeek = selectedDays.toList(),
                        isEnabled = restriccion?.isEnabled ?: true
                    )
                    onSave(newRestriction)
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = primaryDark)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = onPrimaryColor),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor))
            ) {
                Text("Cancelar")

            }
        },
        containerColor = primaryDark
    )
}
