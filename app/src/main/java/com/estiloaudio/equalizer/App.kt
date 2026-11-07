package com.estiloaudio.equalizer

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

/**
 * Guarda o último erro do app (o que fechou sozinho ou um erro tratado em segundo plano),
 * para aparecer em Ajustes e poder ser copiado.
 */
object CrashLog {
    private const val FILE = "crash_log"
    private const val KEY = "last"
    private var app: Context? = null

    fun install(ctx: Context) {
        app = ctx.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            save("FECHOU (thread ${t.name})", e, true)
            old?.uncaughtException(t, e)
        }
    }

    /** Registra um erro que foi tratado (o app não fechou). */
    fun report(e: Throwable) {
        save("tratado", e, false)
    }

    private fun save(kind: String, e: Throwable, sync: Boolean) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stamp = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
            val text = ("$stamp • $kind\n" + sw.toString()).take(5000)
            val ed = app?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.edit() ?: return
            ed.putString(KEY, text)
            if (sync) ed.commit() else ed.apply()
        } catch (_: Throwable) { }
    }

    fun last(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
