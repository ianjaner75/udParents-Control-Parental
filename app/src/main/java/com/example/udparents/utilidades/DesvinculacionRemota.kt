package com.example.udparents.utilidades

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.udparents.seguridad.AdminReceiver
import com.example.udparents.servicio.NotificacionSender
import com.example.udparents.servicio.RegistroUsoService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 📡 Desvinculación remota (Padre → Hijo).
 *
 * El dispositivo del hijo mantiene un listener de Firestore en tiempo real sobre
 * su documento de vinculación. Cuando el padre autoriza la desvinculación desde su
 * app, se escribe la bandera `desvincular = true` en Firestore. Este objeto detecta
 * esa bandera y ejecuta la limpieza completa en el dispositivo del hijo:
 *
 *  1. Confirma al padre vía push (mejor esfuerzo).
 *  2. Restaura el ícono de la app en el Launcher.
 *  3. Detiene el servicio de monitoreo.
 *  4. Limpia la sesión local (SharedPreferences).
 *  5. Remueve los privilegios de Administrador de Dispositivos (removeActiveAdmin),
 *     permitiendo que la app pueda desinstalarse normalmente.
 *  6. Cierra la sesión anónima de Firebase Auth.
 *  7. Elimina el documento de vinculación en Firestore.
 */
object DesvinculacionRemota {

    private const val TAG = "DesvinculacionRemota"
    private const val COLECCION_CODIGOS = "codigos_vinculacion"
    private const val CAMPO_DESVINCULAR = "desvincular"
    private const val CAMPO_DISPOSITIVO_HIJO = "dispositivoHijo"

    private var escuchaActiva: ListenerRegistration? = null

    /**
     * Inicia la escucha en tiempo real de la orden de desvinculación emitida por
     * el padre. Solo se activa en dispositivos de hijo con sesión vinculada.
     * Es idempotente: si ya hay una escucha activa, no crea otra.
     * @param context El contexto de la aplicación.
     */
    fun iniciarEscucha(context: Context) {
        if (escuchaActiva != null) {
            Log.d(TAG, "La escucha de desvinculación ya estaba activa.")
            return
        }
        val appContext = context.applicationContext
        val uidHijo = FirebaseAuth.getInstance().currentUser?.uid
        val uidPadre = SharedPreferencesUtil.obtenerUidPadre(appContext)
        if (uidHijo.isNullOrBlank() || uidPadre.isNullOrBlank()) {
            Log.d(TAG, "No hay sesión de hijo vinculada; no se inicia la escucha.")
            return
        }

        escuchaActiva = FirebaseFirestore.getInstance()
            .collection(COLECCION_CODIGOS)
            .whereEqualTo(CAMPO_DISPOSITIVO_HIJO, uidHijo)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error escuchando la orden de desvinculación: ${error.message}")
                    return@addSnapshotListener
                }
                val docDesvincular = snapshot?.documents?.firstOrNull {
                    it.getBoolean(CAMPO_DESVINCULAR) == true
                }
                if (docDesvincular != null) {
                    Log.i(TAG, "📡 Orden de desvinculación remota recibida del padre")
                    procesarDesvinculacion(appContext, docDesvincular.id)
                }
            }
        Log.i(TAG, "👂 Escucha de desvinculación remota activa")
    }

    /**
     * Detiene la escucha (p. ej. cuando el servicio se destruye o tras procesar
     * una desvinculación). Es idempotente.
     */
    fun detenerEscucha() {
        escuchaActiva?.remove()
        escuchaActiva = null
    }

    /**
     * Ejecuta la desvinculación completa en el dispositivo del hijo.
     * @param context El contexto de la aplicación.
     * @param idDocumento ID del documento de vinculación en Firestore.
     */
    private fun procesarDesvinculacion(context: Context, idDocumento: String) {
        // Evita re-procesamientos mientras se ejecuta la limpieza.
        detenerEscucha()

        // Se captura ANTES de limpiar la sesión local.
        val uidPadre = SharedPreferencesUtil.obtenerUidPadre(context)

        CoroutineScope(Dispatchers.IO).launch {
            // 1) Confirmación al padre (mejor esfuerzo, antes de borrar la sesión).
            if (!uidPadre.isNullOrBlank()) {
                try {
                    NotificacionSender().enviarNotificacionAlPadre(
                        uidPadre,
                        "Desvinculación completada",
                        "El dispositivo de tu hijo fue desvinculado: ícono restaurado y permisos de administrador removidos."
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo confirmar la desvinculación al padre: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                // 2) Restaurar el ícono de la app en el Launcher.
                ModoSigiloso.restaurarIconoApp(context)

                // 3) Detener el servicio de monitoreo.
                try {
                    context.stopService(Intent(context, RegistroUsoService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo detener el servicio: ${e.message}")
                }

                // 4) Limpiar la sesión local (SharedPreferences). Se limpia ANTES de
                //    remover el admin para que el onDisabled de AdminReceiver no envíe
                //    una alerta redundante al padre (su notificarPadre hace early-return).
                SharedPreferencesUtil.limpiarSesionHijo(context)

                // 5) Remover los privilegios de Administrador de Dispositivos.
                try {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                    val admin = ComponentName(context, AdminReceiver::class.java)
                    if (dpm?.isAdminActive(admin) == true) {
                        dpm.removeActiveAdmin(admin)
                        Log.i(TAG, "🔓 Administrador de dispositivo removido")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removiendo el administrador de dispositivo: ${e.message}")
                }

                // 6) Cerrar la sesión anónima del hijo en Firebase Auth.
                try {
                    FirebaseAuth.getInstance().signOut()
                    Log.i(TAG, "👋 Sesión anónima del hijo cerrada")
                } catch (e: Exception) {
                    Log.e(TAG, "Error cerrando la sesión de Firebase Auth: ${e.message}")
                }
            }

            // 7) Eliminar el documento de vinculación en Firestore.
            try {
                FirebaseFirestore.getInstance()
                    .collection(COLECCION_CODIGOS)
                    .document(idDocumento)
                    .delete()
                    .await()
                Log.i(TAG, "🗑️ Documento de vinculación eliminado de Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo eliminar el documento de vinculación: ${e.message}")
            }
        }
    }
}
