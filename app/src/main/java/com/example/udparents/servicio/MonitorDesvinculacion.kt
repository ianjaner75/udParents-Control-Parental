package com.example.udparents.servicio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Arranca el listener inmediato y deja una comprobación persistente como respaldo. */
object MonitorDesvinculacion {
    private const val TRABAJO_UNICO = "udparents_comprobar_desvinculacion_ahora"
    private const val TRABAJO_PERIODICO = "udparents_comprobar_desvinculacion_periodico"

    fun iniciar(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val restricciones = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        workManager.enqueueUniqueWork(
            TRABAJO_UNICO,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<VerificarDesvinculacionWorker>()
                .setConstraints(restricciones)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            TRABAJO_PERIODICO,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<VerificarDesvinculacionWorker>(15, TimeUnit.MINUTES)
                .setConstraints(restricciones)
                .build()
        )

        val intent = Intent(appContext, ServicioDesvinculacion::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: RuntimeException) {
            // El trabajo persistente sigue activo si Android no permite iniciar el servicio ahora.
            Log.w("MonitorDesvinculacion", "No se pudo iniciar el listener en primer plano", e)
        }
    }

    fun detener(context: Context) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).cancelUniqueWork(TRABAJO_UNICO)
        WorkManager.getInstance(appContext).cancelUniqueWork(TRABAJO_PERIODICO)
        appContext.stopService(Intent(appContext, ServicioDesvinculacion::class.java))
    }
}
