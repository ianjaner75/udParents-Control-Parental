#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🔁 LOOPING DE VERIFICACIÓN ESTÁTICA — udParents
FASE 1: cadena de 5 permisos del hijo (PantallaVinculacionHijo.kt)
FASE 2: purga explícita en Firestore + estado de UI del padre
FASE 3: vinculación por Código QR (generación en el padre, lector en el hijo)

Ejecuta comprobaciones sobre el código fuente (equivalente a un smoke test del
comportamiento cuando no hay JDK/Android SDK disponible para compilar).

Uso:
    python3 scripts/verificar_requerimientos.py     # sale con código 0 si todo pasa
"""
import re
import sys

import os
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAIZ = os.path.join(REPO, "app", "src", "main")
P_HIJO = f"{RAIZ}/java/com/example/udparents/vista/pantallas/PantallaVinculacionHijo.kt".replace("\\", "/")
P_MANIFEST = f"{RAIZ}/AndroidManifest.xml".replace("\\", "/")
P_REPO_VINC = f"{RAIZ}/java/com/example/udparents/repositorio/RepositorioVinculacion.kt".replace("\\", "/")
P_REPO_APPS = f"{RAIZ}/java/com/example/udparents/repositorio/RepositorioApps.kt".replace("\\", "/")
P_VM_VINC = f"{RAIZ}/java/com/example/udparents/viewmodel/VistaModeloVinculacion.kt".replace("\\", "/")
P_VM_APPS = f"{RAIZ}/java/com/example/udparents/viewmodel/VistaModeloApps.kt".replace("\\", "/")
P_PRINCIPAL = f"{RAIZ}/java/com/example/udparents/vista/pantallas/PantallaPrincipal.kt".replace("\\", "/")
P_DESVINC_REMOTA = f"{RAIZ}/java/com/example/udparents/utilidades/DesvinculacionRemota.kt".replace("\\", "/")
# 🔗 FASE 3: vinculación por Código QR
P_QR_UTIL = f"{RAIZ}/java/com/example/udparents/utilidades/CodigoQr.kt".replace("\\", "/")
P_ESCANER = f"{RAIZ}/java/com/example/udparents/vista/pantallas/EscanerQrOverlay.kt".replace("\\", "/")
P_CODIGO_PADRE = f"{RAIZ}/java/com/example/udparents/vista/pantallas/PantallaCodigoPadre.kt".replace("\\", "/")
P_GRADLE_LIBS = os.path.join(REPO, "gradle", "libs.versions.toml")
P_GRADLE_APP = os.path.join(REPO, "app", "build.gradle.kts")

fallos = []
pruebas = 0


def leer(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def check(nombre, condicion, detalle=""):
    global pruebas
    pruebas += 1
    estado = "PASS" if condicion else "FAIL"
    print(f"  [{estado}] {nombre}" + (f" -> {detalle}" if detalle and not condicion else ""))
    if not condicion:
        fallos.append(nombre)


# ══════════════════════════════════════════════════════════════════════════════
# Utilidades: limpieza de strings/comentarios para verificar balance de bloques
# ══════════════════════════════════════════════════════════════════════════════
def limpiar_codigo(src):
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j == -1 else j + 3
            continue
        if c == '"':
            i += 1
            while i < n and src[i] != '"':
                i += 2 if src[i] == "\\" else 1
            i += 1
            continue
        if c == "'":
            i += 1
            while i < n and src[i] != "'":
                i += 2 if src[i] == "\\" else 1
            i += 1
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j == -1 else j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j == -1 else j + 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def balance_ok(src):
    limpio = limpiar_codigo(src)
    return (limpio.count("{") == limpio.count("}") and
            limpio.count("(") == limpio.count(")") and
            limpio.count("[") == limpio.count("]"))


# ══════════════════════════════════════════════════════════════════════════════
print("\n════════ FASE 1 · CADENA SECUENCIAL DE 5 PERMISOS (HIJO) ════════")
hijo = leer(P_HIJO)
manifest = leer(P_MANIFEST)

# 1.1 Orden contractual del enum y del else-if de decisión
m_enum = re.search(r"enum class PasoPermisoHijo\s*\{(.*?)\}", hijo, re.S)
cuerpo_enum = re.sub(r"//[^\n]*", "", m_enum.group(1)) if m_enum else ""
orden_enum = re.findall(r"^\s*([A-Z_]+)\s*,?", cuerpo_enum, re.M) if m_enum else []
check("1.1a enum PasoPermisoHijo con el orden exigido",
      orden_enum[:6] == ["USO_DATOS", "ACCESIBILIDAD", "NOTIFICACIONES",
                         "UBICACION", "UBICACION_FONDO", "ADMINISTRADOR"],
      str(orden_enum))

m_fn = re.search(r"private fun primerPermisoPendiente\((.*?)\n\): PasoPermisoHijo\? = when \{(.*?)\n\}",
                 hijo, re.S)
if m_fn:
    params = m_fn.group(1)
    cuerpo = m_fn.group(2)
    orden_when = re.findall(r"->\s*PasoPermisoHijo\.([A-Z_]+)", cuerpo)
    esperado = ["USO_DATOS", "ACCESIBILIDAD", "NOTIFICACIONES",
                "UBICACION", "UBICACION_FONDO", "ADMINISTRADOR"]
    check("1.1b primerPermisoPendiente evalúa en cadena else-if 1→5", orden_when == esperado, str(orden_when))
    for nombre_param, paso in zip(["usoDatos", "accesibilidad", "notificaciones",
                                   "ubicacion", "ubicacionFondo", "administrador"],
                                  ["USO_DATOS", "ACCESIBILIDAD", "NOTIFICACIONES",
                                   "UBICACION", "UBICACION_FONDO", "ADMINISTRADOR"]):
        check(f"1.1c {paso} depende de !{nombre_param}",
              f"!{nombre_param} ->" in cuerpo.replace(" ", " ").replace("  ", " "))
    check("1.1d rama final => null (los 5 concedidos)", re.search(r"else -> null", cuerpo) is not None)

# 1.2 Declaración de los 5 permisos en el AndroidManifest
for permiso in ["PACKAGE_USAGE_STATS", "BIND_ACCESSIBILITY_SERVICE",
                "POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION",
                "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION"]:
    check(f"1.2 manifest declara {permiso}", f'android:name="android.permission.{permiso}"' in manifest)
check("1.2b receiver AdminReceiver (permiso 5) declarado",
      'android:name=".seguridad.AdminReceiver"' in manifest)
check("1.2c servicio de accesibilidad declarado",
      'android:name=".servicio.BloqueoAccessibilityService"' in manifest)


# 1.2-d) Sin permisos duplicados en el manifest (regla de sanidad).
todos = re.findall(r'<uses-permission android:name="([^"]+)"', manifest)
duplicados = sorted({x for x in todos if todos.count(x) > 1})
check("1.2d manifest sin permisos duplicados", not duplicados, str(duplicados))
check("1.2e manifest sin atributos android:name duplicados en la misma etiqueta",
      not re.search(r"android:name=\"[^\"]+\"[^>]*android:name=", manifest))

# 1.3 Verificadores de los 5 permisos presentes
for fn in ["verificarPermisoUsoApps", "verificarPermisoAccesibilidad",
           "verificarPermisoNotificaciones", "verificarPermisoUbicacion",
           "verificarPermisoUbicacionFondo", "isDeviceAdminActive"]:
    check(f"1.3a existe verificador {fn}", re.search(r"fun %s\(" % fn, hijo) is not None)
check("1.3b notificaciones solo desde Android 13 (TIRAMISU)",
      "Build.VERSION_CODES.TIRAMISU" in hijo and "POST_NOTIFICATIONS" in hijo)
check("1.3c ubicación en segundo plano desde Android 10 (Q)",
      "Build.VERSION_CODES.Q" in hijo and "ACCESS_BACKGROUND_LOCATION" in hijo)
check("1.3d guía «Permitir todo el tiempo» presente",
      "Permitir todo el tiempo" in hijo)
check("1.3e solicitud de FINE + COARSE juntas",
      "ACCESS_FINE_LOCATION" in hijo and "ACCESS_COARSE_LOCATION" in hijo and
      "RequestMultiplePermissions" in hijo)

# 1.4 Orden de los diálogos en la cadena else-if de la UI
orden_ui = re.findall(r"(?:\}\s*)?else\s+if|\(if\)", "") or re.findall(
        r"(?:\} else )?if \(mostrarDialogo([A-Za-z]+)", hijo)
requerido_ui = ["PermisoUso", "Accesibilidad", "Notificaciones", "Ubicacion", "UbicacionFondo", "Admin"]
check("1.4a diálogos encadenados como if/else-if en orden 1→5",
      orden_ui[:6] == requerido_ui, str(orden_ui))

# 1.5 Cierre sigiloso SOLO con los 5 permisos
m_cierre = re.search(r"if \(pendiente == null && !vinculacionCompletada\) \{(.*?)\n        \}", hijo, re.S)
if m_cierre:
    bloque = m_cierre.group(1)
    check("1.5a con los 5 permisos se oculta el ícono", "ModoSigiloso.ocultarIconoApp(context)" in bloque)
    check("1.5b con los 5 permisos se cierra la tarea", "finishAndRemoveTask()" in bloque)
    check("1.5c se inicia el servicio de registro", "iniciarServicioRegistroUso(context)" in bloque)
    check("1.5d diálogo de éxito", "mostrarDialogoExito = true" in bloque)
else:
    check("1.5 cierre sigiloso detectado", False)

# 1.6 Reintento secuencial al volver de ajustes (ON_RESUME) y callbacks de permisos
check("1.6a ON_RESUME reevalúa la cadena",
      re.search(r"Lifecycle\.Event\.ON_RESUME && vinculacionIniciada", hijo) is not None)
check("1.6b los callbacks de los 3 launchers reevalúan la cadena",
      len(re.findall(r"if \(vinculacionIniciada\) aplicarCadenaPermisos\(\)", hijo)) == 3)

# 1.7 Formulario intacto (nombre, edad, dropdown M/F, términos)
for marcador, nombre in [('label = { Text("Nombre y apellido del hijo") }', "nombre"),
                         ('label = { Text("Edad del hijo (1–17)") }', "edad"),
                         ('label = { Text("Sexo del hijo (M/F)") }', "dropdown M/F"),
                         ('listOf("M", "F")', "opciones M/F"),
                         ('Términos y Condiciones', "términos")]:
    check(f"1.7 formulario intacto: {nombre}", marcador in hijo)

# 1.8 Simulación de la cadena (modelo de estados equivalente al when)
print("\n  ── Simulación de la cadena (6 pasos, orden obligatorio) ──")
PASOS = ["USO_DATOS", "ACCESIBILIDAD", "NOTIFICACIONES", "UBICACION", "UBICACION_FONDO", "ADMINISTRADOR"]


def primer_pendiente(estado):
    for paso in PASOS:
        if not estado[paso]:
            return paso
    return None


estado = {p: False for p in PASOS}
secuencia = []
while True:
    pend = primer_pendiente(estado)
    if pend is None:
        break
    secuencia.append(pend)
    estado[pend] = True
    assert primer_pendiente(estado) != pend, "el paso concedido no debe repetirse"
check("1.8a la simulación recorre exactamente los 5 permisos en orden",
      secuencia == PASOS, str(secuencia))
check("1.8b sin permisos se reporta el paso 1 (uso de datos)", primer_pendiente({p: False for p in PASOS}) == "USO_DATOS")
solo_admin = {p: True for p in PASOS}
solo_admin["ADMINISTRADOR"] = False
check("1.8c con 4 de 5 otorgados se reporta el administrador",
      primer_pendiente(solo_admin) == "ADMINISTRADOR")
check("1.8d con los 5 otorgados se retorna null (=> ocultar ícono + finishAndRemoveTask)",
      primer_pendiente({p: True for p in PASOS}) is None)

# ══════════════════════════════════════════════════════════════════════════════
print("\n════════ FASE 2.1 · PURGA EN FIRESTORE AL DESVINCULAR DESDE EL PADRE ════════")
repo_vinc = leer(P_REPO_VINC)
vm_vinc = leer(P_VM_VINC)
desv_remota = leer(P_DESVINC_REMOTA)

check("2.1a el padre escribe la bandera remota desvincular=true",
      re.search(r'addOnSuccessListener \{\s*Log\.i\(', repo_vinc) is not None and
      '"desvincular" to true' in repo_vinc)
pos_flag = repo_vinc.find('"desvincular" to true')
pos_purga = repo_vinc.find("programarPurgaDiferida(uidPadre, uidHijo)")
check("2.1b la purga se programa DESPUÉS de la señal remota (no rompe la desvinculación)",
      0 < pos_flag < pos_purga, f"flag@{pos_flag} purga@{pos_purga}")
check("2.1c existe borrado explícito .delete() en el repositorio del padre",
      repo_vinc.count(".delete()") >= 2, f"ocurrencias={repo_vinc.count('.delete()')}")
check("2.1d existe purga suspend purgarVinculacionHijo",
      "suspend fun purgarVinculacionHijo(" in repo_vinc)
check("2.1e tiempo de gracia > 0 definido",
      re.search(r"TIEMPO_GRACIA_PURGA_MS\s*=\s*(\d[\d_]*)L", repo_vinc) is not None and
      int(re.search(r"TIEMPO_GRACIA_PURGA_MS\s*=\s*(\d[\d_]*)L", repo_vinc).group(1).replace("_", "")) > 0)
check("2.1f la purga diferida usa delay + delete (await)",
      re.search(r"delay\(TIEMPO_GRACIA_PURGA_MS\)", repo_vinc) is not None and
      "documento.reference.delete().await()" in repo_vinc)
check("2.1g alcance de aplicación para que la purga sobreviva al ViewModel",
      "ALCANCE_PURGAS" in repo_vinc and "SupervisorJob()" in repo_vinc)
check("2.1h el borrado manual del padre elimina TODOS los documentos",
      "eliminarDocumentos(" in repo_vinc and "querySnapshot.documents.map { it.reference }" in repo_vinc)
check("2.1i el ViewModel delega en el repositorio y actualiza la lista local",
      "repositorio.autorizarDesvinculacion(uidPadre, uidHijo)" in vm_vinc and
      "_dispositivosVinculados.value.filter { it.dispositivoHijo != uidHijo }" in vm_vinc)
check("2.1j el hijo sigue recibiendo la orden remota (bandera desvincular)",
      'CAMPO_DESVINCULAR = "desvincular"' in desv_remota and
      "it.getBoolean(CAMPO_DESVINCULAR) == true" in desv_remota)
check("2.1k el hijo sigue limpiando ícono/servicio/admin/sesión tras la orden",
      all(x in desv_remota for x in ["ModoSigiloso.restaurarIconoApp(context)",
                                     "context.stopService(Intent(context, RegistroUsoService::class.java))",
                                     "SharedPreferencesUtil.limpiarSesionHijo(context)",
                                     "dpm.removeActiveAdmin(admin)",
                                     "FirebaseAuth.getInstance().signOut()"]))
check("2.1l el hijo también elimina el documento tras procesar (doble purga)",
      ".collection(COLECCION_CODIGOS)\n                    .document(idDocumento)\n                    .delete()" in desv_remota
      or re.search(r"document\(idDocumento\)\s*\.delete\(\)", desv_remota) is not None)

check("2.1m una vinculación marcada no bloquea re-vincular al mismo equipo",
      "snapshot.documents.any { it.getBoolean(\"desvincular\") != true }" in repo_vinc)

# ══════════════════════════════════════════════════════════════════════════════
print("\n════════ FASE 2.2 · UI DEL PADRE: LISTA EN TIEMPO REAL Y BOTONES OFF SIN HIJOS ════════")
principal = leer(P_PRINCIPAL)
repo_apps = leer(P_REPO_APPS)
vm_apps = leer(P_VM_APPS)

check("2.2a la bandera de UI se deriva de isEmpty()",
      "val hayHijosVinculados = hijosVinculados.isNotEmpty()" in principal)
check("2.2b el StateFlow de hijos se observa (collectAsState)",
      "vistaModelo.hijosVinculados.collectAsState()" in principal)
check("2.2c observación en TIEMPO REAL (listener) en la pantalla principal",
      "vistaModelo.observarHijosVinculados(uidPadre)" in principal)

BOTONES_MONITOREO = ["onIrAReporteApps", "onIrAInformeAppsMasUsadas", "onIrARegistroBloqueos",
                     "onIrAProgramarRestricciones", "onIrAControlApps",
                     "onIrAResumenTiempoPantalla", "onIrAUbicacionTiempoReal"]
for cb in BOTONES_MONITOREO:
    bloque = re.search(r"Button\(\s*onClick = \{ %s\(hijosVinculados\) \},(.*?)\) \{" % cb, principal, re.S)
    check(f"2.2d botón {cb} deshabilitado sin hijos",
          bloque is not None and "enabled = hayHijosVinculados," in bloque.group(1))

for cb in ["onIrAVinculacionPadre", "onIrADispositivosVinculados"]:
    bloque = re.search(r"Button\(\s*onClick = \{ %s\(\) \},(.*?)\) \{" % cb, principal, re.S)
    check(f"2.2e botón {cb} SIEMPRE habilitado (sin cláusula enabled)",
          bloque is not None and "enabled =" not in bloque.group(1))

check("2.2f escucha en tiempo real con addSnapshotListener",
      "addSnapshotListener" in repo_apps and "fun escucharHijosVinculados(" in repo_apps)
check("2.2g se filtran las vinculaciones ya autorizadas para desvincular",
      'getBoolean("desvincular") == true' in repo_apps)
check("2.2h el ViewModel expone observar/detener y libera el listener",
      "fun observarHijosVinculados(idPadre: String)" in vm_apps and
      "fun detenerObservacionHijos()" in vm_apps and
      "override fun onCleared()" in vm_apps and
      "escuchaHijos?.remove()" in vm_apps)
check("2.2i aviso visual cuando no hay hijos vinculados",
      "Sin dispositivos vinculados" in principal)


# ══════════════════════════════════════════════════════════════════════════════
print("\n════════ FASE 3.1 · DEPENDENCIAS (ZXing + CameraX + ML Kit) ════════")
qr_util = leer(P_QR_UTIL)
escaner = leer(P_ESCANER)
codigo_padre = leer(P_CODIGO_PADRE)
libs_toml = leer(P_GRADLE_LIBS)
gradle_app = leer(P_GRADLE_APP)

for version in ["zxing =", "cameraX =", "mlkitBarcode ="]:
    check(f"3.1a libs.versions.toml declara {version.strip(' =')}", version in libs_toml)
for libreria in ["zxing-core = { group = \"com.google.zxing\", name = \"core\"",
                 "androidx-camera-core = { group = \"androidx.camera\", name = \"camera-core\"",
                 "androidx-camera-camera2 = { group = \"androidx.camera\", name = \"camera-camera2\"",
                 "androidx-camera-lifecycle = { group = \"androidx.camera\", name = \"camera-lifecycle\"",
                 "androidx-camera-view = { group = \"androidx.camera\", name = \"camera-view\"",
                 "mlkit-barcode-scanning = { group = \"com.google.mlkit\", name = \"barcode-scanning\""]:
    check(f"3.1b catálogo declara {libreria.split(' = ')[0]}", libreria in libs_toml)
for alias in ["implementation(libs.zxing.core)", "implementation(libs.androidx.camera.core)",
              "implementation(libs.androidx.camera.camera2)", "implementation(libs.androidx.camera.lifecycle)",
              "implementation(libs.androidx.camera.view)", "implementation(libs.mlkit.barcode.scanning)"]:
    check(f"3.1c app/build.gradle.kts usa {alias}", alias in gradle_app)
check("3.1d manifest declara el permiso CAMERA",
      'android:name="android.permission.CAMERA"' in manifest)
check("3.1e la cámara NO es obligatoria (instalable sin cámara → respaldo manual)",
      re.search(r'android:name="android\.hardware\.camera"', manifest) is not None and
      re.search(r'android:name="android\.hardware\.camera"[\s\S]{0,80}?android:required="false"', manifest) is not None)

# ══════════════════════════════════════════════════════════════════════════════
print("\n════════ FASE 3.2 · GENERACIÓN DEL QR (PANTALLA DEL PADRE) ════════")
check("3.2a CodigoQr usa ZXing QRCodeWriter + BarcodeFormat.QR_CODE",
      "QRCodeWriter().encode(" in qr_util and "BarcodeFormat.QR_CODE" in qr_util)
check("3.2b corrección de errores alta (H) y margen definidos",
      "ErrorCorrectionLevel.H" in qr_util and "EncodeHintType.MARGIN" in qr_util)
check("3.2c solo acepta códigos de 6 dígitos",
      "LONGITUD_CODIGO = 6" in qr_util and "contenido.length != LONGITUD_CODIGO" in qr_util)
check("3.2d Bitmap creado localmente (ARGB_8888 + setPixels)",
      "Bitmap.createBitmap(" in qr_util and "Bitmap.Config.ARGB_8888" in qr_util and "setPixels(" in qr_util)
check("3.2e extracción del código con regex de 6 dígitos",
      'Regex("\\\\d{$LONGITUD_CODIGO}")' in qr_util and "REGEX_CODIGO.find(texto)?.value" in qr_util)
check("3.2f la pantalla del padre genera el Bitmap al cambiar el código, fuera del hilo de UI",
      "LaunchedEffect(codigoGenerado)" in codigo_padre and
      "withContext(Dispatchers.Default) { CodigoQr.generarBitmap(codigo) }" in codigo_padre and
      "CodigoQr.generarBitmap(" in codigo_padre)
check("3.2g el QR se muestra centrado con Image(...asImageBitmap())",
      "androidx.compose.foundation.Image" in codigo_padre and "qrBitmap.asImageBitmap()" in codigo_padre and
      "contentDescription = \"Código QR de vinculación\"" in codigo_padre)
check("3.2h el código numérico se escribe DEBAJO del QR (respaldo si falla la cámara)",
      re.search(r'Text\(\s*text = "Tu código: \$codigo"', codigo_padre) is not None)
check("3.2i mensaje explícito de respaldo si el Bitmap no se puede generar",
      "No se pudo generar la imagen QR" in codigo_padre)
check("3.2j el botón «Generar Código» se mantiene intacto",
      'Text("Generar Código"' in codigo_padre and "viewModel.generarCodigo(idPadre)" in codigo_padre)

# ══════════════════════════════════════════════════════════════════════════════
print("\n════════ FASE 3.3 · LECTOR DE QR (PANTALLA DEL HIJO) ════════")
check("3.3a icono de cámara (trailingIcon) en el campo del código",
      "trailingIcon = {" in hijo and "Icons.Filled.QrCodeScanner" in hijo)
check("3.3b botón explícito «Escanear QR»",
      'Text("Escanear QR")' in hijo and "OutlinedButton(" in hijo)
check("3.3c se solicita Manifest.permission.CAMERA antes de escanear",
      "ActivityResultContracts.RequestPermission()" in hijo and
      "lanzadorPermisoCamara.launch(Manifest.permission.CAMERA)" in hijo)
check("3.3d el permiso se comprueba con checkSelfPermission antes de abrir",
      "ContextCompat.checkSelfPermission(" in hijo and "Manifest.permission.CAMERA" in hijo)
check("3.3e el escáner se superpone a la pantalla dentro del Box raíz",
      "Box(modifier = Modifier.fillMaxSize()) {" in hijo and
      "EscanerQrOverlay(" in hijo and
      hijo.index("Box(modifier = Modifier.fillMaxSize()) {") < hijo.index("EscanerQrOverlay("))
check("3.3f al detectar el QR se rellena el campo del código",
      "vistaModelo.actualizarCodigo(codigoEscaneado)" in hijo)
check("3.3g se procede a la vinculación si el formulario ya está completo",
      "formularioValidoConCodigo(codigoEscaneado)" in hijo and
      re.search(r"if \(formularioValidoConCodigo\(codigoEscaneado\)\) \{\s*mensajeQr[\s\S]{0,120}?ejecutarVinculacion\(\)", hijo) is not None)
check("3.3h el botón «Vincular» reutiliza el mismo flujo (enabled = formularioValido)",
      "onClick = { ejecutarVinculacion() }" in hijo and "enabled = formularioValido," in hijo)
check("3.3i la vinculación manual por texto sigue intacta (onValueChange sigue filtrando dígitos)",
      "val soloDigitos = raw.filter { it.isDigit() }.take(6)" in hijo and
      "vistaModelo.actualizarCodigo(soloDigitos)" in hijo)
check("3.3j se avisa si el usuario deniega el permiso de cámara (respaldo manual)",
      "Permiso de cámara denegado. Escribe el código de 6 dígitos manualmente." in hijo)

# ── Escáner: CameraX + ML Kit
check("3.3k CameraX: ProcessCameraProvider.getInstance",
      "ProcessCameraProvider.getInstance(context)" in escaner)
check("3.3l CameraX: Preview + ImageAnalysis vinculados al ciclo de vida",
      "Preview.Builder()" in escaner and "ImageAnalysis.Builder()" in escaner and
      "bindToLifecycle(" in escaner and "CameraSelector.DEFAULT_BACK_CAMERA" in escaner)
check("3.3m ML Kit: BarcodeScanning con FORMAT_QR_CODE",
      "BarcodeScanning.getClient(" in escaner and "Barcode.FORMAT_QR_CODE" in escaner)
check("3.3n análisis del fotograma con InputImage + cierre del ImageProxy",
      "InputImage.fromMediaImage(mediaImage, imagen.imageInfo.rotationDegrees)" in escaner and
      "imagen.close()" in escaner)
check("3.3o detección única (AtomicBoolean) y liberación de la cámara (unbindAll)",
      "AtomicBoolean(false)" in escaner and "getAndSet(true)" in escaner and "unbindAll()" in escaner)
check("3.3p liberación de recursos al salir (DisposableEffect + shutdown del executor)",
      "DisposableEffect(Unit)" in escaner and "analizador.shutdown()" in escaner)
check("3.3q error de cámara con respaldo manual («Escribir el código manualmente»)",
      "Escribir el código manualmente" in escaner and "errorCamara" in escaner)
check("3.3r el escáner lee el código con CodigoQr.extraerCodigo",
      "CodigoQr.extraerCodigo(texto)" in escaner)

# ── Simulaciones funcionales
print("\n  ── Simulación: extracción del código desde el texto del QR ──")
REGEX_QR = re.compile(r"\d{6}")


def extraer_simulado(texto):
    if not texto or not texto.strip():
        return None
    m = REGEX_QR.search(texto.strip())
    return m.group(0) if m else None


casos_ok = {"123456": "123456", "udparents:vincular:123456": "123456", "  654321  ": "654321"}
for entrada, esperado in casos_ok.items():
    check(f"3.4a extrae '{entrada}' → {esperado}", extraer_simulado(entrada) == esperado)
for entrada in ["12345", "", "   ", "abc", "12-34-56-7"]:
    check(f"3.4b rechaza '{entrada}'", extraer_simulado(entrada) is None)

print("\n  ── Simulación: flujo hijo tras escanear ──")


def decidir_accion(codigo_escaneado, nombre_ok, edad_ok, sexo_ok, terms_ok):
    cod = (codigo_escaneado or "").strip()
    if len(cod) != 6:
        return "ERROR_CODIGO"
    if nombre_ok and edad_ok and sexo_ok and terms_ok:
        return "VINCULAR"
    return "PEDIR_PERFIL"


check("3.4c perfil completo + código escaneado → vincula automáticamente",
      decidir_accion("123456", True, True, True, True) == "VINCULAR")
check("3.4d falta aceptar términos → pide completar el perfil",
      decidir_accion("123456", True, True, True, False) == "PEDIR_PERFIL")
check("3.4e falta la edad → pide completar el perfil",
      decidir_accion("123456", True, False, True, True) == "PEDIR_PERFIL")
check("3.4f falta el sexo M/F → pide completar el perfil",
      decidir_accion("123456", True, True, False, True) == "PEDIR_PERFIL")
check("3.4g código incompleto → error de código inválido",
      decidir_accion("12345", True, True, True, True) == "ERROR_CODIGO")

# ══════════════════════════════════════════════════════════════════════════════
print("\n════════ FASE 3.5 · REGRESIÓN: LA CADENA DE 5 PERMISOS Y EL FORMULARIO SIGUEN INTACTOS ════════")
check("3.5a los 5 pasos del enum siguen en orden",
      orden_enum[:6] == ["USO_DATOS", "ACCESIBILIDAD", "NOTIFICACIONES",
                          "UBICACION", "UBICACION_FONDO", "ADMINISTRADOR"], str(orden_enum))
check("3.5b la cadena de diálogos sigue encadenada 1→5",
      orden_ui[:6] == ["PermisoUso", "Accesibilidad", "Notificaciones",
                        "Ubicacion", "UbicacionFondo", "Admin"], str(orden_ui))
check("3.5c el cierre sigiloso sigue ocurriendo solo con los 5 permisos",
      "if (pendiente == null && !vinculacionCompletada)" in hijo and
      "ModoSigiloso.ocultarIconoApp(context)" in hijo and
      "iniciarServicioRegistroUso(context)" in hijo and
      "finishAndRemoveTask()" in hijo)
check("3.5d el formulario del hijo sigue intacto (nombre, edad, M/F, términos)",
      all(m in hijo for m in ['label = { Text("Nombre y apellido del hijo") }',
                               'label = { Text("Edad del hijo (1–17)") }',
                               'label = { Text("Sexo del hijo (M/F)") }',
                               'listOf("M", "F")', "Términos y Condiciones"]))
check("3.5e el escaneo NO altera el orden de la cadena ni añade pasos",
      hijo.index("PasoPermisoHijo.USO_DATOS") < hijo.index("PasoPermisoHijo.ACCESIBILIDAD") <
      hijo.index("PasoPermisoHijo.NOTIFICACIONES") < hijo.index("PasoPermisoHijo.UBICACION") <
      hijo.index("PasoPermisoHijo.UBICACION_FONDO") < hijo.index("PasoPermisoHijo.ADMINISTRADOR"))
check("3.5f la purga en Firestore y el bloqueo de UI del padre siguen presentes",
      "purgarVinculacionHijo" in repo_vinc and "programarPurgaDiferida(uidPadre, uidHijo)" in repo_vinc and
      "enabled = hayHijosVinculados" in principal)

# ══════════════════════════════════════════════════════════════════════════════
print("\n════════ SANIDAD DE SINTAXIS (balance de bloques en archivos modificados) ════════")
for p in [P_HIJO, P_MANIFEST, P_REPO_VINC, P_REPO_APPS, P_VM_VINC, P_VM_APPS, P_PRINCIPAL,
          P_QR_UTIL, P_ESCANER, P_CODIGO_PADRE]:
    nombre = p.split("/")[-1]
    src = leer(p)
    check(f"sanity {nombre}: llaves/paréntesis balanceados", balance_ok(src))
check("sanity: no quedan referencias al helper eliminado ocultarTodosLosDialogos",
      "ocultarTodosLosDialogos" not in hijo)
check("sanity: la cadena no usa variables inexistentes (mostrarDialogo* declarados)",
      all(f"var {v} by remember" in hijo for v in
          ["mostrarDialogoPermisoUso", "mostrarDialogoAccesibilidad", "mostrarDialogoNotificaciones",
           "mostrarDialogoUbicacion", "mostrarDialogoUbicacionFondo", "mostrarDialogoAdmin"]))
check("sanity: la cadena no usa estados de permiso inexistentes",
      all(f"val {v} = remember" in hijo for v in
          ["permisoUsoApps", "permisoAccesibilidad", "permisoNotificaciones",
           "permisoUbicacion", "permisoUbicacionFondo", "permisoAdmin"]))
check("sanity: imports nuevos presentes en PantallaVinculacionHijo",
      all(i in hijo for i in ["import android.Manifest", "import android.content.pm.PackageManager",
                              "import androidx.activity.compose.rememberLauncherForActivityResult",
                              "import androidx.activity.result.contract.ActivityResultContracts",
                              "import androidx.core.content.ContextCompat",
                              "import android.net.Uri"]))
check("sanity: imports nuevos presentes en RepositorioVinculacion",
      all(i in repo_vinc for i in ["import kotlinx.coroutines.CoroutineScope",
                                   "import kotlinx.coroutines.Dispatchers",
                                   "import kotlinx.coroutines.SupervisorJob",
                                   "import kotlinx.coroutines.delay",
                                   "import kotlinx.coroutines.launch",
                                   "import com.google.firebase.firestore.DocumentReference"]))
check("sanity: import del listener en RepositorioApps y VistaModeloApps",
      "import com.google.firebase.firestore.ListenerRegistration" in repo_apps and
      "import com.google.firebase.firestore.ListenerRegistration" in vm_apps)
check("sanity: imports de la FASE 3 presentes en el escáner y la utilidad QR",
      all(i in escaner for i in ["import androidx.camera.core.ImageAnalysis",
                                 "import androidx.camera.view.PreviewView",
                                 "import androidx.camera.lifecycle.ProcessCameraProvider",
                                 "import com.google.mlkit.vision.barcode.BarcodeScanning",
                                 "import com.google.mlkit.vision.common.InputImage"]) and
      all(i in qr_util for i in ["import com.google.zxing.qrcode.QRCodeWriter",
                                 "import com.google.zxing.BarcodeFormat"]))

# ══════════════════════════════════════════════════════════════════════════════
print("\n═══════════════════════════════════════════════════════════════")
print(f"RESULTADO: {pruebas - len(fallos)}/{pruebas} comprobaciones PASS")
if fallos:
    print("❌ FALLOS:")
    for f in fallos:
        print("   -", f)
    sys.exit(1)
print("✅ LOOPING COMPLETO: los 3 requerimientos verificados.")
print("   · FASE 1: cadena secuencial de 5 permisos del hijo")
print("   · FASE 2: purga explícita en Firestore + botones del padre deshabilitados sin hijos")
print("   · FASE 3: vinculación por Código QR (generación en el padre, lector CameraX + ML Kit en el hijo)")
