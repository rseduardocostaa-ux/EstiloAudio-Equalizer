package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.pow

/**
 * Gráfico do equalizador paramétrico: só a curva de resposta (sem pontos), desenhada sobre o espectro
 * do áudio que está tocando. Não recebe toques: as bandas são ajustadas pelos sliders e pelos knobs.
 */
class CurveView(ctx: Context) : View(ctx) {

    /** Banda selecionada (índice em EqState.s.bands) ou -1. */
    var selected = -1
        set(v) {
            field = v
            invalidate()
        }

    var onSelect: ((Int) -> Unit)? = null
    var onEdit: ((Int) -> Unit)? = null
    var onAdd: ((Float, Float) -> Unit)? = null

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val area = Path()

    private val gridFreqs = floatArrayOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)
    private val gridNames = arrayOf("50", "100", "200", "500", "1k", "2k", "5k", "10k")
    private val range = 15f

    private fun gl() = dpf(10f)
    private fun gr() = width - dpf(10f)
    private fun gt() = dpf(24f)
    private fun gb() = height - dpf(20f)
    private fun yOf(db: Float) = gt() + (gb() - gt()) * (range - db) / (2f * range)
    private fun fAt(x: Float) = 20f * 1000f.pow(((x - gl()) / (gr() - gl())).coerceIn(0f, 1f))
    override fun onDraw(c: Canvas) {
        val s = EqState.s
        val w = width.toFloat()
        val h = height.toFloat()
        val l = gl()
        val r = gr()
        val t = gt()
        val b = gb()
        val lineCol = if (s.bypass) 0xFF9A9AA5.toInt() else C.SLATE

        // grade horizontal (dB)
        line.strokeCap = Paint.Cap.BUTT
        text.typeface = Typeface.DEFAULT
        text.textSize = spf(9.5f)
        text.textAlign = Paint.Align.LEFT
        text.color = withAlpha(C.SUB, 170)
        for (db in intArrayOf(-12, -6, 0, 6, 12)) {
            val y = yOf(db.toFloat())
            line.color = C.GRID
            line.strokeWidth = if (db == 0) dpf(1.6f) else dpf(1f)
            c.drawLine(l, y, r, y, line)
            c.drawText(if (db > 0) "+$db" else "$db", l + dpf(2f), y - dpf(2f), text)
        }

        // grade vertical (frequência)
        text.textAlign = Paint.Align.CENTER
        text.textSize = spf(11f)
        text.color = lineCol
        line.strokeWidth = dpf(1f)
        line.color = C.GRID
        for (k in gridFreqs.indices) {
            val gx = plotX(gridFreqs[k], l, r)
            c.drawLine(gx, t - dpf(6f), gx, b + dpf(4f), line)
            c.drawText(gridNames[k], gx, h - dpf(5f), text)
        }

        // linha vertical fina na frequência da banda selecionada
        val sb = s.bands
        if (!s.bypass && selected in 0 until sb.size) {
            val sx = plotX(sb[selected].freq, l, r)
            line.color = withAlpha(C.PRI_DK, 150)
            line.strokeWidth = dpf(1.2f)
            line.pathEffect = DashPathEffect(floatArrayOf(dpf(4f), dpf(3f)), 0f)
            c.drawLine(sx, t - dpf(6f), sx, b, line)
            line.pathEffect = null
        }

        // curva
        path.reset()
        var px = l
        var first = true
        while (px <= r + 0.5f) {
            val f = fAt(px)
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
            if (s.bypass) 0x339A9AA5 else withAlpha(C.SLATE, 0x38),
            withAlpha(C.SLATE, 0x04),
            Shader.TileMode.CLAMP
        )
        c.drawPath(area, fill)
        fill.shader = null

        line.strokeJoin = Paint.Join.ROUND
        line.strokeCap = Paint.Cap.ROUND
        line.color = lineCol
        line.strokeWidth = dpf(2.5f)
        c.drawPath(path, line)

        // aviso de bypass
        if (s.bypass) {
            text.textAlign = Paint.Align.RIGHT
            text.textSize = spf(11f)
            text.typeface = Typeface.DEFAULT
            text.color = 0xFF7A7A85.toInt()
            text.isFakeBoldText = true
            c.drawText("BYPASS", r - dpf(2f), dpf(14f), text)
            text.isFakeBoldText = false
        }
    }
}
