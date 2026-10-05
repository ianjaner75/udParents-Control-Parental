package com.example.udparents.repositorio

import com.example.udparents.modelo.CodigoVinculacion
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RepositorioVinculacion {

    private val db = FirebaseFirestore.getInstance()

    suspend fun guardarCodigo(codigo: CodigoVinculacion) {
        db.collection("codigos_vinculacion")
            .document(codigo.codigo)
            .set(codigo)
            .await()
    }

    suspend fun existeCodigo(codigo: String): Boolean {
        val doc = db.collection("codigos_vinculacion").document(codigo).get().await()
        return doc.exists()
    }

    suspend fun verificarCodigoValido(codigo: String): Boolean {
        val doc = db.collection("codigos_vinculacion").document(codigo).get().await()
        val data = doc.toObject(CodigoVinculacion::class.java)

        if (data != null) {
            val tiempoActual = System.currentTimeMillis()
            val tiempoExpiracion = 5 * 60 * 1000 // 5 minutos
            return (tiempoActual - data.timestampCreacion) <= tiempoExpiracion
        }
        return false
    }

    fun marcarCodigoComoVinculado(codigo: String, idHijo: String, onResult: (Boolean) -> Unit) {
        db.collection("codigos_vinculacion")
            .document(codigo)
            .update(
                mapOf(
                    "vinculado" to true,
                    "dispositivoHijo" to idHijo,
                    "desinstalacionSolicitada" to false
                )
            )
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    suspend fun obtenerDispositivosVinculados(idPadre: String): List<CodigoVinculacion> {
        val snapshot = db.collection("codigos_vinculacion")
            .whereEqualTo("idPadre", idPadre)
            .whereEqualTo("vinculado", true)
            .get()
            .await()

        return snapshot.documents.mapNotNull { it.toObject(CodigoVinculacion::class.java) }
    }

    fun vincularConDatos(
        codigoVinculacion: CodigoVinculacion,
        onResult: (Boolean) -> Unit
    ) {
        val datos = mapOf(
            "vinculado" to true,
            "dispositivoHijo" to codigoVinculacion.dispositivoHijo,
            "nombreHijo" to codigoVinculacion.nombreHijo,
            "edadHijo" to codigoVinculacion.edadHijo,
            "sexoHijo" to codigoVinculacion.sexoHijo,
            "desinstalacionSolicitada" to false
        )

        db.collection("codigos_vinculacion")
            .document(codigoVinculacion.codigo)
            .update(datos)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
    /**
     * Solicita al dispositivo hijo que retire el vínculo y permita la desinstalación.
     * La comprobación de propiedad es defensiva en el cliente; las reglas de Firestore
     * también deben restringir esta escritura al idPadre autenticado.
     */
    suspend fun solicitarDesinstalacion(codigo: String, idPadre: String): Boolean {
        val referencia = db.collection("codigos_vinculacion").document(codigo)
        val documento = referencia.get().await()
        if (!documento.exists() ||
            documento.getString("idPadre") != idPadre ||
            documento.getBoolean("vinculado") != true
        ) {
            return false
        }

        referencia.update("desinstalacionSolicitada", true).await()
        return true
    }

    suspend fun dispositivoYaVinculado(idDispositivo: String): Boolean {
        val snapshot = db.collection("codigos_vinculacion")
            .whereEqualTo("dispositivoHijo", idDispositivo)
            .get()
            .await()
        return !snapshot.isEmpty
    }
}
