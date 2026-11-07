package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Ícones da barra inferior: 0 = barras (equalizador), 1 = knob (compressor), 2 = linhas (ajustes).
 * A aba ativa ganha uma pílula verde-clara atrás do ícone (64 x 38 dp), como no Material 3.
 */
class TabIcon(ctx: Context, private val kind: Int) : View(ctx) {

    var active = false
        set(v) {
            field = v
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dpi(64f), dpi(38f))
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        if (active) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            p.style = Paint.Style.FILL
            p.color = C.CARD2
            c.drawRoundRect(rect, dpf(16f), dpf(16f), p)
        }

        val col = if (active) C.PRI_DK else C.TABOFF
        p.style = Paint.Style.FILL
        p.color = col
        when (kind) {
            0 -> {
                val bw = dpf(5f)
                val gap = dpf(3f)
                val heights = floatArrayOf(10f, 20f, 14f)
                val total = 3 * bw + 2 * gap
                val x0 = cx - total / 2f
                val base = cy + dpf(10f)
                for (i in 0 until 3) {
                    val x = x0 + i * (bw + gap)
                    rect.set(x, base - dpf(heights[i]), x + bw, base)
                    c.drawRoundRect(rect, dpf(1.5f), dpf(1.5f), p)
                }
            }
            1 -> {
                c.drawCircle(cx, cy, dpf(11f), p)
                p.color = if (active) C.CARD2 else C.CARD
                p.style = Paint.Style.STROKE
                p.strokeWidth = dpf(2.2f)
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx - dpf(3f), cy - dpf(2f), cx - dpf(6.5f), cy - dpf(4f), p)
            }
            else -> {
                val lw = dpf(24f)
                for (i in -1..1) {
                    val y = cy + i * dpf(7f)
                    rect.set(cx - lw / 2f, y - dpf(2f), cx + lw / 2f, y + dpf(2f))
                    c.drawRoundRect(rect, dpf(2f), dpf(2f), p)
                }
            }
        }
    }
}
