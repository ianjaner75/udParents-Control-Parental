package com.example.udparents.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.udparents.navegacion.NavegacionApp
import com.example.udparents.servicio.MonitorDesvinculacion
import com.example.udparents.tema.UdParentsTheme
import com.example.udparents.utilidades.SesionHijoStore
import com.example.udparents.utilidades.VisibilidadLauncher

/** Actividad principal: los dispositivos ya vinculados como Hijo no vuelven al flujo de bienvenida. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SesionHijoStore.obtener(this) != null) {
            VisibilidadLauncher.ocultar(this)
            MonitorDesvinculacion.iniciar(this)
            finish()
            return
        }

        // Recuperación defensiva si el estado local fue eliminado, pero el componente seguía oculto.
        VisibilidadLauncher.mostrar(this)
        setContent {
            UdParentsTheme {
                NavegacionApp()
            }
        }
    }
}
