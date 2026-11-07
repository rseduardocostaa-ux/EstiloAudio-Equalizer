package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Gráfico do equalizador paramétrico: curva de resposta sobre o espectro, com um ponto por banda.
 * Arraste um ponto para mudar frequência (horizontal) e ganho (vertical). Toque duplo num ponto zera o
 * ganho; toque duplo num espaço vazio adiciona uma banda ali.
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
    private fun dbAt(y: Float) = range - (y - gt()) / (gb() - gt()) * 2f * range
    private fun fAt(x: Float) = 20f * 1000f.pow(((x - gl()) / (gr() - gl())).coerceIn(0f, 1f))
    private fun snapDb(v: Float) =
        ((v * 10f).roundToInt() / 10f).coerceIn(-ParamBand.MAX_GAIN, ParamBand.MAX_GAIN)

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
            if (s.bypass) 0x599A9AA5 else withAlpha(C.SLATE, 0x99),
            withAlpha(C.SLATE, 0x08),
            Shader.TileMode.CLAMP
        )
        c.drawPath(area, fill)
        fill.shader = null

        line.strokeJoin = Paint.Join.ROUND
        line.strokeCap = Paint.Cap.ROUND
        line.color = lineCol
        line.strokeWidth = dpf(3f)
        c.drawPath(path, line)

        // pontos das bandas (o selecionado por último, por cima)
        val bands = s.bands
        if (bands.isEmpty() && !s.bypass) {
            text.textAlign = Paint.Align.CENTER
            text.textSize = spf(12f)
            text.color = C.SUB
            c.drawText("Toque duas vezes aqui para adicionar uma banda", w / 2f, yOf(0f) - dpf(14f), text)
        }
        for (pass in 0..1) {
            for (i in 0 until bands.size) {
                if ((i == selected) != (pass == 1)) continue
                drawPoint(c, i, s.bypass)
            }
        }

        // informação da banda selecionada, ou aviso de bypass
        text.textAlign = Paint.Align.LEFT
        text.textSize = spf(11f)
        text.typeface = Typeface.DEFAULT
        if (s.bypass) {
            text.textAlign = Paint.Align.RIGHT
            text.color = 0xFF7A7A85.toInt()
            text.isFakeBoldText = true
            c.drawText("BYPASS", r - dpf(2f), dpf(14f), text)
            text.isFakeBoldText = false
        } else if (selected in 0 until bands.size) {
            val bd = bands[selected]
            text.color = lineCol
            val info = fmtFreq(bd.freq) + "  " + fmtDb(bd.gain) + " dB  Q " + String.format(Locale.US, "%.2f", bd.q)
            c.drawText(info, l + dpf(2f), dpf(14f), text)
        }
    }

    private fun drawPoint(c: Canvas, i: Int, bypass: Boolean) {
        val bd = EqState.s.bands[i]
        val cx = plotX(bd.freq, gl(), gr())
        val cy = yOf(bd.gain.coerceIn(-range, range))
        val sel = i == selected
        val rad = if (sel) dpf(13f) else dpf(10f)

        if (sel && !bypass) {
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(C.PRI, 70)
            c.drawCircle(cx, cy, rad + dpf(6f), fill)
        }
        fill.style = Paint.Style.FILL
        fill.color = if (bypass) 0xFFBDBDC6.toInt() else C.PRI
        c.drawCircle(cx, cy, rad, fill)
        line.strokeJoin = Paint.Join.ROUND
        line.strokeWidth = if (sel) dpf(2.5f) else dpf(1.5f)
        line.color = if (bypass) 0xFF7A7A85.toInt() else (if (sel) C.INK else C.PRI_DK)
        c.drawCircle(cx, cy, rad, line)

        val rank = EqState.s.bands.count { it.freq < bd.freq } + 1
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = spf(if (sel) 11f else 9.5f)
        text.color = if (bypass) 0xFF4A4A55.toInt() else C.ON_PRI
        val fm = text.fontMetrics
        c.drawText(rank.toString(), cx, cy - (fm.ascent + fm.descent) / 2f, text)
        text.typeface = Typeface.DEFAULT
    }

    // ---------- toque ----------

    private var drag = -1
    private var offX = 0f
    private var offY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var ignore = false
    private var lastTapT = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private fun hit(x: Float, y: Float): Int {
        val bands = EqState.s.bands
        var best = -1
        var bestD = dpf(30f)
        for (i in 0 until bands.size) {
            val d = hypot(plotX(bands[i].freq, gl(), gr()) - x, yOf(bands[i].gain.coerceIn(-range, range)) - y)
            if (d < bestD || (d == bestD && i == selected)) {
                bestD = d
                best = i
            }
        }
        return best
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val bands = EqState.s.bands
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val h = hit(e.x, e.y)
                downX = e.x
                downY = e.y
                moved = false
                ignore = false
                drag = -1
                val now = SystemClock.uptimeMillis()
                val dbl = lastTapT != 0L && now - lastTapT < 300 &&
                    hypot(e.x - lastTapX, e.y - lastTapY) < dpf(36f)
                if (h >= 0) parent?.requestDisallowInterceptTouchEvent(true)
                if (dbl) {
                    lastTapT = 0L
                    ignore = true
                    if (h >= 0) {
                        selected = h
                        onSelect?.invoke(h)
                        if (bands[h].gain != 0f) {
                            bands[h].gain = 0f
                            onEdit?.invoke(h)
                        }
                    } else {
                        onAdd?.invoke(fAt(e.x).roundToInt().toFloat(), snapDb(dbAt(e.y)))
                    }
                } else if (h >= 0) {
                    drag = h
                    selected = h
                    offX = plotX(bands[h].freq, gl(), gr()) - e.x
                    offY = yOf(bands[h].gain.coerceIn(-range, range)) - e.y
                    onSelect?.invoke(h)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (drag in 0 until bands.size && !ignore) {
                    if (hypot(e.x - downX, e.y - downY) > dpf(5f)) moved = true
                    if (moved) {
                        val bd = bands[drag]
                        bd.freq = fAt(e.x + offX).roundToInt().toFloat()
                            .coerceIn(ParamBand.MIN_F, ParamBand.MAX_F)
                        bd.gain = snapDb(dbAt(e.y + offY))
                        invalidate()
                        onEdit?.invoke(drag)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (e.actionMasked == MotionEvent.ACTION_UP && !moved && !ignore) {
                    lastTapT = SystemClock.uptimeMillis()
                    lastTapX = e.x
                    lastTapY = e.y
                } else {
                    lastTapT = 0L
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                drag = -1
            }
        }
        return true
    }
}
