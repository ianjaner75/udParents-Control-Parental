package com.example.udparents.vista.pantallas

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.udparents.viewmodel.VistaModeloApps
import kotlinx.coroutines.delay
import kotlin.math.max
import com.example.udparents.modelo.AppUso
import java.util.Calendar

// =================================================================================================
// PANTALLA: Control de Aplicaciones
// HU-009, HU-010 y HU-011
// Muestra el uso de apps, permite establecer límites y bloquear/desbloquear.
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaControlApps(
    uidHijo: String,
    vistaModeloApps: VistaModeloApps = viewModel()
) {
    val usosApps by vistaModeloApps.listaUsos.collectAsState()
    val estadosBloqueo by vistaModeloApps.estadoBloqueoApp.collectAsState()
    val limitesApps by vistaModeloApps.limitesApp.collectAsState()
    val context = LocalContext.current

    val appsAgregadas by remember(usosApps) {
        derivedStateOf {
            usosApps
                .groupBy { it.nombrePaquete }
                .map { (paquete, registros) ->
                    val totalTiempoUso = registros.sumOf { it.tiempoUso }
                    registros.first().copy(tiempoUso = totalTiempoUso)
                }
                .sortedByDescending { it.tiempoUso }
        }
    }

    val tiempoTotalPantalla by remember(appsAgregadas) {
        derivedStateOf {
            appsAgregadas.sumOf { it.tiempoUso }
        }
    }

    // Función para obtener el inicio del día en la zona horaria local
    fun startOfTodayLocal(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // REVISADO: Carga inicial de usos y límites usando la hora local
    LaunchedEffect(uidHijo) {
        val inicioHoy = startOfTodayLocal()
        val ahora = System.currentTimeMillis()
        vistaModeloApps.cargarUsos(uidHijo, inicioHoy, ahora)
        vistaModeloApps.cargarLimites(uidHijo)
    }

    // REVISADO: Bucle para actualizar usos cada minuto usando la hora local
    LaunchedEffect(uidHijo) {
        while (true) {
            val inicioHoy = startOfTodayLocal()
            val ahora = System.currentTimeMillis()
            vistaModeloApps.cargarUsos(uidHijo, inicioHoy, ahora)
            delay(60_000L)
        }
    }

    LaunchedEffect(appsAgregadas) {
        appsAgregadas.forEach {
            vistaModeloApps.verificarSiEstaBloqueada(uidHijo, it.nombrePaquete)
        }
    }

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
                title = { Text("Control de Aplicaciones", color = onPrimaryColor) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryLight,
                    titleContentColor = onPrimaryColor
                )
            )
        },
        containerColor = primaryDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val totalHoras = tiempoTotalPantalla / (60 * 60 * 1000)
            val totalMinutos = (tiempoTotalPantalla % (60 * 60 * 1000)) / 60000
            Text(
                text = "Tiempo total de pantalla hoy: ${totalHoras}h ${totalMinutos}m",
                style = MaterialTheme.typography.titleMedium,
                color = onSurfaceColor
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (appsAgregadas.isEmpty()) {
                Text(
                    "No se encontraron aplicaciones usadas aún.",
                    color = onSurfaceColor
                )
            }

            LazyColumn {
                items(appsAgregadas) { app ->
                    // Este es el único Composable principal para cada item.
                    Column {
                        val bloqueada = estadosBloqueo[app.nombrePaquete] == true
                        val tiempoLimite = limitesApps[app.nombrePaquete] ?: 0L
                        val tiempoUso = app.tiempoUso
                        val tiempoRestante = max(0L, tiempoLimite - tiempoUso)

                        val progreso =
                            if (tiempoLimite > 0) tiempoUso.toFloat() / tiempoLimite.toFloat() else 0f
                        val progresoClamped = progreso.coerceIn(0f, 1f)

                        val colorProgreso = when {
                            progreso > 0.9f -> MaterialTheme.colorScheme.error
                            progreso > 0.7f -> accentColor
                            else -> primaryLight
                        }

                        var nuevoLimiteHoras by remember { mutableStateOf("") }
                        var nuevoLimiteMinutos by remember { mutableStateOf("") }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(surfaceColor, shape = MaterialTheme.shapes.medium)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (app.nombreApp == app.nombrePaquete)
                                            app.nombrePaquete
                                        else
                                            "${app.nombreApp}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = onPrimaryColor
                                    )
                                    Text(
                                        text = "(${app.nombrePaquete})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = onSurfaceColor
                                    )

                                    val usoEnMinutos = tiempoUso / 60000
                                    val horasUso = usoEnMinutos / 60
                                    val minutosUso = usoEnMinutos % 60
                                    Text(
                                        text = "Uso hoy: ${horasUso}h ${minutosUso}m",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = onSurfaceColor
                                    )

                                    if (tiempoLimite > 0L) {
                                        val restanteEnMinutos = tiempoRestante / 60000
                                        val horasRestantes = restanteEnMinutos / 60
                                        val minutosRestantes = restanteEnMinutos % 60
                                        Text(
                                            text = "Tiempo restante: ${horasRestantes}h ${minutosRestantes}m",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (tiempoRestante <= 0) MaterialTheme.colorScheme.error else accentColor
                                        )
                                    }
                                }

                                Switch(
                                    checked = bloqueada,
                                    onCheckedChange = { nuevoEstado ->
                                        vistaModeloApps.cambiarEstadoBloqueo(
                                            uidHijo,
                                            app.nombrePaquete,
                                            nuevoEstado
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = accentColor,
                                        checkedTrackColor = accentColor.copy(alpha = 0.5f),
                                        uncheckedThumbColor = onSurfaceColor,
                                        uncheckedTrackColor = onSurfaceColor.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            if (tiempoLimite > 0L) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = progresoClamped,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    color = colorProgreso,
                                    trackColor = onSurfaceColor.copy(alpha = 0.1f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = nuevoLimiteHoras,
                                    onValueChange = {
                                        if (it.all { char -> char.isDigit() } && it.length <= 2) {
                                            nuevoLimiteHoras = it
                                        }
                                    },
                                    label = { Text("Horas", color = onSurfaceColor) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = accentColor,
                                        unfocusedIndicatorColor = borderColor,
                                        cursorColor = accentColor,
                                        focusedTextColor = onPrimaryColor,
                                        unfocusedTextColor = onPrimaryColor,
                                        unfocusedLabelColor = onSurfaceColor,
                                        focusedLabelColor = accentColor
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = nuevoLimiteMinutos,
                                    onValueChange = {
                                        if (it.all { char -> char.isDigit() } && it.length <= 2) {
                                            val minutes = it.toIntOrNull()
                                            if (minutes == null || minutes <= 59) {
                                                nuevoLimiteMinutos = it
                                            }
                                        }
                                    },
                                    label = { Text("Minutos", color = onSurfaceColor) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = accentColor,
                                        unfocusedIndicatorColor = borderColor,
                                        cursorColor = accentColor,
                                        focusedTextColor = onPrimaryColor,
                                        unfocusedTextColor = onPrimaryColor,
                                        unfocusedLabelColor = onSurfaceColor,
                                        focusedLabelColor = accentColor
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val horas = nuevoLimiteHoras.toLongOrNull() ?: 0L
                                        val minutos = nuevoLimiteMinutos.toLongOrNull() ?: 0L
                                        val milis = (horas * 60 * 60 * 1000) + (minutos * 60 * 1000)
                                        if (milis > 0) {
                                            vistaModeloApps.establecerLimite(
                                                uidHijo,
                                                app.nombrePaquete,
                                                milis
                                            )
                                            nuevoLimiteHoras = ""
                                            nuevoLimiteMinutos = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = primaryDark
                                    )
                                ) {
                                    Text("Limitar")
                                }
                            }
                            if (tiempoLimite > 0L) {
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        vistaModeloApps.establecerLimite(uidHijo, app.nombrePaquete, 0L)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Quitar límite",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Quitar límite")
                                }
                            }

                        }
                        // Este divisor está dentro del Column principal de cada ítem.
                        Divider(
                            modifier = Modifier.padding(top = 12.dp),
                            color = borderColor.copy(alpha = 0.3f)
                        )
                    } // Fin del Column principal del item
                } // Fin del bloque de items
            } // Fin de LazyColumn
        } // Fin del Column principal de la pantalla
    }// Fin de Scaffold
} // Fin de PantallaControlApps