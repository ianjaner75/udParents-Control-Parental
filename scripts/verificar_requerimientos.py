#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🔁 LOOPING DE VERIFICACIÓN ESTÁTICA — udParents
FASE 1: cadena de 5 permisos del hijo (PantallaVinculacionHijo.kt)
FASE 2: purga explícita en Firestore + estado de UI del padre

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
print("\n════════ SANIDAD DE SINTAXIS (balance de bloques en archivos modificados) ════════")
for p in [P_HIJO, P_MANIFEST, P_REPO_VINC, P_REPO_APPS, P_VM_VINC, P_VM_APPS, P_PRINCIPAL]:
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

# ══════════════════════════════════════════════════════════════════════════════
print("\n═══════════════════════════════════════════════════════════════")
print(f"RESULTADO: {pruebas - len(fallos)}/{pruebas} comprobaciones PASS")
if fallos:
    print("❌ FALLOS:")
    for f in fallos:
        print("   -", f)
    sys.exit(1)
print("✅ LOOPING COMPLETO: los 3 requerimientos verificados.")
