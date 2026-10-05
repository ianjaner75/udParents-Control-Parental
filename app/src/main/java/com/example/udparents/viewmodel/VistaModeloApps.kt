package com.example.udparents.viewmodel

// Archivo: VistaModeloApps.kt

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.udparents.modelo.AppUso
import com.example.udparents.modelo.RestriccionHorario
import com.example.udparents.modelo.BloqueoRegistro
import com.example.udparents.repositorio.RepositorioApps
import com.example.udparents.repositorio.RepositorioBloqueos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class VistaModeloApps : ViewModel() {

    private val repositorio = RepositorioApps()
    private val repositorioBloqueos = RepositorioBloqueos()
    private val _listaUsos = MutableStateFlow<List<AppUso>>(emptyList())
    val listaUsos: StateFlow<List<AppUso>> = _listaUsos
    private val _hijosVinculados = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val hijosVinculados: StateFlow<List<Pair<String, String>>> = _hijosVinculados
    private val _estadoBloqueoApp = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val estadoBloqueoApp: StateFlow<Map<String, Boolean>> = _estadoBloqueoApp
    private val _limitesApp = MutableStateFlow<Map<String, Long>>(emptyMap())
    val limitesApp: StateFlow<Map<String, Long>> = _limitesApp
    private val _restriccionesHorario = MutableStateFlow<List<RestriccionHorario>>(emptyList())
    val restriccionesHorario: StateFlow<List<RestriccionHorario>> = _restriccionesHorario
    private val _tiempoPantallaDiario = MutableStateFlow<Map<String, Long>>(emptyMap())
    val tiempoPantallaDiario: StateFlow<Map<String, Long>> = _tiempoPantallaDiario
    private val _tiempoPantallaSemanal = MutableStateFlow<Map<String, Long>>(emptyMap())
    val tiempoPantallaSemanal: StateFlow<Map<String, Long>> = _tiempoPantallaSemanal
    private val _appsMasUsadas = MutableStateFlow<Map<String, Long>>(emptyMap())
    val appsMasUsadas: StateFlow<Map<String, Long>> = _appsMasUsadas
    private val _registroBloqueos = MutableStateFlow<List<BloqueoRegistro>>(emptyList())
    val registroBloqueos: StateFlow<List<BloqueoRegistro>> = _registroBloqueos

    fun cargarRestriccionesHorario(uidHijo: String) {
        viewModelScope.launch {
            try {
                _restriccionesHorario.value = repositorio.obtenerRestriccionesHorario(uidHijo)
                Log.d("VistaModeloApps", "Restricciones de horario cargadas: ${_restriccionesHorario.value.size}")
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al cargar restricciones de horario: ${e.message}", e)
            }
        }
    }

    fun guardarRestriccionHorario(uidHijo: String, restriccion: RestriccionHorario) {
        viewModelScope.launch {
            try {
                repositorio.guardarRestriccionHorario(uidHijo, restriccion)
                cargarRestriccionesHorario(uidHijo) // Recargar para actualizar la UI
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al guardar restricción de horario: ${e.message}", e)
            }
        }
    }

    fun eliminarRestriccionHorario(uidHijo: String, restriccionId: String) {
        viewModelScope.launch {
            try {
                repositorio.eliminarRestriccionHorario(uidHijo, restriccionId)
                cargarRestriccionesHorario(uidHijo) // se  Recargar para actualizar la UI
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al eliminar restricción de horario: ${e.message}", e)
            }
        }
    }

    fun cargarUsos(uidHijo: String, desde: Long, hasta: Long) {
        viewModelScope.launch {
            try {
                val usos = repositorio.obtenerUsosPorFecha(uidHijo, desde, hasta)
                _listaUsos.value = usos
                Log.d("VistaModeloApps", "Usos cargados: ${usos.size} apps.")
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al cargar usos: ${e.message}", e)
            }
        }
    }
    fun cargarHijos(idPadre: String) {
        viewModelScope.launch {
            val hijos = repositorio.obtenerHijosVinculados(idPadre)
            _hijosVinculados.value = hijos
        }
    }

    // Cambia el estado de bloqueo (true para bloquear, false para desbloquear)
    fun cambiarEstadoBloqueo(uidHijo: String, paquete: String, bloquear: Boolean) {
        viewModelScope.launch {
            try {
                repositorio.bloquearApp(uidHijo, paquete, bloquear)
                // Actualiza el estado localmente para reflejar el cambio
                _estadoBloqueoApp.value = _estadoBloqueoApp.value.toMutableMap().apply {
                    put(paquete, bloquear)
                }
                Log.d("VistaModeloApps", "App $paquete ${if (bloquear) "bloqueada" else "desbloqueada"}.")
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al cambiar estado de bloqueo para $paquete: ${e.message}", e)
            }
        }
    }

    // Verifica si una app está bloqueada y actualiza el estado local
    fun verificarSiEstaBloqueada(uidHijo: String, paquete: String) {
        viewModelScope.launch {
            try {
                val bloqueada = repositorio.estaAppBloqueada(uidHijo, paquete)
                _estadoBloqueoApp.value = _estadoBloqueoApp.value.toMutableMap().apply {
                    put(paquete, bloqueada)
                }
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al verificar bloqueo para $paquete: ${e.message}", e)
            }
        }
    }
    fun cargarLimites(uidHijo: String) {
        viewModelScope.launch {
            try {
                // Esta llamada ahora usará la ruta y el campo correctos.
                val limites = repositorio.obtenerLimitesApps(uidHijo)
                _limitesApp.value = limites
                Log.d("VistaModeloApps", "Límites cargados: ${limites.size} apps.")
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al cargar límites: ${e.message}", e)
            }
        }
    }
    fun establecerLimite(uidHijo: String, paquete: String, tiempoLimite: Long) {
        viewModelScope.launch {
            try {
                // la llamada ahora usa la ruta y el campo correctos.
                repositorio.establecerLimiteApp(uidHijo, paquete, tiempoLimite)
                // se actualiz el estado localmente
                _limitesApp.value = _limitesApp.value.toMutableMap().apply {
                    put(paquete, tiempoLimite)
                }
                Log.d("VistaModeloApps", "Límite establecido para $paquete: $tiempoLimite ms.")
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al establecer límite para $paquete: ${e.message}", e)
            }
        }
    }

    // =================================================================================================
    // FUNCIÓN UNIFICADA: Ahora carga los datos diarios y los agrupa para la vista semanal.
    // Reemplaza cargarResumenTiempoPantallaDiario y cargarResumenTiempoPantallaSemanal.
    // =================================================================================================
    fun cargarDatosTiempoPantalla(uidHijo: String) {
        viewModelScope.launch {
            try {
                // Obtenemos los datos de un historial más amplio (ej. 30 días)
                val resumenDiario = repositorio.obtenerUsoPorRangoDeDias(uidHijo, 30)

                // Actualizamos el StateFlow de los datos diarios (la vista diaria lo filtrará a 7 días)
                _tiempoPantallaDiario.value = resumenDiario

                // Agrupamos esos mismos datos para la vista semanal
                val resumenSemanal = groupByIsoWeekAll(resumenDiario)
                _tiempoPantallaSemanal.value = resumenSemanal

                Log.d("VistaModeloApps", "Datos de tiempo de pantalla cargados. ${resumenDiario.size} días y ${resumenSemanal.size} semanas.")
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al cargar datos de tiempo de pantalla: ${e.message}", e)
            }
        }
    }

    /**
     * Carga el informe de aplicaciones más usadas para un hijo y un rango de fechas.
     * @param uidHijo El UID del hijo.
     * @param desde El timestamp de inicio del rango.
     * @param hasta El timestamp de fin del rango.
     */
    fun cargarAppsMasUsadas(uidHijo: String, desde: Long, hasta: Long) {
        viewModelScope.launch {
            try {
                val appsUsadas = repositorio.obtenerAppsMasUsadas(uidHijo, desde, hasta)
                _appsMasUsadas.value = appsUsadas
                Log.d("VistaModeloApps", "Informe de apps más usadas cargado: ${appsUsadas.size} apps.")
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al cargar apps más usadas: ${e.message}", e)
            }
        }
    }
    fun cargarRegistroBloqueos(uidHijo: String) {
        viewModelScope.launch {
            try {
                val bloqueos = repositorioBloqueos.obtenerRegistrosDeBloqueo(uidHijo)
                _registroBloqueos.value = bloqueos
                Log.d("VistaModeloApps", "Registros de bloqueo cargados: ${bloqueos.size} eventos.")
            } catch (e: Exception) {
                Log.e("VistaModeloApps", "Error al cargar registros de bloqueo: ${e.message}", e)
            }
        }
    }

    // =================================================================================================
    // FUNCIÓN AUXILIAR: Agrupa los datos diarios por semana ISO 8601.
    // =================================================================================================
    private fun groupByIsoWeekAll(data: Map<String, Long>): Map<String, Long> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val agg = mutableMapOf<String, Long>()
        data.forEach { (k, v) ->
            val d = runCatching { sdf.parse(k) }.getOrNull() ?: return@forEach
            val wk = weekKeyISO(d)
            agg[wk] = (agg[wk] ?: 0L) + v
        }
        return agg.toList()
            .sortedWith(compareBy(
                { it.first.substringBefore("-W").toIntOrNull() ?: Int.MAX_VALUE },
                { it.first.substringAfter("-W").toIntOrNull() ?: Int.MAX_VALUE }
            ))
            .toMap(LinkedHashMap())
    }

    private fun weekKeyISO(date: Date): String {
        val cal = Calendar.getInstance().apply {
            time = date
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return "%04d-W%02d".format(year, week)
    }

    // NOTA: Las funciones 'cargarResumenTiempoPantallaDiario' y 'cargarResumenTiempoPantallaSemanal'
    // han sido eliminadas ya que la nueva función 'cargarDatosTiempoPantalla' las reemplaza.
}