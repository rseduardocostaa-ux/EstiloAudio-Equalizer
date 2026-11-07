package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Slider vertical de -12 a +12 dB: trilho verde-claro, linha colorida do zero até a alça
 * (muda de cor com o valor) e alça redonda roxa. Toque duplo zera.
 */
class LedSlider(ctx: Context, label0: String, private val highlight: Boolean = false) : View(ctx) {

    var label: String = label0
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    private val lo = -12f
    private val hi = 12f
    private val step = 0.5f

    var value = 0f
        set(v) {
            field = v.coerceIn(lo, hi)
            invalidate()
        }

    var onChange: ((Float) -> Unit)? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var downMoved = false
    private var lastUp = 0L
    private var startY = 0f
    private var ignore = false

    private fun handleH() = dpf(34f)
    private fun trackTop() = dpf(6f)
    private fun trackBottom() = height - dpf(46f)
    private fun topI() = trackTop() + handleH() / 2f
    private fun botI() = trackBottom() - handleH() / 2f
    private fun yOf(v: Float) = topI() + (hi - v) / (hi - lo) * (botI() - topI())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        // trilho
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = dpf(4f)
        p.color = C.TRACK
        c.drawLine(cx, trackTop() + dpf(2f), cx, trackBottom() - dpf(2f), p)

        // marcas a cada 3 dB, cruzando o trilho
        p.strokeCap = Paint.Cap.BUTT
        p.strokeWidth = dpf(1f)
        for (db in -12..12 step 3) {
            val y = yOf(db.toFloat())
            val half = minOf(if (db == 0) dpf(10f) else dpf(7f), w / 2f - dpf(0.5f))
            p.color = if (db == 0) C.ZERO else C.TICK
            c.drawLine(cx - half, y, cx + half, y, p)
        }

        // linha colorida do zero até a alça, com brilho
        if (abs(value) > 0.01f) {
            val col = dbColor(value)
            val y0 = yOf(0f)
            val y1 = yOf(value)
            p.strokeCap = Paint.Cap.ROUND
            p.color = withAlpha(col, 70)
            p.strokeWidth = dpf(10f)
            c.drawLine(cx, y0, cx, y1, p)
            p.color = col
            p.strokeWidth = dpf(4f)
            c.drawLine(cx, y0, cx, y1, p)
        }

        // alça redonda verde-limão (com contorno verde-escuro), com sombra suave
        val hy = yOf(value)
        val r = minOf(dpf(11f), w / 2f - dpf(1f))
        p.style = Paint.Style.FILL
        p.color = 0x14000000
        c.drawCircle(cx, hy + dpf(1f), r + dpf(3f), p)
        p.color = 0x1E000000
        c.drawCircle(cx, hy + dpf(1f), r + dpf(2f), p)
        p.color = 0x2E000000
        c.drawCircle(cx, hy + dpf(1f), r + dpf(1f), p)
        p.color = C.PRI
        c.drawCircle(cx, hy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(1.5f)
        p.color = C.PRI_DK
        c.drawCircle(cx, hy, r - dpf(0.75f), p)
        p.style = Paint.Style.FILL

        // textos
        tp.typeface = FONT_MEDIUM
        val narrow = w < dpf(30f)
        tp.textSize = spf(if (narrow) 9f else if (highlight) 11f else 12f)
        tp.color = C.INK
        c.drawText(label, cx, h - dpf(26f), tp)
        tp.typeface = android.graphics.Typeface.DEFAULT
        tp.textSize = spf(if (narrow) 8.5f else 11f)
        tp.color = C.SUB
        c.drawText(if (narrow) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.1f", value), cx, h - dpf(9f), tp)
    }

    private fun setFromY(y: Float) {
        var v = hi - (y - topI()) / (botI() - topI()) * (hi - lo)
        v = (v / step).roundToInt() * step
        v = v.coerceIn(lo, hi)
        if (v != value) {
            value = v
            onChange?.invoke(v)
        }
    }

    private fun reset() {
        if (value != 0f) {
            value = 0f
            onChange?.invoke(0f)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                startY = e.y
                downMoved = false
                val now = SystemClock.uptimeMillis()
                if (lastUp != 0L && now - lastUp < 300) {
                    reset()
                    ignore = true
                    lastUp = 0L
                } else {
                    ignore = false
                    setFromY(e.y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!ignore) {
                    if (abs(e.y - startY) > dpf(6f)) downMoved = true
                    setFromY(e.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastUp = if (!downMoved && !ignore) SystemClock.uptimeMillis() else 0L
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
