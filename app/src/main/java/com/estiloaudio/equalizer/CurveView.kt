package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import java.util.Locale
import kotlin.math.pow

/** Curva de resposta (azul-acinzentada) sobre o espectro, com os valores das bandas no topo. */
class CurveView(ctx: Context) : View(ctx) {

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val area = Path()

    private val names = arrayOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

    override fun onDraw(c: Canvas) {
        val s = EqState.s
        val w = width.toFloat()
        val h = height.toFloat()
        val l = dpf(10f)
        val r = w - dpf(10f)
        val t = dpf(24f)
        val b = h - dpf(20f)
        val range = 15f
        val lineCol = if (s.bypass) 0xFF9A9AA5.toInt() else C.SLATE
        val grid = 0x384F5D70

        fun yOf(db: Float) = t + (b - t) * (range - db) / (2f * range)

        // grade horizontal
        line.strokeCap = Paint.Cap.BUTT
        line.color = grid
        for (db in intArrayOf(-12, -6, 0, 6, 12)) {
            val y = yOf(db.toFloat())
            line.strokeWidth = if (db == 0) dpf(1.6f) else dpf(1f)
            c.drawLine(l, y, r, y, line)
        }

        // linhas verticais por banda, frequência embaixo e valor em dB em cima
        text.textAlign = Paint.Align.CENTER
        text.typeface = android.graphics.Typeface.DEFAULT
        text.textSize = spf(11f)
        text.color = lineCol
        line.strokeWidth = dpf(1f)
        for (k in 0 until 10) {
            val gx = plotX(EqMath.CENTERS[k], l, r)
            line.color = grid
            c.drawLine(gx, t - dpf(6f), gx, b + dpf(4f), line)
            c.drawText(names[k], gx, h - dpf(5f), text)
            if (!s.bypass) c.drawText(String.format(Locale.US, "%.1f", s.bands[k]), gx, dpf(14f), text)
        }

        // curva
        path.reset()
        var px = l
        var first = true
        while (px <= r + 0.5f) {
            val f = 20f * 1000f.pow((px - l) / (r - l))
            val db = EqMath.responseDb(s, f).coerceIn(-range, range)
            val y = yOf(db)
            if (first) {
                path.moveTo(px, y)
                first = false
            } else {
                path.lineTo(px, y)
            }
            px += dpf(2f)
        }

        // preenchimento degradê sob a curva
        area.set(path)
        area.lineTo(r, b)
        area.lineTo(l, b)
        area.close()
        fill.shader = LinearGradient(
            0f, t, 0f, b,
            if (s.bypass) 0x599A9AA5 else 0x994F5D70.toInt(),
            0x084F5D70,
            Shader.TileMode.CLAMP
        )
        c.drawPath(area, fill)
        fill.shader = null

        line.strokeJoin = Paint.Join.ROUND
        line.strokeCap = Paint.Cap.ROUND
        line.color = lineCol
        line.strokeWidth = dpf(3f)
        c.drawPath(path, line)

        if (!s.bypass) {
            for (f in EqMath.CENTERS) {
                val y = yOf(EqMath.responseDb(s, f).coerceIn(-range, range))
                fill.color = 0xFFFFFFFF.toInt()
                c.drawCircle(plotX(f, l, r), y, dpf(3.5f), fill)
                line.strokeWidth = dpf(2f)
                c.drawCircle(plotX(f, l, r), y, dpf(3.5f), line)
            }
        } else {
            text.textAlign = Paint.Align.RIGHT
            text.color = 0xFF7A7A85.toInt()
            text.isFakeBoldText = true
            text.textSize = spf(11f)
            c.drawText("BYPASS", r - dpf(2f), dpf(14f), text)
            text.isFakeBoldText = false
        }
    }
}
