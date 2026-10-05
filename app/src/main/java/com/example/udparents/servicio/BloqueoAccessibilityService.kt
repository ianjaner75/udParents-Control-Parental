package com.example.udparents.servicio

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.util.Log
import com.example.udparents.vista.pantallas.PantallaBloqueoComposeActivity

class BloqueoAccessibilityService : AccessibilityService() {

    private val TAG = "BloqueoAcc"
    private val ui = Handler(Looper.getMainLooper())

    // Paquetes a vigilar (Ajustes + instaladores de varios OEM)
    private val PKG_SETTINGS = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.huawei.systemmanager"
    )
    private val PKG_INSTALLERS = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.huawei.appmarket"
    )

    // Debounce para no entrar en loops
    private var lastActionMs = 0L
    private val ACTION_COOLDOWN = 600L

    override fun onServiceConnected() {
        Log.d(TAG, "✅ Accesibilidad conectada")
        serviceInfo = serviceInfo.apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
        }
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: "?"

        // Solo actuamos en Settings/Installers
        if (!PKG_SETTINGS.contains(pkg) && !PKG_INSTALLERS.contains(pkg)) return

        val now = System.currentTimeMillis()
        if (now - lastActionMs < ACTION_COOLDOWN) return
        lastActionMs = now

        val root = rootInActiveWindow ?: return
        Log.d(TAG, "📐 $pkg / $cls")

        // 1) Pantalla de DESACTIVAR ADMIN (ej. Samsung: SecDeviceAdminAdd)
        if (cls.contains("DeviceAdminAdd", ignoreCase = true) ||
            cls.contains("SecDeviceAdminAdd", ignoreCase = true) ||
            hasText(root, listOf("Administrador de dispositivo", "Device admin"))
        ) {
            // Intenta pulsar “Cancelar” o “No activar/Desactivar”
            if (clickByAnyText(root, listOf("Cancelar", "Cancel", "No activar", "Do not activate", "No desactivar"))) {
                Log.d(TAG, "↩️ Cancelamos pantalla de admin")
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            abrirBloqueo()
            return
        }

        // 2) Pantalla de INFO DE LA APP (botones “Desinstalar”, “Forzar detención”)
        if (PKG_SETTINGS.contains(pkg) &&
            (hasText(root, listOf("Desinstalar", "Uninstall")) ||
                    hasText(root, listOf("Forzar detención", "Force stop")))
        ) {
            // Empujamos atrás 1-2 veces para sacarlo de ahí
            performGlobalAction(GLOBAL_ACTION_BACK)
            ui.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 150)
            abrirBloqueo()
            return
        }

        // 3) DIÁLOGO DE CONFIRMACIÓN DEL INSTALADOR (“Desinstalar”/“Cancelar”)
        if (PKG_INSTALLERS.contains(pkg) &&
            (cls.contains("AlertDialog") || hasText(root, listOf("Desinstalar", "Uninstall", "Cancelar", "Cancel")))
        ) {
            // Clic en “Cancelar” si existe; si no, BACK
            if (clickByAnyText(root, listOf("Cancelar", "Cancel", "No", "No thanks"))) {
                Log.d(TAG, "🚫 Cancelamos diálogo de desinstalación")
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            abrirBloqueo()
            return
        }
    }

    // ---------- Helpers ----------
    private fun abrirBloqueo() {
        // Lanza tu pantalla de bloqueo (modo heads-up al frente)
        val i = Intent(this, PantallaBloqueoComposeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("motivoBloqueo", "Intento de desinstalar o manipular la app")
            putExtra("bloqueoPermiso", true)
        }
        startActivity(i)
    }

    private fun hasText(root: AccessibilityNodeInfo, texts: List<String>): Boolean {
        texts.forEach { t ->
            val nodes = root.findAccessibilityNodeInfosByText(t)
            if (!nodes.isNullOrEmpty()) return true
        }
        return false
    }

    private fun clickByAnyText(root: AccessibilityNodeInfo, texts: List<String>): Boolean {
        texts.forEach { t ->
            val nodes = root.findAccessibilityNodeInfosByText(t)
            nodes?.forEach { n ->
                if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                var p = n.parent
                // Subimos por el árbol hasta encontrar un clickable
                repeat(5) {
                    if (p == null) return@repeat
                    if (p!!.isClickable) {
                        if (p!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    }
                    p = p!!.parent
                }
            }
        }
        return false
    }
}
