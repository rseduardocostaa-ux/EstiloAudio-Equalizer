package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import java.util.Locale
import kotlin.math.abs

/**
 * Curva do compressor: nível de entrada (horizontal) x nível de saída (vertical), de -60 a 0 dB.
 * Mostra a linha 1:1, a curva com o threshold e o ratio (joelho de 3 dB, como no motor de áudio)
 * e um ponto com o nível do áudio tocando agora. Não inclui o ganho de saída (Gain/Volume).
 */
class CompCurveView(ctx: Context) : View(ctx) {
    companion object {
        const val MIN_DB = -60f
        const val MAX_DB = 0f
        const val KNEE = 3f

        /** Saída (dB) para uma entrada (dB), com joelho suave. */
        fun outDb(x: Float, thr: Float, ratio: Float): Float {
            if (ratio <= 1.01f) return x
            val d = x - thr
            return when {
                2f * d < -KNEE -> x
                2f * abs(d) <= KNEE -> {
                    val t = d + KNEE / 2f
                    x + (1f / ratio - 1f) * t * t / (2f * KNEE)
                }
                else -> thr + d / ratio
            }
        }
    }

    /** -1 = compressor de banda larga; 0..2 = faixa do compressor em 3 faixas. */
    var band = -1

    private var liveDb = -90f
    private var live = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()

    fun feed(inDb: Float, isLive: Boolean) {
        liveDb = inDb
        live = isLive
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = EqState.s
        val bd = band
        val thrV = if (bd >= 0) s.mbThr[bd] else s.threshold
        val ratioV = if (bd >= 0) s.mbRatio[bd] else s.ratio
        val padL = dpf(32f)
        val padR = dpf(10f)
        val padT = dpf(10f)
        val padB = dpf(20f)
        val l = padL
        val r = width - padR
        val t = padT
        val b = height - padB
        if (r <= l || b <= t) return

        fun xOf(db: Float) = l + (r - l) * (db.coerceIn(MIN_DB, MAX_DB) - MIN_DB) / (MAX_DB - MIN_DB)
        fun yOf(db: Float) = b - (b - t) * (db.coerceIn(MIN_DB, MAX_DB) - MIN_DB) / (MAX_DB - MIN_DB)

        // fundo
        p.style = Paint.Style.FILL
        p.color = C.PANEL
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(box, dpf(14f), dpf(14f), p)

        // grade e rótulos
        p.typeface = FONT_MEDIUM
        p.textSize = spf(9.5f)
        p.strokeWidth = dpf(1f)
        var v = MIN_DB
        while (v <= MAX_DB + 0.1f) {
            p.style = Paint.Style.STROKE
            p.color = C.GRID
            c.drawLine(xOf(v), t, xOf(v), b, p)
            c.drawLine(l, yOf(v), r, yOf(v), p)
            p.style = Paint.Style.FILL
            p.color = C.SUB
            p.textAlign = Paint.Align.CENTER
            c.drawText(String.format(Locale.US, "%.0f", v), xOf(v), b + dpf(13f), p)
            p.textAlign = Paint.Align.RIGHT
            c.drawText(String.format(Locale.US, "%.0f", v), l - dpf(4f), yOf(v) + dpf(3.5f), p)
            v += 20f
        }

        // linha 1:1 (sem compressão)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(1.2f)
        p.color = C.ZERO
        p.pathEffect = DashPathEffect(floatArrayOf(dpf(4f), dpf(4f)), 0f)
        c.drawLine(xOf(MIN_DB), yOf(MIN_DB), xOf(MAX_DB), yOf(MAX_DB), p)

        // threshold
        val thr = thrV.coerceIn(MIN_DB, MAX_DB)
        val active = ratioV > 1.01f
        p.color = C.ORANGE
        c.drawLine(xOf(thr), t, xOf(thr), b, p)
        p.pathEffect = null

        // curva
        path.reset()
        var x = MIN_DB
        var first = true
        while (x <= MAX_DB + 0.01f) {
            val px = xOf(x)
            val py = yOf(outDb(x, thrV, ratioV))
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
            x += 0.5f
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(3f)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = if (active) C.SLATE else C.ZERO
        c.drawPath(path, p)

        // ponto do nível atual
        if (bd < 0 && live && liveDb > MIN_DB) {
            val px = xOf(liveDb)
            val py = yOf(outDb(liveDb, thrV, ratioV))
            p.style = Paint.Style.FILL
            p.color = C.PRI
            c.drawCircle(px, py, dpf(6f), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dpf(1.5f)
            p.color = C.PRI_DK
            c.drawCircle(px, py, dpf(6f), p)
        }

        // textos: threshold e ratio
        p.style = Paint.Style.FILL
        p.textSize = spf(11f)
        p.textAlign = Paint.Align.LEFT
        p.color = C.ORANGE
        val thrTxt = String.format(Locale.US, "Threshold %.0f dB", thrV)
        // coloca o texto do lado onde há espaço
        if (xOf(thr) < (l + r) / 2f) {
            c.drawText(thrTxt, xOf(thr) + dpf(5f), t + dpf(12f), p)
        } else {
            p.textAlign = Paint.Align.RIGHT
            c.drawText(thrTxt, xOf(thr) - dpf(5f), t + dpf(12f), p)
        }
        p.textAlign = Paint.Align.LEFT
        p.color = C.INK
        val ratioTxt = if (active) String.format(Locale.US, "Ratio %.1f:1", ratioV) else "Ratio: desligado"
        c.drawText(ratioTxt, l + dpf(6f), b - dpf(8f), p)
    }
}
