package com.example.udparents.vista.pantallas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.udparents.utilidades.PdfUtils
import com.example.udparents.viewmodel.VistaModeloApps
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import java.util.Date
import java.util.Calendar
import kotlinx.coroutines.launch // Importar coroutineScope.launch

//HU13
// Muestra una lista de las apps más utilizadas por el hijo, con su tiempo de uso.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInformeAppsMasUsadas(
    onVolverAlMenuPadre: () -> Unit,
    activity: ComponentActivity
) {
    val viewModel: VistaModeloApps = viewModel()
    val hijosVinculados by viewModel.hijosVinculados.collectAsState()
    val usuario = FirebaseAuth.getInstance().currentUser
    val idPadre = usuario?.uid ?: ""
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope() // Obtener un coroutineScope

    var hijoSeleccionado by remember {
        mutableStateOf<Pair<String, String>?>(null)
    }

    LaunchedEffect(Unit) {
        viewModel.cargarHijos(idPadre)
    }

    LaunchedEffect(hijosVinculados) {
        if (hijosVinculados.isNotEmpty() && hijoSeleccionado == null) {
            hijoSeleccionado = hijosVinculados.first()
        }
    }

    val appsMasUsadas by viewModel.appsMasUsadas.collectAsState()
    var periodoSeleccionado by remember { mutableStateOf("Hoy") }
    val periodos = listOf("Hoy", "Últimos 7 días", "Últimos 30 días")

    LaunchedEffect(hijoSeleccionado, periodoSeleccionado) {
        hijoSeleccionado?.let { hijo ->
            val (desde, hasta) = when (periodoSeleccionado) {
                "Hoy" -> {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val desdeHoy = cal.timeInMillis
                    val hastaHoy = Date().time
                    desdeHoy to hastaHoy
                }
                "Últimos 7 días" -> {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -7)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val desde7 = cal.timeInMillis
                    val hasta7 = Date().time
                    desde7 to hasta7
                }
                "Últimos 30 días" -> {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -30)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val desde30 = cal.timeInMillis
                    val hasta30 = Date().time
                    desde30 to hasta30
                }
                else -> {
                    val hoy = Date().time
                    hoy to hoy
                }
            }
            viewModel.cargarAppsMasUsadas(hijo.first, desde, hasta)
        }
    }

    val primaryDark = Color(0xFF000033)
    val primaryLight = Color(0xFF3F51B5)
    val accentColor = Color(0xFFCDDC39)
    val onPrimaryColor = Color.White
    val surfaceColor = Color(0xFF2C387F)
    val onSurfaceColor = Color(0xFFE8EAF6)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps Más Usadas", color = onPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onVolverAlMenuPadre) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = onPrimaryColor)
                    }
                },
                actions = {
                    val apps = appsMasUsadas
                    if (apps.isNotEmpty() && hijoSeleccionado != null) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val nombreArchivo = buildString {
                                    append("apps_mas_usadas_")
                                    append(hijoSeleccionado!!.second.replace(" ", "_").lowercase())
                                    append("_")
                                    append(
                                        when (periodoSeleccionado) {
                                            "Hoy" -> "hoy"
                                            "Últimos 7 días" -> "ultimos_7_dias"
                                            "Últimos 30 días" -> "ultimos_30_dias"
                                            else -> "periodo"
                                        }
                                    )
                                }
                                PdfUtils.generarPdfDesdeComposable(
                                    context = context,
                                    activity = activity,
                                    fileName = nombreArchivo
                                ) {
                                    ReporteAppsMasUsadasPDF(
                                        nombreHijo = hijoSeleccionado!!.second,
                                        periodoEtiqueta = periodoSeleccionado,
                                        appsMasUsadas = apps
                                    )
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Exportar PDF",
                                tint = onPrimaryColor
                            )
                        }
                    }
                },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hijosVinculados.isNotEmpty()) {
                HijoSelectorInformeApps(
                    hijos = hijosVinculados,
                    hijoSeleccionado = hijoSeleccionado,
                    onHijoSeleccionado = { hijoSeleccionado = it },
                    surfaceColor = surfaceColor,
                    onSurfaceColor = onSurfaceColor
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            PeriodoSelectorInformeApps(
                periodos = periodos,
                periodoSeleccionado = periodoSeleccionado,
                onPeriodoSeleccionado = { periodoSeleccionado = it },
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (appsMasUsadas.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(appsMasUsadas.toList().sortedByDescending { it.second }) { (appName, tiempo) ->
                        AppUsoItemInformeApps(
                            appName = appName,
                            tiempoUso = tiempo,
                            backgroundColor = surfaceColor,
                            contentColor = onSurfaceColor
                        )
                    }
                }
            } else {
                Text(
                    "No hay datos de uso para el período seleccionado.",
                    color = onSurfaceColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ReporteAppsMasUsadasPDF(
    nombreHijo: String,
    periodoEtiqueta: String,
    appsMasUsadas: Map<String, Long>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color.White)
    ) {
        Text(
            text = "Informe de Aplicaciones Más Usadas",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black)
        )
        Spacer(Modifier.height(8.dp))
        Text("Hijo: $nombreHijo", color = Color.Black)
        Text("Periodo: $periodoEtiqueta", color = Color.Black)
        Spacer(Modifier.height(16.dp))

        appsMasUsadas.toList()
            .sortedByDescending { it.second }
            .forEachIndexed { index, (app, millis) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        text = "${index + 1}. $app",
                        style = TextStyle(fontWeight = FontWeight.Bold, color = Color.Black)
                    )
                    Text(
                        text = "Tiempo de uso: ${formatMillisToTime(millis)}",
                        color = Color.Black
                    )
                }
            }
    }
}

/**
 * Muestra un selector de hijo con un dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HijoSelectorInformeApps(
    hijos: List<Pair<String, String>>,
    hijoSeleccionado: Pair<String, String>?,
    onHijoSeleccionado: (Pair<String, String>) -> Unit,
    surfaceColor: Color,
    onSurfaceColor: Color
) {
    var expandido by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = !expandido },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = hijoSeleccionado?.second ?: "Seleccionar hijo",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier.menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = surfaceColor,
                unfocusedContainerColor = surfaceColor,
                focusedTextColor = onSurfaceColor,
                unfocusedTextColor = onSurfaceColor,
                focusedBorderColor = onSurfaceColor,
                unfocusedBorderColor = onSurfaceColor,
            ),
            textStyle = TextStyle(color = onSurfaceColor)
        )
        ExposedDropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor)
        ) {
            hijos.forEach { hijo ->
                DropdownMenuItem(
                    text = {
                        Text(
                            hijo.second,
                            color = onSurfaceColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onHijoSeleccionado(hijo)
                        expandido = false
                    }
                )
            }
        }
    }
}

/**
 * Muestra un selector para el período de tiempo con un dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodoSelectorInformeApps(
    periodos: List<String>,
    periodoSeleccionado: String,
    onPeriodoSeleccionado: (String) -> Unit,
    surfaceColor: Color,
    onSurfaceColor: Color
) {
    var expandido by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = !expandido },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = periodoSeleccionado,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier.menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = surfaceColor,
                unfocusedContainerColor = surfaceColor,
                focusedTextColor = onSurfaceColor,
                unfocusedTextColor = onSurfaceColor,
                focusedBorderColor = onSurfaceColor,
                unfocusedBorderColor = onSurfaceColor,
            ),
            textStyle = TextStyle(color = onSurfaceColor)
        )
        ExposedDropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor)
        ) {
            periodos.forEach { periodo ->
                DropdownMenuItem(
                    text = {
                        Text(
                            periodo,
                            color = onSurfaceColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onPeriodoSeleccionado(periodo)
                        expandido = false
                    }
                )
            }
        }
    }
}

/**
 * Composable para mostrar un ítem de aplicación en la lista.
 */
@Composable
private fun AppUsoItemInformeApps(
    appName: String,
    tiempoUso: Long,
    backgroundColor: Color,
    contentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "App Icon",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tiempo de uso: ${formatMillisToTime(tiempoUso)}",
                    color = contentColor.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Convierte milisegundos a un formato de tiempo legible (ej. "1h 30m").
 */
private fun formatMillisToTime(millis: Long): String {
    if (millis <= 0) return "0m"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "1m"
    }
}