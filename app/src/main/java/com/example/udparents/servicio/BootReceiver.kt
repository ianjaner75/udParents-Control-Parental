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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }else{
                Log.w("BootReceiver", "No hay usuario hijo autenticado. No se inicia servicio.")

            }
        }
    }
}
