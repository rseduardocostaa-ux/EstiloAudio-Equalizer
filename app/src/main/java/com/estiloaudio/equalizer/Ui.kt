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

/**
 * Paleta do app. As cores de superfície, texto e destaque são calculadas a partir da cor principal
 * escolhida pelo usuário e do modo claro/escuro (veja [apply]). Os valores iniciais são o tema
 * claro com verde-limão #B9FC00 (o visual original).
 */
object C {
    var DARK = false
    var ACCENT = 0xFFB9FC00.toInt()

    var BG = 0xFFFBFDF2.toInt()
    var CARD = 0xFFF1F5E2.toInt()
    var CARD2 = 0xFFE3EDC6.toInt()
    var INK = 0xFF1B1D16.toInt()
    var SUB = 0xFF5B6050.toInt()
    var LINE = 0xFFC8CFB6.toInt()
    var PRI = 0xFFB9FC00.toInt()        // destaque (preenchimentos: alças, arcos, interruptor)
    var PRI_DK = 0xFF4A6600.toInt()     // textos e ícones do destaque (escuro no tema claro, claro no escuro)
    var ON_PRI = 0xFF1B2600.toInt()     // texto/ícone sobre a cor de destaque
    var PANEL = 0xFFEEF2E4.toInt()
    var SLATE = 0xFF4F5D70.toInt()      // linha da curva do EQ
    var GRID = 0x384F5D70               // grade do gráfico
    var TRACK = 0xFFDDE5CB.toInt()
    var TICK = 0xFFCDD6BB.toInt()
    var ZERO = 0xFF787D6C.toInt()
    var OFFBG = 0xFFE5E9DB.toInt()
    var TABOFF = 0xFF45493C.toInt()
    var LEDOFF = 0xFFE5E9DB.toInt()
    var SPEC = 0xFF8DB600.toInt()       // barras do espectro
    var GLOW = 0x594A6600               // sombra suave do logo e do título

    // cores de significado (dB, medidor): não mudam com o tema
    const val ACC = 0xFF00E676.toInt()     // verde (medidor e sliders)
    const val CYAN = 0xFF26C6DA.toInt()
    const val ORANGE = 0xFFFF9100.toInt()
    const val YELLOW = 0xFFFFEB3B.toInt()
    const val RED = 0xFFFF1744.toInt()
    const val SOFTRED = 0xFFFF5252.toInt()
    const val WARN = ORANGE
    const val HOT = RED

    private fun hsv(h: Float, s: Float, v: Float): Int =
        Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

    /** Recalcula toda a paleta. Chame antes de montar a interface. */
    fun apply(accent: Int, dark: Boolean) {
        val a = accent or 0xFF000000.toInt()
        DARK = dark
        ACCENT = a
        val hv = FloatArray(3)
        Color.colorToHSV(a, hv)
        val h = hv[0]
        val sat = hv[1]
        val av = hv[2]

        PRI = a
        ON_PRI = if (isLightColor(a)) hsv(h, 0.9f * sat, 0.15f) else 0xFFFFFFFF.toInt()

        if (!dark) {
            BG = hsv(h, 0.04f * sat, 0.99f)
            CARD = hsv(h, 0.08f * sat, 0.96f)
            CARD2 = hsv(h, 0.17f * sat, 0.93f)
            PANEL = hsv(h, 0.06f * sat, 0.95f)
            TRACK = hsv(h, 0.11f * sat, 0.90f)
            LINE = hsv(h, 0.10f * sat, 0.81f)
            TICK = hsv(h, 0.13f * sat, 0.84f)
            OFFBG = hsv(h, 0.06f * sat, 0.91f)
            LEDOFF = OFFBG
            INK = hsv(h, 0.15f * sat, 0.11f)
            SUB = hsv(h, 0.17f * sat, 0.38f)
            ZERO = hsv(h, 0.12f * sat, 0.49f)
            TABOFF = hsv(h, 0.15f * sat, 0.29f)
            PRI_DK = hsv(h, sat, minOf(av, 0.40f))
            SPEC = hsv(h, sat, av * 0.72f)
            SLATE = 0xFF4F5D70.toInt()
            GRID = 0x384F5D70
            GLOW = withAlpha(PRI_DK, 0x59)
        } else {
            BG = hsv(h, 0.22f * sat, 0.07f)
            CARD = hsv(h, 0.28f * sat, 0.13f)
            CARD2 = hsv(h, 0.36f * sat, 0.22f)
            PANEL = hsv(h, 0.26f * sat, 0.10f)
            TRACK = hsv(h, 0.26f * sat, 0.21f)
            LINE = hsv(h, 0.22f * sat, 0.30f)
            TICK = hsv(h, 0.20f * sat, 0.30f)
            OFFBG = hsv(h, 0.24f * sat, 0.18f)
            LEDOFF = OFFBG
            INK = hsv(h, 0.06f * sat, 0.93f)
            SUB = hsv(h, 0.10f * sat, 0.68f)
            ZERO = hsv(h, 0.12f * sat, 0.58f)
            TABOFF = hsv(h, 0.10f * sat, 0.70f)
            PRI_DK = hsv(h, 0.6f * sat, 1f)
            SPEC = a
            SLATE = 0xFFA9B8CF.toInt()
            GRID = 0x38A9B8CF
            GLOW = 0
        }
    }
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

fun fmtFreq(f: Float): String = when {
    f >= 10000f -> String.format(Locale.US, "%.1f kHz", f / 1000f)
    f >= 1000f -> String.format(Locale.US, "%.2f kHz", f / 1000f)
    else -> String.format(Locale.US, "%.0f Hz", f)
}

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

/** true se a cor é clara o bastante para pedir texto escuro por cima (luminância relativa, WCAG). */
fun isLightColor(c: Int): Boolean {
    fun lin(v: Int): Float {
        val x = v / 255f
        return if (x <= 0.03928f) x / 12.92f else Math.pow(((x + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    val l = 0.2126f * lin(Color.red(c)) + 0.7152f * lin(Color.green(c)) + 0.0722f * lin(Color.blue(c))
    return l > 0.19f
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
    t.setTextColor(C.PRI_DK)
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

/** Botão em forma de pílula (tom verde-claro). */
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
        t.setTextColor(C.ON_PRI)
    } else {
        t.background = rounded(C.CARD2, 20f, ctx)
        t.setTextColor(C.PRI_DK)
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
