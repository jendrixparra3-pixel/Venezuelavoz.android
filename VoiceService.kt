package com.venezuela.voz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Locale
import java.util.UUID

/**
 * Servicio en primer plano: escucha (SpeechRecognizer), piensa (backend de IA), habla (TextToSpeech)
 * y le manda el texto/acciones a Minecraft por el WebSocket local.
 * Regla anti-eco: el micrófono NO escucha mientras la IA piensa ni mientras Venezuela habla.
 */
class VoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACCION_INICIAR = "com.venezuela.voz.INICIAR"
        const val ACCION_DETENER = "com.venezuela.voz.DETENER"
        const val ACCION_CALLAR = "com.venezuela.voz.CALLAR"
        const val PUERTO = 8765
        private const val CANAL = "venezuela_voz"
        private const val VENTANA_MS = 20_000L

        @Volatile var activo = false
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var aj: Ajustes
    private lateinit var mem: Memoria
    private var reconocedor: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var servidor: BedrockServer? = null

    private var quiereEscuchar = false
    private var hablando = false
    private var idioma = "es-VE"
    private var erroresSeguidos = 0
    private var ultimaRespuesta = 0L
    private var turno = 0
    private var contadorMensajes = 0
    private val prefijo = UUID.randomUUID().toString().replace("-", "").take(6)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        aj = Ajustes(this)
        mem = Memoria(this, aj)
        tts = TextToSpeech(this, this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CANAL, "Venezuela Voz", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACCION_INICIAR -> iniciar()
            ACCION_DETENER -> detenerTodo()
            ACCION_CALLAR -> callar()
        }
        return START_NOT_STICKY
    }

    // ---------- arranque / parada ----------

    private fun iniciar() {
        if (!iniciarPrimerPlano()) { detenerTodo(); return }
        iniciarServidor()
        quiereEscuchar = true
        erroresSeguidos = 0
        activo = true
        Hub.linea("Escucha activada. Di «Venezuela» y háblame.")
        escuchar()
    }

    private fun iniciarPrimerPlano(): Boolean {
        return try {
            val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(this, CANAL)
                .setContentTitle("Venezuela te está escuchando")
                .setContentText("Micrófono activo. Toca para abrir y detener.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(abrir)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, n)
            true
        } catch (e: Exception) {
            Hub.linea("⚠ Android no me dejó usar el micrófono en segundo plano: ${e.message}")
            false
        }
    }

    private fun iniciarServidor() {
        if (servidor != null) return
        try {
            val dir = if (aj.lan) InetSocketAddress(PUERTO) else InetSocketAddress(InetAddress.getByName("127.0.0.1"), PUERTO)
            val s = BedrockServer(dir, Hub.token)
            s.isReuseAddr = true
            s.start()
            servidor = s
        } catch (e: Exception) {
            Hub.linea("⚠ No pude abrir el servidor local: ${e.message}")
        }
    }

    private fun detenerTodo() {
        quiereEscuchar = false
        activo = false
        turno++
        main.removeCallbacksAndMessages(null)
        try { reconocedor?.destroy() } catch (_: Exception) {}
        reconocedor = null
        tts?.stop()
        hablando = false
        val s = servidor
        servidor = null
        if (s != null) Thread { try { s.stop(300) } catch (_: Exception) {} }.start()
        Hub.cambiarMinecraft(false)
        Hub.token = Hub.generarToken()
        Hub.cambiarEstado(Estado.INACTIVO)
        Hub.linea("Escucha detenida.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        activo = false
        main.removeCallbacksAndMessages(null)
        try { reconocedor?.destroy() } catch (_: Exception) {}
        tts?.shutdown()
        super.onDestroy()
    }

    // ---------- escuchar ----------

    private fun escuchar() {
        if (!quiereEscuchar || hablando || Hub.estado == Estado.PENSANDO) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Hub.linea("⚠ Este teléfono no tiene reconocimiento de voz. Instala o activa la app «Google».")
            detenerTodo()
            return
        }
        try { reconocedor?.destroy() } catch (_: Exception) {}
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        reconocedor = r
        r.setRecognitionListener(escucha)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        r.startListening(i)
        Hub.cambiarEstado(Estado.ESCUCHANDO)
    }

    private fun volverAEscuchar(espera: Long) {
        main.postDelayed({ escuchar() }, espera)
    }

    private val escucha = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() { erroresSeguidos = 0 }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) { /* el audio no se guarda */ }
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            if (!quiereEscuchar || hablando || Hub.estado == Estado.PENSANDO) return
            var espera = 400L
            when (error) {
                9 -> { // permisos
                    Hub.linea("⚠ Sin permiso de micrófono. Dáselo a la app en Ajustes del teléfono.")
                    detenerTodo(); return
                }
                12, 13 -> { // idioma no disponible
                    if (idioma != "es-MX") {
                        idioma = "es-MX"
                        Hub.linea("El español de Venezuela no está disponible en tu reconocimiento de voz; uso es-MX.")
                    } else {
                        Hub.linea("⚠ No hay español disponible en el reconocimiento de voz del teléfono.")
                        detenerTodo(); return
                    }
                }
                1, 2, 4, 11 -> {
                    erroresSeguidos++; espera = 2500
                    Hub.linea("⚠ Sin conexión con el reconocimiento de voz (revisa tu internet).")
                }
                3 -> { erroresSeguidos++; espera = 2500; Hub.linea("⚠ Problema con el micrófono.") }
                10 -> { erroresSeguidos++; espera = 2500; Hub.linea("⚠ Reconocimiento de voz saturado, reintento.") }
                8, 5 -> espera = 1000
                6, 7 -> {} // silencio o nada entendido: es normal, se vuelve a escuchar
                else -> { erroresSeguidos++; Hub.linea("⚠ Error de reconocimiento ($error).") }
            }
            if (erroresSeguidos >= 5) {
                Hub.linea("Demasiados errores seguidos; detuve la escucha.")
                detenerTodo(); return
            }
            volverAEscuchar(espera)
        }

        override fun onResults(results: Bundle?) {
            if (!quiereEscuchar || hablando) return
            erroresSeguidos = 0
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            if (t.isEmpty()) { volverAEscuchar(300); return }
            procesar(t)
        }
    }

    // ---------- pensar ----------

    private fun procesar(texto: String) {
        val enVentana = System.currentTimeMillis() - ultimaRespuesta < VENTANA_MS
        val llamo = Texto.normalizar(texto).contains("venezuela")
        if (aj.palabraClave && !llamo && !enVentana) {
            Hub.linea("(ignorado, no dijiste «Venezuela»): ${texto.take(60)}")
            volverAEscuchar(300)
            return
        }
        if (aj.clave.isBlank()) {
            Hub.linea("⚠ Falta la clave de IA (Gemini o Claude). No invento respuestas sin IA.")
            detenerTodo()
            return
        }
        try { reconocedor?.destroy() } catch (_: Exception) {}
        reconocedor = null
        Hub.cambiarEstado(Estado.PENSANDO)
        Hub.linea("Tú: $texto")
        enviarMinecraft("scriptevent venezuela:heard ${Texto.limpiar(texto)}")

        val miTurno = ++turno
        val historial = mem.historial()
        val notas = mem.notasParaEnviar()
        Thread {
            try {
                val r = AiClient.preguntar(aj.clave, aj.modelo, texto, historial, notas)
                main.post { if (miTurno == turno && quiereEscuchar) responder(texto, r) }
            } catch (e: Exception) {
                main.post {
                    if (miTurno == turno && quiereEscuchar) {
                        Hub.linea("⚠ ${e.message}")
                        Hub.cambiarEstado(Estado.ESCUCHANDO)
                        volverAEscuchar(1500)
                    }
                }
            }
        }.start()
    }

    private fun responder(texto: String, r: RespuestaIa) {
        mem.agregarTurno(texto, r.texto)
        r.recordar?.let { mem.agregarNota(it) }
        Hub.linea("Venezuela: ${r.texto}")

        val limpio = Texto.limpiar(r.texto)
        var llegoAlJuego = true
        Texto.trozos(limpio).forEach { t ->
            contadorMensajes++
            if (!enviarMinecraft("scriptevent venezuela:say $prefijo-m$contadorMensajes|${r.emocion}|$t")) llegoAlJuego = false
        }
        if (r.accion != "none") {
            contadorMensajes++
            if (!enviarMinecraft("scriptevent venezuela:act $prefijo-a$contadorMensajes|${r.accion}")) llegoAlJuego = false
        }
        if (!llegoAlJuego) Hub.linea("(Minecraft no está conectado: solo se oye la voz. Usa /connect 127.0.0.1:$PUERTO/${Hub.token})")
        else if (r.accion != "none") Hub.linea("Acción enviada al juego: ${r.accion}")
        hablar(limpio)
    }

    private fun enviarMinecraft(comando: String): Boolean = servidor?.enviar(comando) ?: false

    // ---------- hablar ----------

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Hub.linea("⚠ No pude iniciar la voz del teléfono (texto a voz).")
            return
        }
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { terminoDeHablar() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { terminoDeHablar() } }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { main.post { terminoDeHablar() } }
        })
        val candidatos = listOf(Locale("es", "VE"), Locale("es", "MX"), Locale("es", "US"), Locale("es", "ES"), Locale("es"))
        for (l in candidatos) {
            val res = t.setLanguage(l)
            if (res == TextToSpeech.LANG_AVAILABLE || res == TextToSpeech.LANG_COUNTRY_AVAILABLE || res == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
                ttsListo = true
                Hub.linea("Voz en español lista (${l.toLanguageTag()}).")
                break
            }
        }
        if (!ttsListo) Hub.linea("⚠ No hay voz en español instalada. Ajustes del teléfono > Idioma > Salida de texto a voz.")
    }

    private fun hablar(texto: String) {
        val t = tts
        if (t == null || !ttsListo) {
            Hub.linea("⚠ La voz del teléfono no está lista; solo muestro el texto.")
            Hub.cambiarEstado(Estado.ESCUCHANDO)
            volverAEscuchar(500)
            return
        }
        hablando = true
        Hub.cambiarEstado(Estado.HABLANDO)
        val p = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, aj.volumen / 100f) }
        t.speak(texto, TextToSpeech.QUEUE_FLUSH, p, "v${System.nanoTime()}")
    }

    private fun terminoDeHablar() {
        if (!hablando) return
        hablando = false
        ultimaRespuesta = System.currentTimeMillis()
        if (quiereEscuchar) {
            Hub.cambiarEstado(Estado.ESCUCHANDO)
            volverAEscuchar(700) // pausa para que no se oiga la cola de su voz
        }
    }

    private fun callar() {
        tts?.stop()
        terminoDeHablar()
    }
}
