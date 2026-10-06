package com.example.udparents.repositorio

import android.util.Log
import com.example.udparents.modelo.CodigoVinculacion
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RepositorioVinculacion {

    private val db = FirebaseFirestore.getInstance()
    private val coleccionCodigos = db.collection("codigos_vinculacion") // Usamos una variable para evitar errores de escritura

    private companion object {
        const val TAG = "RepositorioVinculacion"

        /**
         * ⏳ Tiempo de gracia entre escribir la bandera `desvincular = true` (orden
         * remota) y el borrado físico del documento en Firestore.
         *
         * Este margen es lo que permite que el listener en TIEMPO REAL del
         * dispositivo del hijo reciba la orden y ejecute su limpieza local
         * (restaurar ícono, detener servicio, quitar Administrador de Dispositivos)
         * ANTES de que el documento desaparezca. Así la purga del padre NO rompe
         * la lógica de desvinculación remota.
         */
        const val TIEMPO_GRACIA_PURGA_MS = 20_000L

        /**
         * Alcance de aplicación para la purga diferida: sobrevive a la destrucción
         * del ViewModel/pantalla del padre, garantizando que el `.delete()` se
         * ejecute aunque el padre cierre la app tras autorizar la desvinculación.
         */
        val ALCANCE_PURGAS = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    suspend fun guardarCodigo(codigo: CodigoVinculacion) {
        coleccionCodigos
            .document(codigo.codigo)
            .set(codigo)
            .await()
    }

    suspend fun existeCodigo(codigo: String): Boolean {
        val doc = coleccionCodigos.document(codigo).get().await()
        return doc.exists()
    }

    suspend fun verificarCodigoValido(codigo: String): Boolean {
        val doc = coleccionCodigos.document(codigo).get().await()
        val data = doc.toObject(CodigoVinculacion::class.java)

        if (data != null) {
            val tiempoActual = System.currentTimeMillis()
            val tiempoExpiracion = 5 * 60 * 1000 // 5 minutos
            // 💡 Se añade la verificación para asegurar que el código no esté ya vinculado
            return (tiempoActual - data.timestampCreacion) <= tiempoExpiracion && !data.vinculado
        }
        return false
    }

    fun marcarCodigoComoVinculado(codigo: String, idHijo: String, onResult: (Boolean) -> Unit) {
        coleccionCodigos
            .document(codigo)
            .update(
                mapOf(
                    "vinculado" to true,
                    "dispositivoHijo" to idHijo,
                    "timestampVinculacion" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    /**
     *  NUEVA FUNCIÓN AÑADIDA: Obtiene un objeto CodigoVinculacion completo a partir de su código.
     * Esta función es necesaria para obtener el UID del padre.
     */
    suspend fun obtenerCodigoPorID(codigo: String): CodigoVinculacion? {
        val documento = coleccionCodigos.document(codigo).get().await()
        return if (documento.exists()) {
            documento.toObject(CodigoVinculacion::class.java)
        } else {
            null
        }
    }

    suspend fun obtenerDispositivosVinculados(idPadre: String): List<CodigoVinculacion> {
        val snapshot = coleccionCodigos
            .whereEqualTo("idPadre", idPadre)
            .whereEqualTo("vinculado", true)
            .get()
            .await()

        return snapshot.documents
            .mapNotNull { it.toObject(CodigoVinculacion::class.java) }
            // 🚩 Excluir vinculaciones cuya desvinculación ya fue autorizada
            // (el dispositivo del hijo aún está procesando la orden remota).
            .filter { !it.desvincular }
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
            // ✅ Consentimiento
            "termsAccepted" to codigoVinculacion.termsAccepted,
            "termsVersion" to codigoVinculacion.termsVersion,
            "termsAcceptedAt" to (codigoVinculacion.termsAcceptedAt ?: System.currentTimeMillis()),

            //  útil para auditoría
            "timestampVinculacion" to System.currentTimeMillis()
        )

        coleccionCodigos
            .document(codigoVinculacion.codigo)
            .update(datos)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
    suspend fun dispositivoYaVinculado(idDispositivo: String): Boolean {
        val snapshot = coleccionCodigos
            .whereEqualTo("dispositivoHijo", idDispositivo)
            .get()
            .await()
        // 🚩 Solo bloquea una nueva vinculación si existe al menos un documento
        // ACTIVO. Una vinculación con 'desvincular = true' ya fue autorizada por
        // el padre (y está en proceso de purga), por lo que no debe impedir que
        // el mismo dispositivo se vuelva a vincular.
        return snapshot.documents.any { it.getBoolean("desvincular") != true }
    }
    fun actualizarVinculacion(
        uidPadre: String,
        dispositivo: CodigoVinculacion,
        onResult: (Boolean) -> Unit
    ) {
        // Se actualizan solo los campos que pueden ser editados.
        coleccionCodigos
            .document(dispositivo.codigo)
            .update(
                mapOf(
                    "nombreHijo" to dispositivo.nombreHijo,
                    "edadHijo" to dispositivo.edadHijo,
                    "sexoHijo" to dispositivo.sexoHijo
                )
            )
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    /**
     * Elimina una vinculación de la base de datos de Firestore (borrado físico).
     * Útil cuando el dispositivo del hijo ya no está enlazado (p. ej. se
     * desinstaló la app o se formateó) y solo se quiere limpiar el registro.
     * Para una desvinculación remota en caliente, usar [autorizarDesvinculacion].
     * @param uidPadre El UID del padre.
     * @param uidHijo El UID del hijo a desvincular.
     * @param onResult Callback que indica si la operación fue exitosa o no.
     */
    fun eliminarVinculacion(
        uidPadre: String,
        uidHijo: String,
        onResult: (Boolean) -> Unit
    ) {
        // 🧹 PURGA EXPLÍCITA: se buscan TODOS los documentos de vinculación del
        // hijo y se borran físicamente de Firestore con .delete().
        coleccionCodigos
            .whereEqualTo("idPadre", uidPadre)
            .whereEqualTo("dispositivoHijo", uidHijo)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    onResult(false) // No se encontró el documento para eliminar
                    return@addOnSuccessListener
                }
                eliminarDocumentos(
                    referencias = querySnapshot.documents.map { it.reference },
                    uidHijo = uidHijo,
                    onResult = onResult
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error buscando la vinculación a eliminar (hijo=$uidHijo): ${e.message}", e)
                onResult(false)
            }
    }

    /**
     * 🧹 Borra físicamente una lista de documentos con `.delete()`.
     * El callback solo se invoca cuando TODAS las operaciones terminaron.
     * @param referencias Documentos a eliminar.
     * @param uidHijo UID del hijo (solo para trazabilidad en logs).
     * @param onResult `true` si al menos un borrado no falló.
     */
    private fun eliminarDocumentos(
        referencias: List<DocumentReference>,
        uidHijo: String,
        onResult: (Boolean) -> Unit
    ) {
        if (referencias.isEmpty()) {
            onResult(false)
            return
        }
        var pendientes = referencias.size
        var algunExito = false
        referencias.forEach { referencia ->
            referencia.delete()
                .addOnSuccessListener {
                    algunExito = true
                    Log.i(TAG, "🧹 Documento ${referencia.id} del hijo $uidHijo eliminado de Firestore")
                    if (--pendientes == 0) onResult(algunExito)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ No se pudo eliminar el documento ${referencia.id}: ${e.message}", e)
                    if (--pendientes == 0) onResult(algunExito)
                }
        }
    }

    /**
     * 🔥 Purga explícita (suspend) de la vinculación de un hijo en Firestore.
     * Elimina con `.delete()` TODOS los documentos que coincidan con el par
     * (padre, hijo). Se usa tanto en el borrado manual del padre como en la
     * purga diferida posterior a la desvinculación remota.
     *
     * @return cantidad de documentos eliminados correctamente.
     */
    suspend fun purgarVinculacionHijo(uidPadre: String, uidHijo: String): Int {
        val snapshot = try {
            coleccionCodigos
                .whereEqualTo("idPadre", uidPadre)
                .whereEqualTo("dispositivoHijo", uidHijo)
                .get()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error consultando documentos a purgar (hijo=$uidHijo): ${e.message}", e)
            return 0
        }

        var eliminados = 0
        snapshot.documents.forEach { documento ->
            try {
                documento.reference.delete().await()
                eliminados++
                Log.i(TAG, "🧹 Documento ${documento.id} del hijo $uidHijo eliminado de Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "❌ No se pudo eliminar el documento ${documento.id}: ${e.message}", e)
            }
        }
        if (eliminados == 0) {
            Log.d(TAG, "ℹ️ No quedaban documentos por purgar para el hijo $uidHijo")
        }
        return eliminados
    }

    /**
     * ⏳ Programa la purga diferida del documento del hijo tras autorizar la
     * desvinculación remota: primero se da tiempo al hijo para leer la bandera
     * `desvincular = true` (lógica remota intacta) y después se borra el
     * documento con [purgarVinculacionHijo].
     */
    private fun programarPurgaDiferida(uidPadre: String, uidHijo: String) {
        ALCANCE_PURGAS.launch {
            delay(TIEMPO_GRACIA_PURGA_MS)
            val eliminados = purgarVinculacionHijo(uidPadre, uidHijo)
            Log.i(
                TAG,
                "🧹 Purga diferida completada para el hijo $uidHijo: $eliminados documento(s) eliminado(s)"
            )
        }
    }

    /**
     * 🚩 Autoriza la desvinculación remota de un hijo vinculado.
     *
     * Activa la bandera `desvincular` en Firestore para que el dispositivo del
     * hijo la detecte en tiempo real, restaure su ícono, limpie su sesión local,
     * remueva su privilegio de Administrador de Dispositivos y, finalmente,
     * elimine el documento de vinculación.
     *
     * @param uidPadre El UID del padre.
     * @param uidHijo El UID del hijo cuya desvinculación se autoriza.
     * @param onResult Callback que indica si la operación fue exitosa o no.
     */
    fun autorizarDesvinculacion(
        uidPadre: String,
        uidHijo: String,
        onResult: (Boolean) -> Unit
    ) {
        coleccionCodigos
            .whereEqualTo("idPadre", uidPadre)
            .whereEqualTo("dispositivoHijo", uidHijo)
            .get()
            .addOnSuccessListener { querySnapshot ->
                // 🎯 Se escribe la bandera en el documento EXACTO de la vinculación
                // ACTIVA (vinculado == true), no en cualquier documento que coincida.
                val documentoObjetivo = querySnapshot.documents.firstOrNull {
                    it.getBoolean("vinculado") == true
                }
                if (documentoObjetivo == null) {
                    Log.w(
                        "RepositorioVinculacion",
                        "No se encontró la vinculación activa para autorizar la desvinculación (padre=$uidPadre, hijo=$uidHijo)"
                    )
                    onResult(false)
                    return@addOnSuccessListener
                }
                documentoObjetivo.reference.update(
                    mapOf(
                        "desvincular" to true,
                        "timestampDesvinculacion" to System.currentTimeMillis()
                    )
                )
                    .addOnSuccessListener {
                        Log.i(
                            TAG,
                            "🚩 Bandera 'desvincular=true' escrita en el documento ${documentoObjetivo.id}"
                        )
                        // 🧹 PURGA EXPLÍCITA EN FIRESTORE (además de la orden remota):
                        // el padre borra físicamente el documento del hijo con
                        // .delete(). Se hace de forma DIFERIDA para que el
                        // dispositivo del hijo alcance a leer 'desvincular=true' y
                        // complete su limpieza local; así la desvinculación remota
                        // sigue funcionando igual que antes.
                        programarPurgaDiferida(uidPadre, uidHijo)
                        onResult(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error escribiendo la bandera 'desvincular': ${e.message}", e)
                        onResult(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("RepositorioVinculacion", "Error consultando la vinculación a desvincular: ${e.message}", e)
                onResult(false)
            }
    }
    fun obtenerEstadoAlertaContenidoPadre(
        uidPadre: String,
        onResultado: (Boolean) -> Unit
    ) {
        FirebaseFirestore.getInstance().collection("usuarios")
            .document(uidPadre)
            .get()
            .addOnSuccessListener { documento ->
                val activado = documento.getBoolean("alertaContenidoProhibido") ?: false
                onResultado(activado)
            }
            .addOnFailureListener {
                onResultado(false)
            }
    }

}
