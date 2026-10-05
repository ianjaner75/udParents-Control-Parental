package com.example.udparents.servicio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.udparents.R
import com.example.udparents.modelo.CodigoVinculacion
import com.example.udparents.utilidades.GestorDesvinculacion
import com.example.udparents.utilidades.SesionHijoStore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Listener Firestore de baja inmediata mientras Android mantiene activo el servicio. */
class ServicioDesvinculacion : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val procesandoOrden = AtomicBoolean(false)
    private var listener: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID_NOTIFICACION, crearNotificacion())
        iniciarListener()
        return START_STICKY
    }

    private fun iniciarListener() {
        if (listener != null) return

        val sesion = SesionHijoStore.obtener(this)
        if (sesion == null) {
            stopSelf()
            return
        }

        listener = FirebaseFirestore.getInstance()
            .collection("codigos_vinculacion")
            .document(sesion.codigoVinculacion)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error escuchando la orden de desvinculación", error)
                    return@addSnapshotListener
                }

                val vinculo = snapshot?.toObject(CodigoVinculacion::class.java) ?: return@addSnapshotListener
                if (vinculo.dispositivoHijo != sesion.uidHijo ||
                    !vinculo.desinstalacionSolicitada ||
                    !procesandoOrden.compareAndSet(false, true)
                ) {
                    return@addSnapshotListener
                }

                scope.launch {
                    try {
                        GestorDesvinculacion.procesarOrden(this@ServicioDesvinculacion, sesion)
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo procesar la orden recibida", e)
                    } finally {
                        procesandoOrden.set(false)
                        if (SesionHijoStore.obtener(this@ServicioDesvinculacion) == null) {
                            stopSelf()
                        }
                    }
                }
            }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android limita el tiempo de los servicios dataSync; WorkManager queda como respaldo.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        listener?.remove()
        listener = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_NOTIFICACION,
                "Estado de udParents",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra que udParents está pendiente de las órdenes de desvinculación."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun crearNotificacion(): Notification =
        NotificationCompat.Builder(this, CANAL_NOTIFICACION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Servicio parental activo; se supervisan órdenes de desvinculación.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private companion object {
        const val TAG = "ServicioDesvinculacion"
        const val ID_NOTIFICACION = 2301
        const val CANAL_NOTIFICACION = "estado_udparents"
    }
}
