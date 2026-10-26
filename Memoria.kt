package com.venezuela.voz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contexto de la charla (últimos mensajes) y notas duraderas.
 * - Con "Memoria" apagada: el contexto vive solo mientras la app está abierta y no se guardan ni envían notas.
 * - Con "Memoria" encendida: se guarda en este teléfono. El botón "Borrar memoria" lo elimina todo.
 */
class Memoria(ctx: Context, private val aj: Ajustes) {
    private val sp = ctx.applicationContext.getSharedPreferences("memoria", Context.MODE_PRIVATE)
    private var hist = JSONArray()

    init {
        if (aj.memoria) {
            try { hist = JSONArray(sp.getString("hist", "[]")) } catch (_: Exception) { hist = JSONArray() }
        }
    }

    private fun copia(a: JSONArray): JSONArray = JSONArray(a.toString())

    fun historial(): JSONArray = copia(hist)

    fun notasParaEnviar(): JSONArray = if (aj.memoria) leerNotas() else JSONArray()

    private fun leerNotas(): JSONArray =
        try { JSONArray(sp.getString("notas", "[]")) } catch (_: Exception) { JSONArray() }

    fun agregarTurno(usuario: String, asistente: String) {
        hist.put(JSONObject().put("role", "user").put("content", usuario.take(500)))
        hist.put(JSONObject().put("role", "assistant").put("content", asistente.take(500)))
        while (hist.length() > 10) hist.remove(0)
        if (aj.memoria) sp.edit().putString("hist", hist.toString()).apply()
    }

    fun agregarNota(nota: String) {
        if (!aj.memoria) return
        val n = nota.trim().take(100)
        if (n.isEmpty()) return
        val notas = leerNotas()
        for (i in 0 until notas.length()) if (notas.optString(i).equals(n, ignoreCase = true)) return
        notas.put(n)
        while (notas.length() > 20) notas.remove(0)
        sp.edit().putString("notas", notas.toString()).apply()
    }

    fun cantidadNotas(): Int = leerNotas().length()

    fun borrar() {
        hist = JSONArray()
        sp.edit().clear().apply()
    }
}
