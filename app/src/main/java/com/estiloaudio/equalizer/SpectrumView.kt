package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import kotlin.math.max
import kotlin.math.pow

/** Barras do espectro em tempo real (roxo translúcido), alinhadas com a curva. */
class SpectrumView(ctx: Context) : View(ctx) {

    private val n = EqState.BARS
    private val disp = FloatArray(n)
    private val peak = FloatArray(n)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pk = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private var live = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bar.shader = LinearGradient(
            0f, h - dpf(20f), 0f, dpf(24f),
            0x0D8DB600, 0x668DB600,
            Shader.TileMode.CLAMP
        )
    }

    fun feed(src: FloatArray, live: Boolean) {
        this.live = live
        for (i in 0 until n) {
            val v = if (live && i < src.size) src[i] else 0f
            disp[i] = if (v > disp[i]) v else disp[i] * 0.86f
            peak[i] = max(v, peak[i] - 0.015f)
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val l = dpf(10f)
        val r = w - dpf(10f)
        val top = dpf(24f)
        val base = h - dpf(20f)
        val span = base - top

        var any = false
        pk.color = 0x994A6600.toInt()
        for (i in 0 until n) {
            val f0 = 31.25f * 2f.pow(9f * i / n)
            val f1 = 31.25f * 2f.pow(9f * (i + 1) / n)
            val x0 = plotX(f0, l, r) + dpf(1f)
            val x1 = plotX(f1, l, r) - dpf(1f)
            val v = disp[i]
            if (v > 0.02f) any = true
            val bh = max(dpf(2f), v * span)
            c.drawRect(x0, base - bh, x1, base, bar)
            if (peak[i] > 0.03f) {
                val py = base - peak[i] * span
                c.drawRect(x0, py - dpf(1.5f), x1, py, pk)
            }
        }

        if (!any && !live) {
            text.textAlign = Paint.Align.CENTER
            text.textSize = spf(11.5f)
            text.color = C.SUB
            c.drawText("Aguardando o áudio do Spotify…", w / 2f, top + span * 0.4f, text)
        }
    }
}
