package com.example.udparents.vista.pantallas

import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.udparents.seguridad.AdminReceiver
import com.example.udparents.servicio.RegistroUsoService
import com.example.udparents.utilidades.SharedPreferencesUtil
import com.example.udparents.viewmodel.VistaModeloVinculacion
import com.google.firebase.auth.FirebaseAuth
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

    val permisoUsoApps = remember { mutableStateOf(verificarPermisoUsoApps(context)) }
    val permisoAccesibilidad = remember { mutableStateOf(verificarPermisoAccesibilidad(context)) }
    val permisoAdmin = remember { mutableStateOf(isDeviceAdminActive(context)) }

    val codigoVinculacion by vistaModelo.codigoVinculacion.collectAsState()
    var mensajeError by remember { mutableStateOf("") }
    var mostrarDialogoPermisoUso by remember { mutableStateOf(false) }
    var mostrarDialogoAccesibilidad by remember { mutableStateOf(false) }
    var mostrarDialogoAdmin by remember { mutableStateOf(false) }
    var mostrarDialogoExito by remember { mutableStateOf(false) }
    var vinculacionIniciada by remember { mutableStateOf(false) }
    var mostrarTerminosDialog by remember { mutableStateOf(false) }

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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && vinculacionIniciada) {
                permisoUsoApps.value = verificarPermisoUsoApps(context)
                permisoAccesibilidad.value = verificarPermisoAccesibilidad(context)
                permisoAdmin.value = isDeviceAdminActive(context)

                if (permisoUsoApps.value && permisoAccesibilidad.value && permisoAdmin.value) {
                    ocultarTodosLosDialogos(
                        setUso = { mostrarDialogoPermisoUso = it },
                        setAcc = { mostrarDialogoAccesibilidad = it },
                        setAdm = { mostrarDialogoAdmin = it },
                    )
                    mostrarDialogoExito = true
                    iniciarServicioRegistroUso(context)
                    coroutineScope.launch {
                        delay(3000)
                        activity?.finish()
                    }
                } else {
                    mostrarDialogoPermisoUso = !permisoUsoApps.value
                    mostrarDialogoAccesibilidad = !permisoAccesibilidad.value
                    mostrarDialogoAdmin = !permisoAdmin.value
                }
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
    }

    if (mostrarDialogoAccesibilidad && !permisoAccesibilidad.value) {
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
    }

    if (mostrarDialogoAdmin && !permisoAdmin.value) {
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
            onDismissRequest = { activity?.finish() },
            title = { Text("Vinculación exitosa") },
            text = { Text("El dispositivo fue vinculado correctamente. Cerrando aplicación...") },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) {
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
                    val soloDigitos = raw.filter { it.isDigit() }.take(6)
                    vistaModelo.actualizarCodigo(soloDigitos)
                },
                label = { Text("Código de vinculación (6 dígitos)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Código") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = (codigoVinculacion?.codigo?.length ?: 0) in 1..5,
                supportingText = {
                    val len = codigoVinculacion?.codigo?.length ?: 0
                    if (len in 1..5) Text("Debe tener 6 dígitos.", color = MaterialTheme.colorScheme.error)
                }
            )

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
                onClick = {
                    vistaModelo.vincularHijoConDatos(
                        context = context,
                        onExito = { uidPadre ->
                            mensajeError = ""
                            vinculacionIniciada = true
                            SharedPreferencesUtil.guardarUidPadre(context, uidPadre)
                            Log.d("PantallaVinculacionHijo", "UID del padre guardado: $uidPadre")
                            permisoUsoApps.value = verificarPermisoUsoApps(context)
                            permisoAccesibilidad.value = verificarPermisoAccesibilidad(context)
                            permisoAdmin.value = isDeviceAdminActive(context)
                            mostrarDialogoPermisoUso = !permisoUsoApps.value
                            mostrarDialogoAccesibilidad = !permisoAccesibilidad.value
                            mostrarDialogoAdmin = !permisoAdmin.value
                            if (permisoUsoApps.value && permisoAccesibilidad.value && permisoAdmin.value) {
                                mostrarDialogoExito = true
                                iniciarServicioRegistroUso(context)
                            }
                        },
                        onError = { mensajeError = it }
                    )
                },
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
}

private fun ocultarTodosLosDialogos(
    setUso: (Boolean) -> Unit,
    setAcc: (Boolean) -> Unit,
    setAdm: (Boolean) -> Unit
) {
    setUso(false); setAcc(false); setAdm(false)
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

fun iniciarServicioRegistroUso(context: Context) {
    val intent = Intent(context, RegistroUsoService::class.java)
    Log.d("PantallaVinculacionHijo", "Iniciando servicio de registro de uso")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun isDeviceAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(DevicePolicyManager::class.java)
    val cn = ComponentName(context, AdminReceiver::class.java)
    return dpm?.isAdminActive(cn) == true
}

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
    return try {
        if (context is Activity) {
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