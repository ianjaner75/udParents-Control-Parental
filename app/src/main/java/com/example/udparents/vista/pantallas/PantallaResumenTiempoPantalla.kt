package com.example.udparents.vista.pantallas


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.udparents.viewmodel.VistaModeloApps
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

// =================================================================================================
// NUEVA PANTALLA PARA HU-012: Resumen de Tiempo de Pantalla
// Muestra el uso diario y semanal del dispositivo mediante gráficos de barras.
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaResumenTiempoPantalla(
    onVolverAlMenuPadre: () -> Unit // callback para regresar
) {
    val viewModel: VistaModeloApps = viewModel()
    val hijosVinculados by viewModel.hijosVinculados.collectAsState()

    val usuario = FirebaseAuth.getInstance().currentUser
    val idPadre = usuario?.uid ?: ""

    // Estado para el hijo seleccionado (UID y nombre)
    var hijoSeleccionado by remember {
        mutableStateOf<Pair<String, String>?>(null)
    }

    // Llama a la función del ViewModel con el nombre correcto para cargar los hijos vinculados
    LaunchedEffect(Unit) {
        viewModel.cargarHijos(idPadre)
    }

    // Se actualiza el hijo seleccionado si la lista de hijos vinculados no está vacía.
    LaunchedEffect(hijosVinculados) {
        if (hijosVinculados.isNotEmpty() && hijoSeleccionado == null) {
            hijoSeleccionado = hijosVinculados.first()
        }
    }

    // Cargar los datos cuando el hijo seleccionado cambie
    LaunchedEffect(hijoSeleccionado) {
        hijoSeleccionado?.let {
            // ** EL CAMBIO ES AQUÍ **
            // Antes tenías:
            // viewModel.cargarResumenTiempoPantallaDiario(it.first)
            // viewModel.cargarResumenTiempoPantallaSemanal(it.first)
            // Ahora solo necesitas una llamada:
            viewModel.cargarDatosTiempoPantalla(it.first)
        }
    }

    // Estados para la interfaz
    val tiempoDiario by viewModel.tiempoPantallaDiario.collectAsState()
    val tiempoSemanal by viewModel.tiempoPantallaSemanal.collectAsState()

    // Estado para controlar la vista (diaria o semanal)
    var vistaSeleccionada by remember { mutableStateOf("Diaria") }
    val vistas = listOf("Diaria", "Semanal")

    // Paleta de colores
    val primaryDark = Color(0xFF000033)
    val primaryLight = Color(0xFF3F51B5)
    val accentColor = Color(0xFFCDDC39)
    val onPrimaryColor = Color.White
    val surfaceColor = Color(0xFF2C387F)
    val onSurfaceColor = Color(0xFFE8EAF6)

    val tiempoTotalDiario = remember { mutableStateOf(0L) }
    val tiempoTotalSemanal = remember { mutableStateOf(0L) }

    // CORRECCIÓN: Calcular totales correctos (HOY y ÚLTIMOS 7 DÍAS)
    LaunchedEffect(tiempoDiario) {
        // Aplica el hotfix para que el valor de hoy no exceda el tiempo transcurrido
        val diarioCapped = capToday(tiempoDiario)

        // Diario: solo la clave del día de hoy
        val hoy = hoyKey()
        tiempoTotalDiario.value = diarioCapped[hoy] ?: 0L

        // Semanal: suma de los últimos 7 días (incluye hoy) usando el mapa diario
        val ult7 = filtrarUltimosNDias(diarioCapped, 7)
        tiempoTotalSemanal.value = ult7.values.sum()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen de Tiempo de Pantalla", color = onPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onVolverAlMenuPadre) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = onPrimaryColor)
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
            // Dropdown para seleccionar el hijo
            if (hijosVinculados.isNotEmpty()) {
                HijoSelector(
                    hijos = hijosVinculados,
                    hijoSeleccionado = hijoSeleccionado,
                    onHijoSeleccionado = { hijoSeleccionado = it },
                    surfaceColor = surfaceColor,
                    onSurfaceColor = onSurfaceColor
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Pestañas para vista diaria/semanal
            TabRow(
                selectedTabIndex = vistas.indexOf(vistaSeleccionada),
                containerColor = primaryLight,
                contentColor = onPrimaryColor
            ) {
                vistas.forEachIndexed { index, title ->
                    Tab(
                        selected = vistaSeleccionada == title,
                        onClick = { vistaSeleccionada = title },
                        text = {
                            Text(
                                title,
                                color = if (vistaSeleccionada == title) accentColor else onPrimaryColor
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =================================================================================================
            // NUEVO: Mensaje dinámico de tiempo total de uso
            // =================================================================================================
            if (hijoSeleccionado != null) {
                // Calcular el tiempo total del día o de la semana
                val tiempoTotalFormateado = if (vistaSeleccionada == "Diaria") {
                    formatMillisToTime(tiempoTotalDiario.value)
                } else {
                    formatMillisToTime(tiempoTotalSemanal.value)
                }

                // Mostrar el mensaje de tiempo total
                Text(
                    text = "Uso ${vistaSeleccionada.lowercase()}: $tiempoTotalFormateado",
                    color = onSurfaceColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (vistaSeleccionada == "Diaria") {
                    // Aplica el hotfix al gráfico
                    val ultimos7Dias = filtrarUltimosNDias(capToday(tiempoDiario), 7)
                    GraficoDeBarras(
                        data = ultimos7Dias,
                        titulo = "Uso Diario",
                        barColor = accentColor,
                        labelColor = onSurfaceColor,
                        axisColor = onSurfaceColor
                    )
                } else {
                    // ** CAMBIO AQUÍ: Ahora el gráfico semanal usa los datos diarios agrupados **
                    val semanalISO = groupByIsoWeekAll(capToday(tiempoDiario))
                    GraficoDeBarras(
                        data = semanalISO,
                        titulo = "Uso Semanal",
                        barColor = accentColor,
                        labelColor = onSurfaceColor,
                        axisColor = onSurfaceColor
                    )
                }
            } else {
                Text(
                    "Por favor, vincula un dispositivo para ver los datos.",
                    color = onSurfaceColor,
                    style = MaterialTheme.typography.bodyLarge
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
fun HijoSelector(
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
            )
        )
        ExposedDropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false },
            modifier = Modifier
                .background(Color(0xFF1A237E)) // Fondo completo del menú desplegable (primaryDark)
        ) {
            hijos.forEach { hijo ->
                DropdownMenuItem(
                    text = {
                        Text(
                            hijo.second,
                            color = Color.White, // Texto blanco para contraste
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onHijoSeleccionado(hijo)
                        expandido = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2C387F)) // Fondo de cada ítem (surfaceColor)
                )
            }
        }

    }
}

/**
 * Composable que dibuja un gráfico de barras.
 */
@Composable
fun GraficoDeBarras(
    data: Map<String, Long>,
    titulo: String,
    barColor: Color,
    labelColor: Color,
    axisColor: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val sortedData = if (titulo == "Uso Diario") {
        // "yyyy-MM-dd" ya ordena bien lexicográficamente
        data.toList().sortedBy { it.first }
    } else {
        // Clave "YYYY-W##" → ordena primero por año y luego por semana
        data.toList().sortedWith(compareBy(
            { it.first.substringBefore("-W").toIntOrNull() ?: Int.MAX_VALUE }, // año
            { it.first.substringAfter("-W").toIntOrNull() ?: Int.MAX_VALUE }   // semana
        ))
    }

    val maxTiempo = sortedData.maxOfOrNull { it.second } ?: 0L
    val dataSize = sortedData.size

    if (data.isEmpty() || maxTiempo == 0L) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No hay datos de uso para mostrar.", color = labelColor, fontSize = 16.sp)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = titulo,
                color = labelColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp) // Aumenta la altura para una mejor visualización
                    .padding(8.dp)
            ) {
                val paddingStart = 60.dp.toPx()
                val paddingBottom = 40.dp.toPx()
                val chartWidth = size.width - paddingStart
                val chartHeight = size.height - paddingBottom
                val barSpacing = 16.dp.toPx()
                val barWidth = if (dataSize > 0) (chartWidth - (dataSize - 1) * barSpacing) / dataSize else 0f
                val barColorAlpha = barColor.copy(alpha = 0.8f)
                val barMaxHeight = chartHeight - 20.dp.toPx() // Un poco de espacio arriba

                // Dibuja el eje Y (vertical)
                drawLine(
                    color = axisColor,
                    start = Offset(paddingStart, 0f),
                    end = Offset(paddingStart, chartHeight),
                    strokeWidth = 2.dp.toPx()
                )
                // Dibuja el eje X (horizontal)
                drawLine(
                    color = axisColor,
                    start = Offset(paddingStart, chartHeight),
                    end = Offset(size.width, chartHeight),
                    strokeWidth = 2.dp.toPx()
                )

                // Dibuja las etiquetas del eje Y (tiempo)
                val yAxisLabelsCount = 5
                for (i in 0..yAxisLabelsCount) {
                    val fraction = i.toFloat() / yAxisLabelsCount
                    val y = chartHeight * (1 - fraction)
                    val value = maxTiempo * fraction
                    val label = formatMillisToTime(value.toLong())
                    drawText(
                        textMeasurer = textMeasurer,
                        text = AnnotatedString(label),
                        topLeft = Offset(8.dp.toPx(), y - 10.sp.toPx()),
                        style = TextStyle(fontSize = 12.sp, color = labelColor)
                    )
                }

                // Dibuja las barras y las etiquetas del eje X (días/semanas)
                sortedData.forEachIndexed { index, (label, tiempo) ->
                    val barHeight = (tiempo.toFloat() / maxTiempo.toFloat()) * barMaxHeight
                    val x = paddingStart + (chartWidth / dataSize) * index
                    val barX = x + (chartWidth / dataSize - barWidth) / 2 // Centrar la barra
                    val y = chartHeight - barHeight

                    // Dibuja la barra
                    drawRect(
                        color = barColorAlpha,
                        topLeft = Offset(barX, y),
                        size = Size(width = barWidth, height = barHeight)
                    )

                    // ** CAMBIO AQUÍ: Etiqueta de valor (tiempo) encima de la barra **
                    val valueLabel = formatMillisToShort(tiempo)
                    val textBounds = textMeasurer.measure(
                        AnnotatedString(valueLabel),
                        style = TextStyle(fontSize = 12.sp, color = labelColor)
                    )
                    val labelX = barX + barWidth / 2 - textBounds.size.width / 2
                    val labelY = (y - 6.dp.toPx()).coerceAtLeast(4.dp.toPx()) // un pequeño margen
                    drawText(
                        textMeasurer = textMeasurer,
                        text = AnnotatedString(valueLabel),
                        topLeft = Offset(labelX, labelY),
                        style = TextStyle(fontSize = 12.sp, color = labelColor)
                    )

                    // Dibuja la etiqueta del eje X (Día de la semana)
                    val etiqueta = when (titulo) {
                        "Uso Diario" -> {
                            try {
                                val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(label)
                                SimpleDateFormat("EEE", Locale.getDefault()).format(fecha ?: Date()).substring(0, 3)
                            } catch (_: Throwable) {
                                label // fallback
                            }
                        }
                        else -> {
                            // label viene como "YYYY-W##"
                            val w = label.substringAfter("-W")
                            "Sem $w"
                        }
                    }

                    drawText(
                        textMeasurer = textMeasurer,
                        text = AnnotatedString(etiqueta),
                        topLeft = Offset(x + (chartWidth / dataSize) / 2 - 20.dp.toPx(), chartHeight + 8.dp.toPx()),
                        style = TextStyle(fontSize = 12.sp, color = labelColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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


private fun hoyKey(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date())
}

private fun keysUltimosNDias(n: Int): List<String> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    // normalizamos a inicio del día
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    val keys = mutableListOf<String>()
    for (i in 0 until n) {
        val key = sdf.format(cal.time)
        keys.add(key)
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    // devuelve de más antiguo → más reciente (opcional)
    return keys.reversed()
}

/** Devuelve un mapa con SOLO los últimos N días (rellenando con 0 si falta algún día). */
private fun filtrarUltimosNDias(
    data: Map<String, Long>,
    n: Int
): Map<String, Long> {
    val keys = keysUltimosNDias(n)
    return keys.associateWith { k -> data[k] ?: 0L }
}

// =================================================================================================
// HOTFIX: Helpers para acotar el uso de hoy a lo que realmente ha transcurrido en el día
// Esto evita picos irreales por el cálculo del repositorio (ej. "últimas 24h")
// =================================================================================================

private fun startOfTodayMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun capToday(data: Map<String, Long>): Map<String, Long> {
    val hoy = hoyKey()
    val start = startOfTodayMillis()
    val elapsed = System.currentTimeMillis() - start
    val current = data[hoy] ?: 0L
    // el uso de hoy jamás puede ser mayor al tiempo transcurrido del día
    val capped = minOf(current, elapsed.coerceAtLeast(0L))
    return data.toMutableMap().apply { put(hoy, capped) }
}

// =================================================================================================
// NUEVOS HELPERS PARA CÁLCULO Y AGRUPACIÓN DE SEMANAS EN ISO-8601
// Esto asegura que la semana siempre empiece en lunes y resuelve el problema de las dos barras.
// =================================================================================================
private fun weekKeyISO(date: Date): String {
    val cal = Calendar.getInstance().apply {
        time = date
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }
    val year = cal.get(Calendar.YEAR)
    val week = cal.get(Calendar.WEEK_OF_YEAR)
    return "%04d-W%02d".format(year, week)
}

private fun groupByIsoWeekAll(data: Map<String, Long>): Map<String, Long> {
    // data: "yyyy-MM-dd" -> millis (diario)
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val agg = mutableMapOf<String, Long>()
    data.forEach { (k, v) ->
        val d = runCatching { sdf.parse(k) }.getOrNull() ?: return@forEach
        val wk = weekKeyISO(d)
        agg[wk] = (agg[wk] ?: 0L) + v
    }
    // Ordenamos por (año, semana)
    return agg.toList()
        .sortedWith(compareBy(
            { it.first.substringBefore("-W").toIntOrNull() ?: Int.MAX_VALUE },
            { it.first.substringAfter("-W").toIntOrNull() ?: Int.MAX_VALUE }
        ))
        .toMap(LinkedHashMap()) // preserva orden
}

// Etiqueta compacta para la cifra encima de cada barra
private fun formatMillisToShort(millis: Long): String {
    if (millis <= 0) return "0m"
    val h = TimeUnit.MILLISECONDS.toHours(millis)
    val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}