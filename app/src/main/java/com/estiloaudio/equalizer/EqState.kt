package com.estiloaudio.equalizer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Todas as configurações do app. Salvas automaticamente a cada mudança. */
class EqSettings {
    // Ajustes
    var master = true
    var auto = false
    var global = false

    // Equalizador
    var bypass = false
    var preamp = 0f
    val bands = FloatArray(10)
    var bass = 0f
    var treble = 0f

    // Compressor
    var attack = 10f
    var release = 120f
    var ratio = 1f
    var threshold = -18f
    var gain = 0f
    var balance = 0f
    var volume = 0f

    // Limiter
    var limOn = true
    var limThr = -1f
    var limRel = 60f

    fun toJson(): String {
        val o = JSONObject()
        o.put("master", master); o.put("auto", auto); o.put("global", global)
        o.put("bypass", bypass); o.put("preamp", preamp.toDouble())
        val arr = JSONArray()
        for (b in bands) arr.put(b.toDouble())
        o.put("bands", arr)
        o.put("bass", bass.toDouble()); o.put("treble", treble.toDouble())
        o.put("attack", attack.toDouble()); o.put("release", release.toDouble())
        o.put("ratio", ratio.toDouble()); o.put("threshold", threshold.toDouble())
        o.put("gain", gain.toDouble()); o.put("balance", balance.toDouble())
        o.put("volume", volume.toDouble())
        o.put("limOn", limOn); o.put("limThr", limThr.toDouble()); o.put("limRel", limRel.toDouble())
        return o.toString()
    }

    private fun JSONObject.f(key: String, def: Float): Float = optDouble(key, def.toDouble()).toFloat()

    fun load(o: JSONObject) {
        master = o.optBoolean("master", master)
        auto = o.optBoolean("auto", auto)
        global = o.optBoolean("global", global)
        bypass = o.optBoolean("bypass", bypass)
        preamp = o.f("preamp", preamp)
        val arr = o.optJSONArray("bands")
        if (arr != null) {
            for (i in 0 until min(arr.length(), bands.size)) bands[i] = arr.optDouble(i, 0.0).toFloat()
        }
        bass = o.f("bass", bass); treble = o.f("treble", treble)
        attack = o.f("attack", attack); release = o.f("release", release)
        ratio = o.f("ratio", ratio); threshold = o.f("threshold", threshold)
        gain = o.f("gain", gain); balance = o.f("balance", balance)
        volume = o.f("volume", volume)
        limOn = o.optBoolean("limOn", limOn)
        limThr = o.f("limThr", limThr); limRel = o.f("limRel", limRel)
    }
}

/** Estado compartilhado entre a tela e o serviço (mesmo processo). */
object EqState {
    const val BARS = 32

    val s = EqSettings()
    private var prefs: SharedPreferences? = null
    val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile var bars = FloatArray(BARS)
    @Volatile var grDb = 0f
    @Volatile var sessionOpen = false
    @Volatile var vizOk = false
    @Volatile var uiVisible = false
    @Volatile var running = false

    fun ensure(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("eq", Context.MODE_PRIVATE)
        prefs = p
        val json = p.getString("json", null)
        if (json != null) {
            try { s.load(JSONObject(json)) } catch (_: Exception) { }
        }
    }

    fun update(block: (EqSettings) -> Unit) {
        block(s)
        prefs?.edit()?.putString("json", s.toJson())?.apply()
        notifyChanged()
    }

    fun notifyChanged() {
        for (l in listeners) l()
    }

    fun status(): String {
        if (!s.master && !s.auto) return "Desligado"
        if (running) return if (s.global) "Ativo • modo global" else "Ativo • Spotify"
        return "Aguardando o Spotify…"
    }
}

/** Matemática da curva: usada tanto pela tela quanto pelo motor de áudio. */
object EqMath {
    private const val BASE = 31.25f
    val CENTERS = FloatArray(10) { BASE * 2f.pow(it) }

    /** Interpolação suave (Catmull-Rom) entre as 10 bandas, em escala logarítmica. */
    fun bandDb(b: FloatArray, f: Float): Float {
        val u = ln(f / BASE) / ln(2f)
        if (u <= 0f) return b[0]
        if (u >= 9f) return b[9]
        val i = min(u.toInt(), 8)
        val t = u - i
        val p0 = b[max(i - 1, 0)]
        val p1 = b[i]
        val p2 = b[i + 1]
        val p3 = b[min(i + 2, 9)]
        return 0.5f * (2f * p1 + (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t * t +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t * t * t)
    }

    fun shelfDb(bass: Float, treble: Float, f: Float): Float {
        val lo = 1f / (1f + (f / 160f).pow(2))
        val hi = 1f / (1f + (4000f / f).pow(2))
        return bass * lo + treble * hi
    }

    fun responseDb(s: EqSettings, f: Float): Float =
        if (s.bypass) 0f else bandDb(s.bands, f) + shelfDb(s.bass, s.treble, f)
}
