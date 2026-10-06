package com.example.udparents.utilidades

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 🔗 Código QR de vinculación (ZXing) con esquema personalizado.
 *
 * - [generarBitmap] convierte el código de vinculación de 6 dígitos en un Bitmap
 *   de QR **local** (sin Play Services ni llamadas de red), listo para mostrarse
 *   en la pantalla del padre. El contenido del QR es un URI con esquema propio:
 *   `udparents://vincular?codigo=XXXXXX`, que la cámara del hijo puede reconocer
 *   y abrir directamente en la app sin necesidad de servidor web ni hosting.
 * - [extraerCodigo] obtiene el código de 6 dígitos del texto leído por el
 *   escáner del hijo, aceptando tanto el URI completo como el código plano
 *   (retrocompatibilidad con QR antiguos y entrada manual).
 */
object CodigoQr {

    private const val TAG = "CodigoQr"

    /** Longitud del código de vinculación generado por el padre. */
    const val LONGITUD_CODIGO = 6

    /** Tamaño por defecto del Bitmap generado (px). Suficiente para ~260 dp. */
    const val TAMANO_POR_DEFECTO_PX = 720

    /** URI del esquema personalizado usado en el código QR de vinculación. */
    private const val SCHEME = "udparents"
    private const val HOST = "vincular"
    private const val PARAM_CODIGO = "codigo"

    /** Prefijo completo del URI de vinculación. */
    private const val URI_PREFIX = "$SCHEME://$HOST?$PARAM_CODIGO="

    private val REGEX_CODIGO = Regex("\\d{$LONGITUD_CODIGO}")

    /**
     * Genera el Bitmap del código QR a partir del código de 6 dígitos.
     *
     * El contenido embebido en el QR es un URI con esquema personalizado:
     * `udparents://vincular?codigo=XXXXXX`. Esto permite que la cámara del
     * dispositivo del hijo reconozca el enlace y abra la app directamente.
     *
     * @param codigo Código de vinculación (solo dígitos).
     * @param tamanoPx Lado del Bitmap cuadrado en píxeles.
     * @return El Bitmap del QR o `null` si el código es inválido o ZXing falla
     *         (la UI muestra entonces el código numérico como respaldo).
     */
    fun generarBitmap(codigo: String, tamanoPx: Int = TAMANO_POR_DEFECTO_PX): Bitmap? {
        val soloDigitos = codigo.trim()
        if (soloDigitos.length != LONGITUD_CODIGO || !soloDigitos.all { it.isDigit() }) {
            Log.w(TAG, "Código inválido para generar el QR: '$soloDigitos'")
            return null
        }
        // 🔗 URI con esquema personalizado: el QR ya no es un número plano.
        val contenido = "$URI_PREFIX$soloDigitos"
        return try {
            val matriz = QRCodeWriter().encode(
                contenido,
                BarcodeFormat.QR_CODE,
                tamanoPx,
                tamanoPx,
                mapOf(
                    // H (~30 % de redundancia): el QR se lee aunque esté sucio,
                    // doblado o mal iluminado en la pantalla del padre.
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.CHARACTER_SET to "UTF-8"
                )
            )
            val ancho = matriz.width
            val alto = matriz.height
            val pixeles = IntArray(ancho * alto)
            for (y in 0 until alto) {
                val fila = y * ancho
                for (x in 0 until ancho) {
                    pixeles[fila + x] =
                        if (matriz.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo generar el Bitmap del QR: ${t.message}", t)
            null
        }
    }

    /**
     * Extrae el código de 6 dígitos de un texto escaneado.
     *
     * Acepta tres formatos:
     *  1. URI completo (`udparents://vincular?codigo=123456`) — formato nuevo.
     *  2. Código "pelado" (`123456`) — respaldo manual / QR antiguos.
     *  3. Cualquier cadena que contenga 6 dígitos consecutivos.
     *
     * @return El código de 6 dígitos o `null` si el texto no lo contiene.
     */
    fun extraerCodigo(textoEscaneado: String?): String? {
        val texto = textoEscaneado?.trim().orEmpty()
        if (texto.isEmpty()) return null

        // 1️⃣ Intentar parsear como URI del esquema personalizado.
        try {
            val uri = android.net.Uri.parse(texto)
            if (uri.scheme == SCHEME && uri.host == HOST) {
                val codigoParam = uri.getQueryParameter(PARAM_CODIGO)
                if (codigoParam != null
                    && codigoParam.length == LONGITUD_CODIGO
                    && codigoParam.all { it.isDigit() }
                ) {
                    return codigoParam
                }
            }
        } catch (_: Exception) { /* no es un URI válido, continuar */ }

        // 2️⃣ Respaldo: buscar 6 dígitos consecutivos en cualquier posición.
        return REGEX_CODIGO.find(texto)?.value
    }
}
