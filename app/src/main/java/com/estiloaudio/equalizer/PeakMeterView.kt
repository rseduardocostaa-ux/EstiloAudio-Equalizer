package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import java.util.Locale
import kotlin.math.max

/**
 * Medidor de pico de saída (estimado) com indicador de clipping.
 * Escala de -60 a +6 dBFS. O pico cai devagar e o marcador de pico fica parado por um instante.
 * Estado: 0 = normal, 1 = limiter atuando, 2 = clipping.
 */
class PeakMeterView(ctx: Context) : View(ctx) {
    companion object {
        const val MIN_DB = -60f
        const val MAX_DB = 6f
        private const val FALL_DB_PER_S = 30f
        private const val HOLD_MS = 1200L
        private const val CLIP_HOLD_MS = 1500L
    }

    private var shown = MIN_DB
    private var hold = MIN_DB
    private var holdUntil = 0L
    private var clipUntil = 0L
    private var limUntil = 0L
    private var lastT = 0L

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    /** Chamado pelo ticker da tela (a cada ~33 ms) com o pico estimado e o estado atual. */
    fun feed(db: Float, state: Int, live: Boolean) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastT == 0L) 33f else (now - lastT).coerceIn(1L, 200L).toFloat()
        lastT = now
        val v = if (live) db.coerceIn(MIN_DB, MAX_DB) else MIN_DB
        shown = if (v >= shown) v else max(v, shown - FALL_DB_PER_S * dt / 1000f)
        if (shown >= hold) {
            hold = shown
            holdUntil = now + HOLD_MS
        } else if (now > holdUntil) {
            hold = max(shown, hold - FALL_DB_PER_S * dt / 1000f)
        }
        if (live && state == 2) clipUntil = now + CLIP_HOLD_MS
        if (live && state == 1) limUntil = now + CLIP_HOLD_MS
        invalidate()
    }

    private fun xOf(db: Float, l: Float, w: Float) =
        l + w * ((db.coerceIn(MIN_DB, MAX_DB) - MIN_DB) / (MAX_DB - MIN_DB))

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val clip = now < clipUntil
        val lim = !clip && now < limUntil

        val textSize = spf(11.5f)
        p.typeface = FONT_MEDIUM
        p.textSize = textSize

        // rótulo (esquerda) e valor / aviso (direita)
        p.style = Paint.Style.FILL
        p.color = C.SUB
        p.textAlign = Paint.Align.LEFT
        val baseY = textSize + dpf(2f)
        c.drawText("Pico de saída (estimado)", 0f, baseY, p)

        p.textAlign = Paint.Align.RIGHT
        val txt: String
        val tc: Int
        when {
            clip -> { txt = "CLIPPING"; tc = C.RED }
            lim -> { txt = "LIMITANDO"; tc = C.ORANGE }
            shown <= MIN_DB + 0.5f -> { txt = "-∞ dB"; tc = C.SUB }
            else -> { txt = String.format(Locale.US, "%.1f dB", hold); tc = C.INK }
        }
        p.color = tc
        c.drawText(txt, width.toFloat(), baseY, p)

        // barra
        val top = baseY + dpf(8f)
        val h = height - top - dpf(2f)
        val l = 0f
        val w = width.toFloat()
        val rad = h / 2f
        p.color = C.TRACK
        r.set(l, top, l + w, top + h)
        c.drawRoundRect(r, rad, rad, p)

        val fillX = xOf(shown, l, w)
        if (fillX > l + 1f) {
            c.save()
            c.clipRect(l, top, fillX, top + h)
            // segmentos coloridos por faixa de nível
            val segs = floatArrayOf(MIN_DB, -18f, -6f, 0f, MAX_DB)
            val cols = intArrayOf(C.ACC, C.YELLOW, C.ORANGE, C.RED)
            for (i in 0 until 4) {
                p.color = cols[i]
                r.set(xOf(segs[i], l, w), top, xOf(segs[i + 1], l, w) + 1f, top + h)
                c.drawRect(r, p)
            }
            c.restore()
        }

        // marcador de pico
        if (hold > MIN_DB + 0.5f) {
            val hx = xOf(hold, l, w)
            p.color = if (hold >= 0f) C.RED else C.INK
            r.set(hx - dpf(1.5f), top, hx + dpf(1.5f), top + h)
            c.drawRect(r, p)
        }

        // marca do 0 dB
        val zx = xOf(0f, l, w)
        p.color = C.ZERO
        r.set(zx - dpf(0.5f), top - dpf(2f), zx + dpf(0.5f), top + h + dpf(2f))
        c.drawRect(r, p)

        // luz de clipping na ponta direita
        if (clip) {
            p.color = C.RED
            val cx = w - dpf(6f)
            c.drawCircle(cx, top + h / 2f, h / 2f + dpf(1f), p)
        }
    }
}
