package com.venezuela.voz

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : Activity(), Hub.Listener {

    private lateinit var aj: Ajustes
    private lateinit var mem: Memoria
    private lateinit var estadoTxt: TextView
    private lateinit var btnEscuchar: Button
    private lateinit var mcTxt: TextView
    private lateinit var logTxt: TextView
    private lateinit var etClave: EditText
    private lateinit var etModelo: EditText
    private val REQ = 10

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun texto(t: String, size: Float = 14f, bold: Boolean = false): TextView = TextView(this).apply {
        text = t; textSize = size
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun boton(t: String, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aj = Ajustes(this)
        mem = Memoria(this, aj)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        val perfil = ImageView(this).apply {
            setImageResource(com.venezuela.voz.R.drawable.venezuela_voz_profile)
            contentDescription = "Imagen de perfil de VenezuelaVoz"
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.BLACK)
                setStroke(dp(3), Color.rgb(0, 140, 255))
            }
            clipToOutline = true
        }
        col.addView(perfil, LinearLayout.LayoutParams(dp(190), dp(190)).apply {
            gravity = Gravity.CENTER
            bottomMargin = dp(12)
        })
        col.addView(texto("Venezuela Voz", 26f, true).apply { gravity = Gravity.CENTER })

        estadoTxt = TextView(this).apply {
            textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(8), dp(16), dp(8), dp(16))
        }
        col.addView(estadoTxt)

        btnEscuchar = boton("🎤 Activar escucha") { alPulsarEscuchar() }
        col.addView(btnEscuchar)
        col.addView(boton("🔇 Callar a Venezuela") {
            startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACCION_CALLAR))
        })

        col.addView(texto("Volumen de la voz", 14f, true))
        col.addView(SeekBar(this).apply {
            max = 100; progress = aj.volumen
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) aj.volumen = p }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        col.addView(Switch(this).apply {
            text = "Solo responder si digo «Venezuela» (y 20 s después de que me hable)"
            isChecked = aj.palabraClave
            setOnCheckedChangeListener { _, c -> aj.palabraClave = c }
        })

        col.addView(texto("Memoria", 16f, true))
        col.addView(Switch(this).apply {
            text = "Recordar cosas entre conversaciones (se guarda solo en este teléfono)"
            isChecked = aj.memoria
            setOnCheckedChangeListener { _, c -> aj.memoria = c }
        })
        col.addView(boton("🗑 Borrar memoria") {
            AlertDialog.Builder(this)
                .setTitle("¿Borrar memoria?")
                .setMessage("Se borran las notas y la conversación guardadas (${mem.cantidadNotas()} notas).")
                .setPositiveButton("Borrar") { _, _ -> mem.borrar(); toast("Memoria borrada") }
                .setNegativeButton("Cancelar", null)
                .show()
        })

        col.addView(texto("IA (Gemini gratis o Claude)", 16f, true))
        col.addView(texto("Pega una clave de Google (aistudio.google.com) o de Anthropic Claude (console.anthropic.com, empieza con sk-ant-). La app detecta cuál es. Se guarda solo en este teléfono.", 12f))
        etClave = EditText(this).apply {
            hint = "Clave de IA (Gemini o Claude)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(aj.clave)
        }
        etModelo = EditText(this).apply {
            hint = "Modelo (vacío = el recomendado)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(aj.modelo)
        }
        col.addView(etClave); col.addView(etModelo)
        col.addView(boton("Guardar") { guardarCampos(); toast("Guardado") })

        col.addView(texto("Minecraft", 16f, true))
        mcTxt = texto("")
        col.addView(mcTxt)
        col.addView(Switch(this).apply {
            text = "Aceptar conexión por Wi-Fi (solo si 127.0.0.1 no te funciona)"
            isChecked = aj.lan
            setOnCheckedChangeListener { _, c -> aj.lan = c; toast("Detén y vuelve a activar la escucha"); mostrarComando() }
        })
        col.addView(boton("Copiar comando /connect") { copiarComando() })
        col.addView(texto(
            "En el mundo (con trucos activados) escribe el comando en el chat. Hay que repetirlo cada vez que abras el mundo.", 12f))

        col.addView(boton("🔒 Privacidad: qué se envía y adónde") { mostrarPrivacidad(false) {} })

        col.addView(texto("Registro", 16f, true))
        logTxt = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 12f
            setTextIsSelectable(true)
        }
        col.addView(logTxt)

        mostrarComando()
    }

    override fun onResume() {
        super.onResume()
        Hub.listener = this
        onEstado(Hub.estado)
        onMinecraft(Hub.minecraft)
        logTxt.text = Hub.historialLineas()
    }

    override fun onPause() {
        guardarCampos()
        if (Hub.listener === this) Hub.listener = null
        super.onPause()
    }

    // ---------- Hub.Listener ----------

    override fun onEstado(e: Estado) {
        val (txt, color) = when (e) {
            Estado.INACTIVO -> "● Micrófono apagado" to Color.GRAY
            Estado.ESCUCHANDO -> "🎤 Escuchando…" to Color.rgb(46, 125, 50)
            Estado.PENSANDO -> "💭 Pensando…" to Color.rgb(230, 126, 0)
            Estado.HABLANDO -> "🔊 Venezuela habla (micrófono en pausa)" to Color.rgb(25, 90, 180)
        }
        estadoTxt.text = txt
        estadoTxt.setBackgroundColor(color)
        btnEscuchar.text = if (e == Estado.INACTIVO) "🎤 Activar escucha" else "⏹ Detener escucha"
        mostrarComando()
    }

    override fun onLinea(texto: String) { logTxt.append(texto + "\n") }

    override fun onMinecraft(conectado: Boolean) { mostrarComando() }

    // ---------- acciones ----------

    private fun comando(): String = "/connect 127.0.0.1:${VoiceService.PUERTO}/${Hub.token}"

    private fun mostrarComando() {
        var t = (if (Hub.minecraft) "✅ Minecraft conectado\n" else "⚪ Minecraft sin conectar\n") + "Comando: " + comando()
        if (aj.lan) {
            val ips = ArrayList<String>()
            try {
                for (ni in (NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList())) for (a in ni.inetAddresses.toList())
                    if (a is Inet4Address && !a.isLoopbackAddress) ips.add(a.hostAddress ?: "")
            } catch (_: Exception) {}
            t += "\nO con la IP del Wi-Fi: " + ips.joinToString(" / ") { "/connect $it:${VoiceService.PUERTO}/${Hub.token}" }
        }
        mcTxt.text = t
    }

    private fun copiarComando() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("comando", comando()))
        toast("Copiado: ${comando()}")
    }

    private fun guardarCampos() {
        aj.clave = etClave.text.toString()
        aj.modelo = etModelo.text.toString()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun alPulsarEscuchar() {
        if (Hub.estado != Estado.INACTIVO) {
            startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACCION_DETENER))
            return
        }
        if (!aj.consentimiento) {
            mostrarPrivacidad(true) { aj.consentimiento = true; alPulsarEscuchar() }
            return
        }
        guardarCampos()
        if (aj.clave.isBlank()) { toast("Primero pega tu clave de IA (Gemini o Claude)"); return }
        val faltan = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) faltan.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) faltan.add(Manifest.permission.POST_NOTIFICATIONS)
        if (faltan.isNotEmpty()) { requestPermissions(faltan.toTypedArray(), REQ); return }
        iniciar()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) iniciar()
        else {
            Hub.linea("⚠ Sin permiso de micrófono no puedo escucharte. Puedes dárselo en Ajustes > Apps > Venezuela Voz > Permisos.")
            toast("Falta el permiso del micrófono")
        }
    }

    private fun iniciar() {
        logTxt.text = ""
        startForegroundService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACCION_INICIAR))
    }

    private fun mostrarPrivacidad(pedirAceptar: Boolean, alAceptar: () -> Unit) {
        val b = AlertDialog.Builder(this).setTitle("Privacidad").setMessage(TEXTO_PRIVACIDAD)
        if (pedirAceptar) {
            b.setPositiveButton("Entiendo y acepto") { _, _ -> alAceptar() }
            b.setNegativeButton("Cancelar", null)
        } else b.setPositiveButton("Cerrar", null)
        b.show()
    }

    private val TEXTO_PRIVACIDAD = """
🎤 Micrófono: solo se usa mientras la escucha está activada (hay una notificación fija y un indicador de Android). La app NO guarda audio.

🗣 De voz a texto: lo hace el reconocimiento de voz de tu teléfono (normalmente Google). Ese servicio puede enviar tu audio a Google para convertirlo en texto; eso depende de tu teléfono y no lo controla esta app.

🧠 IA: tu texto, las últimas ~5 vueltas de la charla y las notas de memoria (si la memoria está activada) se envían directamente desde tu teléfono al proveedor de tu clave: Google (Gemini) o Anthropic (Claude). Con la versión gratis de Google, puede usar esos textos para mejorar sus productos. No se envía audio, ubicación ni datos de tu mundo de Minecraft.

🔊 Voz de Venezuela: la genera el motor de texto a voz de tu teléfono.

🎮 Minecraft: lo que dices (texto) y lo que responde Venezuela se mandan al juego dentro de tu mismo teléfono (127.0.0.1).

💾 Memoria: se guarda solo en este teléfono, sin copia de seguridad en la nube. Puedes apagarla y borrarla cuando quieras.

🔑 Clave: la escribes tú y se guarda solo en este teléfono (sin copia de seguridad). No viene dentro del APK.

🌐 Hace falta internet para reconocer la voz y para la IA, y Gemini gratis y Claude tienen límites de uso.
""".trimIndent()
}
