package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.roundToInt

/** Logo do dragão (silhueta roxa) com sombra suave. Desligado: cinza e semitransparente, sem sombra. */
class LogoView(ctx: Context) : View(ctx) {

    private val bmp: Bitmap
    private val shadow: Bitmap
    private val shadowOff = IntArray(2)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gray = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    var off = false
        set(v) {
            field = v
            invalidate()
        }

    init {
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        val src = BitmapFactory.decodeResource(resources, R.drawable.logo_silhueta, opts)
        val h = dpi(38f)
        val w = (src.width * h.toFloat() / src.height).roundToInt()
        bmp = Bitmap.createScaledBitmap(src, w, h, true)
        val blur = Paint()
        blur.maskFilter = BlurMaskFilter(dpf(1.5f), BlurMaskFilter.Blur.NORMAL)
        shadow = bmp.extractAlpha(blur, shadowOff)
        sp.color = 0x596750A4
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(bmp.width, bmp.height)
    }

    override fun onDraw(c: Canvas) {
        if (off) {
            bp.colorFilter = gray
            bp.alpha = 128
        } else {
            c.drawBitmap(shadow, shadowOff[0].toFloat(), shadowOff[1].toFloat() + dpf(1f), sp)
            bp.colorFilter = null
            bp.alpha = 255
        }
        c.drawBitmap(bmp, 0f, 0f, bp)
    }
}

/** Fundo do cabeçalho: cartão lavanda com um brilho roxo suave atrás da logo. */
class HeaderBackground(private val ctx: Context) : Drawable() {

    var glowX = 0f
        set(v) {
            if (field != v) {
                field = v
                invalidateSelf()
            }
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private val m = Matrix()

    override fun draw(c: Canvas) {
        rect.set(bounds)
        val rad = ctx.dpf(16f)
        p.shader = null
        p.color = C.CARD
        c.drawRoundRect(rect, rad, rad, p)

        val rx = ctx.dpf(90f)
        val ry = ctx.dpf(44f)
        val sh = RadialGradient(
            0f, 0f, rx,
            intArrayOf(0x336750A4, 0x006750A4), floatArrayOf(0f, 0.7f),
            Shader.TileMode.CLAMP
        )
        m.reset()
        m.setScale(1f, ry / rx)
        m.postTranslate(rect.left + glowX, rect.centerY())
        sh.setLocalMatrix(m)
        p.shader = sh
        clip.reset()
        clip.addRoundRect(rect, rad, rad, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        c.drawRect(rect, p)
        c.restore()
        p.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
