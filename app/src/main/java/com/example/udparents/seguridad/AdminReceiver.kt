package com.example.udparents.seguridad

import android.content.Context
import android.content.Intent
import android.app.admin.DeviceAdminReceiver
import android.util.Log
import com.example.udparents.repositorio.RepositorioBloqueos
import com.example.udparents.modelo.BloqueoRegistro
import com.example.udparents.utilidades.SharedPreferencesUtil
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminReceiver : DeviceAdminReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i("AdminReceiver", "✅ Admin de dispositivo ACTIVADO")
        notificarPadre(context, "✅ Administrador activado", "Tu hijo activó el Administrador de dispositivo.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w("AdminReceiver", "⚠️ Intento de DESACTIVAR Admin de dispositivo")
        // Mensaje que el sistema muestra antes de desactivar (no bloquea, solo advierte)
        notificarPadre(context, "⚠️ Intento de desactivar", "Tu hijo intentó desactivar el Administrador de dispositivo.")
        return "UdParents necesita este permiso para evitar que se desinstale sin tu autorización."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.e("AdminReceiver", "⛔️ Admin de dispositivo DESACTIVADO")
        notificarPadre(context, "⛔️ Administrador desactivado", "Tu hijo desactivó el Administrador de dispositivo.")
    }

    private fun notificarPadre(context: Context, titulo: String, mensaje: String) {
        val uidPadre = SharedPreferencesUtil.obtenerUidPadre(context) ?: return
        val uidHijo = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Tu clase que envía el push
        val sender = com.example.udparents.servicio.NotificacionSender()
        scope.launch {
            try {
                sender.enviarNotificacionAlPadre(uidPadre, titulo, mensaje)
                val repo = RepositorioBloqueos()
                repo.registrarBloqueo(
                    uidHijo, uidPadre, BloqueoRegistro(
                        uidHijo = uidHijo,
                        nombrePaquete = "ADMIN_DEVICE",
                        nombreApp = titulo,
                        razon = mensaje
                    )
                )
            } catch (e: Exception) {
                Log.e("AdminReceiver", "Error notificando al padre: ${e.message}", e)
            }
        }
    }
}
