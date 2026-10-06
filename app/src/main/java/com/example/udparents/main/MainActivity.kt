package com.example.udparents.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.udparents.navegacion.NavegacionApp
import com.example.udparents.servicio.RegistroUsoService
import com.example.udparents.tema.UdParentsTheme
import com.example.udparents.utilidades.CodigoQr
import com.example.udparents.utilidades.SharedPreferencesUtil

/**
 * Actividad principal que configura el contenido de la aplicación.
 * Aquí se aplica el tema y se lanza la navegación principal.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    /**
     * 🔗 Código de vinculación extraído del Deep Link (`udparents://vincular?codigo=...`).
     * Se expone como estado Compose para que [NavegacionApp] pueda observar el
     * código en tiempo real y reaccionar cuando llega un nuevo intent.
     */
    var deepLinkCode by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔗 Extraer código del Deep Link (si la app se abrió desde el QR).
        deepLinkCode = extraerCodigoDeIntent(intent)

        // 🕶️ Stealth Mode: si ya existe una sesión de hijo vinculada en este
        // dispositivo, NO se muestra la interfaz gráfica de bienvenida. Se
        // garantiza que el servicio de monitoreo siga corriendo y se cierra
        // la actividad de inmediato.
        if (SharedPreferencesUtil.existeSesionHijo(this)) {
            Log.d(TAG, "Sesión de hijo activa: se omite la interfaz de bienvenida.")
            // 🔗 Si hay un Deep Link y el dispositivo ya está vinculado,
            // se ignora silenciosamente (no se re-vincula).
            if (deepLinkCode != null) {
                Log.w(TAG, "Deep Link ignorado: el dispositivo ya está vinculado.")
                deepLinkCode = null
            }
            iniciarServicioRegistroUso(this)
            // 🧹 También se remueve la tarea de recientes/lanzador (caché Samsung).
            finishAndRemoveTask()
            return
        }
        setContent {
            UdParentsTheme {
                NavegacionApp(deepLinkCode = deepLinkCode)
            }
        }
    }

    /**
     * 🔗 Cuando la actividad ya está abierta y recibe un nuevo Deep Link
     * (p. ej. el hijo escanea otro QR sin cerrar la app), `onNewIntent`
     * actualiza el estado para que la pantalla de vinculación reaccione.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val codigo = extraerCodigoDeIntent(intent)
        if (codigo != null) {
            // Si el dispositivo ya está vinculado, se ignora el Deep Link.
            if (SharedPreferencesUtil.existeSesionHijo(this)) {
                Log.w(TAG, "Deep Link (onNewIntent) ignorado: dispositivo ya vinculado.")
                return
            }
            deepLinkCode = codigo
            Log.d(TAG, "🔗 Deep Link recibido (onNewIntent): código=$codigo")
        }
    }

    /**
     * Extrae el código de 6 dígitos del intent de Deep Link.
     * @return El código o `null` si el intent no contiene un URI válido.
     */
    private fun extraerCodigoDeIntent(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        Log.d(TAG, "🔗 Deep Link URI recibido: $uri")
        return CodigoQr.extraerCodigo(uri.toString())
    }

    /**
     * Inicia el servicio de registro de uso de aplicaciones,
     * asegurando compatibilidad con Android 8+ (Oreo) en adelante.
     */
    fun iniciarServicioRegistroUso(context: Context) {
        val intent = Intent(context, RegistroUsoService::class.java)
        // 🛡️ Android 12+ puede rechazar el arranque como foreground service;
        // se captura para evitar crashes y se usa startService como respaldo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "⚠️ startForegroundService bloqueado: ${e.message}. Usando startService como respaldo.")
                try {
                    context.startService(intent)
                } catch (e2: Exception) {
                    Log.e("MainActivity", "❌ No se pudo iniciar el servicio: ${e2.message}", e2)
                }
            }
        } else {
            context.startService(intent)
        }
    }
}
