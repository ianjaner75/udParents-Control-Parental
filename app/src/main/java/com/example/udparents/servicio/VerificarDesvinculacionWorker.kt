package com.example.udparents.servicio

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.udparents.utilidades.GestorDesvinculacion
import com.example.udparents.utilidades.SesionHijoStore

/** Respaldo de baja frecuencia: continúa comprobando la orden tras cierres o reinicios. */
class VerificarDesvinculacionWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sesion = SesionHijoStore.obtener(applicationContext) ?: run {
            MonitorDesvinculacion.detener(applicationContext)
            return Result.success()
        }
        return try {
            GestorDesvinculacion.procesarOrden(applicationContext, sesion)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo comprobar la orden de desvinculación", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "VerificarDesvinculacion"
    }
}
