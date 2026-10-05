package com.example.udparents.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.udparents.modelo.CodigoVinculacion
import com.example.udparents.repositorio.RepositorioVinculacion
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import android.util.Log
import android.provider.Settings
import com.example.udparents.utilidades.RegistroUsoApps



class VistaModeloVinculacion(
    private val repositorio: RepositorioVinculacion = RepositorioVinculacion()
) : ViewModel() {

    private val _codigoGenerado = MutableStateFlow<String?>(null)
    val codigoGenerado: StateFlow<String?> get() = _codigoGenerado

    private val _dispositivosVinculados = MutableStateFlow<List<CodigoVinculacion>>(emptyList())
    val dispositivosVinculados: StateFlow<List<CodigoVinculacion>> get() = _dispositivosVinculados

    private val _codigoVinculacion = MutableStateFlow( CodigoVinculacion())
    val codigoVinculacion: StateFlow<CodigoVinculacion?> = _codigoVinculacion

    private companion object {

        const val MIN_LETRAS_SIN_ESPACIOS = 10
    }
    fun actualizarCodigo(codigo: String) {
        _codigoVinculacion.value = _codigoVinculacion.value.copy(codigo = codigo.trim())
    }
    fun actualizarNombreHijo(nombreHijo: String) {
        _codigoVinculacion.value = _codigoVinculacion.value.copy(
            nombreHijo = nombreHijo
        )
    }

    fun actualizarEdadHijo(edadHijo: Int) {
        _codigoVinculacion.value = _codigoVinculacion.value.copy(edadHijo = edadHijo)
    }
    fun actualizarSexoHijo(sexoHijo: String) {
        _codigoVinculacion.value = _codigoVinculacion.value.copy(sexoHijo = sexoHijo.trim())
    }
    fun actualizarTermsAceptados(aceptado: Boolean) {
        _codigoVinculacion.value = _codigoVinculacion.value.copy(
            termsAccepted = aceptado,
            termsVersion = "1.0",
            termsAcceptedAt = if (aceptado) System.currentTimeMillis() else null
        )
    }
    fun generarCodigo(idPadre: String) {
        viewModelScope.launch {
            var nuevoCodigo: String
            do {
                nuevoCodigo = Random.nextInt(100000, 999999).toString()
            } while (repositorio.existeCodigo(nuevoCodigo))

            val codigo = CodigoVinculacion(
                codigo = nuevoCodigo,
                idPadre = idPadre,
                timestampCreacion = System.currentTimeMillis(),
                vinculado = false
            )

            repositorio.guardarCodigo(codigo)
            _codigoGenerado.value = nuevoCodigo
        }
    }

    fun validarCodigoHijo(
        codigo: String,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (codigo.isBlank()) {
                onError("Por favor ingrese un código.")
                return@launch
            }

            val resultado = repositorio.verificarCodigoValido(codigo)
            if (resultado) {
                onExito()
            } else {
                onError("Código inválido o expirado.")
            }
        }
    }



    fun cargarDispositivosVinculados(idPadre: String) {
        viewModelScope.launch {
            val lista = repositorio.obtenerDispositivosVinculados(idPadre)
            _dispositivosVinculados.value = lista
        }
    }

    /**
     * 💡 Esta función se ha actualizado para obtener el UID del padre usando
     *  función del repositorio.
     * @param onExito Callback que ahora recibe el UID del padre como String.
     */
    fun vincularHijoConDatos(
        context: Context,
        onExito: (String) -> Unit, //  recibe el UID del padre
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val codVinAct = _codigoVinculacion.value

                if (!validarNombreHijo(codVinAct.nombreHijo)) {
                    onError("Escribe nombre y apellido (mín. $MIN_LETRAS_SIN_ESPACIOS letras en total).")
                    return@launch
                }
                if (!validarEdadHijo(codVinAct.edadHijo)) {
                    onError("La edad del hijo debe estar entre 1 y 17 años.")
                    return@launch
                }
                if (!validarSexoHijo(codVinAct.sexoHijo)) {
                    onError("El sexo del hijo debe ser 'M' o 'F' o (masculino/femenino)")
                    return@launch
                }
                if (!codVinAct.termsAccepted) {
                    onError("Debes aceptar los Términos y Condiciones para continuar.")
                    return@launch
                }

                if (codVinAct.dispositivoHijo.isBlank()) {
                    onError("No se ha autenticado el dispositivo del hijo.")
                    return@launch
                }

                // 💡 Se busca el objeto completo para obtener el UID del padre
                val codigoVinculacionEnBD = repositorio.obtenerCodigoPorID(codVinAct.codigo)
                if (codigoVinculacionEnBD == null) {
                    onError("Código no válido")
                    return@launch
                }

                if (!repositorio.verificarCodigoValido(codVinAct.codigo)) {
                    onError("Código expirado")
                    return@launch
                }

                if (repositorio.dispositivoYaVinculado(codVinAct.dispositivoHijo)) {
                    onError("Este dispositivo ya ha sido vinculado previamente.")
                    return@launch
                }
                val codVinNormalizado = codVinAct.copy(
                    nombreHijo = normalizarNombreEntrada(codVinAct.nombreHijo)
                )

                repositorio.vincularConDatos(codVinNormalizado) { exito ->
                    if (exito) {
                        viewModelScope.launch {
                            try {
                                RegistroUsoApps.registrarUsoAplicaciones(context)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            // 💡 Se llama a onExito con el UID del padre obtenido del objeto
                            codigoVinculacionEnBD.idPadre?.let {
                                onExito(it)
                            }
                        }
                    } else {
                        onError("No se pudo vincular el dispositivo.")
                    }
                }

            } catch (e: Exception) {
                onError("Error al procesar la vinculación: ${e.message}")
            }
        }
    }

    fun actualizarDispositivoHijo(uid: String?) {
        uid?.let {
            _codigoVinculacion.value = _codigoVinculacion.value.copy(dispositivoHijo = it)
        }
    }
   //HU 11
    /**
     * Actualiza la información de un perfil de hijo vinculado.
     * @param uidPadre El UID del padre.
     * @param dispositivo El objeto [CodigoVinculacion] con los datos actualizados.
     */
    fun actualizarVinculacion(uidPadre: String, dispositivo: CodigoVinculacion) {
        viewModelScope.launch {
            try {
                repositorio.actualizarVinculacion(uidPadre, dispositivo) { exito ->
                    if (exito) {
                        // Actualizar la lista localmente para reflejar los cambios en la UI.
                        val listaActualizada = _dispositivosVinculados.value.map {
                            if (it.dispositivoHijo == dispositivo.dispositivoHijo) dispositivo else it
                        }
                        _dispositivosVinculados.value = listaActualizada
                        Log.d("VistaModeloVinculacion", "Vinculación actualizada para: ${dispositivo.nombreHijo}")
                    } else {
                        Log.e("VistaModeloVinculacion", "Error al actualizar la vinculación")
                    }
                }
            } catch (e: Exception) {
                Log.e("VistaModeloVinculacion", "Error al actualizar vinculación: ${e.message}", e)
            }
        }
    }

    /**
     * Elimina un perfil de hijo vinculado.
     * @param uidPadre El UID del padre.
     * @param uidHijo El UID del hijo cuyo perfil se va a eliminar.
     */
    fun eliminarVinculacion(uidPadre: String, uidHijo: String) {
        viewModelScope.launch {
            try {
                repositorio.eliminarVinculacion(uidPadre, uidHijo) { exito ->
                    if (exito) {
                        // Eliminar de la lista localmente para reflejar los cambios en la UI.
                        _dispositivosVinculados.value = _dispositivosVinculados.value.filter { it.dispositivoHijo != uidHijo }
                        Log.d("VistaModeloVinculacion", "Vinculación eliminada para el hijo con UID: $uidHijo")
                    } else {
                        Log.e("VistaModeloVinculacion", "Error al eliminar la vinculación")
                    }
                }
            } catch (e: Exception) {
                Log.e("VistaModeloVinculacion", "Error al eliminar vinculación: ${e.message}", e)
            }
        }
    }
     fun validarNombreHijo(nombre: String): Boolean {
        val n = normalizarNombreEntrada(nombre)

        // Debe haber al menos "nombre" y "apellido"
        val partes = n.split(" ")
        val tieneNombreApellido = partes.size >= 2 &&
                partes[0].length >= 2 &&
                partes[1].length >= 2

         val largoOk = n.replace(" ", "").length >= MIN_LETRAS_SIN_ESPACIOS

        return tieneNombreApellido && largoOk
    }

     fun validarEdadHijo(edad: Int): Boolean {
        return edad in 1..17
    }
     fun validarSexoHijo(sexo: String): Boolean {
        val s = sexo.trim().lowercase()
        return s == "m" || s == "f" || s == "masculino" || s == "femenino"
    }
    /** se  normakliza el nombre: quita espacios a los extremos y  espacios internos en uno */
     fun normalizarNombreEntrada(nombre: String): String =
        nombre.trim().replace("\\s+".toRegex(), " ")
    fun validarPerfil(nombre: String, edad: Int, sexo: String): String? {
        if (!validarNombreHijo(nombre)) {
            return "Escribe nombre y apellido (mín. $MIN_LETRAS_SIN_ESPACIOS letras en total)."
        }
        if (!validarEdadHijo(edad)) {
            return "La edad del hijo debe estar entre 1 y 17 años."
        }
        if (!validarSexoHijo(sexo)) {
            return "El sexo debe ser 'M' o 'F' (o masculino/femenino)."
        }
        return null // todo OK
    }

}

