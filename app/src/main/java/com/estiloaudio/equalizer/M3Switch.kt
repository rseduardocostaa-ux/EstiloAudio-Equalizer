package com.estiloaudio.equalizer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Interruptor estilo Material 3 (52 x 32 dp): contorno cinza desligado, roxo ligado. */
class M3Switch(ctx: Context) : View(ctx) {

    private var listener: ((M3Switch, Boolean) -> Unit)? = null
    private var t = 0f
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    var isChecked: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            animateTo(if (v) 1f else 0f)
        }

    init {
        isClickable = true
        setOnClickListener {
            isChecked = !isChecked
            listener?.invoke(this, isChecked)
        }
    }

    fun setOnCheckedChangeListener(l: (M3Switch, Boolean) -> Unit) {
        listener = l
    }

    private fun animateTo(target: Float) {
        anim?.cancel()
        if (!isAttachedToWindow) {
            t = target
            invalidate()
            return
        }
        val a = ValueAnimator.ofFloat(t, target)
        a.duration = 150
        a.addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
        a.start()
        anim = a
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dpi(52f), dpi(32f))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = dpf(2f)

        p.style = Paint.Style.FILL
        p.color = mixColor(C.OFFBG, C.PRI, t)
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, h / 2f, h / 2f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.color = mixColor(C.ZERO, C.PRI, t)
        rect.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f)
        c.drawRoundRect(rect, (h - sw) / 2f, (h - sw) / 2f, p)

        // bolinha: pequena e cinza (desligado) -> grande e branca (ligado)
        val cx = dpf(16f) + (dpf(36f) - dpf(16f)) * t
        val r = dpf(8f) + (dpf(12f) - dpf(8f)) * t
        p.style = Paint.Style.FILL
        p.color = mixColor(C.ZERO, 0xFFFFFFFF.toInt(), t)
        c.drawCircle(cx, h / 2f, r, p)
    }
}
