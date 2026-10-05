package com.example.udparents.vista.pantallas

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.udparents.viewmodel.VistaModeloVinculacion
import com.example.udparents.servicio.MonitorDesvinculacion
import com.example.udparents.utilidades.SesionHijoStore
import com.example.udparents.utilidades.VisibilidadLauncher
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import com.example.udparents.servicio.RegistroUsoService

@Composable
fun PantallaVinculacionHijo(
    vistaModelo: VistaModeloVinculacion,
    onVolverAlPadre: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val auth = FirebaseAuth.getInstance()

    var uidHijo by remember { mutableStateOf(auth.currentUser?.uid) }
    val permisoOtorgado = remember { mutableStateOf(verificarPermisoUsoApps(context)) }

    val codigoVinculacion by vistaModelo.codigoVinculacion.collectAsState()
    var mensajeError by remember { mutableStateOf("") }
    var mostrarDialogoPermiso by remember { mutableStateOf(false) }
    var mostrarDialogoExito by remember { mutableStateOf(false) }
    var vinculacionCompleta by remember { mutableStateOf(false) }
    var sesionHijoActiva by remember(context) {
        mutableStateOf(SesionHijoStore.obtener(context) != null)
    }

    // Una vez vinculado, salir al fondo en vez de volver al selector de roles.
    BackHandler(enabled = sesionHijoActiva) { activity?.finish() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permisoOtorgado.value = verificarPermisoUsoApps(context)

                if (permisoOtorgado.value && vinculacionCompleta && !mostrarDialogoExito) {
                    mostrarDialogoExito = true

                    // 🔁 Iniciar el servicio de registro de uso
                    val intentServicio = Intent(context, RegistroUsoService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intentServicio)
                    } else {
                        context.startService(intentServicio)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Sesión anónima
    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val nuevoUid = auth.currentUser?.uid
                    uidHijo = nuevoUid
                    vistaModelo.actualizarDispositivoHijo(nuevoUid)
                }
            }
        } else {
            uidHijo = auth.currentUser?.uid
            vistaModelo.actualizarDispositivoHijo(uidHijo)
        }
    }

    // Diálogo para pedir permiso
    if (mostrarDialogoPermiso && !permisoOtorgado.value) {
        AlertDialog(
            onDismissRequest = { activity?.finish() },
            title = { Text("Permiso requerido") },
            text = { Text("Debes conceder acceso al uso de aplicaciones para poder registrar la actividad.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) {
                    Text("Abrir ajustes")
                }
            }
        )
    }

    // Diálogo de éxito
    if (mostrarDialogoExito && permisoOtorgado.value) {
        AlertDialog(
            onDismissRequest = { activity?.finish() },
            title = { Text("Vinculación exitosa") },
            text = { Text("El dispositivo ha sido vinculado correctamente.") },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) {
                    Text("Cerrar")
                }
            }
        )

        // También cerrar automáticamente en 3 segundos
        LaunchedEffect(Unit) {
            delay(3000)
            activity?.finish()
        }
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Vinculación del dispositivo", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = codigoVinculacion?.codigo ?: "",
            onValueChange = { vistaModelo.actualizarCodigo(it) },
            label = { Text("Código de vinculación") },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = codigoVinculacion?.nombreHijo ?: "",
            onValueChange = { vistaModelo.actualizarNombreHijo(it) },
            label = { Text("Nombre del hijo") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = codigoVinculacion?.edadHijo?.toString() ?: "",
            onValueChange = {
                vistaModelo.actualizarEdadHijo(it.toIntOrNull() ?: 0)
            },
            label = { Text("Edad del hijo") },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = codigoVinculacion?.sexoHijo ?: "",
            onValueChange = { vistaModelo.actualizarSexoHijo(it) },
            label = { Text("Sexo del hijo (M/F)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (codigoVinculacion?.codigo.isNullOrBlank() ||
                    codigoVinculacion?.nombreHijo.isNullOrBlank() ||
                    codigoVinculacion?.edadHijo!! <= 0 ||
                    codigoVinculacion?.sexoHijo.isNullOrBlank()
                ) {
                    mensajeError = "Por favor completa todos los campos."
                    return@Button
                }

                vistaModelo.vincularHijoConDatos(
                    context = context,
                    onExito = {
                        val codigo = codigoVinculacion?.codigo.orEmpty()
                        val uid = codigoVinculacion?.dispositivoHijo
                            ?.takeIf { it.isNotBlank() }
                            ?: uidHijo.orEmpty()

                        if (!SesionHijoStore.guardar(context, codigo, uid)) {
                            mensajeError = "No se pudo guardar la sesión del dispositivo. Inténtalo de nuevo."
                            return@vincularHijoConDatos
                        }

                        sesionHijoActiva = true
                        VisibilidadLauncher.ocultar(context)
                        MonitorDesvinculacion.iniciar(context)
                        mensajeError = ""
                        vinculacionCompleta = true
                        if (!permisoOtorgado.value) {
                            mostrarDialogoPermiso = true
                        } else {
                            mostrarDialogoExito = true

                            // 🔁 Iniciar servicio si ya hay permiso
                            val intentServicio = Intent(context, RegistroUsoService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intentServicio)
                            } else {
                                context.startService(intentServicio)
                            }
                        }
                    },
                    onError = { mensajeError = it }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Vincular")
        }

        if (!sesionHijoActiva) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { onVolverAlPadre() }) {
                Text("Volver al menú principal")
            }
        }

        if (mensajeError.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(mensajeError, color = Color.Red)
        }
    }
}

// ✅ Verifica si el permiso de uso de apps está habilitado
fun verificarPermisoUsoApps(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
