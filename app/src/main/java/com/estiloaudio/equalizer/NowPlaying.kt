package com.estiloaudio.equalizer

import android.graphics.Bitmap
import android.graphics.Color
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import kotlin.math.abs

/**
 * Música que está tocando no Spotify. É preenchida pelo [MediaListener] e lida pela tela.
 * Tudo roda na thread principal.
 */
object NowPlaying {
    const val SPOTIFY = "com.spotify.music"

    /** O serviço de leitura de notificações está ativo (acesso liberado e conectado). */
    var connected = false
    var hasSession = false
    var title = ""
    var artist = ""
    var cover: Bitmap? = null
    var playing = false
    var durationMs = 0L
    var controller: MediaController? = null

    /** Quando a tela foi refeita por mudança de cor da capa (evita refazer sem parar). */
    var lastRecreate = 0L

    private var posMs = 0L
    private var posTime = 0L
    private var speed = 1f

    private val listeners = ArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun notifyChanged() {
        for (l in ArrayList(listeners)) {
            try {
                l()
            } catch (e: Throwable) {
                CrashLog.report(e)
            }
        }
    }

    fun setState(s: PlaybackState?) {
        if (s == null) {
            playing = false
            return
        }
        playing = s.state == PlaybackState.STATE_PLAYING || s.state == PlaybackState.STATE_BUFFERING
        posMs = s.position.coerceAtLeast(0L)
        posTime = s.lastPositionUpdateTime
        speed = s.playbackSpeed
    }

    fun positionMs(): Long {
        var p = posMs
        if (playing && posTime > 0L) {
            p += ((SystemClock.elapsedRealtime() - posTime) * speed).toLong()
        }
        if (durationMs > 0L) p = p.coerceAtMost(durationMs)
        return p.coerceAtLeast(0L)
    }

    /** Progresso da faixa, de 0 a 1 (0 se a duração é desconhecida). */
    fun fraction(): Float =
        if (durationMs > 0L) (positionMs().toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

/** Descobre a cor dominante da capa (sem bibliotecas: histograma de matiz numa miniatura). */
object CoverColor {

    /** Devolve a cor viva mais presente da capa. Capa sem cor (preto e branco) devolve um cinza. 0 = falhou. */
    fun extract(src: Bitmap): Int {
        val small: Bitmap = try {
            val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
            Bitmap.createScaledBitmap(soft, 40, 40, true)
        } catch (_: Exception) {
            return 0
        }
        val w = small.width
        val h = small.height
        val n = w * h
        if (n <= 0) return 0
        val px = IntArray(n)
        small.getPixels(px, 0, w, 0, 0, w, h)

        val bins = 36
        val wSum = FloatArray(bins)
        val rSum = FloatArray(bins)
        val gSum = FloatArray(bins)
        val bSum = FloatArray(bins)
        var valueSum = 0f
        var vivid = 0f
        val hsv = FloatArray(3)
        for (c in px) {
            if (Color.alpha(c) < 128) continue
            Color.colorToHSV(c, hsv)
            valueSum += hsv[2]
            if (hsv[2] < 0.18f || hsv[1] < 0.22f) continue   // ignora quase preto e quase cinza
            val wt = hsv[1] * hsv[2]
            val b = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
            wSum[b] += wt
            rSum[b] += Color.red(c) * wt
            gSum[b] += Color.green(c) * wt
            bSum[b] += Color.blue(c) * wt
            vivid += wt
        }

        if (vivid < n * 0.04f) {
            val g = ((valueSum / n).coerceIn(0.35f, 0.9f) * 255f).toInt() and 0xF8
            return Color.rgb(g, g, g)
        }

        var best = 0
        var bestScore = -1f
        for (i in 0 until bins) {
            val score = wSum[i] + 0.5f * (wSum[(i + bins - 1) % bins] + wSum[(i + 1) % bins])
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        var wt = 0f
        var r = 0f
        var g = 0f
        var b = 0f
        for (k in -1..1) {
            val i = (best + k + bins) % bins
            wt += wSum[i]
            r += rSum[i]
            g += gSum[i]
            b += bSum[i]
        }
        if (wt <= 0f) return 0
        // arredonda para evitar mudanças minúsculas de cor entre uma leitura e outra da mesma capa
        return Color.rgb(((r / wt).toInt()) and 0xF8, ((g / wt).toInt()) and 0xF8, ((b / wt).toInt()) and 0xF8)
    }

    /** true se as duas cores são praticamente a mesma. */
    fun similar(a: Int, b: Int): Boolean =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b)) < 40
}
