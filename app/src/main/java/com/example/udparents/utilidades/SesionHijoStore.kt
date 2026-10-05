package com.example.udparents.utilidades

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.udparents.main.MainActivity

/** Datos locales mínimos que permiten reconocer el vínculo infantil tras reiniciar la app. */
data class SesionHijo(
    val codigoVinculacion: String,
    val uidHijo: String
)

object SesionHijoStore {
    private const val NOMBRE_PREFERENCIAS = "sesion_hijo"
    private const val CLAVE_ROL = "rol"
    private const val CLAVE_CODIGO = "codigo_vinculacion"
    private const val CLAVE_UID = "uid_hijo"
    private const val ROL_HIJO = "hijo"

    private fun preferencias(context: Context) =
        context.getSharedPreferences(NOMBRE_PREFERENCIAS, Context.MODE_PRIVATE)

    fun guardar(context: Context, codigoVinculacion: String, uidHijo: String): Boolean {
        if (codigoVinculacion.isBlank() || uidHijo.isBlank()) return false

        return preferencias(context).edit()
            .putString(CLAVE_ROL, ROL_HIJO)
            .putString(CLAVE_CODIGO, codigoVinculacion.trim())
            .putString(CLAVE_UID, uidHijo.trim())
            .commit()
    }

    fun obtener(context: Context): SesionHijo? {
        val preferencias = preferencias(context)
        if (preferencias.getString(CLAVE_ROL, null) != ROL_HIJO) return null

        val codigo = preferencias.getString(CLAVE_CODIGO, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val uid = preferencias.getString(CLAVE_UID, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return SesionHijo(codigo, uid)
    }

    fun limpiar(context: Context): Boolean =
        preferencias(context).edit().clear().commit()
}

/** Habilita o deshabilita únicamente la actividad que publica el icono Launcher. */
object VisibilidadLauncher {
    fun ocultar(context: Context): Boolean = establecerVisibilidad(context, visible = false)

    fun mostrar(context: Context): Boolean = establecerVisibilidad(context, visible = true)

    @Suppress("DEPRECATION")
    private fun establecerVisibilidad(context: Context, visible: Boolean): Boolean {
        return try {
            val componente = ComponentName(context, MainActivity::class.java)
            val estado = if (visible) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                componente,
                estado,
                PackageManager.DONT_KILL_APP
            )
            true
        } catch (_: RuntimeException) {
            false
        }
    }
}
