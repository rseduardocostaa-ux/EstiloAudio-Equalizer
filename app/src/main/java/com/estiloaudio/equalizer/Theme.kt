package com.estiloaudio.equalizer

import android.content.Context

/** Preferências de aparência: cor principal e modo claro/escuro (salvos no aparelho). */
object ThemePrefs {
    const val DEFAULT_ACCENT = 0xFFB9FC00.toInt()
    private const val FILE = "estilo_audio_ui"
    private const val K_ACCENT = "accent"
    private const val K_DARK = "dark"
    private const val K_SPOTIFY = "spotify_layout"
    private const val K_COVER = "cover_base"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun accent(ctx: Context): Int = prefs(ctx).getInt(K_ACCENT, DEFAULT_ACCENT)

    fun dark(ctx: Context): Boolean = prefs(ctx).getBoolean(K_DARK, false)

    fun saveAccent(ctx: Context, color: Int) {
        prefs(ctx).edit().putInt(K_ACCENT, color or 0xFF000000.toInt()).apply()
    }

    fun saveDark(ctx: Context, dark: Boolean) {
        prefs(ctx).edit().putBoolean(K_DARK, dark).apply()
    }

    /** Layout Spotify: fundo preto com degradê e a cor da capa da música que está tocando. */
    fun spotify(ctx: Context): Boolean = prefs(ctx).getBoolean(K_SPOTIFY, false)

    fun saveSpotify(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_SPOTIFY, on).apply()
    }

    /** Última cor dominante de capa vista (0 = nenhuma ainda). */
    fun cover(ctx: Context): Int = prefs(ctx).getInt(K_COVER, 0)

    fun saveCover(ctx: Context, color: Int) {
        prefs(ctx).edit().putInt(K_COVER, color or 0xFF000000.toInt()).apply()
    }

    /** Cor-base do Layout Spotify: a última capa vista ou, se não houve nenhuma, a cor principal escolhida. */
    fun spotifyBase(ctx: Context): Int {
        val c = cover(ctx)
        return if (c == 0) accent(ctx) else c
    }

    /** Carrega as preferências e recalcula a paleta [C]. Deve rodar antes de montar a interface. */
    fun apply(ctx: Context) {
        if (spotify(ctx)) C.applySpotify(spotifyBase(ctx)) else C.apply(accent(ctx), dark(ctx))
    }

    fun toHex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)

    /** Aceita "B9FC00", "#b9fc00" ou "#fc0" (3 dígitos). Retorna null se o código for inválido. */
    fun parseHex(text: String): Int? {
        var t = text.trim().removePrefix("#").trim()
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (!Regex("[0-9a-fA-F]{6}").matches(t)) return null
        return (0xFF000000L or t.toLong(16)).toInt()
    }
}

/** Cores sugeridas no seletor de cor principal. */
val ACCENT_PALETTE: List<Int> = listOf(
    0xB9FC00, 0x76FF03, 0x00E676, 0x1DE9B6, 0x00E5FF, 0x40C4FF,
    0x2979FF, 0x448AFF, 0x536DFE, 0x7C4DFF, 0xB388FF, 0xE040FB,
    0xFF4081, 0xFF1744, 0xFF5252, 0xFF6E40, 0xFF9100, 0xFFC400,
    0xFFEB3B, 0xFFD54F, 0x69F0AE, 0x4DB6AC, 0x26C6DA, 0x42A5F5,
    0x5C6BC0, 0xAB47BC, 0xEC407A, 0xEF5350, 0xFF7043, 0xFFA726,
    0xA1887F, 0x90A4AE, 0x78909C, 0x8D6E63, 0x66BB6A, 0x8BC34A
).map { it or 0xFF000000.toInt() }
