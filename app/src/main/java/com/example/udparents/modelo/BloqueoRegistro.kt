    package com.example.udparents.modelo

    import com.google.firebase.firestore.ServerTimestamp
    import java.util.Date

    /**
     * Clase de datos para representar un intento de acceso a una app bloqueada.
     * Se utiliza para registrar la información en Firestore.
     */
    data class BloqueoRegistro(
        val uidHijo: String = "",
        val nombrePaquete: String = "",
        val nombreApp: String = "",
        val razon: String = "",
        val contadorIntentos: Long = 1,
        val intentos: List<String> = listOf(), // lista de timestamps tipo "12:34:56"
        val fecha: String = "",
        val uidPadre: String = "",
    )

