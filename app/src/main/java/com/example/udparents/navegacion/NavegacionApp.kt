package com.example.udparents.navegacion

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.udparents.viewmodel.VistaModeloUsuario
import com.example.udparents.vista.pantallas.*

/**
 * Rutas nombradas para facilitar la navegación.
 */
object Rutas {
    const val INICIO_SESION = "inicio_sesion"
    const val REGISTRO = "registro"
    const val RECUPERAR = "recuperar"
    const val PRINCIPAL = "principal"
    const val CODIGO_PADRE = "codigo_padre"
    const val VINCULACION_HIJO = "vinculacion_hijo"
    const val BIENVENIDA = "bienvenida"
    const val DISPOSITIVOS_VINCULADOS = "dispositivos_vinculados"
    const val REPORTE_APPS = "reporte_apps"
    const val CONTROL_APPS = "control_apps"
    const val PROGRAMAR_RESTRICCIONES = "programar_restricciones"
    const val DETALLES_RESTRICCIONES = "programar_restricciones_detalles/{uidHijo}/{nombreHijo}"
    const val DETALLES_CONTROL = "control_apps/{uidHijo}"
    const val RESUMEN_TIEMPO = "resumen_tiempo"
    const val INFORME_APPS_MAS_USADAS = "informe_apps_mas_usadas"
    const val REGISTRO_BLOQUEOS = "registro_bloqueos"

}

// Función de extensión para encontrar la actividad de forma segura
fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Controlador de navegación que define las pantallas disponibles y su flujo.
 */
@Composable
fun NavegacionApp() {
    val navController = rememberNavController()
    val activity = LocalContext.current.findActivity()

    if (activity == null) {
        return
    }

    NavHost(
        navController = navController,
        startDestination = Rutas.BIENVENIDA
    ) {
        composable(Rutas.INICIO_SESION) {
            PantallaInicioSesion(
                onIniciarSesionExitoso = {
                    navController.navigate(Rutas.PRINCIPAL) {
                        popUpTo(Rutas.INICIO_SESION) { inclusive = true }
                    }
                },
                onIrARegistro = {
                    navController.navigate(Rutas.REGISTRO)
                },
                onOlvidoContrasena = {
                    navController.navigate(Rutas.RECUPERAR)
                }
            )
        }
        composable(Rutas.REGISTRO) {
            PantallaRegistro(
                viewModel = viewModel(),
                onRegistroExitoso = {
                    navController.navigate(Rutas.INICIO_SESION) {
                        popUpTo(Rutas.REGISTRO) { inclusive = true }
                    }
                },
                onIrAInicioSesion = {
                    navController.popBackStack(Rutas.INICIO_SESION, inclusive = false)
                }
            )
        }
        composable(Rutas.RECUPERAR) {
            PantallaRecuperarContrasena(
                viewModel = viewModel(),
                onRecuperacionEnviada = {
                    navController.popBackStack(Rutas.INICIO_SESION, false)
                },
                onVolver = {
                    navController.popBackStack()
                }
            )
        }
        composable(Rutas.PRINCIPAL) {
            PantallaPrincipal(
                onCerrarSesion = {
                    navController.navigate(Rutas.INICIO_SESION) {
                        popUpTo(0)
                        launchSingleTop = true
                    }
                },
                onIrAVinculacionPadre = {
                    navController.navigate(Rutas.CODIGO_PADRE)
                },
                onIrADispositivosVinculados = {
                    navController.navigate(Rutas.DISPOSITIVOS_VINCULADOS)
                },
                onIrAReporteApps = { hijos ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("hijosVinculados", hijos)
                    navController.navigate(Rutas.REPORTE_APPS)
                },
                onIrAControlApps = { hijos ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("hijosVinculados", hijos)
                    navController.navigate(Rutas.CONTROL_APPS)
                },
                onIrAProgramarRestricciones = { hijos ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("hijosVinculados", hijos)
                    navController.navigate(Rutas.PROGRAMAR_RESTRICCIONES)
                },
                onIrAResumenTiempoPantalla = { hijos ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("hijosVinculados", hijos)
                    navController.navigate(Rutas.RESUMEN_TIEMPO)
                },
                onIrAInformeAppsMasUsadas = { hijos ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("hijosVinculados", hijos)
                    navController.navigate(Rutas.INFORME_APPS_MAS_USADAS)
                },
                onIrARegistroBloqueos = {
                    navController.navigate(Rutas.REGISTRO_BLOQUEOS)
                }
            )
        }
        composable(Rutas.CODIGO_PADRE) {
            PantallaCodigoPadre(
                onVolverAlMenuPrincipal = {
                    navController.navigate(Rutas.PRINCIPAL) {
                        popUpTo(Rutas.PRINCIPAL) { inclusive = true }
                    }
                }
            )
        }
        composable(Rutas.DISPOSITIVOS_VINCULADOS) {
            PantallaDispositivosVinculados(
                onVolverAlMenuPadre = {
                    navController.navigate(Rutas.PRINCIPAL) {
                        popUpTo(Rutas.PRINCIPAL) { inclusive = true }
                    }
                }
            )
        }
        composable(Rutas.VINCULACION_HIJO) {
            PantallaVinculacionHijo(
                vistaModelo = viewModel(),
                onVolverAlPadre = {
                    navController.navigate(Rutas.BIENVENIDA) {
                        popUpTo(Rutas.VINCULACION_HIJO) { inclusive = true }
                    }
                }
            )
        }
        composable(Rutas.BIENVENIDA) {
            PantallaBienvenida(
                onPadreSeleccionado = {
                    navController.navigate(Rutas.INICIO_SESION)
                },
                onHijoSeleccionado = {
                    navController.navigate(Rutas.VINCULACION_HIJO)
                }
            )
        }
        composable(Rutas.REPORTE_APPS) {
            val hijos = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<List<Pair<String, String>>>("hijosVinculados")
                ?: emptyList()

            PantallaReporteApps(
                listaHijos = hijos,
                onVolver = {
                    navController.popBackStack()
                }
            )
        }
        composable(Rutas.CONTROL_APPS) {
            val hijos = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<List<Pair<String, String>>>("hijosVinculados")
                ?: emptyList()

            PantallaSeleccionHijo(
                titulo = "Control de Aplicaciones",
                listaHijos = hijos,
                onHijoSeleccionado = { uidHijo, _ ->
                    navController.navigate(Rutas.DETALLES_CONTROL.replace("{uidHijo}", uidHijo))
                },
                onVolver = {
                    navController.popBackStack()
                }
            )
        }
        composable(Rutas.PROGRAMAR_RESTRICCIONES) {
            val hijos = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<List<Pair<String, String>>>("hijosVinculados")
                ?: emptyList()

            PantallaSeleccionHijo(
                titulo = "Programar Restricciones",
                listaHijos = hijos,
                onHijoSeleccionado = { uidHijo, nombreHijo ->
                    navController.navigate(Rutas.DETALLES_RESTRICCIONES.replace("{uidHijo}", uidHijo).replace("{nombreHijo}", nombreHijo))
                },
                onVolver = {
                    navController.popBackStack()
                }
            )
        }
        composable(Rutas.RESUMEN_TIEMPO) {
            PantallaResumenTiempoPantalla(
                onVolverAlMenuPadre = { navController.popBackStack() }
            )
        }
        composable(Rutas.INFORME_APPS_MAS_USADAS) {
            PantallaInformeAppsMasUsadas(
                onVolverAlMenuPadre = { navController.popBackStack() },
                activity = activity
            )
        }

        composable(Rutas.REGISTRO_BLOQUEOS) {
            PantallaRegistroBloqueos(
                onVolverAlMenuPadre = { navController.popBackStack() },
                activity = activity,
                navController = navController
            )
        }

        composable(
            route = Rutas.DETALLES_CONTROL,
            arguments = listOf(navArgument("uidHijo") { type = NavType.StringType })
        ) { backStackEntry ->
            val uidHijo = backStackEntry.arguments?.getString("uidHijo") ?: ""
            PantallaControlApps(uidHijo = uidHijo)
        }
        composable(
            route = Rutas.DETALLES_RESTRICCIONES,
            arguments = listOf(
                navArgument("uidHijo") { type = NavType.StringType },
                navArgument("nombreHijo") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uidHijo = backStackEntry.arguments?.getString("uidHijo")
            val nombreHijo = backStackEntry.arguments?.getString("nombreHijo")
            if (uidHijo != null && nombreHijo != null) {
                PantallaProgramarRestricciones(
                    uidHijo = uidHijo,
                    nombreHijo = nombreHijo,
                    onVolver = { navController.popBackStack() }
                )
            }
        }
    }
}
