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

    // ============================================================
    // 🕶️ Sesión local del hijo (Stealth Mode)
    // ============================================================
    private const val KEY_UID_HIJO = "uid_hijo"
    private const val KEY_SESION_HIJO_ACTIVA = "sesion_hijo_activa"

    /**
     * Persiste la sesión del hijo en este dispositivo una vez que la
     * vinculación fue exitosa.
     * @param context El contexto de la aplicación.
     * @param uidHijo El UID (anónimo) del dispositivo del hijo.
     */
    fun guardarSesionHijo(context: Context, uidHijo: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString(KEY_UID_HIJO, uidHijo)
            putBoolean(KEY_SESION_HIJO_ACTIVA, true)
            apply()
        }
    }

    /**
     * Indica si existe una sesión de hijo activa (dispositivo vinculado).
     * @param context El contexto de la aplicación.
     */
    fun existeSesionHijo(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SESION_HIJO_ACTIVA, false)
    }

    /**
     * Obtiene el UID del hijo guardado localmente.
     * @param context El contexto de la aplicación.
     */
    fun obtenerUidHijo(context: Context): String? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UID_HIJO, null)
    }

    /**
     * Limpia por completo la sesión local del hijo (desvinculación local):
     * borra el UID del hijo, la bandera de sesión y el UID del padre.
     * @param context El contexto de la aplicación.
     */
    fun limpiarSesionHijo(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            remove(KEY_UID_HIJO)
            remove(KEY_SESION_HIJO_ACTIVA)
            remove(KEY_UID_PADRE)
            apply()
        }
    }
}
