package com.estiloaudio.equalizer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Uma banda do equalizador paramétrico: frequência (Hz), ganho (dB), Q e tipo de filtro. */
class ParamBand(
    @Volatile var freq: Float,
    @Volatile var gain: Float,
    @Volatile var q: Float = DEFAULT_Q,
    @Volatile var type: Int = PEAK
) {
    fun copy() = ParamBand(freq, gain, q, type)

    companion object {
        const val PEAK = 0        // pico/vale (sino)
        const val LOW_SHELF = 1   // prateleira de graves
        const val HIGH_SHELF = 2  // prateleira de agudos
        const val MIN_F = 20f
        const val MAX_F = 20000f
        const val MIN_Q = 0.3f
        const val MAX_Q = 10f
        const val MAX_GAIN = 12f
        const val DEFAULT_Q = 1.41f
        const val SHELF_Q = 0.71f
        const val MAX_BANDS = 20
    }
}

/** Todas as configurações do app. Salvas automaticamente a cada mudança. */
class EqSettings {
    // Ajustes
    var master = true
    var auto = false
    var global = false

    // Equalizador
    var bypass = false
    var preamp = 0f
    var autoPreamp = false
    val bands = CopyOnWriteArrayList<ParamBand>()
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

    // Cortes (filtros passa-alta e passa-baixa). Slope: 0 = 12 dB/oitava, 1 = 24 dB/oitava
    var lowCutOn = false
    var lowCutF = 80f
    var lowCutSlope = 0
    var highCutOn = false
    var highCutF = 16000f
    var highCutSlope = 0

    // Compressor em 3 faixas (graves / médios / agudos)
    var multiband = false
    var x1 = 200f
    var x2 = 3000f
    val mbThr = FloatArray(3) { -18f }
    val mbRatio = FloatArray(3) { 1f }
    val mbAtk = floatArrayOf(30f, 10f, 5f)
    val mbRel = floatArrayOf(250f, 120f, 60f)
    val mbGain = FloatArray(3)

    fun resetMultiband() {
        x1 = 200f; x2 = 3000f
        for (i in 0 until 3) {
            mbThr[i] = -18f; mbRatio[i] = 1f; mbGain[i] = 0f
        }
        mbAtk[0] = 30f; mbAtk[1] = 10f; mbAtk[2] = 5f
        mbRel[0] = 250f; mbRel[1] = 120f; mbRel[2] = 60f
    }

    // Limiter
    var limOn = true
    var limThr = -1f
    var limRel = 60f

    init {
        resetBands()
    }

    /** Volta para 10 bandas planas (31 Hz a 16 kHz, uma por oitava). */
    fun resetBands() {
        bands.clear()
        for (f in EqMath.CENTERS) bands.add(ParamBand(f, 0f))
    }

    fun toJson(): String {
        val o = JSONObject()
        o.put("master", master); o.put("auto", auto); o.put("global", global)
        o.put("bypass", bypass); o.put("preamp", preamp.toDouble())
        o.put("autoPreamp", autoPreamp)
        val arr = JSONArray()
        for (b in bands) {
            val x = JSONObject()
            x.put("f", b.freq.toDouble()); x.put("g", b.gain.toDouble())
            x.put("q", b.q.toDouble()); x.put("t", b.type)
            arr.put(x)
        }
        o.put("pbands", arr)
        o.put("bass", bass.toDouble()); o.put("treble", treble.toDouble())
        o.put("attack", attack.toDouble()); o.put("release", release.toDouble())
        o.put("ratio", ratio.toDouble()); o.put("threshold", threshold.toDouble())
        o.put("gain", gain.toDouble()); o.put("balance", balance.toDouble())
        o.put("volume", volume.toDouble())
        o.put("lcOn", lowCutOn); o.put("lcF", lowCutF.toDouble()); o.put("lcS", lowCutSlope)
        o.put("hcOn", highCutOn); o.put("hcF", highCutF.toDouble()); o.put("hcS", highCutSlope)
        o.put("mb", multiband); o.put("x1", x1.toDouble()); o.put("x2", x2.toDouble())
        o.put("mbThr", arr3(mbThr)); o.put("mbRatio", arr3(mbRatio)); o.put("mbAtk", arr3(mbAtk))
        o.put("mbRel", arr3(mbRel)); o.put("mbGain", arr3(mbGain))
        o.put("limOn", limOn); o.put("limThr", limThr.toDouble()); o.put("limRel", limRel.toDouble())
        return o.toString()
    }

    private fun arr3(a: FloatArray): JSONArray {
        val j = JSONArray()
        for (v in a) j.put(v.toDouble())
        return j
    }

    private fun readArr3(o: JSONObject, key: String, dst: FloatArray, lo: Float, hi: Float) {
        val j = o.optJSONArray(key) ?: return
        for (i in 0 until minOf(3, j.length())) dst[i] = j.optDouble(i, dst[i].toDouble()).toFloat().coerceIn(lo, hi)
    }

    private fun JSONObject.f(key: String, def: Float): Float = optDouble(key, def.toDouble()).toFloat()

    fun load(o: JSONObject) {
        master = o.optBoolean("master", master)
        auto = o.optBoolean("auto", auto)
        global = o.optBoolean("global", global)
        bypass = o.optBoolean("bypass", bypass)
        preamp = o.f("preamp", preamp)
        autoPreamp = o.optBoolean("autoPreamp", autoPreamp)
        val pb = o.optJSONArray("pbands")
        val legacy = o.optJSONArray("bands")
        if (pb != null) {
            bands.clear()
            for (i in 0 until pb.length()) {
                val x = pb.optJSONObject(i) ?: continue
                bands.add(
                    ParamBand(
                        x.f("f", 1000f).coerceIn(ParamBand.MIN_F, ParamBand.MAX_F),
                        x.f("g", 0f).coerceIn(-ParamBand.MAX_GAIN, ParamBand.MAX_GAIN),
                        x.f("q", ParamBand.DEFAULT_Q).coerceIn(ParamBand.MIN_Q, ParamBand.MAX_Q),
                        x.optInt("t", ParamBand.PEAK).coerceIn(0, 2)
                    )
                )
            }
        } else if (legacy != null) {
            // configuração antiga (10 bandas fixas): vira 10 bandas paramétricas nas mesmas frequências
            resetBands()
            for (i in 0 until min(legacy.length(), bands.size)) {
                bands[i].gain = legacy.optDouble(i, 0.0).toFloat().coerceIn(-ParamBand.MAX_GAIN, ParamBand.MAX_GAIN)
            }
        }
        bass = o.f("bass", bass); treble = o.f("treble", treble)
        attack = o.f("attack", attack); release = o.f("release", release)
        ratio = o.f("ratio", ratio); threshold = o.f("threshold", threshold)
        gain = o.f("gain", gain); balance = o.f("balance", balance)
        volume = o.f("volume", volume)
        lowCutOn = o.optBoolean("lcOn", lowCutOn)
        lowCutF = o.f("lcF", lowCutF).coerceIn(20f, 500f)
        lowCutSlope = o.optInt("lcS", lowCutSlope).coerceIn(0, 1)
        highCutOn = o.optBoolean("hcOn", highCutOn)
        highCutF = o.f("hcF", highCutF).coerceIn(2000f, 20000f)
        highCutSlope = o.optInt("hcS", highCutSlope).coerceIn(0, 1)
        multiband = o.optBoolean("mb", multiband)
        x1 = o.f("x1", x1).coerceIn(60f, 1000f)
        x2 = o.f("x2", x2).coerceIn(1000f, 12000f)
        readArr3(o, "mbThr", mbThr, -60f, 0f)
        readArr3(o, "mbRatio", mbRatio, 1f, 20f)
        readArr3(o, "mbAtk", mbAtk, 1f, 200f)
        readArr3(o, "mbRel", mbRel, 10f, 2000f)
        readArr3(o, "mbGain", mbGain, -12f, 24f)
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
    @Volatile var inDb = -90f        // nível de entrada medido (dBFS), para a curva do compressor
    @Volatile var peakDb = -90f      // pico de saída estimado (dBFS)
    @Volatile var peakState = 0      // 0 normal, 1 limiter atuando, 2 clipping
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

    private const val FS = 48000.0

    /** Resposta (dB) de um filtro biquad (fórmulas do "Audio EQ Cookbook") na frequência f, a 48 kHz. */
    fun bandDb(b: ParamBand, f: Float): Float {
        if (abs(b.gain) < 0.01f) return 0f
        val big = 10.0.pow(b.gain / 40.0)
        val w0 = 2.0 * PI * b.freq / FS
        val cw = cos(w0)
        val alpha = sin(w0) / (2.0 * b.q)
        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double
        when (b.type) {
            ParamBand.LOW_SHELF -> {
                val sa = 2.0 * sqrt(big) * alpha
                b0 = big * ((big + 1) - (big - 1) * cw + sa)
                b1 = 2 * big * ((big - 1) - (big + 1) * cw)
                b2 = big * ((big + 1) - (big - 1) * cw - sa)
                a0 = (big + 1) + (big - 1) * cw + sa
                a1 = -2 * ((big - 1) + (big + 1) * cw)
                a2 = (big + 1) + (big - 1) * cw - sa
            }
            ParamBand.HIGH_SHELF -> {
                val sa = 2.0 * sqrt(big) * alpha
                b0 = big * ((big + 1) + (big - 1) * cw + sa)
                b1 = -2 * big * ((big - 1) + (big + 1) * cw)
                b2 = big * ((big + 1) + (big - 1) * cw - sa)
                a0 = (big + 1) - (big - 1) * cw + sa
                a1 = 2 * ((big - 1) - (big + 1) * cw)
                a2 = (big + 1) - (big - 1) * cw - sa
            }
            else -> {
                b0 = 1 + alpha * big
                b1 = -2 * cw
                b2 = 1 - alpha * big
                a0 = 1 + alpha / big
                a1 = -2 * cw
                a2 = 1 - alpha / big
            }
        }
        val w = 2.0 * PI * f / FS
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = -(b1 * s1 + b2 * s2)
        val dr = a0 + a1 * c1 + a2 * c2
        val di = -(a1 * s1 + a2 * s2)
        val den = dr * dr + di * di
        if (den <= 0.0) return 0f
        return (10.0 * log10((nr * nr + ni * ni) / den)).toFloat()
    }

    /** Resposta (dB) dos cortes: filtros Butterworth de 2ª (12 dB/oit) ou 4ª ordem (24 dB/oit). */
    fun cutDb(s: EqSettings, f: Float): Float {
        var db = 0f
        if (s.lowCutOn) {
            val n = if (s.lowCutSlope == 0) 2 else 4
            db -= 10f * log10(1f + (s.lowCutF / f).pow(2 * n))
        }
        if (s.highCutOn) {
            val n = if (s.highCutSlope == 0) 2 else 4
            db -= 10f * log10(1f + (f / s.highCutF).pow(2 * n))
        }
        return db
    }

    fun shelfDb(bass: Float, treble: Float, f: Float): Float {
        val lo = 1f / (1f + (f / 160f).pow(2))
        val hi = 1f / (1f + (4000f / f).pow(2))
        return bass * lo + treble * hi
    }

    fun responseDb(s: EqSettings, f: Float): Float {
        if (s.bypass) return 0f
        var db = shelfDb(s.bass, s.treble, f) + cutDb(s, f)
        for (b in s.bands) db += bandDb(b, f)
        return db
    }

    /**
     * Preamp automático: quanto baixar o ganho de entrada (valor negativo ou zero) para o
     * pico mais alto da curva de resposta não passar de 0 dB.
     */
    fun autoPreampDb(s: EqSettings): Float {
        if (s.bypass) return 0f
        var peak = 0f
        val n = 160
        for (i in 0 until n) {
            val f = 20f * 1000f.pow(i / (n - 1f))
            peak = max(peak, responseDb(s, f))
        }
        return -peak
    }
}
