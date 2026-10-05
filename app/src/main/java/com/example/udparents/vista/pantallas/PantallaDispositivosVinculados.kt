package com.example.udparents.vista.pantallas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.udparents.modelo.CodigoVinculacion
import com.example.udparents.viewmodel.VistaModeloVinculacion
import com.google.firebase.auth.FirebaseAuth


// Se han implementado las validaciones reutilizables del ViewModel.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDispositivosVinculados(
    onVolverAlMenuPadre: () -> Unit // callback para regresar
) {
    val viewModel: VistaModeloVinculacion = viewModel()
    val dispositivos by viewModel.dispositivosVinculados.collectAsState()

    val usuario = FirebaseAuth.getInstance().currentUser
    val idPadre = usuario?.uid ?: ""

    // Estados para controlar los diálogos de edición y eliminación
    var mostrarDialogoEdicion by remember { mutableStateOf(false) }
    var mostrarDialogoEliminacion by remember { mutableStateOf(false) }
    var dispositivoAEditar by remember { mutableStateOf<CodigoVinculacion?>(null) }
    var dispositivoAEliminar by remember { mutableStateOf<CodigoVinculacion?>(null) }

    LaunchedEffect(idPadre) {
        if (idPadre.isNotEmpty()) {
            viewModel.cargarDispositivosVinculados(idPadre)
        }
    }

    // Paleta de colores
    val primaryDark = Color(0xFF1A237E)
    val primaryLight = Color(0xFF3F51B5)
    val accentColor = Color(0xFFCDDC39)
    val onPrimaryColor = Color.White
    val surfaceColor = Color(0xFF3949AB)
    val onSurfaceColor = Color(0xFFE8EAF6)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Hijos", color = onPrimaryColor, fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (dispositivos.isEmpty()) {
                Text(
                    "No hay dispositivos vinculados.",
                    color = onSurfaceColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dispositivos) { dispositivo ->
                        DispositivoVinculadoCard(
                            dispositivo = dispositivo,
                            onEditClick = {
                                dispositivoAEditar = it
                                mostrarDialogoEdicion = true
                            },
                            onDeleteClick = {
                                dispositivoAEliminar = it
                                mostrarDialogoEliminacion = true
                            },
                            surfaceColor = surfaceColor,
                            onSurfaceColor = onSurfaceColor,
                            accentColor = accentColor,
                            errorColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Diálogo de edición
    if (mostrarDialogoEdicion && dispositivoAEditar != null) {
        AddEditHijoDialog(
            dispositivo = dispositivoAEditar!!,
            onDismiss = { mostrarDialogoEdicion = false },
            onSave = { actualizado ->
                // Normalizamos nombre y estandarizamos sexo a M/F antes de guardar
                val nombreNorm = viewModel.normalizarNombreEntrada(actualizado.nombreHijo)
                val sexoMF = when (actualizado.sexoHijo.trim().lowercase()) {
                    "m", "masculino" -> "M"
                    "f", "femenino" -> "F"
                    else -> actualizado.sexoHijo // por si ya pasa "M"/"F"
                }
                val corregido = actualizado.copy(nombreHijo = nombreNorm, sexoHijo = sexoMF)
                viewModel.actualizarVinculacion(idPadre, corregido)
                mostrarDialogoEdicion = false
            },
            primaryLight = primaryLight,
            accentColor = accentColor,
            validator = { n, e, s -> viewModel.validarPerfil(n, e, s) }
        )
    }

    // Diálogo de confirmación de eliminación
    if (mostrarDialogoEliminacion && dispositivoAEliminar != null) {
        ConfirmDeleteDialog(
            dispositivo = dispositivoAEliminar!!,
            onDismiss = { mostrarDialogoEliminacion = false },
            onConfirm = {
                viewModel.eliminarVinculacion(idPadre, it.dispositivoHijo)
                mostrarDialogoEliminacion = false
            },
            primaryLight = primaryLight,
            onPrimaryColor = onPrimaryColor,
            errorColor = MaterialTheme.colorScheme.error
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispositivoVinculadoCard(
    dispositivo: CodigoVinculacion,
    onEditClick: (CodigoVinculacion) -> Unit,
    onDeleteClick: (CodigoVinculacion) -> Unit,
    surfaceColor: Color,
    onSurfaceColor: Color,
    accentColor: Color,
    errorColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Icono de perfil",
                    tint = onSurfaceColor,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                val sexoLegible = when (dispositivo.sexoHijo.uppercase()) {
                    "M" -> "Masculino"
                    "F" -> "Femenino"
                    else -> dispositivo.sexoHijo
                }

                Text(
                    "Nombre: ${dispositivo.nombreHijo}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Edad: ${dispositivo.edadHijo}", fontSize = 14.sp, color = onSurfaceColor)
                Text("Sexo: $sexoLegible", fontSize = 14.sp, color = onSurfaceColor)
                Text(
                    "UID: ${dispositivo.dispositivoHijo}",
                    fontSize = 10.sp,
                    color = onSurfaceColor.copy(alpha = 0.7f)
                )
            }

            Row {
                IconButton(onClick = { onEditClick(dispositivo) }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = accentColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { onDeleteClick(dispositivo) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = errorColor
                    )
                }
            }
        }
    }
}

// Nuevo composable para el diálogo de edición (versión actualizada)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHijoDialog(
    dispositivo: CodigoVinculacion,
    onDismiss: () -> Unit,
    onSave: (CodigoVinculacion) -> Unit,
    primaryLight: Color,
    accentColor: Color,
    validator: (String, Int, String) -> String?
) {
    var nombre by remember { mutableStateOf(dispositivo.nombreHijo) }
    var edadTexto by remember { mutableStateOf(dispositivo.edadHijo.takeIf { it > 0 }?.toString() ?: "") }
    var sexo by remember {
        mutableStateOf(
            when (dispositivo.sexoHijo.trim().lowercase()) {
                "m", "masculino" -> "M"
                "f", "femenino" -> "F"
                else -> dispositivo.sexoHijo // por si ya viene "M"/"F"
            }
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    // Dropdown para sexo
    var abierto by remember { mutableStateOf(false) }
    val opcionesSexo = listOf("M", "F")

    fun validarYSetError() {
        val edadInt = edadTexto.toIntOrNull() ?: 0
        error = validator(nombre, edadInt, sexo)
    }

    LaunchedEffect(nombre, edadTexto, sexo) { validarYSetError() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar perfil de ${dispositivo.nombreHijo}") },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                        validarYSetError()
                    },
                    label = { Text("Nombre y apellido") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
                    ),
                    isError = error?.contains("apellido") == true
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = edadTexto,
                    onValueChange = { nuevo ->
                        val soloDigitos = nuevo.filter { it.isDigit() }.take(2)
                        edadTexto = soloDigitos
                        validarYSetError()
                    },
                    label = { Text("Edad (1–17)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error?.contains("edad") == true
                )
                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = abierto,
                    onExpandedChange = { abierto = !abierto },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = sexo,
                        onValueChange = { /* readOnly */ },
                        label = { Text("Sexo (M/F)") },
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
                        isError = error?.contains("sexo", ignoreCase = true) == true
                    )
                    ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                        opcionesSexo.forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text(opcion) },
                                onClick = {
                                    sexo = opcion
                                    abierto = false
                                    validarYSetError()
                                }
                            )
                        }
                    }
                }

                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val edadInt = edadTexto.toIntOrNull() ?: 0
                    val msg = validator(nombre, edadInt, sexo)
                    if (msg == null) {
                        onSave(
                            dispositivo.copy(
                                nombreHijo = nombre,
                                edadHijo = edadInt,
                                sexoHijo = sexo
                            )
                        )
                    } else {
                        error = msg
                    }
                },
                enabled = error == null,
                colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
            ) { Text("Guardar", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = accentColor) }
        }
    )
}

// Nuevo composable para el diálogo de confirmación de eliminación
@Composable
fun ConfirmDeleteDialog(
    dispositivo: CodigoVinculacion,
    onDismiss: () -> Unit,
    onConfirm: (CodigoVinculacion) -> Unit,
    primaryLight: Color,
    onPrimaryColor: Color,
    errorColor: Color
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar eliminación") },
        text = { Text("¿Estás seguro de que quieres eliminar el perfil de ${dispositivo.nombreHijo} y su vinculación?") },
        confirmButton = {
            Button(
                onClick = { onConfirm(dispositivo) },
                colors = ButtonDefaults.buttonColors(containerColor = errorColor)
            ) { Text("Eliminar", color = onPrimaryColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = primaryLight) }
        }
    )
}