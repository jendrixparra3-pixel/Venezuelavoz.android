package com.venezuela.voz

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.UUID

/**
 * Servidor WebSocket local. En Minecraft Bedrock, con trucos activados, el comando
 * /connect 127.0.0.1:8765 hace que el juego se conecte aquí; desde aquí mandamos comandos
 * (/scriptevent) que el complemento recibe.
 */
class BedrockServer(dir: InetSocketAddress, private val token: String) : WebSocketServer(dir) {
    @Volatile private var cliente: WebSocket? = null

    init { connectionLostTimeout = 0 }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        val ruta = (handshake?.resourceDescriptor ?: "").trim('/')
        if (ruta != token) {
            Hub.linea("⚠ Conexión rechazada: al comando /connect le falta el código (o es incorrecto). Copia el comando desde la app.")
            conn?.close(1008, "codigo")
            return
        }
        cliente = conn
        Hub.cambiarMinecraft(true)
        Hub.linea("Minecraft conectado.")
        enviar("scriptevent venezuela:hello ok")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        if (conn != null && conn == cliente) {
            if (reason?.contains("closecode", ignoreCase = true) == true) {
                Hub.linea("Minecraft cerró la conexión con un código de cierre propio de Bedrock ($code). Suele pasar al salir del mundo o al pasar la app a segundo plano.")
            }
            cliente = null
            Hub.cambiarMinecraft(false)
            Hub.linea("Minecraft se desconectó.")
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) { /* no necesitamos las respuestas del juego */ }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        val m = ex?.message ?: ""
        // El cierre de Bedrock trae un código no estándar que la librería reporta como error: no es un fallo de tu app.
        if (m.contains("closecode must not be sent over the wire", ignoreCase = true)) return
        Hub.linea("⚠ Servidor local: $m")
    }

    override fun onStart() { Hub.linea("Servidor local listo en el puerto $port.") }

    fun conectado(): Boolean = cliente?.isOpen == true

    /** Devuelve false si Minecraft no está conectado. */
    fun enviar(comando: String): Boolean {
        val c = cliente ?: return false
        if (!c.isOpen) return false
        return try {
            val cabecera = JSONObject()
                .put("version", 1)
                .put("requestId", UUID.randomUUID().toString())
                .put("messageType", "commandRequest")
                .put("messagePurpose", "commandRequest")
            val cuerpo = JSONObject()
                .put("origin", JSONObject().put("type", "player"))
                .put("commandLine", comando)
                .put("version", 1)
            c.send(JSONObject().put("header", cabecera).put("body", cuerpo).toString())
            true
        } catch (e: Exception) { false }
    }
}
