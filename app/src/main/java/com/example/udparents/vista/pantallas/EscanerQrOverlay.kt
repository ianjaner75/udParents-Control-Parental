package com.example.udparents.vista.pantallas

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.udparents.utilidades.CodigoQr
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG_ESCANER = "EscanerQr"

/**
 * 📷 Escáner de Código QR de vinculación (CameraX + ML Kit Barcode Scanning).
 *
 * Overlay a pantalla completa que se dibuja SOBRE la pantalla del hijo:
 *  1. Se obtiene el [ProcessCameraProvider] fuera del hilo principal.
 *  2. Se vinculan los casos de uso `Preview` + `ImageAnalysis` al ciclo de vida.
 *  3. ML Kit analiza cada fotograma buscando un QR; al detectarlo se extrae el
 *     código de 6 dígitos con [CodigoQr.extraerCodigo], se libera la cámara y se
 *     notifica con [onCodigoDetectado] para rellenar el campo y vincular.
 *
 * Si la cámara no está disponible, el permiso falta o ML Kit falla, el overlay
 * muestra un error y ofrece el respaldo MANUAL ("escribir el código"): la
 * vinculación por texto nunca se pierde.
 *
 * Nota: el permiso `Manifest.permission.CAMERA` se solicita en la pantalla del
 * hijo ANTES de mostrar este composable (ActivityResultContracts.RequestPermission).
 */
@Composable
fun EscanerQrOverlay(
    onCodigoDetectado: (String) -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    val cicloDeVida = LocalLifecycleOwner.current
    val analizador = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    // 🚦 Garantiza que solo se procese la PRIMERA detección (evita disparos dobles).
    val yaDetectado = remember { AtomicBoolean(false) }
    var errorCamara by remember { mutableStateOf<String?>(null) }
    var proveedorCamara by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // 🔓 Verificación de último momento del permiso de cámara.
    LaunchedEffect(Unit) {
        val concedido = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!concedido) {
            errorCamara = "Se necesita el permiso de cámara para escanear el código QR."
        }
    }

    // 🔧 Proveedor de cámara (la llamada bloqueante `get()` va fuera del hilo principal).
    LaunchedEffect(Unit) {
        if (errorCamara != null) return@LaunchedEffect
        try {
            proveedorCamara = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(context).get()
            }
        } catch (t: Throwable) {
            Log.e(TAG_ESCANER, "CameraX no disponible: ${t.message}", t)
            errorCamara = "No se pudo iniciar la cámara en este dispositivo."
        }
    }

    // 🎥 Preview + análisis de fotogramas con ML Kit.
    LaunchedEffect(proveedorCamara) {
        val proveedor = proveedorCamara ?: return@LaunchedEffect
        try {
            val vistaPrevia = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val escaner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
            analisis.setAnalyzer(analizador) { imagen ->
                if (yaDetectado.get()) {
                    imagen.close()
                    return@setAnalyzer
                }
                procesarFotograma(escaner, imagen, yaDetectado) { texto ->
                    val codigo = CodigoQr.extraerCodigo(texto)
                    if (codigo != null && !yaDetectado.getAndSet(true)) {
                        Log.i(TAG_ESCANER, "🔗 QR detectado; código extraído: $codigo")
                        // 📴 Se suelta la cámara de inmediato antes de continuar.
                        runCatching { proveedor.unbindAll() }
                        onCodigoDetectado(codigo)
                    }
                }
            }
            proveedor.unbindAll()
            proveedor.bindToLifecycle(
                cicloDeVida,
                CameraSelector.DEFAULT_BACK_CAMERA,
                vistaPrevia,
                analisis
            )
            errorCamara = null
        } catch (t: Throwable) {
            Log.e(TAG_ESCANER, "No se pudo vincular la cámara: ${t.message}", t)
            errorCamara = "No se pudo abrir la cámara. Escribe el código de 6 dígitos manualmente."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 🧹 Liberación de recursos SIEMPRE (cambio de pantalla o cierre).
            try {
                proveedorCamara?.unbindAll()
            } catch (t: Throwable) {
                Log.w(TAG_ESCANER, "No se pudo desvincular la cámara: ${t.message}")
            }
            analizador.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val mensajeError = errorCamara
        if (mensajeError == null) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // 🔲 Marco guía para encuadrar el QR.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(260.dp)
                    .border(3.dp, Color(0xFFCDDC39), RoundedCornerShape(16.dp))
            )

            if (proveedorCamara == null) {
                CircularProgressIndicator(
                    color = Color(0xFFCDDC39),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Apunta la cámara al código QR de la pantalla del padre",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "¿La cámara no funciona? Pulsa ✕ y escribe el código de 6 dígitos.",
                    color = Color(0xFFBDBDBD),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📷", fontSize = 40.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = mensajeError,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onCancelar,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCDDC39))
                ) {
                    Text("Escribir el código manualmente", color = Color(0xFF1A237E))
                }
            }
        }

        IconButton(
            onClick = onCancelar,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cerrar escáner",
                tint = Color.White
            )
        }
    }
}

/**
 * 🔎 Analiza un fotograma con ML Kit y entrega el primer texto de QR detectado.
 * Cierra siempre el [ImageProxy] (en `onComplete`) para no bloquear la cámara.
 */
private fun procesarFotograma(
    escaner: BarcodeScanner,
    imagen: ImageProxy,
    yaDetectado: AtomicBoolean,
    onTextoDetectado: (String) -> Unit
) {
    val mediaImage = imagen.image
    if (mediaImage == null) {
        imagen.close()
        return
    }
    val entrada = InputImage.fromMediaImage(mediaImage, imagen.imageInfo.rotationDegrees)
    escaner.process(entrada)
        .addOnSuccessListener { codigos ->
            if (!yaDetectado.get()) {
                codigos.firstNotNullOfOrNull { it.rawValue }?.let(onTextoDetectado)
            }
        }
        .addOnFailureListener { e ->
            Log.w(TAG_ESCANER, "Fallo al analizar el fotograma: ${e.message}")
        }
        .addOnCompleteListener {
            imagen.close()
        }
}
