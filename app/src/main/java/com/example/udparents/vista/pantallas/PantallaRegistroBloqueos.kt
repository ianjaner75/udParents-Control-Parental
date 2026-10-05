package com.example.udparents.vista.pantallas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.udparents.modelo.BloqueoRegistro
import com.example.udparents.utilidades.PdfUtils
import com.example.udparents.viewmodel.VistaModeloApps
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRegistroBloqueos(
    onVolverAlMenuPadre: () -> Unit,
    activity: ComponentActivity,
    navController: NavController
) {
    val viewModel: VistaModeloApps = viewModel()
    val hijosVinculados by viewModel.hijosVinculados.collectAsState()
    val usuario = FirebaseAuth.getInstance().currentUser
    val idPadre = usuario?.uid ?: ""
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hijoSeleccionado by remember { mutableStateOf<Pair<String, String>?>(null) }
    val registroBloqueos by viewModel.registroBloqueos.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.cargarHijos(idPadre)
    }

    LaunchedEffect(hijosVinculados) {
        if (hijosVinculados.isNotEmpty() && hijoSeleccionado == null) {
            hijoSeleccionado = hijosVinculados.first()
        }
    }

    LaunchedEffect(hijoSeleccionado) {
        hijoSeleccionado?.let { hijo ->
            viewModel.cargarRegistroBloqueos(hijo.first)
        }
    }

    val primaryDark = Color(0xFF000033)
    val primaryLight = Color(0xFF3F51B5)
    val onPrimaryColor = Color.White
    val surfaceColor = Color(0xFF2C387F)
    val onSurfaceColor = Color(0xFFE8EAF6)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registro de Bloqueos", color = onPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onVolverAlMenuPadre) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = onPrimaryColor)
                    }
                },
                actions = {
                    if (registroBloqueos.isNotEmpty()) {
                        // Ver PDF
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val nombreArchivo = "bloqueos_${hijoSeleccionado?.second ?: "hijo"}"
                                PdfUtils.generarPdfDesdeComposable(
                                    context = context,
                                    activity = activity,
                                    fileName = nombreArchivo,
                                ) {
                                    ReporteDeBloqueosPDF(registroBloqueos)
                                }
                            }
                        }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Ver PDF", tint = onPrimaryColor)
                        }
                        // Guardar/Compartir PDF en Descargas
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val nombre = "bloqueos_${hijoSeleccionado?.second ?: "hijo"}"
                                val uri = PdfUtils.generarPdfDesdeComposableAStorage(
                                    context = context,
                                    activity = activity,
                                    fileName = nombre
                                ) { ReporteDeBloqueosPDF(registroBloqueos) }

                                uri?.let { PdfUtils.compartirPdf(context, it) }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Guardar/Compartir", tint = onPrimaryColor)
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
                HijoSelectorRegistroBloqueos(
                    hijos = hijosVinculados,
                    hijoSeleccionado = hijoSeleccionado,
                    onHijoSeleccionado = { hijoSeleccionado = it },
                    surfaceColor = surfaceColor,
                    onSurfaceColor = onSurfaceColor
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (registroBloqueos.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(registroBloqueos) { bloqueo ->
                        BloqueoItem(
                            bloqueo = bloqueo,
                            backgroundColor = surfaceColor,
                            contentColor = onSurfaceColor
                        )
                    }
                }
            } else {
                Text(
                    "No hay registros de bloqueos para el período seleccionado.",
                    color = onSurfaceColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ReporteDeBloqueosPDF(bloqueos: List<BloqueoRegistro>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color.White)
    ) {
        Text(
            text = "Informe de Bloqueos de Aplicaciones",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black)
        )
        Spacer(modifier = Modifier.height(16.dp))
        bloqueos.forEach { bloqueo ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(text = "App: ${bloqueo.nombreApp}", style = TextStyle(fontWeight = FontWeight.Bold, color = Color.Black))
                Text(text = "Razón: ${bloqueo.razon}", color = Color.Black)
                Text(text = "Intentos: ${bloqueo.contadorIntentos}", color = Color.Black)
                Text(text = "Fecha: ${bloqueo.fecha}", color = Color.Black)
                Text(text = "Horas: ${bloqueo.intentos.joinToString(", ")}", color = Color.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HijoSelectorRegistroBloqueos(
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

@Composable
private fun BloqueoItem(
    bloqueo: BloqueoRegistro,
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
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Ícono de bloqueo",
                tint = Color.Red,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bloqueo.nombreApp,
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Intentos: ${bloqueo.contadorIntentos}",
                    color = contentColor.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                bloqueo.intentos.forEach { intento ->
                    Text(
                        text = "• $intento",
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Razón: ${bloqueo.razon}",
                    color = contentColor.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}