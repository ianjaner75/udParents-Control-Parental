package com.example.udparents.repositorio
import com.google.firebase.firestore.SetOptions
import com.example.udparents.modelo.AppUso
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log
import com.example.udparents.modelo.RestriccionHorario
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar
import java.util.Date
import com.google.firebase.firestore.FieldValue
import java.util.Locale

class RepositorioApps {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "RepositorioApps"

    // *** Función existente: registrarUsoAplicacion (barrido de 30s) ***
    suspend fun registrarUsoAplicacion(uidHijo: String, appUso: AppUso) {
        val clave = "${appUso.nombrePaquete}_${formatearFecha(appUso.fechaUso)}"
        val docRef = db.collection("hijos").document(uidHijo)
            .collection("uso_apps").document(clave)

        try {
            // Usa merge para solo actualizar los campos proporcionados, el tiempoUso ya viene acumulado.
            docRef.set(appUso, SetOptions.merge()).await() // .await() para asegurar que la operación se complete
            Log.d(TAG, "✅ Uso de app registrado/actualizado: ${appUso.nombreApp} (${appUso.tiempoUso} ms) para ${uidHijo}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al registrar uso de app ${appUso.nombrePaquete}: ${e.message}", e)
        }
    }

    // *** FUNCIÓN CORREGIDA: incrementarUsoAplicacion (para el monitoreo en tiempo real) ***
    suspend fun incrementarUsoAplicacion(uidHijo: String, packageName: String, appName: String, incrementBy: Long) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fecha = formatearFecha(calendar.timeInMillis)
        val docId = "${packageName}_$fecha"
        val docRef = db.collection("hijos").document(uidHijo)
            .collection("uso_apps").document(docId)

        try {
            // Intenta obtener el documento primero
            val docSnapshot = docRef.get().await()

            if (docSnapshot.exists()) {
                // Si el documento ya existe, usa FieldValue.increment() para actualizar de forma atómica.
                // Esto garantiza que múltiples actualizaciones concurrentes no sobrescriban el valor.
                docRef.update("tiempoUso", FieldValue.increment(incrementBy)).await()
                Log.d(TAG, "📈 Uso de $packageName incrementado en $incrementBy ms.")
            } else {
                // Si el documento no existe, lo creamos con el valor inicial.
                val appUsoInicial = AppUso(
                    nombrePaquete = packageName,
                    nombreApp = appName,
                    fechaUso = calendar.timeInMillis,
                    tiempoUso = incrementBy
                )
                //  SetOptions.merge() para crear el documento o fusionarlo si ya existe con otros campos
                docRef.set(appUsoInicial, SetOptions.merge()).await()
                Log.d(TAG, "➕ Documento de uso para $packageName creado con $incrementBy ms.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inesperado al incrementar/crear uso de $packageName: ${e.message}", e)
        }
    }

    suspend fun guardarRestriccionHorario(uidHijo: String, restriccion: RestriccionHorario) {
        val docRef = db.collection("hijos").document(uidHijo)
            .collection("horarios_restriccion").document(restriccion.id.ifEmpty { db.collection("hijos").document(uidHijo).collection("horarios_restriccion").document().id }) // Genera ID si está vacío
        try {
            docRef.set(restriccion).await()
            Log.d(TAG, "✅ Restricción de horario guardada/actualizada: ${restriccion.ruleName} (ID: ${docRef.id})")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al guardar restricción de horario: ${e.message}", e)
        }
    }

    suspend fun obtenerRestriccionesHorario(uidHijo: String): List<RestriccionHorario> {
        return try {
            val snapshot = db.collection("hijos").document(uidHijo)
                .collection("horarios_restriccion")
                .get()
                .await()
            snapshot.documents.mapNotNull { it.toObject(RestriccionHorario::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener restricciones de horario: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun eliminarRestriccionHorario(uidHijo: String, restriccionId: String) {
        try {
            db.collection("hijos").document(uidHijo)
                .collection("horarios_restriccion").document(restriccionId)
                .delete().await()
            Log.d(TAG, "✅ Restricción de horario eliminada: $restriccionId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al eliminar restricción de horario: ${e.message}", e)
        }
    }

    suspend fun establecerLimiteApp(uidHijo: String, packageName: String, tiempoLimite: Long) {
        try {
            val ref = db.collection("hijos").document(uidHijo)
                .collection("limites_apps")
                .document(packageName)

            ref.set(mapOf("tiempoLimite" to tiempoLimite)).await()
            Log.d(TAG, "✅ Límite establecido para $packageName: ${tiempoLimite} ms")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al guardar límite para $packageName: ${e.message}", e)
        }
    }

    suspend fun obtenerLimiteApp(uidHijo: String, paquete: String): Long {
        return try {
            val doc = Firebase.firestore
                .collection("hijos")
                .document(uidHijo)
                .collection("limites_apps")
                .document(paquete)
                .get()
                .await()

            doc.getLong("tiempoLimite") ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener límite para $paquete: ${e.message}", e)
            0L
        }
    }

    suspend fun obtenerUsoAppDelDia(uidHijo: String, paquete: String): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fecha = formatearFecha(calendar.timeInMillis)
        val docId = "${paquete}_$fecha"

        val doc = db.collection("hijos").document(uidHijo)
            .collection("uso_apps")
            .document(docId)
            .get()
            .await()

        return doc.getLong("tiempoUso") ?: 0L
    }

    suspend fun obtenerLimitesApps(uidHijo: String): Map<String, Long> {
        return try {
            val snapshot = db.collection("hijos").document(uidHijo)
                .collection("limites_apps")
                .get()
                .await()

            snapshot.documents.associate {
                val paquete = it.id
                val limite = it.getLong("tiempoLimite") ?: 0L
                paquete to limite
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener límites: ${e.message}", e)
            emptyMap()
        }
    }

    suspend fun obtenerUsosPorFecha(idHijo: String, desde: Long, hasta: Long): List<AppUso> {
        val desdeDia = formatearFecha(desde)
        val hastaDia = formatearFecha(hasta)

        val snapshot = db.collection("hijos")
            .document(idHijo)
            .collection("uso_apps")
            .get()
            .await()

        return snapshot.documents.mapNotNull { it.toObject(AppUso::class.java) }
            .filter { appUso ->
                val fecha = formatearFecha(appUso.fechaUso)
                fecha >= desdeDia && fecha <= hastaDia
            }
    }

    suspend fun obtenerHijosVinculados(idPadre: String): List<Pair<String, String>> {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("codigos_vinculacion")
            .whereEqualTo("idPadre", idPadre)
            .whereEqualTo("vinculado", true)
            .get()
            .await()

        return snapshot.documents.mapNotNull {
            val uid = it.getString("dispositivoHijo")
            val nombre = it.getString("nombreHijo") ?: "Hijo"
            if (uid != null) Pair(uid, nombre) else null
        }
    }
    suspend fun bloquearApp(uidHijo: String, paquete: String, bloquear: Boolean) {
        try {
            val ref = db.collection("bloqueos")
                .document(uidHijo)
                .collection("apps")
                .document(paquete)

            val datos = mapOf("bloqueada" to bloquear)
            ref.set(datos).await()
        } catch (e: Exception) {
            e.printStackTrace() //  manejar esto con logs o mostrar error en UI
        }
    }

    suspend fun estaAppBloqueada(uidHijo: String, paquete: String): Boolean {
        return try {
            val snapshot = db.collection("bloqueos")
                .document(uidHijo)
                .collection("apps")
                .document(paquete)
                .get()
                .await()

            snapshot.getBoolean("bloqueada") == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun formatearFecha(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    // =================================================================================================
    // FUNCIÓN ACTUALIZADA: Obtiene el tiempo total de pantalla por día, ahora con CAP
    // =================================================================================================
    suspend fun obtenerTiempoPantallaDiario(uidHijo: String): Map<String, Long> {
        return try {
            val calendar = Calendar.getInstance()
            calendar.firstDayOfWeek = Calendar.MONDAY
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val inicioSemana = calendar.timeInMillis

            val snapshot = db.collection("hijos").document(uidHijo)
                .collection("uso_apps")
                .whereGreaterThanOrEqualTo("fechaUso", inicioSemana)
                .get()
                .await()

            val resumen = snapshot.documents.mapNotNull { it.toObject(AppUso::class.java) }
                .groupBy { formatearFecha(it.fechaUso) }
                .mapValues { (_, usosDelDia) -> usosDelDia.sumOf { it.tiempoUso } }
                .toMutableMap()

            // --- CAP de hoy ---
            val hoyCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val hoyKey = formatearFecha(System.currentTimeMillis())
            val transcurridoHoy = System.currentTimeMillis() - hoyCal.timeInMillis
            val actualHoy = resumen[hoyKey] ?: 0L
            if (actualHoy > transcurridoHoy) {
                Log.w(TAG, "CAPEANDO hoy: actual=$actualHoy > transcurrido=$transcurridoHoy")
                resumen[hoyKey] = transcurridoHoy
            }

            Log.d(TAG, "✅ Resumen diario (capado hoy) cargado: $resumen")
            resumen
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener resumen de tiempo diario: ${e.message}", e)
            emptyMap()
        }
    }

    // =================================================================================================
    // FUNCIÓN ACTUALIZADA: Obtiene el tiempo total de pantalla por semana, derivado del resumen diario
    // =================================================================================================
    suspend fun obtenerTiempoPantallaSemanal(uidHijo: String): Map<String, Long> {
        return try {
            // 1) Trae el diario (capado) para ~4 semanas
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.WEEK_OF_YEAR, -4)
            }
            val inicioHace4Semanas = cal.timeInMillis

            val snapshot = db.collection("hijos").document(uidHijo)
                .collection("uso_apps")
                .whereGreaterThanOrEqualTo("fechaUso", inicioHace4Semanas)
                .get()
                .await()

            val diario = snapshot.documents.mapNotNull { it.toObject(AppUso::class.java) }
                .groupBy { formatearFecha(it.fechaUso) }
                .mapValues { (_, usosDelDia) -> usosDelDia.sumOf { it.tiempoUso } }
                .toMutableMap()

            // --- CAP de hoy, igual que arriba ---
            val hoyCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val hoyKey = formatearFecha(System.currentTimeMillis())
            val transcurridoHoy = System.currentTimeMillis() - hoyCal.timeInMillis
            val actualHoy = diario[hoyKey] ?: 0L
            if (actualHoy > transcurridoHoy) {
                Log.w(TAG, "CAPEANDO hoy: actual=$actualHoy > transcurrido=$transcurridoHoy")
                diario[hoyKey] = transcurridoHoy
            }

            // 2) Agrupa esas fechas por número de semana con año para evitar mezclas
            val resumenSemanal = diario.entries.groupBy { (keyFecha, _) ->
                val c = Calendar.getInstance()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                c.time = sdf.parse(keyFecha) ?: Date()
                val yy = c.get(Calendar.YEAR)
                val wk = c.get(Calendar.WEEK_OF_YEAR)
                "${yy}-W${wk}"
            }.mapValues { (_, entries) ->
                entries.sumOf { it.value }
            }

            Log.d(TAG, "✅ Resumen semanal (derivado de diario capado) cargado: $resumenSemanal")
            resumenSemanal
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener resumen de tiempo semanal: ${e.message}", e)
            emptyMap()
        }
    }

    suspend fun obtenerAppsMasUsadas(uidHijo: String, desde: Long, hasta: Long): Map<String, Long> {
        return try {
            // Realiza la consulta a Firestore filtrando los documentos por el rango de fechas.
            val snapshot = db.collection("hijos").document(uidHijo)
                .collection("uso_apps")
                .whereGreaterThanOrEqualTo("fechaUso", desde)
                .whereLessThanOrEqualTo("fechaUso", hasta)
                .get()
                .await()

            // Mapea los documentos a objetos AppUso.
            val usos = snapshot.documents.mapNotNull { it.toObject(AppUso::class.java) }

            // Agrupa los objetos por el nombre de la aplicación y suma el tiempo de uso para cada grupo.
            val resumenPorApp = usos.groupBy { it.nombreApp }
                .mapValues { (_, usosDeLaApp) ->
                    usosDeLaApp.sumOf { it.tiempoUso }
                }

            Log.d(TAG, "✅ Informe de apps más usadas obtenido para el rango de fechas. ${resumenPorApp.size} apps encontradas.")
            resumenPorApp
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener informe de apps más usadas: ${e.message}", e)
            emptyMap()
        }
    }
    /**
     * Obtiene el uso de aplicaciones para un rango de fechas específico.
     * Se puede usar para alimentar tanto la vista diaria como la semanal.
     * @param uidHijo El UID del hijo.
     * @param diasAnteriores El número de días que se quieren obtener (ej. 30 o 40).
     * @return Un mapa con la fecha (yyyy-MM-dd) como clave y el tiempo total de uso diario en milisegundos.
     */
    suspend fun obtenerUsoPorRangoDeDias(uidHijo: String, diasAnteriores: Int): Map<String, Long> {
        return try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                add(Calendar.DAY_OF_YEAR, -diasAnteriores)
            }
            val fechaHaceNDias = calendar.timeInMillis

            // Se hace una consulta a Firestore por el rango de fechas
            val snapshot = db.collection("hijos").document(uidHijo)
                .collection("uso_apps")
                .whereGreaterThanOrEqualTo("fechaUso", fechaHaceNDias)
                .get()
                .await()

            // Agrupa los datos por día para obtener el resumen diario
            val resumen = snapshot.documents.mapNotNull { it.toObject(AppUso::class.java) }
                .groupBy { formatearFecha(it.fechaUso) }
                .mapValues { (_, usosDelDia) -> usosDelDia.sumOf { it.tiempoUso } }

            Log.d(TAG, "✅ Uso por rango de ${diasAnteriores} días obtenido: ${resumen.size} días con datos.")
            resumen
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener uso por rango de días: ${e.message}", e)
            emptyMap()
        }
    }
}