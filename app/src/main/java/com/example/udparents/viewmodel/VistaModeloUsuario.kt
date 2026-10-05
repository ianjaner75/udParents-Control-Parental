package com.example.udparents.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.udparents.repositorio.RepositorioUsuario
import com.example.udparents.modelo.Usuario
import com.example.udparents.utilidades.esCorreoValido
import com.example.udparents.utilidades.esContrasenaValida
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VistaModeloUsuario : ViewModel() {

    private val repositorio = RepositorioUsuario()

    private val _alertaContenido = MutableStateFlow(false)
    val alertaContenido: StateFlow<Boolean> = _alertaContenido

    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje

    private val _usuario = MutableStateFlow(Usuario())
    val usuario: StateFlow<Usuario> = _usuario
    private companion object {
        // mínimo de letras
        const val MIN_LETRAS_SIN_ESPACIOS = 10
    }
    fun actualizarCorreo(correo: String) {
        _usuario.value = _usuario.value.copy(correo = correo.trim())
    }

    fun actualizarContrasena(contrasena: String) {
        _usuario.value = _usuario.value.copy(contrasena = contrasena.trim())
    }

    fun actualizarNombre(nombre: String) {
        _usuario.value = _usuario.value.copy(nombre = nombre)
    }
    private fun normalizarNombreEntrada(nombre: String): String =
        nombre.trim().replace("\\s+".toRegex(), " ")
    fun registrarUsuario(onResultado: (Boolean, String?) -> Unit) {
        val usuarioActual = _usuario.value
        Log.d("VMUsuario", "registrarUsuario() -> start con: correo=${usuarioActual.correo}")

        // Validaciones UI
        if (!validarNombrePadre(usuarioActual.nombre)) {
            val msg = "Escribe nombre y apellido (mín. $MIN_LETRAS_SIN_ESPACIOS letras en total)."
            _mensaje.value = msg
            Log.w("VMUsuario", "Nombre inválido: '${usuarioActual.nombre}'")
            onResultado(false, msg)
            return
        }
        if (!validarContrasenaSegura(usuarioActual.contrasena)) {
            val msg = "La contraseña debe tener al menos 8 caracteres, incluyendo mayúsculas, minúsculas, números y símbolos."
            _mensaje.value = msg
            Log.w("VMUsuario", "Contraseña no cumple política")
            onResultado(false, msg)
            return
        }
        if (!usuarioActual.esValido()) {
            val msg = "Por favor completa los campos correctamente"
            _mensaje.value = msg
            Log.w("VMUsuario", "Modelo de usuario no válido")
            onResultado(false, msg)
            return
        }

        // Normaliza nombre (espacios, etc.)
        val usuarioNormalizado = usuarioActual.copy(
            nombre = normalizarNombreEntrada(usuarioActual.nombre)
        )

        _cargando.value = true
        _mensaje.value = null
        Log.d("VMUsuario", "Llamando a repositorio.registrarUsuario()")

        //el repositorio  envía el correo de verificación
        repositorio.registrarUsuario(usuarioNormalizado) { exito, error ->
            _cargando.value = false
            if (exito) {
                // NO volvemos a enviar el correo acá.
                _mensaje.value = "✅ Registro exitoso. Revisa tu correo para verificar tu cuenta."
                Log.d("VMUsuario", "Registro OK y verificación enviada por el repositorio")
                onResultado(true, null)
            } else {
                val msg = error ?: "Error desconocido"
                _mensaje.value = msg
                Log.e("VMUsuario", "Fallo en registro: $msg")
                onResultado(false, msg)
            }
        }
    }


    fun iniciarSesion(onExito: () -> Unit, onError: (String) -> Unit) {
        val usuarioActual = _usuario.value
        if (!usuarioActual.esValidoParaLogin()) {
            onError("Correo o contraseña inválidos")
            return
        }

        _cargando.value = true
        repositorio.iniciarSesion(usuarioActual) { exito, mensaje ->
            _cargando.value = false
            if (exito) onExito() else onError(mensaje ?: "Error desconocido")
        }
    }
    fun recuperarContrasena( onResultado: (Boolean, String?) -> Unit) {
        val usuarioActual = _usuario.value
        if (!usuarioActual.esValidoParaRecuperar()) {
            _mensaje.value = "Por favor completa los campos correctamente"
            onResultado(false, _mensaje.value)
            return
        }
        _cargando.value = true
        repositorio.recuperarContrasena(usuarioActual) { exito, mensaje ->
            _cargando.value = false
            if (exito) {
                _mensaje.value = "✅ Se ha enviado un correo para restablecer tu contraseña."
                onResultado(true, _mensaje.value)
            } else {
                _mensaje.value = mensaje ?: "Error desconocido"
                 onResultado(false, _mensaje.value)
            }
        }

    }
    fun limpiarMensaje() {
        _mensaje.value = null
    }
    fun cargarEstadoAlerta(uid: String) {
        repositorio.obtenerEstadoAlertaContenido(uid) { estado ->
            estado?.let { _alertaContenido.value = it }
        }
    }

    fun actualizarEstadoAlerta(uid: String, nuevoEstado: Boolean) {
        _alertaContenido.value = nuevoEstado
        repositorio.actualizarEstadoAlertaContenido(uid, nuevoEstado) { exito ->
            if (!exito) {
            }
        }
    }
    private fun validarNombrePadre(nombre: String): Boolean {
        val n = normalizarNombreEntrada(nombre)

        // Debe tener al menos dos palabras (nombre y apellido) con 2+ caracteres cada una
        val partes = n.split(" ")
        val tieneNombreApellido = partes.size >= 2 &&
                partes[0].length >= 2 &&
                partes[1].length >= 2

        // Mínimo de letras totales (sin contar espacios)
        val largoOk = n.replace(" ", "").length >= MIN_LETRAS_SIN_ESPACIOS

        return tieneNombreApellido && largoOk
    }

    private fun validarContrasenaSegura(contrasena: String): Boolean {
        // La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un dígito y un carácter especial.
        val regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$".toRegex()
        return contrasena.matches(regex)
    }
}

