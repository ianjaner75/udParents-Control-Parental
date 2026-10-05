package com.example.udparents.servicio

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.udparents.R
import com.example.udparents.modelo.BloqueoRegistro
import com.example.udparents.repositorio.RepositorioApps
import com.example.udparents.repositorio.RepositorioBloqueos
import com.example.udparents.utilidades.RegistroUsoApps
import com.example.udparents.utilidades.SharedPreferencesUtil
import com.example.udparents.vista.pantallas.PantallaBloqueoComposeActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import android.app.usage.UsageStats
import android.media.RingtoneManager
import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.os.Process
import android.provider.Settings
import java.util.Calendar
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.IntentFilter
import android.os.PowerManager
import android.app.KeyguardManager
import com.example.udparents.seguridad.AdminReceiver


class RegistroUsoService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var tareaMonitoreo: Job? = null
    private var tareaRegistroUso: Job? = null
    private val intervaloChequeoAppEnUso = 1000L // 1 segundo para el chequeo de bloqueo (ajustado para mayor reactividad)
    private val intervaloRegistroUso = 30 * 1000L // 30 segundos para el barrido general en Firebase
    private var paqueteBloqueadoActual: String? = null
    // notificadoTiempoRestante y notificadoUltimosSegundos ya no se utilizan.
    private var lastCheckTime: Long = 0L
    // UMBRAL_TIEMPO_RESTANTE_MS ya no se utiliza.
    private var notified60s = false
    private var notified30s = false
    private var notified10s = false
    private var mostrandoBloqueoPermisos = false
    // Estado de permisos para avisos
    private var ultimoEstadoPermisos: Boolean? = null // null=desconocido, true=OK, false=faltan
    private var ultimoAvisoPermisosMs: Long = 0L
    private val COOLDOWN_AVISO_MS = 20_000L // 20s anticancel spam
    private var huboFalloPermisos = false   // ⬅️ NUEVO: ya hubo fallo desde que arrancó
    // NUEVO: estado de pantalla/bloqueo
    private lateinit var pm: PowerManager
    private lateinit var km: KeyguardManager
    private var isInteractive = true
    private var isUnlocked = true
    private var ultimoBloqueoMs: Long = 0L
    private val COOLDOWN_BLOQUEO_MS = 1500L  // 1.5s para no spamear la Activity (ajustado)

    // NUEVO: estado para detectar cambio de app en foreground
    private var paqueteAnteriorEnUso: String? = null

    // Recordatorios periódicos de tiempo restante (además de los umbrales 60/30/10)
    private var ultimoAvisoTiempoRestanteMs = 0L
    private val COOLDOWN_RECORDATORIO_MS = 5_000L  // cada 5 s como máximo (ajústalo)
    private val COTA_RECORDATORIO_MS = 60_000L      // solo recordar cuando queda ≤ 60 s (ajústalo)


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Inicializa PowerManager y KeyguardManager
        pm = getSystemService(PowerManager::class.java)
        km = getSystemService(KeyguardManager::class.java)
        isInteractive = pm.isInteractive
        isUnlocked = !km.isKeyguardLocked
        // Receiver para pantalla/bloqueo
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        registerReceiver(screenReceiver, filter)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isInteractive = true
                    isUnlocked = !km.isKeyguardLocked
                    // lastCheckTime se actualiza en el primer tick válido de verificarAppEnUso
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isInteractive = false
                    lastCheckTime = 0L
                    paqueteAnteriorEnUso = null // Reinicia al apagarse
                }
                Intent.ACTION_USER_PRESENT, Intent.ACTION_USER_UNLOCKED -> {
                    isUnlocked = true
                    isInteractive = pm.isInteractive
                    // lastCheckTime se actualiza en el primer tick válido
                }
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RegistroUsoService", "✅ Servicio iniciado correctamente")
        Log.d("RegistroUsoService", "🧬 Servicio sigue corriendo tras cierre")
        mostrarNotificacion()
        // 🔒 Asegurar que el admin de dispositivo esté activo (impide desinstalación)
        if (!isDeviceAdminActive(applicationContext)) {
            solicitarActivacionDeviceAdmin(applicationContext)
        }

        tareaMonitoreo = scope.launch {
            while (isActive) {
                try {
                    // 1) Verifica permisos críticos
                    val permisosOk = verificarPermisosEsenciales()
                    // 2) Solo si están OK, continua tu lógica normal
                    if (permisosOk) {
                        verificarAppEnUso()
                    }
                } catch (e: Exception) {
                    Log.e("RegistroUsoService", "❌ Error monitoreando apps: ${e.message}", e)
                }
                delay(intervaloChequeoAppEnUso)
            }
        }

        // Tarea para registrar el uso total y sincronizar con Firebase
        // Desactivada para evitar doble conteo, como lo sugeriste
        // tareaRegistroUso = scope.launch {
        //     while (isActive) {
        //         try {
        //             Log.d("RegistroUsoService", "📝 Iniciando registro periódico de uso de apps en Firebase (barrido general)...")
        //             RegistroUsoApps.registrarUsoAplicaciones(applicationContext)
        //             Log.d("RegistroUsoService", "✅ Registro periódico de uso de apps finalizado.")
        //         } catch (e: Exception) {
        //             Log.e("RegistroUsoService", "❌ Error registrando uso de apps: ${e.message}", e)
        //         }
        //         delay(intervaloRegistroUso)
        //     }
        // }

        return START_STICKY
    }

    private suspend fun verificarAppEnUso() {
        // GATE: NO sumar si pantalla apagada o dispositivo bloqueado
        if (!isInteractive || !isUnlocked) {
            lastCheckTime = 0L
            return
        }

        val context = applicationContext
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ahora = System.currentTimeMillis()
        val hace10Segundos = ahora - 10_000

        val stats: List<UsageStats> = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, hace10Segundos, ahora
            )
        } catch (e: SecurityException) {
            lastCheckTime = 0L
            paqueteAnteriorEnUso = null
            Log.w("RegistroUsoService", "Permiso de uso revocado en caliente", e)
            return
        }

        if (stats.isNullOrEmpty()) {
            lastCheckTime = 0L
            paqueteAnteriorEnUso = null
            Log.d("RegistroUsoService", "No se encontraron estadísticas de uso recientes.")
            return
        }

        val appEnUso = stats.maxByOrNull { it.lastTimeUsed } ?: run {
            Log.d("RegistroUsoService", "No se pudo determinar la aplicación en uso más reciente.")
            return
        }
        val paqueteActual = appEnUso.packageName

        // Filtro básico: evita contarte a ti mismo y al launcher
        val home = homePackage(context)
        if (paqueteActual == packageName || paqueteActual == home) {
            // no contamos tiempo en el lanzador o en nuestra app
            lastCheckTime = 0L
            paqueteAnteriorEnUso = paqueteActual
            return
        }

        val uidHijo = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w("RegistroUsoService", "UID del hijo no disponible, no se puede verificar bloqueo.")
            return
        }
        val repositorio = RepositorioApps()
        val nombreAppActual = obtenerNombreApp(context, paqueteActual)

        // Cambio de app en foreground: inicializa delta y sal del tick
        val now = System.currentTimeMillis()
        val cambioDeApp = (paqueteAnteriorEnUso != paqueteActual)
        if (cambioDeApp) {
            paqueteAnteriorEnUso = paqueteActual
            notified60s = false
            notified30s = false
            notified10s = false
            ultimoAvisoTiempoRestanteMs = 0L
            lastCheckTime = now  // arrancamos medición para la nueva app
        }

// Calcula delta: si cambió de app, no sumes tiempo en este tick
        val timeElapsed = if (cambioDeApp || lastCheckTime == 0L) 0L else now - lastCheckTime
        lastCheckTime = now


        if (timeElapsed > 0 && timeElapsed <= 60_000) {
            // Incrementar el uso de la aplicación activa con el tiempo transcurrido real
            repositorio.incrementarUsoAplicacion(uidHijo, paqueteActual, nombreAppActual, timeElapsed)
        }

        val tiempoUsoActual = repositorio.obtenerUsoAppDelDia(uidHijo, paqueteActual)

        var debeBloquear = false
        var motivoBloqueo = ""
        var tituloNotificacion = ""
        var mensajeNotificacion = ""

        // 1. Verificar bloqueo manual
        val bloqueadaManual = repositorio.estaAppBloqueada(uidHijo, paqueteActual)
        if (bloqueadaManual) {
            debeBloquear = true
            motivoBloqueo = "Bloqueo manual"
            tituloNotificacion = "Alerta: Aplicación bloqueada"
            mensajeNotificacion = "Tu hijo ha intentado abrir la aplicación '$nombreAppActual' que has bloqueado manualmente."
        }

        // 2. Verificar bloqueo por límite de tiempo
        if (!debeBloquear) {
            val tiempoLimite = repositorio.obtenerLimiteApp(uidHijo, paqueteActual)
            val tiempoRestante = tiempoLimite - tiempoUsoActual

            if (tiempoLimite > 0L) {
                when {
                    tiempoRestante <= 60_000L && tiempoRestante > 30_000L && !notified60s -> {
                        mostrarNotificacionTiempoRestante(nombreAppActual, tiempoRestante)
                        notified60s = true
                    }
                    tiempoRestante <= 30_000L && tiempoRestante > 10_000L && !notified30s -> {
                        mostrarNotificacionTiempoRestante(nombreAppActual, tiempoRestante)
                        notified30s = true
                    }
                    tiempoRestante <= 10_000L && tiempoRestante > 0L && !notified10s -> {
                        mostrarNotificacionUltimosSegundos(nombreAppActual)
                        notified10s = true
                    }
                    tiempoRestante > 60_000L -> {
                        notified60s = false
                        notified30s = false
                        notified10s = false
                    }
                }
            }

            // --- Recordatorio periódico mientras esté por debajo de la cota ---
            if (tiempoLimite > 0L && tiempoRestante in 1..COTA_RECORDATORIO_MS) {
                val ahoraMs = System.currentTimeMillis()
                val fueraDeCooldown = (ahoraMs - ultimoAvisoTiempoRestanteMs) >= COOLDOWN_RECORDATORIO_MS
                if (fueraDeCooldown) {
                    mostrarNotificacionTiempoRestante(nombreAppActual, tiempoRestante)
                    ultimoAvisoTiempoRestanteMs = ahoraMs
                }
            }
            // Si el tiempo vuelve a subir por encima de la cota, limpiamos el “reloj” de recordatorios
            if (tiempoRestante > COTA_RECORDATORIO_MS) {
                ultimoAvisoTiempoRestanteMs = 0L
            }

            val bloqueadaPorLimite = tiempoLimite > 0L && tiempoUsoActual >= tiempoLimite
            if (bloqueadaPorLimite) {
                debeBloquear = true
                motivoBloqueo = "Límite de tiempo excedido"
                tituloNotificacion = "Alerta: Límite de tiempo de pantalla"
                mensajeNotificacion = "Tu hijo ha excedido el límite de tiempo para la aplicación '$nombreAppActual'."
            }
        }

        // 3. Verificar bloqueo por horario
        if (!debeBloquear) {
            val restricciones = repositorio.obtenerRestriccionesHorario(uidHijo)
            val calendar = Calendar.getInstance()
            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val currentTimeMillisOfDay = (calendar.get(Calendar.HOUR_OF_DAY) * 60 * 60 * 1000) +
                    (calendar.get(Calendar.MINUTE) * 60 * 1000) +
                    (calendar.get(Calendar.SECOND) * 1000)

            for (restriccion in restricciones) {
                if (restriccion.isEnabled &&
                    (restriccion.packageName == paqueteActual || restriccion.packageName == "ALL_APPS") &&
                    restriccion.daysOfWeek.contains(currentDayOfWeek)
                ) {
                    if (restriccion.startTimeMillis < restriccion.endTimeMillis) {
                        if (currentTimeMillisOfDay >= restriccion.startTimeMillis &&
                            currentTimeMillisOfDay < restriccion.endTimeMillis) {
                            debeBloquear = true
                            motivoBloqueo = "Restricción por horario"
                            tituloNotificacion = "Alerta: Horario de uso"
                            mensajeNotificacion = "Tu hijo ha intentado usar la aplicación '$nombreAppActual' fuera de su horario permitido."
                            break
                        }
                    } else {
                        if (currentTimeMillisOfDay >= restriccion.startTimeMillis ||
                            currentTimeMillisOfDay < restriccion.endTimeMillis) {
                            debeBloquear = true
                            motivoBloqueo = "Restricción por horario"
                            tituloNotificacion = "Alerta: Horario de uso"
                            mensajeNotificacion = "Tu hijo ha intentado usar la aplicación '$nombreAppActual' fuera de su horario permitido."
                            break
                        }
                    }
                }
            }
        }

        if (debeBloquear) {
            val ahora = System.currentTimeMillis()
            val fueraDeCooldown = (ahora - ultimoBloqueoMs) > COOLDOWN_BLOQUEO_MS

            // Lanza el bloqueo si es otra app o si ya pasó el cooldown
            if (paqueteActual != paqueteBloqueadoActual || fueraDeCooldown) {
                paqueteBloqueadoActual = paqueteActual
                ultimoBloqueoMs = ahora

                Log.d("RegistroUsoService", "🔒 Bloqueo forzado: $nombreAppActual ($paqueteActual) - Motivo: $motivoBloqueo")

                val uidPadre = SharedPreferencesUtil.obtenerUidPadre(applicationContext) ?: ""

                if (uidPadre.isNotBlank()) {
                    val sender = NotificacionSender()
                    scope.launch {
                        sender.enviarNotificacionAlPadre(
                            uidPadre = uidPadre,
                            titulo = tituloNotificacion,
                            mensaje = mensajeNotificacion
                        )
                    }

                    val repoBloqueos = RepositorioBloqueos()
                    val bloqueoRegistro = BloqueoRegistro(
                        uidHijo = uidHijo,
                        nombrePaquete = paqueteActual,
                        nombreApp = nombreAppActual,
                        razon = motivoBloqueo
                    )
                    scope.launch {
                        try {
                            repoBloqueos.registrarBloqueo(uidHijo, uidPadre, bloqueoRegistro)
                        } catch (_: Exception) { }
                    }
                }

                val intent = Intent(context, PantallaBloqueoComposeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("nombreApp", nombreAppActual)
                    putExtra("paqueteBloqueado", paqueteActual)
                    putExtra("motivoBloqueo", motivoBloqueo)
                }
                context.startActivity(intent)
            }
        } else {
            // Solo limpia cuando de verdad NO deba bloquear
            if (paqueteBloqueadoActual != null) {
                Log.d("RegistroUsoService", "✅ App ya no requiere bloqueo: $paqueteActual")
            }
            paqueteBloqueadoActual = null
        }
    }

    private fun obtenerNombreApp(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun homePackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.w("RegistroUsoService", "🛑 Servicio detenido inesperadamente")
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        scope.cancel()
        tareaMonitoreo?.cancel()
        tareaRegistroUso?.cancel()
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                "uso_app_channel",
                "Registro de uso de aplicaciones",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente para el monitoreo de apps en segundo plano."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    private fun mostrarNotificacion() {
        crearCanalNotificacion()

        val notificacion: Notification = NotificationCompat.Builder(this, "uso_app_channel")
            .setContentTitle("UdParents")
            .setContentText("Monitoreando el uso de aplicaciones...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(1, notificacion)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartService = Intent(applicationContext, RegistroUsoService::class.java).setPackage(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartService)
        } else {
            applicationContext.startService(restartService)
        }
        // Opcional: Reforzar la notificación en el restart
        mostrarNotificacion()
    }
    private fun mostrarNotificacionTiempoRestante(nombreApp: String, tiempoRestanteMs: Long) {
        val minutos = tiempoRestanteMs / 60000
        val segundos = (tiempoRestanteMs % 60000) / 1000
        val tiempoTexto = if (minutos > 0) "$minutos min" else "$segundos segundos"

        val canalId = "canal_tiempo_restante"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val canal = NotificationChannel(
                canalId,
                "Tiempo restante de uso",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificación cuando el tiempo de uso está por agotarse"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setSound(sonido, null)
            }
            manager.createNotificationChannel(canal)
        }

        val notificacion = NotificationCompat.Builder(this, canalId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ Queda poco tiempo")
            .setContentText("Queda $tiempoTexto para usar la app '$nombreApp'")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        manager.notify(2, notificacion)
    }
    private fun mostrarNotificacionUltimosSegundos(nombreApp: String) {
        val canalId = "canal_ultimos_segundos"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val canal = NotificationChannel(
                canalId,
                "Últimos segundos de uso",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificación crítica cuando solo quedan segundos de uso"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setSound(sonido, null)
            }
            manager.createNotificationChannel(canal)
        }

        val notificacion = NotificationCompat.Builder(this, canalId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ Últimos segundos")
            .setContentText("Se bloqueará la app '$nombreApp' en pocos segundos")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        manager.notify(3, notificacion)
    }
    /** Devuelve el componente completo del servicio de accesibilidad registrado */
    private fun componenteServicioAccesibilidad(): String {
        val serviceClass = "com.example.udparents.servicio.BloqueoAccessibilityService"
        return "$packageName/$serviceClass"
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        // Android guarda una lista separada por ":" con "package/ServiceClass"
        return enabled.split(':').any { it.equals(componenteServicioAccesibilidad(), ignoreCase = true) }
    }

    private fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }


    /** Verifica permisos críticos y:
     * (1) muestra BLOQUEO full-screen en el teléfono del hijo cuando faltan,
     * (2) avisa al PADRE cuando se desactivan o restauran (mensajes claros),
     * (3) incluye Admin de dispositivo como requisito.
     */
    private fun verificarPermisosEsenciales(): Boolean {
        val accesibilidadOk = isAccessibilityServiceEnabled(applicationContext)
        val usoOk = isUsageAccessGranted(applicationContext)
        val adminOk = isDeviceAdminActive(applicationContext)   // ⬅️ Admin integrado
        val permisosOk = accesibilidadOk && usoOk && adminOk

        val ahora = System.currentTimeMillis()
        val cambioEstado = (ultimoEstadoPermisos == null) || (ultimoEstadoPermisos != permisosOk)
        val fueraDeCooldown = (ahora - ultimoAvisoPermisosMs) >= COOLDOWN_AVISO_MS

        if (!permisosOk) {
            // Construir textos claros para HIJO y PADRE
            val faltan = mutableListOf<String>()
            if (!accesibilidadOk) faltan.add("accesibilidad")
            if (!usoOk)           faltan.add("uso de datos")
            if (!adminOk)         faltan.add("administrador de dispositivo")

            val motivoBloqueoHijo = "Permisos desactivados: ${faltan.joinToString(" y ")}"
            val mensajePadre = "Tu hijo deshabilitó ${faltan.joinToString(" y ")}."

            // Mostrar FULL-SCREEN en el teléfono del hijo sin esperar clic
            if (!mostrandoBloqueoPermisos) {
                mostrandoBloqueoPermisos = true
                Log.w("RegistroUsoService", "🚫 Faltan permisos ($motivoBloqueoHijo). Full-screen.")
                mostrarBloqueoPermisosFullScreen(motivoBloqueoHijo)
            }

            // Aviso al padre (primer cambio a fallo o tras cooldown)
            if (cambioEstado || fueraDeCooldown) {
                avisarPadreCambioPermisos(
                    titulo = "⚠️ Permiso deshabilitado",
                    mensaje = mensajePadre
                )
                ultimoAvisoPermisosMs = ahora
            }

            ultimoEstadoPermisos = false
            huboFalloPermisos = true
            return false

        } else {
            // Todos OK: quitar estado de bloqueo y avisar al padre una vez
            if (mostrandoBloqueoPermisos) {
                Log.d("RegistroUsoService", "✅ Permisos restaurados. Volviendo a monitoreo normal.")
                mostrandoBloqueoPermisos = false
            }

            if (huboFalloPermisos && (ultimoEstadoPermisos != true)) {
                avisarPadreCambioPermisos(
                    titulo = "✅ Permisos restaurados",
                    mensaje = "Tu hijo reactivó accesibilidad, uso de datos y administrador de dispositivo."
                )
                ultimoAvisoPermisosMs = ahora
                huboFalloPermisos = false
            }

            ultimoEstadoPermisos = true
            return true
        }
    }

    private fun mostrarBloqueoPermisosFullScreen(motivo: String) {
        val channelId = "canal_bloqueo_permisos_fullscreen"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Canal con IMPORTANCE_HIGH (requerido para full-screen); recrea si hace falta
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "Bloqueo por permisos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Muestra pantalla de bloqueo cuando faltan permisos críticos"
                setShowBadge(false)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        // Intent hacia la Activity de bloqueo (mostrada en pantalla completa)
        val intent = Intent(applicationContext, PantallaBloqueoComposeActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra("motivoBloqueo", motivo)
            putExtra("bloqueoPermiso", true)
        }

        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val fullScreenPi = PendingIntent.getActivity(applicationContext, 1001, intent, piFlags)

        // Notificación tipo “llamada” para forzar heads-up / full-screen inmediatamente
        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Permiso requerido")
            .setContentText(motivo)
            .setPriority(NotificationCompat.PRIORITY_MAX)               // MAX
            .setCategory(Notification.CATEGORY_CALL)                    // CALL/ALARM ayudan a abrir full-screen
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPi, true)                    // clave
            .build()
        nm.notify(1002, notif)

        //  Fallback opcional:
        //  si el sistema no abre full-screen por sí solo, intentamos abrir directo
        // (Android < 10 lo permite; en >= 10 puede ser bloqueado por BAL).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                startActivity(intent)
            } catch (_: Exception) {
                // Ignorar si el sistema lo bloquea
            }
        }
    }

    private fun avisarPadreCambioPermisos(titulo: String, mensaje: String) {
        val uidPadre = SharedPreferencesUtil.obtenerUidPadre(applicationContext) ?: ""
        if (uidPadre.isBlank()) {
            Log.w("RegistroUsoService", "No hay uidPadre guardado; no se envía push.")
            return
        }
        val sender = NotificacionSender()
        scope.launch {
            try {
                sender.enviarNotificacionAlPadre(
                    uidPadre = uidPadre,
                    titulo = titulo,
                    mensaje = mensaje
                )
                Log.d("RegistroUsoService", "📨 Push enviada al padre: $titulo - $mensaje")
            } catch (e: Exception) {
                Log.e("RegistroUsoService", "❌ Error enviando push al padre: ${e.message}", e)
            }
        }

        val uidHijo = FirebaseAuth.getInstance().currentUser?.uid
        if (!uidPadre.isBlank() && !uidHijo.isNullOrBlank()) {
            val repoBloqueos = RepositorioBloqueos()
            val registro = BloqueoRegistro(
                uidHijo = uidHijo,
                nombrePaquete = "PERMISOS",
                nombreApp = titulo,          // p.ej. "Permiso deshabilitado" / "Permisos restaurados"
                razon = mensaje              // detalle del cambio
            )
            scope.launch {
                try {
                    repoBloqueos.registrarBloqueo(uidHijo, uidPadre, registro)
                    Log.d("RegistroUsoService", "🗂️ Evento de permisos registrado en Firebase.")
                } catch (e: Exception) {
                    Log.e("RegistroUsoService", "❌ Error registrando evento de permisos: ${e.message}", e)
                }
            }
        }
    }
    //  Helpers Device Admin: comprobar y solicitar activación

    private fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val cn = ComponentName(context, AdminReceiver::class.java)
        return dpm?.isAdminActive(cn) == true
    }

    private fun solicitarActivacionDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val cn = ComponentName(context, AdminReceiver::class.java)
        if (dpm?.isAdminActive(cn) != true) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "UdParents necesita este permiso para impedir que se desinstale sin autorización."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent) // abre la pantalla de activación
        }
    }
}