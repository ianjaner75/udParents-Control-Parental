package com.example.udparents.vista.pantallas

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.udparents.navegacion.findActivity
import com.example.udparents.seguridad.AdminReceiver
import com.example.udparents.utilidades.ModoSigiloso
import com.example.udparents.servicio.RegistroUsoService
import com.example.udparents.utilidades.SharedPreferencesUtil
import com.example.udparents.viewmodel.VistaModeloVinculacion
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaVinculacionHijo(
    vistaModelo: VistaModeloVinculacion,
    onVolverAlPadre: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val auth = FirebaseAuth.getInstance()
    val coroutineScope = rememberCoroutineScope()

    var uidHijo by remember { mutableStateOf(auth.currentUser?.uid) }

    // ══════════════════════════════════════════════════════════════════════════
    // 🔗 CADENA SECUENCIAL DE 5 PERMISOS OBLIGATORIOS DEL HIJO
    //    1) Uso de datos (AppOps GET_USAGE_STATS)  → intacto
    //    2) Accesibilidad (servicio de bloqueo)    → intacto
    //    3) Notificaciones (POST_NOTIFICATIONS, Android 13+)
    //    4) Ubicación (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION y guía a
    //       ACCESS_BACKGROUND_LOCATION → «Permitir todo el tiempo»)
    //    5) Administrador de Dispositivos          → intacto
    //
    //    El orden es CONTRACTUAL: solo se muestra el diálogo del PRIMER permiso
    //    pendiente y no se avanza al siguiente hasta que el anterior está
    //    concedido. Al tener los 5, se oculta el ícono (Stealth Mode) y se
    //    cierra la actividad con finishAndRemoveTask().
    // ══════════════════════════════════════════════════════════════════════════
    val permisoUsoApps = remember { mutableStateOf(verificarPermisoUsoApps(context)) }
    val permisoAccesibilidad = remember { mutableStateOf(verificarPermisoAccesibilidad(context)) }
    val permisoNotificaciones = remember { mutableStateOf(verificarPermisoNotificaciones(context)) }
    val permisoUbicacion = remember { mutableStateOf(verificarPermisoUbicacion(context)) }
    val permisoUbicacionFondo = remember { mutableStateOf(verificarPermisoUbicacionFondo(context)) }
    val permisoAdmin = remember { mutableStateOf(isDeviceAdminActive(context)) }

    val codigoVinculacion by vistaModelo.codigoVinculacion.collectAsState()
    var mensajeError by remember { mutableStateOf("") }
    var mostrarDialogoPermisoUso by remember { mutableStateOf(false) }
    var mostrarDialogoAccesibilidad by remember { mutableStateOf(false) }
    var mostrarDialogoNotificaciones by remember { mutableStateOf(false) }
    var mostrarDialogoUbicacion by remember { mutableStateOf(false) }
    var mostrarDialogoUbicacionFondo by remember { mutableStateOf(false) }
    var mostrarDialogoAdmin by remember { mutableStateOf(false) }
    var mostrarDialogoExito by remember { mutableStateOf(false) }
    var vinculacionIniciada by remember { mutableStateOf(false) }
    var vinculacionCompletada by remember { mutableStateOf(false) }
    var mostrarTerminosDialog by remember { mutableStateOf(false) }

    /** ♻️ Re-lee el estado REAL de los 5 permisos (fuente de verdad: el sistema). */
    val refrescarEstadoPermisos: () -> Unit = {
        permisoUsoApps.value = verificarPermisoUsoApps(context)
        permisoAccesibilidad.value = verificarPermisoAccesibilidad(context)
        permisoNotificaciones.value = verificarPermisoNotificaciones(context)
        permisoUbicacion.value = verificarPermisoUbicacion(context)
        permisoUbicacionFondo.value = verificarPermisoUbicacionFondo(context)
        permisoAdmin.value = isDeviceAdminActive(context)
    }

    /**
     * 🔁 Evaluación de la cadena: recalcula el estado real, abre ÚNICAMENTE el
     * diálogo del primer permiso pendiente y, si ya no queda ninguno, ejecuta el
     * cierre sigiloso (Stealth Mode + finishAndRemoveTask). Es idempotente.
     */
    val aplicarCadenaPermisos: () -> Unit = {
        refrescarEstadoPermisos()
        val pendiente = primerPermisoPendiente(
            usoDatos = permisoUsoApps.value,
            accesibilidad = permisoAccesibilidad.value,
            notificaciones = permisoNotificaciones.value,
            ubicacion = permisoUbicacion.value,
            ubicacionFondo = permisoUbicacionFondo.value,
            administrador = permisoAdmin.value
        )
        // Solo el primer permiso pendiente de la cadena queda visible.
        mostrarDialogoPermisoUso = pendiente == PasoPermisoHijo.USO_DATOS
        mostrarDialogoAccesibilidad = pendiente == PasoPermisoHijo.ACCESIBILIDAD
        mostrarDialogoNotificaciones = pendiente == PasoPermisoHijo.NOTIFICACIONES
        mostrarDialogoUbicacion = pendiente == PasoPermisoHijo.UBICACION
        mostrarDialogoUbicacionFondo = pendiente == PasoPermisoHijo.UBICACION_FONDO
        mostrarDialogoAdmin = pendiente == PasoPermisoHijo.ADMINISTRADOR

        if (pendiente == null && !vinculacionCompletada) {
            vinculacionCompletada = true
            mostrarDialogoExito = true
            iniciarServicioRegistroUso(context)
            // 🧹 Purga visual inmediata del ícono (caché de Samsung One UI):
            // se ejecuta aquí, desde la UI, en cuanto los 5 permisos están
            // otorgados, sin esperar nada ni requerir acción del usuario.
            ModoSigiloso.ocultarIconoApp(context)
            coroutineScope.launch {
                delay(3000)
                activity?.finishAndRemoveTask()
            }
        }
    }

    // 🔔 Paso 3: callback del diálogo de sistema de notificaciones (Android 13+).
    val lanzadorNotificaciones = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (vinculacionIniciada) aplicarCadenaPermisos()
    }

    // 📍 Paso 4: callback del diálogo de sistema de ubicación (precisa + aproximada).
    val lanzadorUbicacion = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (vinculacionIniciada) aplicarCadenaPermisos()
    }

    // 🕓 Paso 4-bis: callback del permiso de ubicación en segundo plano
    // («Permitir todo el tiempo»). En Android 11+ el sistema redirige a los
    // ajustes de ubicación de la app para que el usuario elija esa opción.
    val lanzadorUbicacionFondo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (vinculacionIniciada) aplicarCadenaPermisos()
    }

    val solicitarPermisoNotificaciones: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lanzadorNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // En Android < 13 el permiso se concede al instalar: se avanza solo.
            aplicarCadenaPermisos()
        }
    }

    val solicitarPermisoUbicacion: () -> Unit = {
        lanzadorUbicacion.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val solicitarPermisoUbicacionFondo: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Se solicita EN SOLITARIO (requisito de Android 11+): el sistema
            // muestra la guía para elegir «Permitir todo el tiempo».
            lanzadorUbicacionFondo.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            // En Android < 10 la ubicación en segundo plano viene implícita.
            aplicarCadenaPermisos()
        }
    }

    val nombreHijo = codigoVinculacion?.nombreHijo.orEmpty()
    val nombreNormalizado = remember(nombreHijo) {
        nombreHijo.trim().replace("\\s+".toRegex(), " ")
    }
    val partes = nombreNormalizado.split(" ")
    val tieneNombreApellido = partes.size >= 2 && partes[0].length >= 2 && partes[1].length >= 2
    val largoOk = nombreNormalizado.replace(" ", "").length >= 10
    val nombreValido = nombreHijo.isNotBlank() && tieneNombreApellido && largoOk
    val edadValida = (codigoVinculacion?.edadHijo ?: 0) in 1..17
    val sexoTexto = codigoVinculacion?.sexoHijo.orEmpty()
    val sexoValido = sexoTexto.trim().equals("m", true) || sexoTexto.trim().equals("f", true) ||
            sexoTexto.trim().equals("masculino", true) || sexoTexto.trim().equals("femenino", true)
    val codigoValido = (codigoVinculacion?.codigo?.length == 6)
    val termsAceptados = codigoVinculacion?.termsAccepted == true
    val formularioValido = codigoValido && nombreValido && edadValida && sexoValido && termsAceptados

    // ══════════════════════════════════════════════════════════════════════════
    // 📷 VINCULACIÓN POR CÓDIGO QR
    //    Complementa — NUNCA reemplaza — la vinculación manual por texto: el
    //    escaneo solo rellena el mismo campo del código y reutiliza el mismo
    //    flujo de vinculación.
    // ══════════════════════════════════════════════════════════════════════════
    var mostrarEscanerQr by remember { mutableStateOf(false) }
    var mensajeQr by remember { mutableStateOf("") }

    /** ✅ Valida el formulario con un código concreto (p. ej. el recién escaneado). */
    fun formularioValidoConCodigo(codigo: String): Boolean =
        codigo.trim().length == 6 && nombreValido && edadValida && sexoValido && termsAceptados

    /** 🚀 Ejecuta la vinculación con los datos actuales del formulario. */
    val ejecutarVinculacion: () -> Unit = {
        vistaModelo.vincularHijoConDatos(
            context = context,
            onExito = { uidPadre ->
                mensajeError = ""
                vinculacionIniciada = true
                SharedPreferencesUtil.guardarUidPadre(context, uidPadre)
                Log.d("PantallaVinculacionHijo", "UID del padre guardado: $uidPadre")
                // 🔗 Arranca la cadena secuencial de los 5 permisos:
                // se abrirá el diálogo del primer permiso pendiente.
                aplicarCadenaPermisos()
            },
            onError = { mensajeError = it }
        )
    }

    // 🔓 Permiso de cámara: se solicita ANTES de abrir el escáner.
    val lanzadorPermisoCamara = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            mensajeQr = ""
            mostrarEscanerQr = true
        } else {
            mensajeQr = "Permiso de cámara denegado. Escribe el código de 6 dígitos manualmente."
        }
    }

    /** 📷 Abre el escáner (pidiendo el permiso de cámara si aún no está concedido). */
    val iniciarEscanerQr: () -> Unit = {
        val concedido = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (concedido) {
            mensajeQr = ""
            mostrarEscanerQr = true
        } else {
            lanzadorPermisoCamara.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * 🎯 Resultado del escáner: rellena el campo del código y, si el formulario
     * ya está completo, procede automáticamente a la vinculación.
     */
    val onCodigoQrDetectado: (String) -> Unit = { codigoEscaneado ->
        mostrarEscanerQr = false
        mensajeError = ""
        vistaModelo.actualizarCodigo(codigoEscaneado)
        if (formularioValidoConCodigo(codigoEscaneado)) {
            mensajeQr = "✅ Código QR detectado: $codigoEscaneado. Vinculando…"
            ejecutarVinculacion()
        } else {
            mensajeQr = "✅ Código QR detectado: $codigoEscaneado. Completa el perfil del hijo y pulsa «Vincular»."
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            // 🔁 Cada vez que el usuario vuelve de los ajustes del sistema se
            // reevalúa la cadena completa: si avanzó un permiso, aparece el
            // diálogo del siguiente; si ya están los 5, se cierra en sigilo.
            if (event == Lifecycle.Event.ON_RESUME && vinculacionIniciada) {
                aplicarCadenaPermisos()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnCompleteListener {
                if (it.isSuccessful) {
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

    // 🔄 Diálogos de permisos evaluados de forma ESTRICTAMENTE SECUENCIAL:
    //   1) Uso de apps → 2) Accesibilidad → 3) Notificaciones → 4) Ubicación
    //   (precisa/aproximada y, después, «todo el tiempo») → 5) Administrador.
    // Solo se muestra el PRIMER permiso pendiente de la cadena; los permisos ya
    // otorgados se saltan automáticamente gracias a la condición !permisoX.value.
    // El diálogo de éxito (y el Stealth Mode + finishAndRemoveTask) solo se
    // activa cuando los 5 permisos están otorgados.
    if (mostrarDialogoPermisoUso && !permisoUsoApps.value) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Permiso de uso",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Permiso de uso de apps requerido")
                }
            },
            text = { Text("Activa el permiso para acceder al uso de aplicaciones.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) { Text("Abrir ajustes") }
            }
        )
    } else if (mostrarDialogoAccesibilidad && !permisoAccesibilidad.value) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Accesibilidad",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Activar servicio de bloqueo")
                }
            },
            text = { Text("Activa el servicio UDParents en Accesibilidad para poder bloquear apps.") },
            confirmButton = {
                TextButton(onClick = { pedirPermisoAccesibilidad(context) }) {
                    Text("Ir a Accesibilidad")
                }
            }
        )
    } else if (mostrarDialogoNotificaciones && !permisoNotificaciones.value) {
        // 3️⃣ Notificaciones (Android 13+ · POST_NOTIFICATIONS)
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notificaciones",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Permiso de notificaciones")
                }
            },
            text = {
                Text("UdParents necesita enviarte notificaciones para avisarte al instante de los bloqueos y alertas del dispositivo de tu hijo.")
            },
            confirmButton = {
                TextButton(onClick = { solicitarPermisoNotificaciones() }) {
                    Text("Permitir notificaciones")
                }
            }
        )
    } else if (mostrarDialogoUbicacion && !permisoUbicacion.value) {
        // 4️⃣ Ubicación en primer plano (precisa + aproximada)
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Ubicación",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Permiso de ubicación")
                }
            },
            text = {
                Text("UdParents reporta la ubicación del dispositivo de tu hijo para que puedas verla en el mapa en tiempo real. Selecciona «Permitir» o «Permitir solo esta vez» cuando el sistema lo pregunte.")
            },
            confirmButton = {
                TextButton(onClick = { solicitarPermisoUbicacion() }) {
                    Text("Permitir ubicación")
                }
            }
        )
    } else if (mostrarDialogoUbicacionFondo && !permisoUbicacionFondo.value) {
        // 4️⃣-bis Ubicación en segundo plano → «Permitir todo el tiempo»
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Ubicación en segundo plano",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Ubicación «Permitir todo el tiempo»")
                }
            },
            text = {
                Text("Para seguir reportando la ubicación aunque la app esté cerrada o la pantalla apagada, elige «Permitir todo el tiempo» en los ajustes de ubicación de UdParents.")
            },
            confirmButton = {
                TextButton(onClick = { solicitarPermisoUbicacionFondo() }) {
                    Text("Permitir todo el tiempo")
                }
            },
            dismissButton = {
                TextButton(onClick = { abrirAjustesUbicacionApp(context) }) {
                    Text("Abrir ajustes")
                }
            }
        )
    } else if (mostrarDialogoAdmin && !permisoAdmin.value) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Administrador de dispositivo",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Administrador de dispositivo")
                }
            },
            text = {
                Text("Activa UdParents como administrador para impedir su desinstalación sin autorización.")
            },
            confirmButton = {
                TextButton(onClick = {
                    solicitarActivacionDeviceAdmin(context)
                }) { Text("Activar administrador") }
            }
        )
    }

    if (mostrarDialogoExito) {
        AlertDialog(
            onDismissRequest = { cerrarActividadConRetraso(coroutineScope, activity) },
            title = { Text("Vinculación exitosa") },
            text = { Text("El dispositivo fue vinculado correctamente. Cerrando aplicación...") },
            confirmButton = {
                TextButton(onClick = { cerrarActividadConRetraso(coroutineScope, activity) }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (mostrarTerminosDialog) {
        val scrollState = rememberScrollState()
        AlertDialog(
            onDismissRequest = { mostrarTerminosDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Términos y Condiciones",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Términos y Condiciones")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 0.dp, max = 320.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        """
UdParents es una aplicación destinada exclusivamente a ayudar a madres, padres o acudientes a administrar el uso de aplicaciones en el dispositivo del menor bajo su cuidado.

• La app recolecta y procesa información de uso de aplicaciones con el único fin de aplicar límites de tiempo, bloqueos por horarios y alertas al acudiente.
• UdParents NO tiene fines maliciosos ni accede a contenido personal como mensajes, fotos o archivos, salvo lo estrictamente necesario para aplicar las funciones descritas.
• El acudiente es responsable de configurar adecuadamente la app y de informar al menor sobre su uso.
• La aceptación de estos términos autoriza a UdParents a registrar el consentimiento, la versión de términos aceptada y la fecha/hora del consentimiento.
• Puedes consultar, actualizar o retirar el consentimiento desinstalando la app o contactando al soporte del proyecto académico.

Al seleccionar “Acepto”, confirmas que eres el acudiente del menor y que autorizas el uso descrito.
                    """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vistaModelo.actualizarTermsAceptados(true)
                    mostrarTerminosDialog = false
                }) {
                    Text("Acepto")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarTerminosDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 📦 Box raíz: permite superponer el escáner de QR a pantalla completa.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Vinculación del dispositivo") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                value = codigoVinculacion?.codigo ?: "",
                onValueChange = { raw ->
                    mensajeError = ""
                    // ✍️ Al escribir manualmente se limpia el aviso del escaneo.
                    mensajeQr = ""
                    val soloDigitos = raw.filter { it.isDigit() }.take(6)
                    vistaModelo.actualizarCodigo(soloDigitos)
                },
                    label = { Text("Código de vinculación (6 dígitos)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Código") },
                    trailingIcon = {
                        // 📷 Icono de cámara: abre el escáner de QR.
                        IconButton(onClick = { iniciarEscanerQr() }) {
                            Icon(
                                imageVector = Icons.Filled.QrCodeScanner,
                                contentDescription = "Escanear QR",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = (codigoVinculacion?.codigo?.length ?: 0) in 1..5,
                    supportingText = {
                        val len = codigoVinculacion?.codigo?.length ?: 0
                        if (len in 1..5) Text("Debe tener 6 dígitos.", color = MaterialTheme.colorScheme.error)
                    }
                )

                // 📷 Botón explícito «Escanear QR» (alternativa al código manual).
                OutlinedButton(
                    onClick = { iniciarEscanerQr() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = "Escanear QR"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escanear QR")
                }

                if (mensajeQr.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mensajeQr,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = codigoVinculacion?.nombreHijo ?: "",
                    onValueChange = {
                        mensajeError = ""
                        vistaModelo.actualizarNombreHijo(it)
                    },
                    label = { Text("Nombre y apellido del hijo") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    isError = (codigoVinculacion?.nombreHijo?.isNotBlank() == true) && !nombreValido,
                    supportingText = {
                        if ((codigoVinculacion?.nombreHijo?.isNotBlank() == true) && !nombreValido) {
                            Text("Escribe nombre y apellido (mín. 10 letras en total).", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = codigoVinculacion?.edadHijo?.takeIf { it > 0 }?.toString() ?: "",
                    onValueChange = { txt ->
                        mensajeError = ""
                        val valor = txt.toIntOrNull()
                        if (valor == null) vistaModelo.actualizarEdadHijo(0)
                        else vistaModelo.actualizarEdadHijo(valor)
                    },
                    label = { Text("Edad del hijo (1–17)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = (codigoVinculacion?.edadHijo ?: 0) !in 1..17,
                    supportingText = {
                        if ((codigoVinculacion?.edadHijo ?: 0) !in 1..17) {
                            Text("Ingresa una edad válida entre 1 y 17.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                var abierto by remember { mutableStateOf(false) }
                val opcionesSexo = listOf("M", "F")
                ExposedDropdownMenuBox(
                    expanded = abierto,
                    onExpandedChange = { abierto = !abierto },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = codigoVinculacion?.sexoHijo ?: "",
                        onValueChange = { /* readOnly */ },
                        label = { Text("Sexo del hijo (M/F)") },
                        readOnly = true,
                        leadingIcon = { Icon(imageVector = Icons.Filled.Person, contentDescription = "Sexo") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
                        isError = sexoTexto.isNotBlank() && !sexoValido,
                        supportingText = {
                            if (sexoTexto.isBlank()) Text("Selecciona M o F.")
                            else if (!sexoValido) Text("Valor inválido. Selecciona M o F.", color = MaterialTheme.colorScheme.error)
                        }
                    )
                    ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                        opcionesSexo.forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text(opcion) },
                                onClick = {
                                    mensajeError = ""
                                    vistaModelo.actualizarSexoHijo(opcion)
                                    abierto = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = codigoVinculacion?.termsAccepted == true,
                        onCheckedChange = { checked ->
                            vistaModelo.actualizarTermsAceptados(checked)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Acepto los",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { mostrarTerminosDialog = true }) {
                            Text(
                                "Términos y Condiciones",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(0.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { ejecutarVinculacion() },
                    enabled = formularioValido,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Vincular")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { onVolverAlPadre() }) {
                    Text("Volver al menú principal")
                }

                if (mensajeError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(mensajeError, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // 📷 Escáner de QR (CameraX + ML Kit) superpuesto a la pantalla del hijo.
        //    Si el usuario cancela o la cámara falla, el código se escribe manualmente.
        if (mostrarEscanerQr) {
            EscanerQrOverlay(
                onCodigoDetectado = onCodigoQrDetectado,
                onCancelar = { mostrarEscanerQr = false }
            )
        }
    }
}

/**
 * 🔗 Pasos de la cadena secuencial de los 5 permisos obligatorios del hijo.
 * El ORDEN declarado aquí ES el orden de solicitud y es contractual:
 * ningún permiso se pide antes de que el anterior esté concedido.
 */
private enum class PasoPermisoHijo {
    USO_DATOS,        // 1) Uso de datos (AppOps GET_USAGE_STATS)
    ACCESIBILIDAD,    // 2) Accesibilidad (servicio de bloqueo)
    NOTIFICACIONES,   // 3) POST_NOTIFICATIONS (Android 13+)
    UBICACION,        // 4) ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
    UBICACION_FONDO,  // 4-bis) ACCESS_BACKGROUND_LOCATION → «Permitir todo el tiempo»
    ADMINISTRADOR     // 5) Administrador de Dispositivos
}

/**
 * Devuelve el PRIMER permiso pendiente respetando el orden de la cadena
 * (else-if secuencial), o `null` cuando los 5 permisos ya están concedidos.
 * Es la única fuente de verdad para decidir qué diálogo mostrar y cuándo
 * ocultar el ícono de la app.
 */
private fun primerPermisoPendiente(
    usoDatos: Boolean,
    accesibilidad: Boolean,
    notificaciones: Boolean,
    ubicacion: Boolean,
    ubicacionFondo: Boolean,
    administrador: Boolean
): PasoPermisoHijo? = when {
    !usoDatos -> PasoPermisoHijo.USO_DATOS
    !accesibilidad -> PasoPermisoHijo.ACCESIBILIDAD
    !notificaciones -> PasoPermisoHijo.NOTIFICACIONES
    !ubicacion -> PasoPermisoHijo.UBICACION
    !ubicacionFondo -> PasoPermisoHijo.UBICACION_FONDO
    !administrador -> PasoPermisoHijo.ADMINISTRADOR
    else -> null
}

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

fun verificarPermisoAccesibilidad(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(context.packageName)
}

fun pedirPermisoAccesibilidad(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * 3️⃣ Notificaciones.
 * Android 13+ (API 33) exige POST_NOTIFICATIONS en tiempo de ejecución; en
 * versiones anteriores el permiso se concede al instalar la app, por lo que el
 * paso se considera automáticamente satisfecho.
 */
fun verificarPermisoNotificaciones(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 4️⃣ Ubicación en primer plano.
 * Se solicitan juntas ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION (obligatorio
 * desde Android 12); se considera concedido si el usuario otorgó la ubicación
 * precisa o la aproximada.
 */
fun verificarPermisoUbicacion(context: Context): Boolean {
    val precisa = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val aproximada = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return precisa || aproximada
}

/**
 * 4️⃣-bis Ubicación en segundo plano («Permitir todo el tiempo»).
 * Solo existe como permiso diferenciado desde Android 10 (API 29); en versiones
 * anteriores se concede junto con la ubicación en primer plano.
 */
fun verificarPermisoUbicacionFondo(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 🕓 Abre los ajustes de la app como respaldo cuando el diálogo del sistema para
 * «Permitir todo el tiempo» no puede mostrarse (p. ej. tras una denegación
 * permanente). Guía al usuario al permiso de ubicación de UdParents.
 */
fun abrirAjustesUbicacionApp(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("PantallaVinculacionHijo", "No se pudieron abrir los ajustes de la app: ${e.message}", e)
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: Exception) {
            Toast.makeText(
                context,
                "Abre Ajustes → Aplicaciones → UdParents → Permisos → Ubicación.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/**
 * 🕶️ Cierra la actividad con un pequeño retraso (500 ms) después de que el
 * usuario aceptó el Administrador de Dispositivos y se guardaron las
 * preferencias de sesión. La pausa da tiempo a que el estado
 * COMPONENT_ENABLED_STATE_DISABLED del alias se propague a nivel de sistema y
 * el Launcher (p. ej. Samsung One UI) elimine el ícono fantasma antes de que
 * la app salga del primer plano.
 */
private fun cerrarActividadConRetraso(scope: CoroutineScope, activity: Activity?) {
    scope.launch {
        delay(500)
        // 🧹 finishAndRemoveTask(): además de cerrar, elimina la tarea de la
        // vista de recientes y del lanzador, ayudando a que Samsung One UI
        // suelte el ícono fantasma tras el Stealth Mode.
        activity?.finishAndRemoveTask()
    }
}

fun iniciarServicioRegistroUso(context: Context) {
    val intent = Intent(context, RegistroUsoService::class.java)
    Log.d("PantallaVinculacionHijo", "Iniciando servicio de registro de uso")
    // 🛡️ El servicio se inicia AQUÍ, mientras la actividad aún está visible y
    // ANTES de entrar en Stealth Mode (el ícono solo lo oculta el servicio una
    // vez promovido a foreground). En Android 12+ el sistema puede rechazar el
    // arranque como foreground service; se captura el error y se usa startService
    // como respaldo para que el flujo de vinculación NUNCA crashee.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e("PantallaVinculacionHijo", "⚠️ startForegroundService bloqueado por el sistema: ${e.message}. Usando startService como respaldo.")
            try {
                context.startService(intent)
            } catch (e2: Exception) {
                Log.e("PantallaVinculacionHijo", "❌ No se pudo iniciar el servicio: ${e2.message}", e2)
            }
        }
    } else {
        context.startService(intent)
    }
}

fun isDeviceAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(DevicePolicyManager::class.java)
    val cn = ComponentName(context, AdminReceiver::class.java)
    return dpm?.isAdminActive(cn) == true
}

/**
 * 🔒 Solicita la activación del Administrador de Dispositivos.
 *
 * 🛡️ DEBE ejecutarse únicamente desde la UI (botón del diálogo de Administrador
 * en esta pantalla), con la actividad en PRIMER PLANO y el usuario interactuando.
 * Nunca debe lanzarse desde un servicio: Android 14 bloquea los lanzamientos de
 * actividad en segundo plano (Background Activity Launch) para
 * ACTION_ADD_DEVICE_ADMIN. [startIntentSafely] resuelve y utiliza la Actividad
 * actual para el lanzamiento.
 */
fun solicitarActivacionDeviceAdmin(context: Context) {
    val dpm = context.getSystemService(DevicePolicyManager::class.java)
    val cn = ComponentName(context, AdminReceiver::class.java)
    if (dpm?.isAdminActive(cn) == true) {
        Toast.makeText(context, "Administrador de dispositivo ya está activo", Toast.LENGTH_SHORT).show()
        Log.d("AdminIntent", "Ya activo, no se abre nada")
        return
    }
    val addIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "UdParents necesita este permiso para impedir que se desinstale sin autorización."
        )
    }
    if (startIntentSafely(context, addIntent)) {
        Log.d("AdminIntent", "Lanzado ACTION_ADD_DEVICE_ADMIN")
        return
    } else {
        Log.w("AdminIntent", "Fallo ACTION_ADD_DEVICE_ADMIN")
    }
    val adminsIntent = Intent("android.settings.ACTION_DEVICE_ADMIN_SETTINGS")
    if (startIntentSafely(context, adminsIntent)) {
        Toast.makeText(context, "Abriendo \"Administradores de dispositivo\"…", Toast.LENGTH_SHORT).show()
        Log.d("AdminIntent", "Lanzado ACTION_DEVICE_ADMIN_SETTINGS (literal)")
        return
    } else {
        Log.w("AdminIntent", "Fallo abrir ACTION_DEVICE_ADMIN_SETTINGS (literal)")
    }
    val securityIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
    if (startIntentSafely(context, securityIntent)) {
        Toast.makeText(context, "Ve a \"Administradores de dispositivo\" y activa UdParents.", Toast.LENGTH_LONG).show()
        Log.d("AdminIntent", "Lanzado ACTION_SECURITY_SETTINGS (fallback final)")
    } else {
        Log.e("AdminIntent", "No se pudo abrir ninguna pantalla de admin")
        Toast.makeText(context, "No se pudo abrir la activación del administrador.", Toast.LENGTH_LONG).show()
    }
}

private fun startIntentSafely(context: Context, intent: Intent): Boolean {
    val pm = context.packageManager
    val canHandle = intent.resolveActivity(pm) != null
    if (!canHandle) return false
    // 🛡️ Android 14 bloquea los lanzamientos de actividad desde contextos que
    // no están en primer plano (Background Activity Launch). Se prioriza SIEMPRE
    // la Actividad actual y visible (activity.startActivity) para lanzar el intent,
    // incluyendo el de ACTION_ADD_DEVICE_ADMIN.
    val actividad = context.findActivity()
    return try {
        if (actividad != null) {
            actividad.startActivity(intent)
        } else if (context is Activity) {
            context.startActivity(intent)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        true
    } catch (t: Throwable) {
        false
    }
}