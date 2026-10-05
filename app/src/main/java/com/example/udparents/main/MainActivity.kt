package com.example.udparents.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.example.udparents.navegacion.NavegacionApp
import com.example.udparents.servicio.RegistroUsoService
import com.example.udparents.tema.UdParentsTheme

/**
 * Actividad principal que configura el contenido de la aplicación.
 * Aquí se aplica el tema y se lanza la navegación principal.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
