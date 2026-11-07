package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Espectro do áudio que está tocando (atrás da curva do EQ), em dois estilos:
 * curva (área suave e preenchida) ou blocos (colunas de blocos, como o medidor de ganho).
 * Sobe rápido e cai devagar, para não ficar piscando. A curva do EQ não depende disto.
 */
class SpectrumView(ctx: Context) : View(ctx) {

    private val n = EqState.BARS
    private val disp = FloatArray(n)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val top = Path()
    private val area = Path()
    private val xs = FloatArray(n)
    private val ys = FloatArray(n)
    private var live = false

    /** true = colunas de blocos; false = curva suave. */
    var blocks = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    private val bn = 24                       // colunas no modo blocos
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fill.shader = LinearGradient(
            0f, h - dpf(20f), 0f, dpf(24f),
            withAlpha(C.SPEC, 0x10), withAlpha(C.SPEC, 0x7A),
            Shader.TileMode.CLAMP
        )
    }

    fun feed(src: FloatArray, live: Boolean) {
        this.live = live
        for (i in 0 until n) {
            val v = if (live && i < src.size) src[i] else 0f
            // sobe suave e cai devagar
            disp[i] = if (v > disp[i]) disp[i] + (v - disp[i]) * 0.25f else disp[i] * 0.94f
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val l = dpf(10f)
        val r = w - dpf(10f)
        val topY = dpf(24f)
        val base = h - dpf(20f)
        val span = base - topY

        var any = false
        for (i in 0 until n) {
            val f0 = 31.25f * 2f.pow(9f * i / n)
            val f1 = 31.25f * 2f.pow(9f * (i + 1) / n)
            xs[i] = plotX(sqrt(f0 * f1), l, r)
            ys[i] = base - disp[i] * span
            if (disp[i] > 0.02f) any = true
        }

        if (any && blocks) {
            drawBlocks(c, l, r, span, base)
        } else if (any) {
            // linha suave passando pelos pontos (curvas entre os pontos médios)
            top.reset()
            top.moveTo(l, ys[0])
            top.lineTo(xs[0], ys[0])
            for (i in 1 until n) {
                val mx = (xs[i - 1] + xs[i]) / 2f
                val my = (ys[i - 1] + ys[i]) / 2f
                top.quadTo(xs[i - 1], ys[i - 1], mx, my)
            }
            top.lineTo(xs[n - 1], ys[n - 1])
            top.lineTo(r, ys[n - 1])

            area.set(top)
            area.lineTo(r, base)
            area.lineTo(l, base)
            area.close()
            c.drawPath(area, fill)

            edge.color = withAlpha(C.SPEC, 0xCC)
            edge.strokeWidth = dpf(1.5f)
            c.drawPath(top, edge)
        }

        if (!any && !live) {
            text.textAlign = Paint.Align.CENTER
            text.textSize = spf(11.5f)
            text.color = C.SUB
            c.drawText("Aguardando o áudio do Spotify…", w / 2f, base - dpf(8f), text)
        }
    }

    /** Colunas de blocos alinhadas à escala de frequência do gráfico. */
    private fun drawBlocks(c: Canvas, l: Float, r: Float, span: Float, base: Float) {
        val segH = dpf(4f)
        val gap = dpf(1.5f)
        val step = segH + gap
        val total = (span / step).toInt().coerceAtLeast(1)
        val colGap = dpf(1.5f)
        val radius = dpf(1.5f)
        for (b in 0 until bn) {
            // média das barras do espectro que caem nesta faixa de frequência
            val a0 = b * n.toFloat() / bn
            val a1 = (b + 1) * n.toFloat() / bn
            var sum = 0f
            var wsum = 0f
            var i = a0.toInt()
            while (i < n && i.toFloat() < a1) {
                val ov = min(a1, i + 1f) - max(a0, i.toFloat())
                if (ov > 0f) {
                    sum += disp[i] * ov
                    wsum += ov
                }
                i++
            }
            val v = if (wsum > 0f) sum / wsum else 0f
            val lit = (v * total + 0.4f).toInt().coerceIn(0, total)
            if (lit == 0) continue

            val f0 = 31.25f * 2f.pow(9f * b / bn)
            val f1 = 31.25f * 2f.pow(9f * (b + 1) / bn)
            val x0 = plotX(f0, l, r) + colGap / 2f
            val x1 = plotX(f1, l, r) - colGap / 2f
            for (k in 0 until lit) {
                val yb = base - k * step
                rect.set(x0, yb - segH, x1, yb)
                val t = k / total.toFloat()
                // grave muito forte: o topo da coluna esquenta (cor do app, depois laranja, depois vermelho)
                val col = when {
                    t < 0.62f -> C.SPEC
                    t < 0.84f -> mixColor(C.SPEC, C.ORANGE, (t - 0.62f) / 0.22f)
                    else -> mixColor(C.ORANGE, C.RED, (t - 0.84f) / 0.16f)
                }
                seg.color = withAlpha(col, 0x55 + (0x99 * t).toInt())
                c.drawRoundRect(rect, radius, radius, seg)
            }
        }
    }
}
