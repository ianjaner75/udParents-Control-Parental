package com.example.udparents.utilidades

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.udparents.admin.UdParentsDeviceAdminReceiver
import com.example.udparents.modelo.CodigoVinculacion
import com.example.udparents.servicio.MonitorDesvinculacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/** Comprueba la orden en el servidor y ejecuta una desvinculación idempotente. */
object GestorDesvinculacion {
    private const val TAG = "GestorDesvinculacion"
    private val lock = Any()

    suspend fun procesarOrden(context: Context, sesion: SesionHijo): Boolean {
        val appContext = context.applicationContext
        if (SesionHijoStore.obtener(appContext) != sesion) return false

        val referencia = FirebaseFirestore.getInstance()
            .collection("codigos_vinculacion")
            .document(sesion.codigoVinculacion)
        val documento = referencia.get(Source.SERVER).await()
        val vinculo = documento.toObject(CodigoVinculacion::class.java) ?: return false
        if (vinculo.dispositivoHijo != sesion.uidHijo || !vinculo.desinstalacionSolicitada) {
            return false
        }

        // Primero se retira el administrador y se revela el icono, sin esperar otra llamada de red.
        val sesionLimpiada = synchronized(lock) {
            if (SesionHijoStore.obtener(appContext) != sesion) {
                false
            } else if (!revocarAdministradorSiActivo(appContext)) {
                Log.e(TAG, "No se pudo retirar el administrador; se reintentará la orden")
                false
            } else if (!VisibilidadLauncher.mostrar(appContext)) {
                Log.e(TAG, "No se pudo volver a habilitar MainActivity")
                false
            } else if (!SesionHijoStore.limpiar(appContext)) {
                Log.e(TAG, "No se pudo limpiar la sesión local del hijo")
                false
            } else {
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser?.uid == sesion.uidHijo) {
                    auth.signOut()
                }
                true
            }
        }
        if (!sesionLimpiada) return false

        // La UI ya es recuperable; ahora se limpia el vínculo remoto para que el Padre vea el cambio.
        try {
            referencia.update(
                mapOf(
                    "desinstalacionSolicitada" to false,
                    "vinculado" to false,
                    "dispositivoHijo" to ""
                )
            ).await()
        } catch (e: Exception) {
            // No se revierte el acceso local: la desvinculación del dispositivo ya fue aplicada.
            Log.w(TAG, "No se pudo actualizar el documento de vinculación", e)
        }

        MonitorDesvinculacion.detener(appContext)
        Log.i(TAG, "Vínculo retirado; el icono de la aplicación está visible")
        return true
    }

    private fun revocarAdministradorSiActivo(context: Context): Boolean {
        return try {
            val administrador = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as? DevicePolicyManager ?: return true
            val componente = ComponentName(context, UdParentsDeviceAdminReceiver::class.java)
            if (!administrador.isAdminActive(componente)) {
                true
            } else {
                administrador.removeActiveAdmin(componente)
                !administrador.isAdminActive(componente)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo retirar el administrador de dispositivo", e)
            false
        }
    }
}
