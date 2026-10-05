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


    fun actualizarCodigo(codigo: String) {
        _codigoVinculacion.value = _codigoVinculacion.value.copy(codigo = codigo.trim())
    }
    fun actualizarNombreHijo(nombreHijo: String) {
        _codigoVinculacion.value = _codigoVinculacion.value.copy(nombreHijo = nombreHijo.trim())
    }
    fun actualizarEdadHijo(edadHijo: Int) {
        _codigoVinculacion.value = _codigoVinculacion.value.copy(edadHijo = edadHijo)
    }
    fun actualizarSexoHijo(sexoHijo: String) {
        _codigoVinculacion.value = _codigoVinculacion.value.copy(sexoHijo = sexoHijo.trim())
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

    fun validarCodigoVinculacion(
        codigo: String,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val existe = repositorio.existeCodigo(codigo)
                if (existe) {
                    val idHijo = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    repositorio.marcarCodigoComoVinculado(codigo, idHijo) { actualizado ->
                        if (actualizado) {
                            onExito()
                        } else {
                            onError("No se pudo actualizar la vinculación.")
                        }
                    }
                } else {
                    onError("Código no válido")
                }
            } catch (e: Exception) {
                onError("Error al validar el código: ${e.message}")
            }
        }
    }

    fun cargarDispositivosVinculados(idPadre: String) {
        viewModelScope.launch {
            val lista = repositorio.obtenerDispositivosVinculados(idPadre)
            _dispositivosVinculados.value = lista
        }
    }

    fun vincularHijoConDatos(
        context: Context,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val codVinAct = _codigoVinculacion.value
                if (codVinAct.dispositivoHijo.isBlank()) {
                    onError("No se ha autenticado el dispositivo del hijo.")
                    return@launch
                }

                val existe = repositorio.existeCodigo(codVinAct.codigo)
                if (!existe) {
                    onError("Código no válido")
                    return@launch
                }

                val esValido = repositorio.verificarCodigoValido(codVinAct.codigo)
                if (!esValido) {
                    onError("Código expirado")
                    return@launch
                }

                val yaVinculado = repositorio.dispositivoYaVinculado(codVinAct.dispositivoHijo)
                if (yaVinculado) {
                    onError("Este dispositivo ya ha sido vinculado previamente.")
                    return@launch
                }

                repositorio.vincularConDatos(codVinAct) { exito ->
                    if (exito) {
                        viewModelScope.launch {
                            try {
                                RegistroUsoApps.registrarUsoAplicaciones(context)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            onExito()
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

    fun solicitarDesinstalacion(
        codigo: String,
        idPadre: String,
        onResultado: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val solicitado = repositorio.solicitarDesinstalacion(codigo, idPadre)
                if (solicitado) {
                    _dispositivosVinculados.value = _dispositivosVinculados.value.map { dispositivo ->
                        if (dispositivo.codigo == codigo) {
                            dispositivo.copy(desinstalacionSolicitada = true)
                        } else {
                            dispositivo
                        }
                    }
                    onResultado(true, null)
                } else {
                    onResultado(false, "No se encontró un dispositivo vinculado a esta cuenta.")
                }
            } catch (e: Exception) {
                onResultado(false, "No se pudo enviar la orden: ${e.localizedMessage ?: "error de conexión"}")
            }
        }
    }

    fun actualizarDispositivoHijo(uid: String?) {
        uid?.let {
            _codigoVinculacion.value = _codigoVinculacion.value.copy(dispositivoHijo = it)
        }
    }


}
