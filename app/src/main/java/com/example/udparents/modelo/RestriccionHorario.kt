package com.example.udparents.modelo

import java.util.Calendar

data class RestriccionHorario(
    val id: String = "", // ID único para el documento en Firebase
    val packageName: String = "", // Paquete de la app (o "ALL_APPS" para todas las apps)
    val appName: String = "", // Nombre amigable de la app (ej. "YouTube" o "Todas las Apps")
    val startTimeMillis: Long = 0, // Hora de inicio del bloqueo en milisegundos desde la medianoche (ej. 8 AM = 8 * 60 * 60 * 1000)
    val endTimeMillis: Long = 0, // Hora de fin del bloqueo en milisegundos desde la medianoche
    val daysOfWeek: List<Int> = emptyList(), // Lista de días de la semana (ej. Calendar.MONDAY, Calendar.TUESDAY)
    val isEnabled: Boolean = true, // Si la regla está activa o no
    val ruleName: String = "" // Nombre opcional para la regla (ej. "Hora de Dormir", "Estudio")
)
