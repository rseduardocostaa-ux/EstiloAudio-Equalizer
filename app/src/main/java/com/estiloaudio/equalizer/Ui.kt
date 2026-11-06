package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** Paleta clara estilo Material (fundo lavanda, cartões tonais, roxo como cor de destaque). */
object C {
    const val BG = 0xFFFEF7FF.toInt()
    const val CARD = 0xFFF3EDF7.toInt()
    const val CARD2 = 0xFFE8DEF8.toInt()
    const val INK = 0xFF1D1B20.toInt()
    const val SUB = 0xFF605D66.toInt()
    const val LINE = 0xFFCAC4D0.toInt()
    const val PRI = 0xFF6750A4.toInt()
    const val PANEL = 0xFFEEF1F7.toInt()
    const val SLATE = 0xFF4F5D70.toInt()
    const val TRACK = 0xFFE0D8EA.toInt()
    const val TICK = 0xFFCFC6DA.toInt()
    const val ZERO = 0xFF79747E.toInt()
    const val OFFBG = 0xFFE6E0E9.toInt()
    const val TABOFF = 0xFF49454F.toInt()
    const val LEDOFF = 0xFFE6E0E9.toInt()

    const val ACC = 0xFF00E676.toInt()     // verde (medidor e sliders)
    const val CYAN = 0xFF26C6DA.toInt()
    const val ORANGE = 0xFFFF9100.toInt()
    const val YELLOW = 0xFFFFEB3B.toInt()
    const val RED = 0xFFFF1744.toInt()
    const val SOFTRED = 0xFFFF5252.toInt()
    const val WARN = ORANGE
    const val HOT = RED
}

/** Fonte "medium" (peso 500) do Material. */
val FONT_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()
fun Context.spf(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

fun View.dpf(v: Float): Float = context.dpf(v)
fun View.dpi(v: Float): Int = context.dpi(v)
fun View.spf(v: Float): Float = context.spf(v)

fun fmtDb(v: Float): String =
    if (abs(v) < 0.05f) "0" else String.format(Locale.US, "%+.1f", v)

/** Posição horizontal (escala logarítmica, 20 Hz a 20 kHz) usada pela curva e pelo espectro. */
fun plotX(f: Float, l: Float, r: Float): Float = l + (r - l) * (ln(f / 20f) / ln(1000f))

fun mixColor(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    return Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
    )
}

/** Cor do valor em dB: ciano (corte), verde (zero), amarelo e vermelho (reforço). */
fun dbColor(v: Float): Int = when {
    v < 0f -> mixColor(C.ACC, C.CYAN, -v / 12f)
    v < 6f -> mixColor(C.ACC, C.YELLOW, v / 6f)
    else -> mixColor(C.YELLOW, C.SOFTRED, (v - 6f) / 6f)
}

fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

fun rounded(color: Int, radiusDp: Float, ctx: Context, strokeColor: Int = 0, strokeDp: Float = 0f): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = ctx.dpf(radiusDp)
    if (strokeDp > 0f) g.setStroke(ctx.dpi(strokeDp), strokeColor)
    return g
}

/** Bloco de conteúdo. flat = sem caixa. */
fun card(ctx: Context, flat: Boolean = false): LinearLayout {
    val l = LinearLayout(ctx)
    l.orientation = LinearLayout.VERTICAL
    if (flat) {
        l.setPadding(0, 0, 0, 0)
    } else {
        l.background = rounded(C.CARD, 16f, ctx)
        val p = ctx.dpi(14f)
        l.setPadding(p, p, p, p)
    }
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.bottomMargin = if (flat) 0 else ctx.dpi(14f)
    l.layoutParams = lp
    return l
}

fun sectionTitle(ctx: Context, text: String, topDp: Float = 16f): TextView {
    val t = TextView(ctx)
    t.text = text
    t.setTextColor(C.PRI)
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    t.letterSpacing = 0.06f
    t.typeface = Typeface.DEFAULT_BOLD
    t.setPadding(0, ctx.dpi(topDp), 0, ctx.dpi(8f))
    return t
}

fun bodyText(ctx: Context, text: String, sizeSp: Float = 13f, color: Int = C.SUB): TextView {
    val t = TextView(ctx)
    t.text = text
    t.setTextColor(color)
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    t.setLineSpacing(0f, 1.12f)
    return t
}

/** Botão em forma de pílula (tom lavanda). */
fun pill(ctx: Context, text: String): TextView {
    val t = TextView(ctx)
    t.text = text
    t.gravity = Gravity.CENTER
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    t.typeface = FONT_MEDIUM
    t.setPadding(ctx.dpi(16f), ctx.dpi(10f), ctx.dpi(16f), ctx.dpi(10f))
    stylePill(t, false)
    return t
}

fun stylePill(t: TextView, on: Boolean) {
    val ctx = t.context
    if (on) {
        t.background = rounded(C.PRI, 20f, ctx)
        t.setTextColor(0xFFFFFFFF.toInt())
    } else {
        t.background = rounded(C.CARD2, 20f, ctx)
        t.setTextColor(C.PRI)
    }
}

/** Linha horizontal de 3 colunas iguais; com 1 ou 2 filhos, eles ficam centralizados. */
fun weightRow(ctx: Context, vararg children: View): LinearLayout {
    val r = LinearLayout(ctx)
    r.orientation = LinearLayout.HORIZONTAL
    val side = (3 - children.size).coerceAtLeast(0) / 2f
    fun spacer() {
        if (side > 0f) r.addView(View(ctx), LinearLayout.LayoutParams(0, 1, side))
    }
    spacer()
    for (c in children) {
        r.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    spacer()
    return r
}
