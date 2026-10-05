package com.example.udparents.vista.pantallas

import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.udparents.viewmodel.VistaModeloApps
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

enum class TipoFecha { DESDE, HASTA }

fun formatoTiempo(ms: Long): String {
    val totalMin = ms / 1000 / 60
    val horas = totalMin / 60
    val minutos = totalMin % 60

    return when {
        horas > 0 -> "${horas}h ${minutos}m"
        minutos > 0 -> "${minutos}m"
        else -> "Menos de 1m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaReporteApps(
    listaHijos: List<Pair<String, String>>,
    vistaModelo: VistaModeloApps = viewModel(),
    onVolver: () -> Unit
) {
    val usos by vistaModelo.listaUsos.collectAsState()
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val calendarDesde = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    val calendarHasta = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
    }

    val fechaDesde = remember { mutableStateOf(calendarDesde.timeInMillis) }
    val fechaHasta = remember { mutableStateOf(calendarHasta.timeInMillis) }

    val fechaDesdeTexto = remember { mutableStateOf(dateFormat.format(Date(fechaDesde.value))) }
    val fechaHastaTexto = remember { mutableStateOf(dateFormat.format(Date(fechaHasta.value))) }

    var expandedHijos by remember { mutableStateOf(false) }
    var hijoSeleccionado by remember { mutableStateOf<Pair<String, String>?>(null) }

    var mostrarFicha by remember { mutableStateOf(true) } // SEmuestra ficha por defecto

    fun mostrarSelectorFecha(
        context: Context,
        tipo: TipoFecha,
        calendario: Calendar,
        onFechaSeleccionada: (Long, String) -> Unit
    ) {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                calendario.set(year, month, day)
                if (tipo == TipoFecha.DESDE) {
                    calendario.set(Calendar.HOUR_OF_DAY, 0)
                    calendario.set(Calendar.MINUTE, 0)
                    calendario.set(Calendar.SECOND, 0)
                    calendario.set(Calendar.MILLISECOND, 0)
                } else {
                    calendario.set(Calendar.HOUR_OF_DAY, 23)
                    calendario.set(Calendar.MINUTE, 59)
                    calendario.set(Calendar.SECOND, 59)
                    calendario.set(Calendar.MILLISECOND, 999)
                }
                val fechaMillis = calendario.timeInMillis
                onFechaSeleccionada(fechaMillis, dateFormat.format(Date(fechaMillis)))
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // --- NUEVA PALETA DE COLORES  ---
    val primaryDark = Color(0xFF000033) // Azul oscuro muy profundo (fondo principal)
    val primaryLight = Color(0xFF3F51B5) // Azul primario para TopAppBar, botón principal
    val accentColor = Color(0xFFCDDC39) // Verde lima vibrante para botones de acción y texto destacado
    val onPrimaryColor = Color.White // Texto sobre azules
    val surfaceColor = Color(0xFF2C387F) // Azul oscuro para tarjetas de apps
    val onSurfaceColor = Color(0xFFE8EAF6) // Texto claro sobre tarjetas de apps
    val borderColor = Color(0xFF9FA8DA) // Borde para OutlinedButtons

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte de Uso de Apps", color = onPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = onPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryLight, // Color de la barra superior
                    titleContentColor = onPrimaryColor // Color del título
                )
            )
        },
        containerColor = primaryDark // Color de fondo del Scaffold
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Selector de Hijo
            ExposedDropdownMenuBox(
                expanded = expandedHijos,
                onExpandedChange = { expandedHijos = !expandedHijos },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    readOnly = true,
                    value = hijoSeleccionado?.second ?: "Selecciona un hijo",
                    onValueChange = {},
                    label = { Text("Hijo", color = onSurfaceColor) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedHijos) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = surfaceColor,
                        unfocusedContainerColor = surfaceColor,
                        disabledContainerColor = surfaceColor,
                        focusedIndicatorColor = accentColor, // Indicador de foco en verde lima
                        unfocusedIndicatorColor = borderColor, // Indicador normal en azul claro
                        cursorColor = accentColor,
                        focusedTextColor = onPrimaryColor,
                        unfocusedTextColor = onPrimaryColor
                    )
                )

                ExposedDropdownMenu(
                    expanded = expandedHijos,
                    onDismissRequest = { expandedHijos = false },
                    modifier = Modifier.background(surfaceColor) // Fondo del menú desplegable
                ) {
                    listaHijos.forEach { hijo ->
                        DropdownMenuItem(
                            text = { Text(hijo.second, color = onPrimaryColor) },
                            onClick = {
                                hijoSeleccionado = hijo
                                expandedHijos = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Selectores de Fecha
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        mostrarSelectorFecha(context, TipoFecha.DESDE, calendarDesde) { millis, texto ->
                            fechaDesde.value = millis
                            fechaDesdeTexto.value = texto
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent, // Fondo transparente
                        contentColor = accentColor // Texto e icono en verde lima
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor)) // Borde en azul claro
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha desde")
                    Spacer(Modifier.width(4.dp))
                    Text("Desde: ${fechaDesdeTexto.value}")
                }

                OutlinedButton(
                    onClick = {
                        mostrarSelectorFecha(context, TipoFecha.HASTA, calendarHasta) { millis, texto ->
                            fechaHasta.value = millis
                            fechaHastaTexto.value = texto
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = accentColor
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor))
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha hasta")
                    Spacer(Modifier.width(4.dp))
                    Text("Hasta: ${fechaHastaTexto.value}")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Botón Consultar
            Button(
                onClick = {
                    hijoSeleccionado?.let { (id, _) ->
                        vistaModelo.cargarUsos(id, fechaDesde.value, fechaHasta.value)
                    }
                },
                enabled = hijoSeleccionado != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor, // Fondo en verde lima
                    contentColor = primaryDark // Texto en azul oscuro para contraste
                )
            ) {
                Text("Consultar Reporte")
            }

            Spacer(Modifier.height(16.dp))

            // Sección de Reporte
            if (usos.isEmpty()) {
                Text(
                    "No se encontraron datos de uso en el rango seleccionado para el hijo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceColor
                )
            } else {
                val totalMs = usos.sumOf { it.tiempoUso }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = primaryLight) // Tarjeta de resumen con azul primario
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = "Tiempo total", modifier = Modifier.size(24.dp), tint = onPrimaryColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tiempo total de uso: ${formatoTiempo(totalMs)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = onPrimaryColor // Texto blanco sobre azul
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Botón para alternar la vista de la ficha técnica
                OutlinedButton(
                    onClick = { mostrarFicha = !mostrarFicha },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = accentColor // Texto en verde lima
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor)) // Borde en azul claro
                ) {
                    Text(if (mostrarFicha) "Ocultar Detalles de Uso" else "Mostrar Detalles de Uso")
                }

                Spacer(Modifier.height(16.dp))

                // Lista de usos de aplicaciones (Ficha Técnica se mejoro xd)
                if (mostrarFicha) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        items(usos) { app ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = surfaceColor) // Tarjetas de app con azul oscuro
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val appIcon: Drawable? = remember(app.nombrePaquete) {
                                        try {
                                            context.packageManager.getApplicationIcon(app.nombrePaquete)
                                        } catch (e: PackageManager.NameNotFoundException) {
                                            null
                                        }
                                    }

                                    if (appIcon != null) {
                                        Image(
                                            bitmap = appIcon.toBitmap().asImageBitmap(),
                                            contentDescription = "Icono de ${app.nombreApp}",
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.nombreApp,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = onPrimaryColor // Texto blanco
                                        )
                                        Text(
                                            text = "(${app.nombrePaquete})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = onSurfaceColor
                                        )
                                        Text(
                                            text = "Fecha: ${dateFormat.format(Date(app.fechaUso))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = onSurfaceColor
                                        )
                                        Text(
                                            text = "Tiempo de uso: ${formatoTiempo(app.tiempoUso)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = onPrimaryColor // Texto blanco
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
