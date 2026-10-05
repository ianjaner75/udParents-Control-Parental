package com.example.udparents.utilidades

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.app.usage.UsageStatsManager
import android.content.pm.PackageManager
import android.util.Log
import com.example.udparents.modelo.AppUso
import com.example.udparents.repositorio.RepositorioApps
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.usage.UsageStats
import java.util.Calendar

object RegistroUsoApps {

    private const val TAG = "RegistroUsoAppsDEBUG"

    suspend fun registrarUsoAplicaciones(context: Context) {
        val uidHijo = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w(TAG, "UID del hijo no disponible, no se puede registrar el uso.")
            return
        }
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val ahora = System.currentTimeMillis()

        val calendar = Calendar.getInstance().apply {
            timeInMillis = ahora
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMillis = calendar.timeInMillis

        val stats: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            todayStartMillis,
            ahora
        )

        if (stats.isNullOrEmpty()) {
            Log.w(TAG, "Sin permisos de uso o sin datos de uso para el día actual. `usageStatsManager.queryUsageStats` devolvió vacío.")
            if (!tienePermisoUsageStats(context)) {
                Log.e(TAG, "¡ALERTA! El permiso de ACCESO A DATOS DE USO no está concedido. Por favor, ve a Configuración > Acceso especial > Acceso a datos de uso y habilítalo para UdParents.")
            }
            return
        } else {
            Log.d(TAG, "📊 Se encontraron ${stats.size} UsageStats para el día. Procesando...")
        }

        val repositorio = RepositorioApps()

        // Obtener la aplicación que está actualmente en primer plano
        // Ecrucial para evitar sobrescribir su uso con un valor potencialmente desactualizado
        val topApp = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, ahora - 10000, ahora)
            .maxByOrNull { it.lastTimeUsed }
        val paqueteEnPrimerPlano = topApp?.packageName

        withContext(Dispatchers.IO) {
            var appsProcesadas = 0
            var appsRegistradas = 0
            for (app in stats) {
                val tiempoUso = app.totalTimeInForeground
                val packageName = app.packageName

                // Si la aplicación está actualmente en primer plano, la ignoramos en este barrido.
                // Su tiempo de uso se está incrementando en tiempo real por el otro mecanismo.
                if (packageName == paqueteEnPrimerPlano) {
                    Log.d(TAG, "➡️ Ignorando app en primer plano ($packageName) en el barrido general. Su uso se incrementa en tiempo real.")
                    continue
                }

                if (packageName == context.packageName) {
                    Log.d(TAG, "➡️ Ignorando la propia aplicación: $packageName")
                    continue
                }

                if (tiempoUso <= 0) {
                    Log.d(TAG, "➡️ Ignorando app sin tiempo en primer plano: $packageName (Tiempo: ${tiempoUso} ms)")
                    continue
                }

                if (esAplicacionSistema(context, packageName)) {
                    Log.d(TAG, "➡️ Ignorando app del sistema: $packageName")
                    continue
                }

                val nombreAppRaw = obtenerNombreApp(context, packageName)
                val nombreApp = if (nombreAppRaw == packageName) packageName else nombreAppRaw
                Log.d(TAG, "✅ Lista para registrar: $nombreApp ($packageName), Tiempo: ${tiempoUso} ms")

                val appUso = AppUso(
                    nombrePaquete = packageName,
                    nombreApp = nombreApp,
                    fechaUso = todayStartMillis,
                    tiempoUso = tiempoUso // Este es el tiempo consolidado del UsageStatsManager
                )
                try {
                    // Esta llamada ahora solo actualiza apps que NO están en primer plano
                    repositorio.registrarUsoAplicacion(uidHijo, appUso)
                    appsRegistradas++
                } catch (e: Exception) {
                    Log.e(TAG, "❌ ERROR al registrar ${nombreApp} ($packageName) en Firebase: ${e.message}", e)
                }
                appsProcesadas++
            }
            Log.d(TAG, "🏁 Proceso de registro de apps finalizado. Se procesaron ${appsProcesadas} y se registraron ${appsRegistradas} aplicaciones válidas.")
        }
    }

    private fun obtenerNombreApp(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun esAplicacionSistema(context: Context, packageName: String): Boolean {
        val excludedPackages = setOf(
            "android",
            "com.google.android.gms",
            "com.android.providers.media",
            "com.android.systemui",
            "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.phone",
            "com.android.providers.telephony",
            "com.android.settings",
            "com.samsung.android.app.launcher",
            "com.google.android.apps.restore",
            "com.google.android.networkstack",
            "com.google.android.networkstack.tethering",
            "com.sec.android.app.samsungapps",
            "com.google.android.webview",
            "com.sec.imsservice",
            "com.samsung.android.honeyboard"
        )

        if (excludedPackages.contains(packageName)) {
            Log.d(TAG, "esAplicacionSistema: $packageName está en la lista de exclusión explícita.")
            return true
        }

        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            false
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "esAplicacionSistema: Paquete no encontrado al verificar si es del sistema: $packageName. NO lo consideramos app del sistema para fines de registro. Error: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "esAplicacionSistema: Error inesperado al verificar paquete $packageName: ${e.message}", e)
            true
        }
    }

    fun tienePermisoUsageStats(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
