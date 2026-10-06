package com.example.udparents.vista.pantallas

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.udparents.servicio.MiFirebaseMessagingService
import com.example.udparents.viewmodel.VistaModeloApps
import com.example.udparents.viewmodel.VistaModeloUsuario
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun PantallaPrincipal(
    onCerrarSesion: () -> Unit,
    onIrAVinculacionPadre: () -> Unit,
    onIrADispositivosVinculados: () -> Unit,
    onIrAReporteApps: (List<Pair<String, String>>) -> Unit,
    onIrAControlApps: (List<Pair<String, String>>) -> Unit,
    onIrAProgramarRestricciones: (List<Pair<String, String>>) -> Unit,
    onIrAResumenTiempoPantalla: (List<Pair<String, String>>) -> Unit,
    onIrAInformeAppsMasUsadas: (List<Pair<String, String>>) -> Unit,
    onIrARegistroBloqueos: (List<Pair<String, String>>) -> Unit,
    onIrAUbicacionTiempoReal: (List<Pair<String, String>>) -> Unit
) {
    val vistaModelo: VistaModeloApps = viewModel()
    val hijosVinculados by vistaModelo.hijosVinculados.collectAsState()
    val uidPadre = FirebaseAuth.getInstance().currentUser?.uid
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val vistaModeloUsuario: VistaModeloUsuario = viewModel()

    LaunchedEffect(uidPadre) {
        uidPadre?.let { vistaModeloUsuario.cargarEstadoAlerta(it) }
    }
    val alertaActivada by vistaModeloUsuario.alertaContenido.collectAsState()

    // 🔒 BLOQUEO CONDICIONAL DE LA UI: los botones de monitoreo solo se habilitan
    //    cuando hay al menos un hijo vinculado (`hijosVinculados.isEmpty() == false`).
    //    "Generar código de vinculación" y "Ver Dispositivos Vinculados" quedan
    //    SIEMPRE habilitados para poder vincular un nuevo dispositivo.
    val hayHijosVinculados = hijosVinculados.isNotEmpty()


    // 1. 👂 Observar los hijos vinculados EN TIEMPO REAL (StateFlow).
    //    Si el padre desvincula/purga a un hijo en Firestore, la lista se vacía
    //    sola y la UI reacciona en el acto (bloqueo condicional de botones).
    LaunchedEffect(uidPadre) {
        if (uidPadre != null) {
            vistaModelo.observarHijosVinculados(uidPadre)
        } else {
            vistaModelo.detenerObservacionHijos()
        }
    }

    // 2. Lógica para obtener y guardar el token de FCM cuando el padre inicie sesión
    LaunchedEffect(uidPadre) {
        if (uidPadre != null) {
            coroutineScope.launch {
                val fcmService = MiFirebaseMessagingService()
                val token = fcmService.obtenerTokenFCM()
                if (token != null) {
                    fcmService.enviarTokenAFirestore(token)
                } else {
                    Log.e("PantallaPrincipal", "No se pudo obtener el token de FCM.")
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF003366))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(Color(0xFFE6E6E6), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Text("Bienvenido 👋", fontSize = 24.sp, color = Color(0xFF003366), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            // Estado local para saber si está activada la alerta (por ahora por defecto en false)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📩 Alertas por correo",
                            fontSize = 18.sp,
                            color = Color(0xFF003366)
                        )
                        Text(
                            text = "Recibe notificaciones cuando tu hijo intente acceder a contenido bloqueado.",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                    }
                    Switch(
                        checked = alertaActivada,
                        onCheckedChange = { activado ->
                            uidPadre?.let { vistaModeloUsuario.actualizarEstadoAlerta(it, activado) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF006699),
                            uncheckedThumbColor = Color.LightGray
                        )
                    )
                }
            }


            Button(
                onClick = { onIrAVinculacionPadre() },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF006699),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generar código de vinculación", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onIrADispositivosVinculados() },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF336699),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Ver Dispositivos Vinculados", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🚫 Sin hijos vinculados: aviso explícito. Los botones de monitoreo
            //    quedan deshabilitados; solo "Generar código de vinculación" y
            //    "Ver Dispositivos Vinculados" siguen operativos.
            if (!hayHijosVinculados) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "🚫 Sin dispositivos vinculados",
                            fontSize = 16.sp,
                            color = Color(0xFF8A6D3B)
                        )
                        Text(
                            text = "Genera un código de vinculación y actívalo en el dispositivo de tu hijo para habilitar las opciones de monitoreo.",
                            fontSize = 14.sp,
                            color = Color(0xFF8A6D3B)
                        )
                    }
                }
            }

            Button(
                onClick = { onIrAReporteApps(hijosVinculados) },
                enabled = hayHijosVinculados,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6699CC),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver historial de uso", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onIrAInformeAppsMasUsadas(hijosVinculados) },
                enabled = hayHijosVinculados,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6699CC),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apps Más Usadas", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onIrARegistroBloqueos(hijosVinculados) },
                enabled = hayHijosVinculados,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6699CC),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Registro de Bloqueos", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onIrAProgramarRestricciones(hijosVinculados) },
                enabled = hayHijosVinculados,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF99CCFF),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Programar restricciones", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onIrAControlApps(hijosVinculados) },
                enabled = hayHijosVinculados,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF99CCFF),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Control de aplicaciones", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onIrAResumenTiempoPantalla(hijosVinculados) },
                enabled = hayHijosVinculados,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF336699),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Resumen de Tiempo de Pantalla", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onIrAUbicacionTiempoReal(hijosVinculados) },
                enabled = hayHijosVinculados,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF006666),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📍 Ubicación en Tiempo Real", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    onCerrarSesion()
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF003366),
                    disabledContainerColor = Color(0xFFB0BEC5),
                    disabledContentColor = Color(0xFF607D8B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar sesión", color = Color.White)
            }
        }
    }
}
