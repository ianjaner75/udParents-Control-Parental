package com.example.udparents.repositorio

import android.util.Log
import com.example.udparents.modelo.UbicacionHijo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * 📍 Repositorio de ubicación en tiempo real.
 *
 * - El dispositivo del HIJO escribe sus coordenadas (un documento por hijo).
 * - El dispositivo del PADRE se suscribe en tiempo real con un snapshot
 *   listener para renderizar el mapa.
 */
class RepositorioUbicacion {

    private val db = FirebaseFirestore.getInstance()
    private val coleccionUbicaciones = db.collection("ubicaciones_hijo")

    /**
     * Hijo: publica las coordenadas actuales (best-effort, silencioso).
     * Nunca lanza excepciones hacia el servicio para no afectar el monitoreo.
     */
    suspend fun guardarUbicacion(ubicacion: UbicacionHijo) {
        try {
            coleccionUbicaciones
                .document(ubicacion.uidHijo)
                .set(ubicacion)
                .await()
        } catch (e: Exception) {
            Log.e("RepositorioUbicacion", "Error guardando la ubicación: ${e.message}")
        }
    }

    /**
     * Padre: suscripción en tiempo real a las coordenadas del hijo.
     * @return El [ListenerRegistration] para cancelar la escucha en onDispose.
     */
    fun escucharUbicacion(
        uidHijo: String,
        onUbicacion: (UbicacionHijo?) -> Unit
    ): ListenerRegistration {
        return coleccionUbicaciones
            .document(uidHijo)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("RepositorioUbicacion", "Error escuchando la ubicación: ${error.message}")
                    return@addSnapshotListener
                }
                onUbicacion(snapshot?.toObject(UbicacionHijo::class.java))
            }
    }
}
