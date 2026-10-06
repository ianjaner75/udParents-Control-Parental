package com.example.udparents.utilidades

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 🔗 Código QR de vinculación (ZXing).
 *
 * - [generarBitmap] convierte el código de vinculación de 6 dígitos en un Bitmap
 *   de QR **local** (sin Play Services ni llamadas de red), listo para mostrarse
 *   en la pantalla del padre.
 * - [extraerCodigo] obtiene el código de 6 dígitos del texto leído por el
 *   escáner del hijo, de modo que la vinculación manual por texto siga siendo
 *   el respaldo cuando la cámara falle.
 *
 * El QR contiene EXACTAMENTE el código de 6 dígitos, por lo que también es
 * legible por cualquier lector de QR genérico.
 */
object CodigoQr {

    private const val TAG = "CodigoQr"

    /** Longitud del código de vinculación generado por el padre. */
    const val LONGITUD_CODIGO = 6

    /** Tamaño por defecto del Bitmap generado (px). Suficiente para ~260 dp. */
    const val TAMANO_POR_DEFECTO_PX = 720

    private val REGEX_CODIGO = Regex("\\d{$LONGITUD_CODIGO}")

    /**
     * Genera el Bitmap del código QR a partir del código de 6 dígitos.
     *
     * @param codigo Código de vinculación (solo dígitos).
     * @param tamanoPx Lado del Bitmap cuadrado en píxeles.
     * @return El Bitmap del QR o `null` si el código es inválido o ZXing falla
     *         (la UI muestra entonces el código numérico como respaldo).
     */
    fun generarBitmap(codigo: String, tamanoPx: Int = TAMANO_POR_DEFECTO_PX): Bitmap? {
        val contenido = codigo.trim()
        if (contenido.length != LONGITUD_CODIGO || !contenido.all { it.isDigit() }) {
            Log.w(TAG, "Código inválido para generar el QR: '$contenido'")
            return null
        }
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
     * Acepta tanto el código "pelado" (`123456`) como cualquier cadena que lo
     * contenga (p. ej. `udparents:vincular:123456`).
     *
     * @return El código de 6 dígitos o `null` si el texto no lo contiene.
     */
    fun extraerCodigo(textoEscaneado: String?): String? {
        val texto = textoEscaneado?.trim().orEmpty()
        if (texto.isEmpty()) return null
        return REGEX_CODIGO.find(texto)?.value
    }
}
