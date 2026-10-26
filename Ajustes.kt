package com.venezuela.voz

import android.content.Context

/** Ajustes guardados solo en este teléfono (sin copia de seguridad en la nube). */
class Ajustes(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** Clave de Google (Gemini). La escribe el usuario; se guarda solo en este teléfono. */
    var clave: String
        get() = sp.getString("clave", "") ?: ""
        set(v) { sp.edit().putString("clave", v.trim()).apply() }
    var modelo: String
        get() = sp.getString("modelo", AiClient.MODELO_POR_DEFECTO) ?: AiClient.MODELO_POR_DEFECTO
        set(v) { sp.edit().putString("modelo", v.trim().ifEmpty { AiClient.MODELO_POR_DEFECTO }).apply() }
    var palabraClave: Boolean
        get() = sp.getBoolean("palabra", true)
        set(v) { sp.edit().putBoolean("palabra", v).apply() }
    var memoria: Boolean
        get() = sp.getBoolean("memoria", true)
        set(v) { sp.edit().putBoolean("memoria", v).apply() }
    var lan: Boolean
        get() = sp.getBoolean("lan", false)
        set(v) { sp.edit().putBoolean("lan", v).apply() }
    var volumen: Int
        get() = sp.getInt("volumen", 80)
        set(v) { sp.edit().putInt("volumen", v.coerceIn(0, 100)).apply() }
    var consentimiento: Boolean
        get() = sp.getBoolean("consentimiento", false)
        set(v) { sp.edit().putBoolean("consentimiento", v).apply() }
}
