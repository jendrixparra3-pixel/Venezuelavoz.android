package com.venezuela.voz

import android.os.Handler
import android.os.Looper

enum class Estado { INACTIVO, ESCUCHANDO, PENSANDO, HABLANDO }

/** Canal simple entre el servicio (voz/IA/Minecraft) y la pantalla. Todo llega al hilo principal. */
object Hub {
    private val main = Handler(Looper.getMainLooper())

    interface Listener {
        fun onEstado(e: Estado)
        fun onLinea(texto: String)
        fun onMinecraft(conectado: Boolean)
    }

    @Volatile var listener: Listener? = null
    @Volatile var estado: Estado = Estado.INACTIVO
    @Volatile var minecraft: Boolean = false

    /** Código secreto de esta sesión: va al final del comando /connect y evita conexiones ajenas. */
    @Volatile var token: String = generarToken()
    fun generarToken(): String {
        val letras = "abcdefghjkmnpqrstuvwxyz23456789"
        val r = java.security.SecureRandom()
        return (1..8).map { letras[r.nextInt(letras.length)] }.joinToString("")
    }
    private val lineas = ArrayList<String>()

    fun cambiarEstado(e: Estado) { estado = e; main.post { listener?.onEstado(e) } }
    fun cambiarMinecraft(c: Boolean) { minecraft = c; main.post { listener?.onMinecraft(c) } }
    fun linea(t: String) {
        synchronized(lineas) { lineas.add(t); while (lineas.size > 40) lineas.removeAt(0) }
        main.post { listener?.onLinea(t) }
    }
    fun historialLineas(): String = synchronized(lineas) { lineas.joinToString("\n") }
}
