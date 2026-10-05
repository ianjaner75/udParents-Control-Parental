package com.example.udparents.servicio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.udparents.R // Asegúrate de que R exista en tu proyecto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await

class MiFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM_Service"

    /**
     * Este método se llama cada vez que se genera un nuevo token de notificación.
     * Guardamos este token en Firestore para que el dispositivo del hijo lo pueda usar.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Nuevo token de FCM: $token")
        // Aquí se guarda el token en Firestore.
        // Si el usuario ya está logeado, lo guardamos.
        // Si no, lo guardamos cuando el usuario inicie sesión.
        enviarTokenAFirestore(token)
    }

    /**
     * Este método se llama cuando el dispositivo recibe una notificación del hijo.
     * Construye y muestra la notificación en el dispositivo del padre.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Mensaje recibido de: ${remoteMessage.from}")

        // Verificamos si el mensaje tiene datos.
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Carga de datos del mensaje: ${remoteMessage.data}")
            val titulo = remoteMessage.data["titulo"] ?: "Notificación de UD Parents"
            val mensaje = remoteMessage.data["cuerpo"] ?: "El uso del dispositivo ha superado un límite."
            mostrarNotificacion(titulo, mensaje)
        }
    }

    /**
     * Muestra una notificación en la barra de notificaciones.
     */
    private fun mostrarNotificacion(titulo: String, mensaje: String) {
        val channelId = "notificacion_exceso_tiempo"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Se requiere un canal de notificación para Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones de Tiempo de Uso",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Canal para notificaciones de exceso de tiempo de uso."
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    /**
     * Guarda el token de FCM del usuario padre en Firestore.
     */
    fun enviarTokenAFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("usuarios").document(uid)
            val data = hashMapOf(
                "fcmToken" to token
            )
            userRef.update(data as Map<String, Any>)
                .addOnSuccessListener {
                    Log.d(TAG, "Token de FCM actualizado en Firestore para el usuario $uid")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error al actualizar el token de FCM", e)
                }
        }
    }

    /**
     * Función para obtener el token de FCM actual.
     */
    suspend fun obtenerTokenFCM(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener el token de FCM", e)
            null
        }
    }
}
