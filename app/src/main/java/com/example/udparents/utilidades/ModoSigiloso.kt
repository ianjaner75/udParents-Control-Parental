package com.example.udparents.utilidades

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * 🕶️ Modo Sigiloso (Stealth Mode).
 *
 * Oculta o restaura el ícono de la app en el Launcher habilitando/deshabilitando
 * el componente que posee el intent-filter MAIN/LAUNCHER (MainActivity), mediante
 * [PackageManager.setComponentEnabledSetting]. El proceso de la app sigue vivo;
 * solo desaparece el ícono. Con DONT_KILL_APP la app no se mata al cambiar el estado.
 */
object ModoSigiloso {

    private const val TAG = "ModoSigiloso"

    /** Componente con el intent-filter MAIN/LAUNCHER declarado en el AndroidManifest. */
    private const val COMPONENTE_LAUNCHER = "com.example.udparents.main.MainActivity"

    /**
     * Oculta el ícono de la app del Launcher.
     * @param context El contexto de la aplicación.
     */
    fun ocultarIconoApp(context: Context) {
        cambiarEstadoComponente(context, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    /**
     * Restaura el ícono de la app en el Launcher.
     * @param context El contexto de la aplicación.
     */
    fun restaurarIconoApp(context: Context) {
        cambiarEstadoComponente(context, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    /**
     * Indica si el ícono de la app se encuentra actualmente oculto.
     * @param context El contexto de la aplicación.
     */
    fun iconoOculto(context: Context): Boolean {
        val componente = ComponentName(context.packageName, COMPONENTE_LAUNCHER)
        return context.packageManager.getComponentEnabledSetting(componente) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun cambiarEstadoComponente(context: Context, estado: Int) {
        try {
            val componente = ComponentName(context.packageName, COMPONENTE_LAUNCHER)
            val estadoActual = context.packageManager.getComponentEnabledSetting(componente)
            if (estadoActual == estado) {
                Log.d(TAG, "El ícono ya está en el estado deseado; no se hace nada.")
                return
            }
            context.packageManager.setComponentEnabledSetting(
                componente,
                estado,
                PackageManager.DONT_KILL_APP
            )
            if (estado == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                Log.i(TAG, "🕶️ Ícono de la app oculto del Launcher")
            } else {
                Log.i(TAG, "👁️ Ícono de la app restaurado en el Launcher")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cambiando la visibilidad del ícono: ${e.message}", e)
        }
    }
}
