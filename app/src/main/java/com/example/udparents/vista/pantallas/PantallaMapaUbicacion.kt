package com.example.udparents.vista.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.udparents.modelo.UbicacionHijo
import com.example.udparents.repositorio.RepositorioUbicacion
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🗺️ Pantalla de Ubicación en Tiempo Real (solo PADRE).
 *
 * Renderiza un mapa de Google nativo (Maps Compose) con un marcador dinámico
 * que sigue en tiempo real las coordenadas del hijo, leídas desde Firestore
 * mediante un snapshot listener. Pantalla 100% independiente: no modifica
 * ninguna otra pantalla del proyecto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMapaUbicacion(
    uidHijo: String,
    nombreHijo: String,
    onVolver: () -> Unit
) {
    val repositorio = remember { RepositorioUbicacion() }
    var ubicacion by remember { mutableStateOf<UbicacionHijo?>(null) }
    var primeraPosicion by remember { mutableStateOf(true) }
    val cameraPositionState = rememberCameraPositionState()

    // Paleta consistente con el resto de la app del padre.
    val primaryDark = Color(0xFF1A237E)
    val primaryLight = Color(0xFF3F51B5)
    val onPrimaryColor = Color.White

    // 📡 Suscripción en tiempo real a las coordenadas del hijo en Firestore.
    DisposableEffect(uidHijo) {
        val registro = repositorio.escucharUbicacion(uidHijo) { nueva ->
            if (nueva != null) ubicacion = nueva
        }
        onDispose { registro.remove() }
    }

    // 🎥 La cámara sigue al marcador conforme llegan nuevas coordenadas.
    val posicionActual = ubicacion?.let { LatLng(it.latitud, it.longitud) }
    LaunchedEffect(posicionActual) {
        posicionActual?.let { destino ->
            if (primeraPosicion) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(destino, 17f))
                primeraPosicion = false
            } else {
                cameraPositionState.animate(CameraUpdateFactory.newLatLng(destino))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📍 Ubicación de $nombreHijo", color = onPrimaryColor, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = onPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryLight,
                    titleContentColor = onPrimaryColor
                )
            )
        },
        containerColor = primaryDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    compassEnabled = true,
                    mapToolbarEnabled = false
                )
            ) {
                val actual = ubicacion
                if (actual != null) {
                    val centro = LatLng(actual.latitud, actual.longitud)
                    // Halo de precisión (radio de exactitud del GPS).
                    if (actual.precision > 0f) {
                        Circle(
                            center = centro,
                            radius = actual.precision.toDouble(),
                            fillColor = Color(0x333F51B5),
                            strokeColor = Color(0xFF3F51B5),
                            strokeWidth = 2f
                        )
                    }
                    Marker(
                        state = MarkerState(position = centro),
                        title = nombreHijo,
                        snippet = "Última actualización: ${formatearHora(actual.timestamp)}"
                    )
                }
            }

            // Tarjeta de estado superpuesta al mapa.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF2FFFFFF))
            ) {
                val actual = ubicacion
                if (actual != null) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Ubicación",
                                tint = primaryLight
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Última actualización: ${formatearHora(actual.timestamp)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryDark
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Precisión: ±${actual.precision.toInt()} m",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Esperando la primera ubicación de $nombreHijo…",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "El dispositivo del hijo reporta sus coordenadas automáticamente.",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

/** Formatea un timestamp como hora local legible (HH:mm:ss). */
private fun formatearHora(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
