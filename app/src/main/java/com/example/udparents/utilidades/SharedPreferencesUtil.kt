package com.example.udparents.utilidades

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesUtil {

    private const val PREFS_NAME = "udparents_prefs"
    private const val KEY_UID_PADRE = "uid_padre"

    /**
     * Guarda el UID del padre en SharedPreferences.
     * @param context El contexto de la aplicación.
     * @param uid El UID del padre a guardar.
     */
    fun guardarUidPadre(context: Context, uid: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString(KEY_UID_PADRE, uid)
            apply()
        }
    }

    /**
     * Obtiene el UID del padre de SharedPreferences.
     * @param context El contexto de la aplicación.
     * @return El UID del padre o una cadena vacía si no se encuentra.
     */
    fun obtenerUidPadre(context: Context): String? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UID_PADRE, null)
    }

    /**
     * Borra el UID del padre, útil para desvincular.
     * @param context El contexto de la aplicación.
     */
    fun borrarUidPadre(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            remove(KEY_UID_PADRE)
            apply()
        }
    }
}
