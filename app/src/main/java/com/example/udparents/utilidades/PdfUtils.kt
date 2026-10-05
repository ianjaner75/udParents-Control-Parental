package com.example.udparents.utilidades

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CompletableDeferred
import java.io.OutputStream
import android.util.Log

object PdfUtils {

    /**
     * Genera un PDF desde un Composable y lo abre en un visor de PDF.
     */
    suspend fun generarPdfDesdeComposable(
        context: Context,
        activity: ComponentActivity,
        fileName: String,
        content: @Composable () -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                101
            )
            return
        }
        val deferredUri = CompletableDeferred<Uri?>()
        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { content() }
        }

        val rootView = activity.findViewById<View>(android.R.id.content) as ViewGroup
        rootView.addView(composeView)

        // Inicializamos fuera del try para que sean accesibles en finally
        var document: PdfDocument? = null
        var out: OutputStream? = null

        composeView.post { // sE usa composeView.post para asegurar que la vista esté medida y dibujada
            try {
                composeView.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

                document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(
                    composeView.measuredWidth,
                    composeView.measuredHeight,
                    1
                ).create()
                val page = document!!.startPage(pageInfo) // Usar !! después de inicializar
                composeView.draw(page.canvas)
                document!!.finishPage(page) // Usar !! después de inicializar

                val safeName = if (fileName.endsWith(".pdf", true)) fileName else "$fileName.pdf"

                var uri: Uri? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    out = uri?.let { resolver.openOutputStream(it) }

                    if (out == null) throw IllegalStateException("No se pudo abrir OutputStream")

                    document!!.writeTo(out!!) // Usar !!
                    out!!.flush() // Usar !!

                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    uri?.let { resolver.update(it, values, null, null) } // Usar ?.let para Uri
                } else {
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloads.exists()) downloads.mkdirs()
                    val file = File(downloads, safeName)
                    out = FileOutputStream(file)
                    document!!.writeTo(out!!) // Usar !!
                    out!!.flush() // Usar !!
                    uri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".provider", // Corregido: .provider
                        file
                    )
                }
                deferredUri.complete(uri)
            } catch (t: Throwable) {
                Log.e("PdfUtils", "Error al generar el PDF: ${t.message}", t)
                deferredUri.complete(null)
            } finally {
                try {
                    rootView.removeView(composeView)
                    out?.close()
                    document?.close() // Cierra el documento si se inicializó
                } catch (_: Throwable) {}
            }
        }
        val pdfUri = deferredUri.await()
        pdfUri?.let {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "No se encontró un visor de PDF.", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(context, "Error al generar o abrir el PDF.", Toast.LENGTH_SHORT).show()
    }

    /**
     *  Genera PDF desde un Composable y lo guarda en "Descargas".
     * Devuelve el Uri del archivo o null si falló.
     */
    suspend fun generarPdfDesdeComposableAStorage(
        context: Context,
        activity: ComponentActivity,
        fileName: String,
        content: @Composable () -> Unit
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                101
            )
            return null
        }
        val deferredUri = CompletableDeferred<Uri?>()
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { content() }
        }

        val rootView = activity.findViewById<View>(android.R.id.content) as ViewGroup
        rootView.addView(composeView)

        // Inicializamos fuera del try para que sean accesibles en finally
        var document: PdfDocument? = null
        var out: OutputStream? = null

        composeView.post { // Se usa composeView.post para asegurar que la vista esté medida y dibujada
            try {
                composeView.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

                document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(
                    composeView.measuredWidth,
                    composeView.measuredHeight,
                    1
                ).create()
                val page = document!!.startPage(pageInfo)
                composeView.draw(page.canvas)
                document!!.finishPage(page)

                val safeName = if (fileName.endsWith(".pdf", true)) fileName else "$fileName.pdf"

                var uri: Uri? = null
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        out = uri?.let { resolver.openOutputStream(it) }
                        if (out == null) throw IllegalStateException("No se pudo abrir OutputStream")

                        document!!.writeTo(out!!)
                        out!!.flush()

                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        uri?.let { resolver.update(it, values, null, null) } // Usar ?.let
                    } else {
                        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!downloads.exists()) downloads.mkdirs()
                        val file = File(downloads, safeName)
                        out = FileOutputStream(file)
                        document!!.writeTo(out!!)
                        out!!.flush()
                        uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider", // Corregido: .provider
                            file
                        )
                    }
                    deferredUri.complete(uri)
                } catch (t: Throwable) {
                    Log.e("PdfUtils", "Error al generar y guardar PDF: ${t.message}", t)
                    deferredUri.complete(null)
                } finally {
                    try { out?.close() } catch (_: Throwable) {}
                    document?.close() // Cierra el documento si se inicializó
                }
            } catch (t: Throwable) {
                Log.e("PdfUtils", "Error general al generar el PDF: ${t.message}", t)
                deferredUri.complete(null)
            } finally {
                rootView.removeView(composeView)
            }
        }
        return deferredUri.await()
    }

    /**
     *  Compartir un PDF mediante ACTION_SEND.
     */
    fun compartirPdf(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir PDF"))
    }
}