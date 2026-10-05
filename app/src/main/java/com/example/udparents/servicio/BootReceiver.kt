package com.example.udparents.servicio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Dispositivo reiniciado")

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val serviceIntent = Intent(context, RegistroUsoService::class.java)
                // 🛡️ Tras un reinicio, Android 12+ puede rechazar el arranque del
                // foreground service; se captura para no crashear en el boot.
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "⚠️ No se pudo iniciar el servicio tras el boot: ${e.message}")
                    try {
                        context.startService(serviceIntent)
                    } catch (e2: Exception) {
                        Log.e("BootReceiver", "❌ Respaldo startService también falló: ${e2.message}")
                    }
                }
            }else{
                Log.w("BootReceiver", "No hay usuario hijo autenticado. No se inicia servicio.")

            }
        }
    }
}
