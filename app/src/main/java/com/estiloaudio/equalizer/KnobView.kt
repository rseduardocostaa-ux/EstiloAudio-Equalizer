package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Knob claro com arco roxo e brilho suave. Arraste para cima/baixo; toque duplo restaura.
 * dyn = o arco muda de cor com o valor (Bass/Treble). chip = versão grande do equalizador,
 * com o nome e o valor numa etiqueta no canto em vez de embaixo.
 */
class KnobView(
    ctx: Context,
    private val label: String,
    private val lo: Float,
    private val hi: Float,
    private val def: Float,
    private val bipolar: Boolean,
    private val step: Float,
    private val dyn: Boolean,
    private val chip: Boolean,
    private val fmt: (Float) -> String
) : View(ctx) {

    var value = def
        set(v) {
            field = v.coerceIn(lo, hi)
            invalidate()
        }

    var onChange: ((Float) -> Unit)? = null

    private val ap = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private var startY = 0f
    private var startValue = 0f
    private var moved = false
    private var lastUp = 0L
    private var ignore = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(dpi(88f), widthMeasureSpec), if (chip) dpi(98f) else dpi(122f))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val size = min(w, if (chip) dpf(98f) else dpf(82f))
        val k = size / 82f                       // 1 unidade do desenho original (viewBox 82)
        val cx = w / 2f
        val cy = if (chip) size / 2f else dpf(4f) + size / 2f
        val col = if (dyn) dbColor(value) else C.PRI
        rect.set(cx - 37f * k, cy - 37f * k, cx + 37f * k, cy + 37f * k)

        // trilho discreto
        ap.style = Paint.Style.STROKE
        ap.strokeCap = Paint.Cap.ROUND
        ap.strokeWidth = 2f * k
        ap.color = C.TRACK
        c.drawArc(rect, 135f, 270f, false, ap)

        // arco do valor, com brilho
        val frac = if (hi > lo) (value - lo) / (hi - lo) else 0f
        val start: Float
        val sweep: Float
        if (bipolar) {
            start = 270f
            sweep = (frac - 0.5f) * 270f
        } else {
            start = 135f
            sweep = frac * 270f
        }
        if (abs(sweep) > 0.5f) {
            ap.strokeWidth = 9f * k
            ap.color = withAlpha(col, 46)
            c.drawArc(rect, start, sweep, false, ap)
            ap.strokeWidth = 4.5f * k
            ap.color = col
            c.drawArc(rect, start, sweep, false, ap)
        }

        // corpo lavanda
        ap.style = Paint.Style.FILL
        ap.color = C.CARD2
        c.drawCircle(cx, cy, 31f * k, ap)
        ap.style = Paint.Style.STROKE
        ap.strokeWidth = 1f * k
        ap.color = C.LINE
        c.drawCircle(cx, cy, 31f * k, ap)

        // ponteiro
        val ang = Math.toRadians((135f + frac * 270f).toDouble())
        val x0 = cx + cos(ang).toFloat() * 15.5f * k
        val y0 = cy + sin(ang).toFloat() * 15.5f * k
        val x1 = cx + cos(ang).toFloat() * 26.7f * k
        val y1 = cy + sin(ang).toFloat() * 26.7f * k
        ap.strokeCap = Paint.Cap.ROUND
        ap.strokeWidth = 2.5f * k
        ap.color = C.INK
        c.drawLine(x0, y0, x1, y1, ap)

        if (chip) {
            drawChip(c)
        } else {
            tp.textAlign = Paint.Align.CENTER
            tp.typeface = FONT_MEDIUM
            tp.textSize = spf(13f)
            tp.color = C.INK
            c.drawText(label, w / 2f, dpf(100f), tp)
            tp.typeface = Typeface.DEFAULT
            tp.textSize = spf(12f)
            tp.color = C.SUB
            c.drawText(fmt(value), w / 2f, dpf(117f), tp)
        }
    }

    /** Etiqueta no canto superior esquerdo: nome e valor. */
    private fun drawChip(c: Canvas) {
        val txt = fmt(value)
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = spf(11f)
        tp.typeface = FONT_MEDIUM
        val w1 = tp.measureText(label)
        tp.typeface = Typeface.DEFAULT
        val w2 = tp.measureText(txt)
        val padH = dpf(8f)
        val padV = dpf(3f)
        val lh = spf(11f) * 1.25f
        val left = dpf(4f)
        val top = dpf(2f)
        rect.set(left, top, left + max(w1, w2) + 2f * padH, top + 2f * lh + 2f * padV)
        ap.style = Paint.Style.FILL
        ap.color = C.CARD2
        c.drawRoundRect(rect, dpf(8f), dpf(8f), ap)
        tp.typeface = FONT_MEDIUM
        tp.color = C.INK
        c.drawText(label, left + padH, top + padV + lh * 0.8f, tp)
        tp.typeface = Typeface.DEFAULT
        tp.color = C.SUB
        c.drawText(txt, left + padH, top + padV + lh * 1.8f, tp)
    }

    private fun setValueFromDrag(y: Float) {
        var v = startValue + (startY - y) / dpf(220f) * (hi - lo)
        if (step > 0f) v = (v / step).roundToInt() * step
        v = v.coerceIn(lo, hi)
        if (v != value) {
            value = v
            onChange?.invoke(v)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                startY = e.y
                startValue = value
                moved = false
                val now = SystemClock.uptimeMillis()
                if (lastUp != 0L && now - lastUp < 300) {
                    ignore = true
                    lastUp = 0L
                    if (value != def) {
                        value = def
                        onChange?.invoke(def)
                    }
                } else {
                    ignore = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!ignore) {
                    if (abs(e.y - startY) > dpf(6f)) moved = true
                    setValueFromDrag(e.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastUp = if (!moved && !ignore) SystemClock.uptimeMillis() else 0L
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
