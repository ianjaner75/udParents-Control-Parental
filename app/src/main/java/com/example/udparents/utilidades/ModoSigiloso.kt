package com.example.udparents.utilidades

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

    /** Componente con el intent-filter MAIN/LAUNCHER declarado en el AndroidManifest.
     *  Se usa el activity-alias (y no la MainActivity base) para que el ícono se
     *  pueda ocultar/restaurar en caliente incluso en Launchers con caché agresiva
     *  (p. ej. Samsung One UI). */
    private const val COMPONENTE_LAUNCHER = "com.example.udparents.main.MainActivityAlias"

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
            val estadoInicial = context.packageManager.getComponentEnabledSetting(componente)
            Log.d(TAG, "Estado inicial del alias: $estadoInicial (estado objetivo: $estado)")
            // 🔄 El estado se aplica SIEMPRE (sin early-return aunque ya coincida),
            // de modo que el sistema re-procese el componente y los Launchers con
            // caché agresiva (Samsung One UI) reciban el evento de cambio.
            context.packageManager.setComponentEnabledSetting(
                componente,
                estado,
                PackageManager.DONT_KILL_APP
            )
            // ✅ Verificación inmediata: leer el estado recién aplicado para saber
            // con certeza si el sistema lo aceptó (visible en logcat).
            val estadoVerificado = context.packageManager.getComponentEnabledSetting(componente)
            if (estado == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                // 🔄 Forzar el refresco del Launcher: algunos (p. ej. Samsung One UI)
                // no aplican el estado DISABLED en caliente mientras la app sigue
                // activa en primer plano. Enviar al usuario a la pantalla de inicio
                // obliga al Launcher a redibujar la cuadrícula y eliminar el ícono
                // fantasma de inmediato.
                try {
                    val home = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(home)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo enviar al Launcher para forzar el refresco: ${e.message}")
                }
                if (estadoVerificado == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    Log.i(TAG, "🕶️ Ícono oculto: el sistema APLICÓ DISABLED al alias (verificado: $estadoVerificado)")
                } else {
                    Log.e(TAG, "❌ El sistema NO aplicó DISABLED al alias (esperado 2, leído: $estadoVerificado)")
                }
            } else {
                if (estadoVerificado == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    Log.i(TAG, "👁️ Ícono restaurado: el sistema APLICÓ ENABLED al alias (verificado: $estadoVerificado)")
                } else {
                    Log.e(TAG, "❌ El sistema NO aplicó ENABLED al alias (esperado 1, leído: $estadoVerificado)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cambiando la visibilidad del ícono: ${e.message}", e)
        }
    }
}
