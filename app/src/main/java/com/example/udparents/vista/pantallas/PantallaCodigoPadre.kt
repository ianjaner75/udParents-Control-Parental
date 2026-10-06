package com.example.udparents.vista.pantallas

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.udparents.utilidades.CodigoQr
import com.example.udparents.viewmodel.VistaModeloVinculacion
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaCodigoPadre(
    onVolverAlMenuPrincipal: () -> Unit
) {
    val viewModel: VistaModeloVinculacion = viewModel()
    val codigoGenerado by viewModel.codigoGenerado.collectAsState()

    val usuario = FirebaseAuth.getInstance().currentUser
    val idPadre = usuario?.uid ?: ""

    // 🔗 QR generado LOCALMENTE con ZXing a partir del código de 6 dígitos.
    // La codificación (ZXing) corre en Dispatchers.Default para NO bloquear el
    // hilo de UI; se recalcula solo cuando cambia el código generado.
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(codigoGenerado) {
        val codigo = codigoGenerado
        qrBitmap = if (codigo == null) {
            null
        } else {
            withContext(Dispatchers.Default) { CodigoQr.generarBitmap(codigo) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Generar Código de Vinculación", fontSize = 22.sp, color = Color(0xFF003366))

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (idPadre.isNotEmpty()) {
                    viewModel.generarCodigo(idPadre)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366))
        ) {
            Text("Generar Código", color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (codigoGenerado != null) {
            val codigo = codigoGenerado.orEmpty()

            // 📱 Código QR centrado en la pantalla del padre.
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                val bitmapActual = qrBitmap
                if (bitmapActual != null) {
                    Image(
                        bitmap = bitmapActual.asImageBitmap(),
                        contentDescription = "Código QR de vinculación",
                        modifier = Modifier
                            .size(260.dp)
                            .padding(12.dp)
                    )
                } else {
                    Text(
                        text = "No se pudo generar la imagen QR.\nEscribe el código manualmente en el otro dispositivo.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🔢 Código numérico debajo del QR: respaldo si la cámara falla.
            Text(
                text = "Tu código: $codigo",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Escanea este QR desde el dispositivo del menor o escribe el código de 6 dígitos manualmente en la pantalla de vinculación.",
                fontSize = 13.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 🔙 Botón para volver al menú principal
        TextButton(onClick = { onVolverAlMenuPrincipal() }) {
            Text("Volver al menú principal", color = Color(0xFF003366))
        }
    }
}
