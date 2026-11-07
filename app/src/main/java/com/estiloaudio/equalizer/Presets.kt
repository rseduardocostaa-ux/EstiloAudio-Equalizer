package com.estiloaudio.equalizer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Um preset do equalizador: lista de bandas paramétricas, preamp, bass e treble. */
class Preset(
    val name: String,
    val bands: List<ParamBand>,
    val preamp: Float = 0f,
    val bass: Float = 0f,
    val treble: Float = 0f,
    val builtIn: Boolean = false
) {
    /** true se os ajustes atuais do EQ são iguais a este preset (a ordem das bandas não importa). */
    fun matches(s: EqSettings): Boolean {
        fun eq(a: Float, b: Float, tol: Float = 0.05f) = abs(a - b) < tol
        if (!eq(preamp, s.preamp) || !eq(bass, s.bass) || !eq(treble, s.treble)) return false
        if (bands.size != s.bands.size) return false
        val a = bands.sortedBy { it.freq }
        val b = s.bands.sortedBy { it.freq }
        for (i in a.indices) {
            if (a[i].type != b[i].type || !eq(a[i].freq, b[i].freq, 0.5f) ||
                !eq(a[i].gain, b[i].gain) || !eq(a[i].q, b[i].q, 0.02f)
            ) return false
        }
        return true
    }

    fun applyTo(s: EqSettings) {
        s.bands.clear()
        for (b in bands) s.bands.add(b.copy())
        s.preamp = preamp
        s.bass = bass
        s.treble = treble
        s.bypass = false
    }
}

object Presets {
    /** Preset padrão de 10 bandas (31 Hz a 16 kHz, uma por oitava), a partir dos ganhos em dB. */
    private fun builtIn(name: String, vararg g: Int): Preset =
        Preset(name, List(10) { ParamBand(EqMath.CENTERS[it], g[it].toFloat()) }, 0f, 0f, 0f, true)

    //                                   31  62 125 250 500  1K  2K  4K  8K 16K
    val BUILT_IN: List<Preset> = listOf(
        builtIn("Flat",            0,  0,  0,  0,  0,  0,  0,  0,  0,  0),
        builtIn("Rock",            5,  4,  3,  1, -1, -1,  1,  3,  4,  5),
        builtIn("Pop",            -1,  2,  4,  4,  2,  0, -1, -1,  1,  2),
        builtIn("Graves fortes",   8,  7,  5,  3,  1,  0,  0,  0,  0,  0),
        builtIn("Agudos",          0,  0,  0,  0,  0,  1,  3,  5,  6,  7),
        builtIn("Vocal",          -3, -2, -1,  1,  3,  4,  4,  3,  1,  0),
        builtIn("Eletrônica",      6,  5,  2,  0, -2,  2,  1,  2,  5,  6),
        builtIn("Hip-Hop",         6,  5,  2,  3, -1, -1,  2,  0,  2,  3),
        builtIn("Funk",            7,  6,  3,  1,  0, -1,  0,  2,  3,  3),
        builtIn("Jazz",            3,  2,  1,  2, -2, -2,  0,  1,  2,  3),
        builtIn("Clássica",        4,  3,  2,  1, -1, -1,  0,  2,  3,  4),
        builtIn("Acústico",        4,  4,  3,  1,  1,  1,  2,  3,  3,  2),
        builtIn("Loudness",        6,  4,  1,  0, -1,  0, -1,  2,  5,  6),
        builtIn("Menos graves",   -6, -5, -3, -1,  0,  0,  0,  0,  0,  0)
    )

    const val MAX_NAME = 24
    private const val FILE = "presets"
    private const val KEY = "list"

    fun listUser(ctx: Context): List<Preset> {
        val json = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val out = ArrayList<Preset>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pb = o.optJSONArray("pbands")
                val legacy = o.optJSONArray("bands")
                val bands = ArrayList<ParamBand>()
                if (pb != null) {
                    for (k in 0 until pb.length()) {
                        val x = pb.optJSONObject(k) ?: continue
                        bands.add(
                            ParamBand(
                                x.optDouble("f", 1000.0).toFloat().coerceIn(ParamBand.MIN_F, ParamBand.MAX_F),
                                x.optDouble("g", 0.0).toFloat().coerceIn(-ParamBand.MAX_GAIN, ParamBand.MAX_GAIN),
                                x.optDouble("q", ParamBand.DEFAULT_Q.toDouble()).toFloat()
                                    .coerceIn(ParamBand.MIN_Q, ParamBand.MAX_Q),
                                x.optInt("t", ParamBand.PEAK).coerceIn(0, 2)
                            )
                        )
                    }
                } else if (legacy != null) {
                    // preset salvo na versão 1.2 (10 bandas fixas)
                    for (k in 0 until minOf(legacy.length(), 10)) {
                        bands.add(ParamBand(EqMath.CENTERS[k], legacy.optDouble(k, 0.0).toFloat()))
                    }
                }
                out.add(
                    Preset(
                        o.getString("name"), bands,
                        o.optDouble("preamp", 0.0).toFloat(),
                        o.optDouble("bass", 0.0).toFloat(),
                        o.optDouble("treble", 0.0).toFloat()
                    )
                )
            }
        } catch (_: Exception) { }
        return out
    }

    private fun writeUser(ctx: Context, list: List<Preset>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("name", p.name)
            val ba = JSONArray()
            for (b in p.bands) {
                val x = JSONObject()
                x.put("f", b.freq.toDouble()); x.put("g", b.gain.toDouble())
                x.put("q", b.q.toDouble()); x.put("t", b.type)
                ba.put(x)
            }
            o.put("pbands", ba)
            o.put("preamp", p.preamp.toDouble())
            o.put("bass", p.bass.toDouble())
            o.put("treble", p.treble.toDouble())
            arr.put(o)
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun isBuiltInName(name: String): Boolean = BUILT_IN.any { it.name.equals(name, ignoreCase = true) }

    /** Salva os ajustes atuais com o nome dado (substitui um preset seu com o mesmo nome). */
    fun saveCurrent(ctx: Context, name: String, s: EqSettings) {
        val list = listUser(ctx).filter { !it.name.equals(name, ignoreCase = true) }.toMutableList()
        list.add(Preset(name, s.bands.map { it.copy() }, s.preamp, s.bass, s.treble))
        writeUser(ctx, list)
    }

    fun exists(ctx: Context, name: String): Boolean = listUser(ctx).any { it.name.equals(name, ignoreCase = true) }

    fun delete(ctx: Context, name: String) {
        writeUser(ctx, listUser(ctx).filter { !it.name.equals(name, ignoreCase = true) })
    }

    /** Preset (padrão ou seu) igual aos ajustes atuais, ou null. */
    fun current(ctx: Context, s: EqSettings): Preset? =
        (BUILT_IN + listUser(ctx)).firstOrNull { it.matches(s) }
}
