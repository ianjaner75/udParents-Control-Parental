package com.example.udparents.vista.pantallas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.udparents.modelo.CodigoVinculacion
import com.example.udparents.viewmodel.VistaModeloVinculacion
import com.google.firebase.auth.FirebaseAuth

@Composable
fun PantallaDispositivosVinculados(
    onVolverAlMenuPadre: () -> Unit
) {
    val viewModel: VistaModeloVinculacion = viewModel()
    val dispositivos by viewModel.dispositivosVinculados.collectAsState()
    val idPadre = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    var dispositivoPendiente by remember { mutableStateOf<CodigoVinculacion?>(null) }
    var enviandoOrden by remember { mutableStateOf(false) }
    var mensajeOrden by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(idPadre) {
        if (idPadre.isNotEmpty()) {
            viewModel.cargarDispositivosVinculados(idPadre)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Dispositivos vinculados",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onVolverAlMenuPadre) {
            Text("Volver al menú principal")
        }

        mensajeOrden?.let { mensaje ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(mensaje, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (dispositivos.isEmpty()) {
            Text(
                "No hay dispositivos vinculados.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(dispositivos, key = { it.codigo }) { dispositivo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE6E6E6))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Código: ${dispositivo.codigo}", fontSize = 16.sp)
                            Text(
                                "Dispositivo hijo (UID): ${dispositivo.dispositivoHijo}",
                                fontSize = 14.sp,
                                color = Color.DarkGray
                            )
                            Text("Nombre: ${dispositivo.nombreHijo}", fontSize = 14.sp)
                            Text("Edad: ${dispositivo.edadHijo}", fontSize = 14.sp)
                            Text("Sexo: ${dispositivo.sexoHijo}", fontSize = 14.sp)

                            Spacer(modifier = Modifier.height(8.dp))

                            if (dispositivo.desinstalacionSolicitada) {
                                Text(
                                    "Orden enviada. El dispositivo debe procesarla para permitir la desinstalación.",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { dispositivoPendiente = dispositivo },
                                    enabled = idPadre.isNotBlank() && !enviandoOrden
                                ) {
                                    Text("Autorizar desvinculación y desinstalación")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    dispositivoPendiente?.let { dispositivo ->
        AlertDialog(
            onDismissRequest = {
                if (!enviandoOrden) dispositivoPendiente = null
            },
            title = { Text("Autorizar desinstalación") },
            text = {
                Text(
                    "Se enviará una orden a ${dispositivo.nombreHijo.ifBlank { "este dispositivo" }} " +
                        "para retirar el vínculo y permitir que la aplicación se desinstale manualmente."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !enviandoOrden,
                    onClick = {
                        enviandoOrden = true
                        viewModel.solicitarDesinstalacion(dispositivo.codigo, idPadre) { exito, error ->
                            enviandoOrden = false
                            if (exito) {
                                mensajeOrden = "Orden de desvinculación enviada."
                                dispositivoPendiente = null
                            } else {
                                mensajeOrden = error ?: "No se pudo enviar la orden."
                            }
                        }
                    }
                ) {
                    Text(if (enviandoOrden) "Enviando…" else "Enviar orden")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !enviandoOrden,
                    onClick = { dispositivoPendiente = null }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
