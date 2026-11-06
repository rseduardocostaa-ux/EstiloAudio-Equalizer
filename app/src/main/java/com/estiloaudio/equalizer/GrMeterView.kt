package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import java.util.Locale
import kotlin.math.min

/** Medidor de redução de ganho (estimado), 0 a 24 dB, em LEDs. */
class GrMeterView(ctx: Context) : View(ctx) {

    var value = 0f
        set(v) {
            field = v
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(dpi(200f), widthMeasureSpec), dpi(52f))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()

        tp.textSize = spf(11f)
        tp.typeface = FONT_MEDIUM
        tp.color = C.SUB
        tp.textAlign = Paint.Align.LEFT
        c.drawText("REDUÇÃO DE GANHO", 0f, dpf(12f), tp)

        tp.textAlign = Paint.Align.RIGHT
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.color = C.INK
        tp.textSize = spf(12f)
        c.drawText(String.format(Locale.US, "-%.1f dB", min(value, 99f)), w, dpf(12f), tp)

        val n = 24
        val gap = dpf(2f)
        val sw = (w - gap * (n - 1)) / n
        val top = dpf(22f)
        val bottom = dpf(46f)
        val lit = value.coerceIn(0f, n.toFloat())
        for (i in 0 until n) {
            val x = i * (sw + gap)
            p.color = when {
                lit <= i + 0.25f -> C.LEDOFF
                i >= 16 -> C.HOT
                i >= 9 -> C.WARN
                else -> C.ACC
            }
            c.drawRoundRect(x, top, x + sw, bottom, dpf(3f), dpf(3f), p)
        }
    }
}
