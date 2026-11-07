package com.estiloaudio.equalizer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exportar e importar tudo num arquivo .json: ajustes (equalizador, compressor, limiter, opções),
 * presets salvos, tema (cor e claro/escuro) e o ícone do cabeçalho.
 */
object Backup {
    private const val APP = "EstiloAudio"
    private const val VERSION = 1
    const val MAX_BYTES = 1_000_000

    private const val PRESETS_FILE = "presets"
    private const val PRESETS_KEY = "list"

    fun export(ctx: Context): String {
        EqState.ensure(ctx)
        val o = JSONObject()
        o.put("app", APP)
        o.put("version", VERSION)
        o.put("settings", JSONObject(EqState.s.toJson()))

        val raw = ctx.getSharedPreferences(PRESETS_FILE, Context.MODE_PRIVATE).getString(PRESETS_KEY, null)
        val presets = try { if (raw != null) JSONArray(raw) else JSONArray() } catch (_: Exception) { JSONArray() }
        o.put("presets", presets)

        val theme = JSONObject()
        theme.put("accent", ThemePrefs.toHex(ThemePrefs.accent(ctx)))
        theme.put("dark", ThemePrefs.dark(ctx))
        o.put("theme", theme)
        o.put("logo", LogoPrefs.load(ctx))
        return o.toString(2)
    }

    /** Aplica o backup. Retorna null se deu certo, ou a mensagem de erro. */
    fun import(ctx: Context, text: String): String? {
        val o = try { JSONObject(text) } catch (_: Exception) { return "O arquivo não é um backup válido." }
        if (o.optString("app") != APP) return "Este arquivo não é um backup do Estilo Áudio."
        val st = o.optJSONObject("settings") ?: return "O backup não tem os ajustes."

        EqState.ensure(ctx)
        try {
            EqState.update { it.load(st) }
        } catch (_: Exception) {
            return "Não foi possível ler os ajustes do backup."
        }

        // presets: os do backup entram e substituem os seus de mesmo nome; os outros ficam
        val incoming = o.optJSONArray("presets")
        if (incoming != null && incoming.length() > 0) {
            val prefs = ctx.getSharedPreferences(PRESETS_FILE, Context.MODE_PRIVATE)
            val cur = try {
                JSONArray(prefs.getString(PRESETS_KEY, null) ?: "[]")
            } catch (_: Exception) { JSONArray() }
            val names = HashSet<String>()
            for (i in 0 until incoming.length()) {
                incoming.optJSONObject(i)?.optString("name")?.let { if (it.isNotEmpty()) names.add(it.lowercase()) }
            }
            val merged = JSONArray()
            for (i in 0 until cur.length()) {
                val p = cur.optJSONObject(i) ?: continue
                if (p.optString("name").lowercase() !in names) merged.put(p)
            }
            for (i in 0 until incoming.length()) {
                val p = incoming.optJSONObject(i) ?: continue
                if (p.optString("name").isNotEmpty()) merged.put(p)
            }
            prefs.edit().putString(PRESETS_KEY, merged.toString()).apply()
        }

        val theme = o.optJSONObject("theme")
        if (theme != null) {
            ThemePrefs.parseHex(theme.optString("accent", ""))?.let { ThemePrefs.saveAccent(ctx, it) }
            if (theme.has("dark")) ThemePrefs.saveDark(ctx, theme.optBoolean("dark", false))
        }
        val logo = o.optString("logo", "")
        if (logo.isNotEmpty()) LogoPrefs.save(ctx, logo)
        return null
    }
}
