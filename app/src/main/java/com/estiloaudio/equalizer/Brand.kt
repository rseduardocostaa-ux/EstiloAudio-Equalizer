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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/** Escolha do ícone do cabeçalho, salva no aparelho: "flag" (padrão), "logo" (dragão) ou um emoji. */
object LogoPrefs {
    const val FLAG = "flag"
    const val DRAGON = "logo"
    private const val FILE = "estilo_audio_ui"
    private const val KEY = "logo"

    fun load(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, FLAG) ?: FLAG

    fun save(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, v).apply()
    }
}

/** Emojis sugeridos no seletor (além da bandeira e da logo do app). */
val LOGO_EMOJIS = listOf(
    "🎧", "🎵", "🎶", "🎸", "🥁", "🎹",
    "🎤", "🔊", "🔥", "⚡", "💚", "💛",
    "💙", "💜", "❤️", "⭐", "✨", "🚀",
    "👑", "💎", "😎", "🤘", "🙌", "💪",
    "🐉", "🦁", "🐺", "🦅", "🌴", "☀️",
    "🌙", "🌈", "🏆", "⚽", "🎮", "🏍️",
    "🚗", "🍀", "🌊", "🎉", "🕺", "🎯"
)

/** Primeiro "caractere" do texto (um emoji inteiro, mesmo formado por vários códigos), ou null se vazio. */
fun firstGrapheme(s: String): String? {
    val t = s.trim()
    if (t.isEmpty()) return null
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(t)
    val end = it.next()
    return if (end > 0) t.substring(0, end) else null
}

/**
 * Ícone do cabeçalho: bandeira do Brasil (retângulo de cantos arredondados), a logo do dragão
 * na cor do tema (verde) ou um emoji. Desligado: cinza e semitransparente, sem sombra.
 */
class LogoView(
    ctx: Context,
    kind0: String = LogoPrefs.load(ctx),
    private val hDp: Float = 38f
) : View(ctx) {

    var kind: String = kind0
        set(v) {
            field = v
            requestLayout()
            invalidate()
        }

    var off = false
        set(v) {
            field = v
            invalidate()
        }

    private val hPx = dpi(hDp)
    private var bmp: Bitmap? = null
    private var shadow: Bitmap? = null
    private val shadowOff = IntArray(2)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val layerPaint = Paint()
    private val gray = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    private val rect = RectF()
    private val clip = Path()
    private val tmp = Path()

    init {
        sp.color = 0x594A6600
        bp.colorFilter = PorterDuffColorFilter(C.PRI, PorterDuff.Mode.SRC_IN)
        layerPaint.colorFilter = gray
        layerPaint.alpha = 128
    }

    private fun prepareDragon() {
        if (bmp != null) return
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        val src = BitmapFactory.decodeResource(resources, R.drawable.logo_silhueta, opts)
        val w = (src.width * hPx.toFloat() / src.height).roundToInt()
        val b = Bitmap.createScaledBitmap(src, w, hPx, true)
        val blur = Paint()
        blur.maskFilter = BlurMaskFilter(dpf(1.5f), BlurMaskFilter.Blur.NORMAL)
        shadow = b.extractAlpha(blur, shadowOff)
        bmp = b
    }

    private fun flagH() = hPx * 0.74f
    private fun flagW() = flagH() * 1.43f
    private fun emojiSize() = hPx * 0.72f
    private fun emojiW(): Float {
        tp.textSize = emojiSize()
        return max(tp.measureText(kind), hPx * 0.8f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        when (kind) {
            LogoPrefs.DRAGON -> {
                prepareDragon()
                setMeasuredDimension(bmp!!.width, hPx)
            }
            LogoPrefs.FLAG -> setMeasuredDimension((flagW() + dpf(6f)).roundToInt(), hPx)
            else -> setMeasuredDimension((emojiW() + dpf(4f)).roundToInt(), hPx)
        }
    }

    override fun onDraw(c: Canvas) {
        if (off) {
            val n = c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), layerPaint)
            drawContent(c, false)
            c.restoreToCount(n)
        } else {
            drawContent(c, true)
        }
    }

    private fun drawContent(c: Canvas, withShadow: Boolean) {
        when (kind) {
            LogoPrefs.DRAGON -> {
                prepareDragon()
                if (withShadow) {
                    c.drawBitmap(shadow!!, shadowOff[0].toFloat(), shadowOff[1].toFloat() + dpf(1f), sp)
                }
                c.drawBitmap(bmp!!, 0f, 0f, bp)
            }
            LogoPrefs.FLAG -> drawFlag(c, withShadow)
            else -> {
                tp.textSize = emojiSize()
                tp.color = 0xFF000000.toInt()
                val fm = tp.fontMetrics
                val y = height / 2f - (fm.ascent + fm.descent) / 2f
                c.drawText(kind, width / 2f, y, tp)
            }
        }
    }

    /** Bandeira do Brasil desenhada em vetor: retângulo arredondado, losango, círculo, faixa e estrelas. */
    private fun drawFlag(c: Canvas, withShadow: Boolean) {
        val fh = flagH()
        val fw = flagW()
        val l = (width - fw) / 2f
        val t = (height - fh) / 2f
        val rad = fh * 0.2f
        p.style = Paint.Style.FILL

        if (withShadow) {
            val a = intArrayOf(0x14, 0x1E, 0x2E)
            for (i in 3 downTo 1) {
                p.color = a[3 - i] shl 24
                rect.set(l - dpf(i * 0.6f), t - dpf(i * 0.6f) + dpf(1f), l + fw + dpf(i * 0.6f), t + fh + dpf(i * 0.6f) + dpf(1f))
                c.drawRoundRect(rect, rad + dpf(i * 0.6f), rad + dpf(i * 0.6f), p)
            }
        }

        rect.set(l, t, l + fw, t + fh)
        clip.reset()
        clip.addRoundRect(rect, rad, rad, Path.Direction.CW)
        c.save()
        c.clipPath(clip)

        p.color = 0xFF009C3B.toInt()
        c.drawRect(rect, p)

        tmp.reset()
        tmp.moveTo(l + fw * 0.090f, t + fh * 0.5f)
        tmp.lineTo(l + fw * 0.5f, t + fh * 0.07f)
        tmp.lineTo(l + fw * 0.906f, t + fh * 0.5f)
        tmp.lineTo(l + fw * 0.5f, t + fh * 0.924f)
        tmp.close()
        p.color = 0xFFFFDF00.toInt()
        c.drawPath(tmp, p)

        val cx = l + fw * 0.5f
        val cy = t + fh * 0.5f
        val r = fw * 0.179f
        p.color = 0xFF002776.toInt()
        c.drawCircle(cx, cy, r, p)

        c.save()
        tmp.reset()
        tmp.addCircle(cx, cy, r, Path.Direction.CW)
        c.clipPath(tmp)
        // faixa branca (curva) e algumas estrelas
        tmp.reset()
        tmp.moveTo(cx - r, cy - 0.30f * r)
        tmp.quadTo(cx, cy - 0.42f * r, cx + r, cy + 0.38f * r)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.22f
        p.color = 0xFFFFFFFF.toInt()
        c.drawPath(tmp, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        val st = r * 0.11f
        val pts = floatArrayOf(
            -0.72f, 0.14f, -0.40f, 0.30f, -0.05f, 0.42f, 0.28f, 0.58f,
            0.52f, 0.22f, 0.50f, -0.58f, -0.30f, -0.62f
        )
        var k = 0
        while (k < pts.size) {
            c.drawRect(cx + pts[k] * r - st, cy + pts[k + 1] * r - st, cx + pts[k] * r + st, cy + pts[k + 1] * r + st, p)
            k += 2
        }
        c.restore()
        c.restore()
    }
}

/** Fundo do cabeçalho: cartão verde-claro com um brilho verde-limão suave atrás do ícone. */
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
            intArrayOf(0x40B9FC00, 0x00B9FC00), floatArrayOf(0f, 0.7f),
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
