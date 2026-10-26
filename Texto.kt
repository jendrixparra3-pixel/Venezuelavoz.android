package com.venezuela.voz

import java.text.Normalizer
import java.util.Locale

object Texto {
    /** Quita saltos de línea, el símbolo de color de Minecraft y la barra que separa acción|texto. */
    fun limpiar(s: String): String =
        s.replace(Regex("[\\r\\n]+"), " ").replace("§", "").replace("|", ",").trim()

    /** Minúsculas y sin tildes, para buscar la palabra «venezuela» aunque el reconocimiento la escriba distinto. */
    fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Parte un texto largo en trozos por palabras para no pasarse del largo de un comando de Minecraft. */
    fun trozos(texto: String, max: Int = 180): List<String> {
        val out = ArrayList<String>()
        var actual = StringBuilder()
        for (palabra in texto.split(" ")) {
            if (palabra.isEmpty()) continue
            if (actual.isNotEmpty() && actual.length + 1 + palabra.length > max) {
                out.add(actual.toString())
                actual = StringBuilder()
            }
            if (actual.isNotEmpty()) actual.append(' ')
            actual.append(palabra)
        }
        if (actual.isNotEmpty()) out.add(actual.toString())
        return out
    }
}
