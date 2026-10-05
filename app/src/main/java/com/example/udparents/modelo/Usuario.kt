package com.example.udparents.modelo

import com.example.udparents.utilidades.campoNoVacio
import com.example.udparents.utilidades.esContrasenaValida
import com.example.udparents.utilidades.esCorreoValido

data class Usuario(
    val nombre: String = "",
    val correo: String = "",
    val contrasena: String = "",
    val alertaContenidoProhibido: Boolean = false,//para el envio de la notificacion al correo

) {
    fun esValido(): Boolean {
        return  campoNoVacio(nombre) && esCorreoValido(correo) && esContrasenaValida(contrasena)
    }
    fun esValidoParaLogin(): Boolean {
        return esCorreoValido(correo) && esContrasenaValida(contrasena)
    }
    fun esValidoParaRecuperar(): Boolean {
        return esCorreoValido(correo)
    }
}
