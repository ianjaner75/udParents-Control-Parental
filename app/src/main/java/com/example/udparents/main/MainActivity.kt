package com.example.udparents.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.example.udparents.navegacion.NavegacionApp
import com.example.udparents.servicio.RegistroUsoService
import com.example.udparents.tema.UdParentsTheme
import com.example.udparents.utilidades.SharedPreferencesUtil

/**
 * Actividad principal que configura el contenido de la aplicación.
 * Aquí se aplica el tema y se lanza la navegación principal.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 🕶️ Stealth Mode: si ya existe una sesión de hijo vinculada en este
        // dispositivo, NO se muestra la interfaz gráfica de bienvenida. Se
        // garantiza que el servicio de monitoreo siga corriendo y se cierra
        // la actividad de inmediato.
        if (SharedPreferencesUtil.existeSesionHijo(this)) {
            Log.d("MainActivity", "Sesión de hijo activa: se omite la interfaz de bienvenida.")
            iniciarServicioRegistroUso(this)
            finish()
            return
        }
        setContent {
            UdParentsTheme {
                NavegacionApp()
            }
        }
    }

    /**
     * Inicia el servicio de registro de uso de aplicaciones,
     * asegurando compatibilidad con Android 8+ (Oreo) en adelante.
     */
    fun iniciarServicioRegistroUso(context: Context) {
        val intent = Intent(context, RegistroUsoService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}
