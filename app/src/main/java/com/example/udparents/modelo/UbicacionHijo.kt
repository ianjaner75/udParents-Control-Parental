package com.example.udparents.modelo

/**
 * 📍 Ubicación en tiempo real de un hijo vinculado.
 *
 * Se almacena en Firestore (colección `ubicaciones_hijo`, un documento por
 * hijo) y se actualiza periódicamente desde el servicio del dispositivo del
 * hijo. El padre la lee en tiempo real mediante un snapshot listener.
 */
data class UbicacionHijo(
    val uidHijo: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val precision: Float = 0f,
    val velocidad: Float = 0f,
    val timestamp: Long = 0L
)
