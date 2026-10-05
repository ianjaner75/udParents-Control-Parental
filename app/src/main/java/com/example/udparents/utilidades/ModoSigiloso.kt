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
                // 🧹 PURGA VISUAL para Launchers con caché agresiva (Samsung One UI),
                // que mantienen un ícono fantasma en el cajón aunque el estado sea
                // DISABLED. Nota: ACTION_PACKAGE_CHANGED es un broadcast protegido
                // que las apps NO pueden enviar; se usa el equivalente funcional:
                // 1) Re-creación del componente: se re-habilita y se vuelve a
                //    deshabilitar de inmediato. Cada cambio hace que el sistema
                //    re-emita el evento de cambio del paquete, obligando al
                //    Launcher a re-sincronizar su lista y soltar el ícono huérfano.
                try {
                    val pm = context.packageManager
                    pm.setComponentEnabledSetting(
                        componente,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    pm.setComponentEnabledSetting(
                        componente,
                        estado,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Fallo en la re-creación del componente: ${e.message}")
                }
                // 2) Forzar el redibujado llevando al usuario a la pantalla de
                //    inicio: el cajón se reconstruye en primer plano y sin la app.
                try {
                    val home = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(home)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo enviar al Launcher para forzar el refresco: ${e.message}")
                }
                // ✅ Verificación final del estado tras la purga.
                val estadoFinal = context.packageManager.getComponentEnabledSetting(componente)
                if (estadoFinal == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    Log.i(TAG, "🕶️ Purga visual completada: alias en DISABLED (inicial: $estadoInicial, verificado: $estadoVerificado, final: $estadoFinal)")
                } else {
                    Log.e(TAG, "❌ El alias NO quedó en DISABLED tras la purga (esperado 2, leído: $estadoFinal)")
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
