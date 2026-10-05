package com.example.udparents.repositorio

import android.util.Log
import com.example.udparents.modelo.BloqueoRegistro
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class RepositorioBloqueos {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "RepositorioBloqueos"

    suspend fun registrarBloqueo(uidHijo: String, uidPadre: String, nuevoBloqueo: BloqueoRegistro) {
        val fecha = obtenerFechaActual()
        val hora = obtenerHoraActual()
        val paquete = nuevoBloqueo.nombrePaquete

        val docId = "$fecha-$paquete"
        val docRef = db.collection("registrosBloqueo").document(docId)

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val contadorActual = snapshot.getLong("contadorIntentos") ?: 0
                    val intentosActuales = snapshot.get("intentos") as? List<String> ?: listOf()
                    val nuevosIntentos = intentosActuales + hora
                    val actualizaciones = mapOf(
                        "contadorIntentos" to contadorActual + 1,
                        "intentos" to nuevosIntentos
                    )
                    transaction.update(docRef, actualizaciones)
                } else {
                    val nuevoRegistro = nuevoBloqueo.copy(
                        fecha = fecha,
                        contadorIntentos = 1,
                        intentos = listOf(hora),
                        uidPadre = uidPadre,
                        uidHijo = uidHijo
                    )
                    transaction.set(docRef, nuevoRegistro)
                }
            }.await()
            Log.d(TAG, "✅ Registro de intento de acceso actualizado correctamente.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al registrar intento de bloqueo: ${e.message}", e)
        }
    }

    suspend fun obtenerRegistrosDeBloqueo(uidHijo: String): List<BloqueoRegistro> {
        return try {
            val snapshot = db.collection("registrosBloqueo")
                .whereEqualTo("uidHijo", uidHijo)
                .get()
                .await()

            snapshot.documents.mapNotNull { it.toObject(BloqueoRegistro::class.java) }
                .sortedByDescending { it.fecha }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener registros de bloqueo: ${e.message}", e)
            emptyList()
        }
    }

    private fun obtenerFechaActual(): String {
        val formato = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formato.format(Date())
    }

    private fun obtenerHoraActual(): String {
        val formato = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formato.format(Date())
    }
}
