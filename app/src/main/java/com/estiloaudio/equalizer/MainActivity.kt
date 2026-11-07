package com.estiloaudio.equalizer

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusView: TextView
    private lateinit var titleView: TextView
    private lateinit var logoView: LogoView
    private lateinit var headerBg: HeaderBackground
    private var headerOff: Boolean? = null
    private lateinit var pages: List<ScrollView>
    private lateinit var tabIcons: List<TabIcon>

    private lateinit var curve: CurveView
    private lateinit var spectrum: SpectrumView
    private lateinit var sliders: List<LedSlider> // 0 = preamp, 1..10 = bandas
    private lateinit var bassKnob: KnobView
    private lateinit var trebleKnob: KnobView
    private lateinit var bypassBtn: TextView

    private lateinit var grMeter: GrMeterView
    private lateinit var attackKnob: KnobView
    private lateinit var releaseKnob: KnobView
    private lateinit var ratioKnob: KnobView
    private lateinit var thresholdKnob: KnobView
    private lateinit var gainKnob: KnobView
    private lateinit var balanceKnob: KnobView
    private lateinit var volumeKnob: KnobView
    private lateinit var limSwitch: M3Switch
    private lateinit var limThrKnob: KnobView
    private lateinit var limRelKnob: KnobView

    private lateinit var masterSwitch: M3Switch
    private lateinit var autoSwitch: M3Switch
    private lateinit var globalSwitch: M3Switch
    private lateinit var notifStatus: TextView
    private lateinit var micStatus: TextView

    private var syncing = false

    private val ticker = object : Runnable {
        override fun run() {
            val live = EqState.vizOk && EqState.running
            spectrum.feed(EqState.bars, live)
            grMeter.value = if (EqState.running) EqState.grDb else 0f
            statusView.text = EqState.status()
            val s = EqState.s
            val off = !s.master && !s.auto
            if (headerOff != off) {
                headerOff = off
                applyHeaderStyle(off)
            }
            if (masterSwitch.isChecked != s.master) {
                syncing = true
                masterSwitch.isChecked = s.master
                syncing = false
            }
            handler.postDelayed(this, 33)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EqState.ensure(this)

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        setContentView(buildUi())
        refreshFromState()
        selectTab(0)

        askPermissionsOnce()
        syncService()
    }

    override fun onResume() {
        super.onResume()
        EqState.uiVisible = true
        EqState.notifyChanged()
        refreshPermsUi()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        EqState.uiVisible = false
        EqState.notifyChanged()
        handler.removeCallbacks(ticker)
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(C.BG)
        root.setPadding(dpi(6f), 0, dpi(6f), 0)

        // cabeçalho: cartão fino com a logo, o nome e o status
        headerBg = HeaderBackground(this)
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dpi(8f), dpi(2f), dpi(10f), dpi(2f))
        header.background = headerBg
        header.clipChildren = false
        header.clipToPadding = false

        logoView = LogoView(this)
        logoView.contentDescription = "Trocar o ícone"
        logoView.setOnClickListener { showLogoPicker() }
        header.addView(logoView, LinearLayout.LayoutParams(-2, -2))

        titleView = TextView(this)
        titleView.text = "Estilo Áudio"
        titleView.setTextColor(C.PRI_DK)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        titleView.typeface = FONT_MEDIUM
        titleView.letterSpacing = 0.01f
        titleView.maxLines = 1
        val tlp = LinearLayout.LayoutParams(0, -2, 1f)
        tlp.marginStart = dpi(8f)
        header.addView(titleView, tlp)

        statusView = TextView(this)
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        statusView.typeface = FONT_MEDIUM
        header.addView(statusView)
        applyHeaderStyle(false)

        val hlp = LinearLayout.LayoutParams(-1, -2)
        hlp.topMargin = dpi(6f)
        hlp.bottomMargin = dpi(6f)
        root.addView(header, hlp)

        // alinha o centro da logo com a linha do Preamp (1,5 de 11,5 partes da largura, menos 5 dp do separador)
        header.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val hw = (r - l).toFloat()
            if (hw > 0f && logoView.measuredWidth > 0) {
                val center = (hw - dpf(5f)) * 0.75f / 11.5f
                val want = (center - logoView.measuredWidth / 2f - header.paddingLeft).coerceAtLeast(0f).toInt()
                val lp = logoView.layoutParams as LinearLayout.LayoutParams
                if (lp.marginStart != want) {
                    lp.marginStart = want
                    logoView.layoutParams = lp
                }
                headerBg.glowX = logoView.left + logoView.width / 2f
            }
        }

        // páginas
        val eq = buildEqPage()
        val comp = buildCompPage()
        val set = buildSettingsPage()
        pages = listOf(eq, comp, set)
        val frame = FrameLayout(this)
        for (p in pages) frame.addView(p, FrameLayout.LayoutParams(-1, -1))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))

        // barra de abas (pílula verde-clara com 3 ícones)
        val wrap = LinearLayout(this)
        wrap.setPadding(0, dpi(4f), 0, dpi(6f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.background = rounded(C.CARD, 28f, this)
        bar.setPadding(0, dpi(4f), 0, dpi(4f))
        val names = listOf("Equalizador", "Compressor", "Ajustes")
        tabIcons = names.mapIndexed { i, n ->
            val holder = FrameLayout(this)
            val icon = TabIcon(this, i)
            icon.contentDescription = n
            holder.addView(icon, FrameLayout.LayoutParams(dpi(64f), dpi(38f), Gravity.CENTER))
            holder.setOnClickListener { selectTab(i) }
            bar.addView(holder, LinearLayout.LayoutParams(0, dpi(56f), 1f))
            icon
        }
        wrap.addView(bar, LinearLayout.LayoutParams(-1, -2))
        root.addView(wrap)

        // margens do sistema (barra de status / navegação)
        root.setOnApplyWindowInsetsListener { _, ins ->
            root.setPadding(dpi(6f), insetTop(ins), dpi(6f), 0)
            wrap.setPadding(0, dpi(4f), 0, dpi(6f) + insetBottom(ins))
            ins
        }
        return root
    }

    /** Seletor do ícone do cabeçalho: bandeira, logo do app (na cor do tema) ou qualquer emoji. */
    private fun showLogoPicker() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val current = LogoPrefs.load(this)

        fun choose(k: String) {
            LogoPrefs.save(this, k)
            logoView.kind = k
            dlg.dismiss()
        }

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = rounded(C.BG, 24f, this)
        box.setPadding(dpi(16f), dpi(18f), dpi(16f), dpi(16f))

        val title = TextView(this)
        title.text = "Ícone do cabeçalho"
        title.setTextColor(C.INK)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        title.typeface = FONT_MEDIUM
        box.addView(title)
        val sub = bodyText(this, "Toque em um para usar no topo do app.", 13f)
        sub.setPadding(0, dpi(2f), 0, dpi(10f))
        box.addView(sub)

        val options = listOf(LogoPrefs.FLAG, LogoPrefs.DRAGON) + LOGO_EMOJIS
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        val perRow = 6
        var start = 0
        while (start < options.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0 until perRow) {
                val idx = start + j
                val lp = LinearLayout.LayoutParams(0, dpi(52f), 1f)
                lp.setMargins(dpi(3f), dpi(3f), dpi(3f), dpi(3f))
                if (idx < options.size) {
                    val k = options[idx]
                    val cell = FrameLayout(this)
                    cell.background =
                        if (k == current) rounded(C.CARD2, 14f, this, C.PRI_DK, 2f) else rounded(C.CARD, 14f, this)
                    cell.contentDescription = when (k) {
                        LogoPrefs.FLAG -> "Bandeira do Brasil"
                        LogoPrefs.DRAGON -> "Logo do app"
                        else -> k
                    }
                    cell.addView(LogoView(this, k, 30f), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                    cell.setOnClickListener { choose(k) }
                    row.addView(cell, lp)
                } else {
                    row.addView(View(this), lp)
                }
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
            start += perRow
        }
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(grid, ViewGroup.LayoutParams(-1, -2))
        box.addView(scroll, LinearLayout.LayoutParams(-1, dpi(300f)))

        val input = EditText(this)
        input.hint = "Ou digite/cole outro emoji"
        input.setSingleLine()
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        input.setTextColor(C.INK)
        input.setHintTextColor(C.SUB)
        input.background = rounded(C.CARD, 14f, this)
        input.setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
        val use = pill(this, "Usar")
        use.setOnClickListener {
            val g = firstGrapheme(input.text.toString())
            if (g == null) {
                Toast.makeText(this, "Digite um emoji primeiro", Toast.LENGTH_SHORT).show()
            } else {
                choose(g)
            }
        }
        val inRow = LinearLayout(this)
        inRow.orientation = LinearLayout.HORIZONTAL
        inRow.gravity = Gravity.CENTER_VERTICAL
        inRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val ulp = LinearLayout.LayoutParams(-2, -2)
        ulp.marginStart = dpi(8f)
        inRow.addView(use, ulp)
        val irlp = LinearLayout.LayoutParams(-1, -2)
        irlp.topMargin = dpi(10f)
        box.addView(inRow, irlp)

        val wrap = FrameLayout(this)
        wrap.setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        wrap.addView(box, FrameLayout.LayoutParams(-1, -2))
        dlg.setContentView(wrap)
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dlg.show()
    }

    /** Cabeçalho ligado (verde) ou desligado (cinza, sem sombra). */
    private fun applyHeaderStyle(off: Boolean) {
        logoView.off = off
        titleView.setTextColor(if (off) C.SUB else C.PRI_DK)
        if (off) titleView.setShadowLayer(0f, 0f, 0f, 0) else titleView.setShadowLayer(dpf(2f), 0f, dpf(1f), 0x594A6600)
        statusView.setTextColor(if (off) C.SUB else C.PRI_DK)
        statusView.background = rounded(if (off) C.OFFBG else C.CARD2, 12f, this)
        statusView.setPadding(dpi(10f), dpi(4f), dpi(10f), dpi(4f))
    }

    @Suppress("DEPRECATION")
    private fun insetTop(ins: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= 30) ins.getInsets(WindowInsets.Type.systemBars()).top
        else ins.systemWindowInsetTop

    @Suppress("DEPRECATION")
    private fun insetBottom(ins: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= 30) ins.getInsets(WindowInsets.Type.systemBars()).bottom
        else ins.systemWindowInsetBottom

    private fun selectTab(i: Int) {
        for (k in pages.indices) pages[k].visibility = if (k == i) View.VISIBLE else View.GONE
        for (k in tabIcons.indices) tabIcons[k].active = (k == i)
    }

    private fun page(content: LinearLayout, fill: Boolean = false): ScrollView {
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.isFillViewport = fill
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, 0, 0, dpi(12f))
        sv.addView(content, ViewGroup.LayoutParams(-1, -2))
        return sv
    }

    // ----- Aba 1: Equalizador

    private fun buildEqPage(): ScrollView {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        // sliders (cartão verde-claro)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val labels = listOf("Preamp", "31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
        val list = ArrayList<LedSlider>()
        for (i in labels.indices) {
            val sl = LedSlider(this, labels[i], i == 0)
            sl.onChange = { v ->
                EqState.update { s ->
                    if (i == 0) {
                        s.preamp = v
                    } else {
                        s.bands[i - 1] = v
                    }
                }
                curve.invalidate()
            }
            list.add(sl)
            val w = if (i == 0) 1.5f else 1f
            row.addView(sl, LinearLayout.LayoutParams(0, -1, w))
            if (i == 0) {
                val div = View(this)
                div.setBackgroundColor(C.LINE)
                val lp = LinearLayout.LayoutParams(dpi(1f), -1)
                lp.topMargin = dpi(6f)
                lp.bottomMargin = dpi(46f)
                lp.marginStart = dpi(2f)
                lp.marginEnd = dpi(2f)
                row.addView(div, lp)
            }
        }
        sliders = list
        val sliderCard = LinearLayout(this)
        sliderCard.background = rounded(C.CARD, 16f, this)
        sliderCard.setPadding(0, dpi(6f), 0, 0)
        sliderCard.minimumHeight = dpi(270f)
        sliderCard.addView(row, LinearLayout.LayoutParams(-1, -1))
        // os sliders ocupam a altura que sobra na tela (como na prévia)
        col.addView(sliderCard, LinearLayout.LayoutParams(-1, 0, 1f))

        // painel: espectro (barras) + curva por cima
        val panel = FrameLayout(this)
        panel.background = rounded(C.PANEL, 16f, this)
        panel.clipToOutline = true
        spectrum = SpectrumView(this)
        curve = CurveView(this)
        panel.addView(spectrum, FrameLayout.LayoutParams(-1, -1))
        panel.addView(curve, FrameLayout.LayoutParams(-1, -1))
        val plp = LinearLayout.LayoutParams(-1, dpi(140f))
        plp.topMargin = dpi(6f)
        plp.bottomMargin = dpi(6f)
        col.addView(panel, plp)

        // botões (esquerda) e knobs Bass / Treble (direita)
        bassKnob = KnobView(this, "Bass", -12f, 12f, 0f, true, 0.5f, true, true) { fmtDb(it) + " dB" }
        trebleKnob = KnobView(this, "Treble", -12f, 12f, 0f, true, 0.5f, true, true) { fmtDb(it) + " dB" }
        bassKnob.onChange = { v -> EqState.update { it.bass = v }; curve.invalidate() }
        trebleKnob.onChange = { v -> EqState.update { it.treble = v }; curve.invalidate() }

        bypassBtn = pill(this, "Bypass")
        bypassBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        bypassBtn.setPadding(dpi(10f), dpi(9f), dpi(10f), dpi(9f))
        bypassBtn.setOnClickListener {
            EqState.update { it.bypass = !it.bypass }
            stylePill(bypassBtn, EqState.s.bypass)
            curve.invalidate()
        }
        val resetBtn = pill(this, "Reset")
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        resetBtn.setPadding(dpi(10f), dpi(9f), dpi(10f), dpi(9f))
        resetBtn.setOnClickListener {
            EqState.update { s ->
                s.preamp = 0f
                s.bands.fill(0f)
                s.bass = 0f
                s.treble = 0f
            }
            refreshFromState()
        }
        val btnCol = LinearLayout(this)
        btnCol.orientation = LinearLayout.VERTICAL
        btnCol.gravity = Gravity.CENTER
        val blp = LinearLayout.LayoutParams(-1, -2)
        blp.bottomMargin = dpi(10f)
        btnCol.addView(bypassBtn, blp)
        btnCol.addView(resetBtn, LinearLayout.LayoutParams(-1, -2))

        val kRow = LinearLayout(this)
        kRow.orientation = LinearLayout.HORIZONTAL
        kRow.gravity = Gravity.CENTER_VERTICAL
        kRow.setPadding(0, 0, 0, dpi(4f))
        kRow.addView(btnCol, LinearLayout.LayoutParams(0, -2, 27f))
        val k1 = LinearLayout.LayoutParams(0, -2, 36.5f)
        k1.marginStart = dpi(4f)
        kRow.addView(bassKnob, k1)
        val k2 = LinearLayout.LayoutParams(0, -2, 36.5f)
        k2.marginStart = dpi(4f)
        kRow.addView(trebleKnob, k2)
        col.addView(kRow, LinearLayout.LayoutParams(-1, -2))

        return page(col, true)
    }

    // ----- Aba 2: Compressor

    private fun knob(
        label: String, min: Float, max: Float, def: Float, step: Float,
        fmt: (Float) -> String, bipolar: Boolean = false, apply: (EqSettings, Float) -> Unit
    ): KnobView {
        val k = KnobView(this, label, min, max, def, bipolar, step, false, false, fmt)
        k.onChange = { v -> EqState.update { apply(it, v) } }
        return k
    }

    private fun buildCompPage(): ScrollView {
        val col = LinearLayout(this)

        val cCard = card(this, true)
        cCard.addView(sectionTitle(this, "Compressor"))
        grMeter = GrMeterView(this)
        cCard.addView(grMeter, LinearLayout.LayoutParams(-1, -2))
        attackKnob = knob("Attack", 1f, 200f, 10f, 1f, { String.format(Locale.US, "%.0f ms", it) }) { s, v -> s.attack = v }
        releaseKnob = knob("Release", 10f, 2000f, 120f, 10f, { String.format(Locale.US, "%.0f ms", it) }) { s, v -> s.release = v }
        ratioKnob = knob("Ratio", 1f, 20f, 1f, 0.5f, { if (it <= 1.01f) "Off" else String.format(Locale.US, "%.1f:1", it) }) { s, v -> s.ratio = v }
        thresholdKnob = knob("Threshold", -60f, 0f, -18f, 1f, { String.format(Locale.US, "%.0f dB", it) }) { s, v -> s.threshold = v }
        gainKnob = knob("Gain", -12f, 24f, 0f, 0.5f, { fmtDb(it) + " dB" }) { s, v -> s.gain = v }
        cCard.addView(weightRow(this, attackKnob, releaseKnob, ratioKnob))
        cCard.addView(weightRow(this, thresholdKnob, gainKnob))
        col.addView(cCard)

        val oCard = card(this, true)
        oCard.addView(sectionTitle(this, "Saída"))
        balanceKnob = knob("Balance", -100f, 100f, 0f, 1f, {
            when {
                it < -0.5f -> "L ${(-it).toInt()}"
                it > 0.5f -> "R ${it.toInt()}"
                else -> "Centro"
            }
        }, true) { s, v -> s.balance = v }
        volumeKnob = knob("Volume", -24f, 6f, 0f, 0.5f, { fmtDb(it) + " dB" }) { s, v -> s.volume = v }
        oCard.addView(weightRow(this, balanceKnob, volumeKnob))
        col.addView(oCard)

        val lCard = card(this, true)
        val lHead = LinearLayout(this)
        lHead.orientation = LinearLayout.HORIZONTAL
        lHead.gravity = Gravity.CENTER_VERTICAL
        val lt = sectionTitle(this, "Limiter de saída")
        lHead.addView(lt, LinearLayout.LayoutParams(0, -2, 1f))
        limSwitch = newSwitch()
        limSwitch.setOnCheckedChangeListener { _, on -> if (!syncing) EqState.update { it.limOn = on } }
        lHead.addView(limSwitch)
        lCard.addView(lHead)
        limThrKnob = knob("Threshold", -20f, 0f, -1f, 0.5f, { String.format(Locale.US, "%.1f dB", it) }) { s, v -> s.limThr = v }
        limRelKnob = knob("Release", 5f, 500f, 60f, 5f, { String.format(Locale.US, "%.0f ms", it) }) { s, v -> s.limRel = v }
        lCard.addView(weightRow(this, limThrKnob, limRelKnob))
        col.addView(lCard)

        val resetComp = pill(this, "Reset")
        resetComp.setOnClickListener {
            EqState.update { s ->
                s.attack = 10f
                s.release = 120f
                s.ratio = 1f
                s.threshold = -18f
                s.gain = 0f
                s.balance = 0f
                s.volume = 0f
            }
            refreshFromState()
        }
        val rlp = LinearLayout.LayoutParams(-2, -2)
        rlp.gravity = Gravity.END
        rlp.topMargin = dpi(4f)
        rlp.bottomMargin = dpi(10f)
        col.addView(resetComp, rlp)

        col.addView(bodyText(this, "O medidor de redução de ganho é uma estimativa calculada a partir do nível do áudio.", 12f))
        return page(col)
    }

    // ----- Aba 3: Ajustes

    private fun newSwitch(): M3Switch = M3Switch(this)

    private fun switchRow(parent: LinearLayout, title: String, desc: String, sw: M3Switch) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dpi(7f), 0, dpi(7f))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(bodyText(this, title, 15f, C.INK))
        texts.addView(bodyText(this, desc, 12f))
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val slp = LinearLayout.LayoutParams(-2, -2)
        slp.marginStart = dpi(12f)
        row.addView(sw, slp)
        parent.addView(row)
    }

    private fun permRow(parent: LinearLayout, title: String, desc: String, onClick: () -> Unit): TextView {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dpi(7f), 0, dpi(7f))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(bodyText(this, title, 15f, C.INK))
        val status = bodyText(this, desc, 12f)
        texts.addView(status)
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val btn = pill(this, "Permitir")
        btn.setOnClickListener { onClick() }
        val plp2 = LinearLayout.LayoutParams(-2, -2)
        plp2.marginStart = dpi(12f)
        row.addView(btn, plp2)
        parent.addView(row)
        return status
    }

    private fun buildSettingsPage(): ScrollView {
        val col = LinearLayout(this)

        val gCard = card(this)
        gCard.addView(sectionTitle(this, "Geral", 0f))
        masterSwitch = newSwitch()
        masterSwitch.setOnCheckedChangeListener { _, on ->
            if (!syncing) {
                EqState.update { it.master = on }
                syncService()
            }
        }
        switchRow(gCard, "Equalizador ligado", "Liga ou desliga todos os efeitos.", masterSwitch)
        autoSwitch = newSwitch()
        autoSwitch.setOnCheckedChangeListener { _, on ->
            if (!syncing) {
                EqState.update { it.auto = on }
                syncService()
            }
        }
        switchRow(gCard, "Ligar sozinho quando o Spotify tocar", "Com o equalizador desligado, os efeitos entram quando o Spotify começa a tocar.", autoSwitch)
        globalSwitch = newSwitch()
        globalSwitch.setOnCheckedChangeListener { _, on ->
            if (!syncing) {
                EqState.update { it.global = on }
                syncService()
            }
        }
        switchRow(gCard, "Modo global (experimental)", "Tenta aplicar o efeito a todo o áudio do aparelho. Use só se o modo normal não funcionar.", globalSwitch)
        gCard.addView(bodyText(this, "A última configuração é salva automaticamente.", 12f))
        col.addView(gCard)

        val pCard = card(this)
        pCard.addView(sectionTitle(this, "Permissões", 0f))
        notifStatus = permRow(pCard, "Notificações", "") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
        micStatus = permRow(pCard, "Microfone (só para o espectro)", "") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 12)
        }
        val appSettings = pill(this, "Abrir ajustes do app")
        appSettings.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        val alp = LinearLayout.LayoutParams(-1, -2)
        alp.topMargin = dpi(6f)
        pCard.addView(appSettings, alp)
        col.addView(pCard)

        val bCard = card(this)
        bCard.addView(sectionTitle(this, "Segundo plano", 0f))
        bCard.addView(bodyText(this, "Para funcionar com a tela apagada, deixe o app sem restrição de bateria.", 13f))
        val batt = pill(this, "Otimização de bateria")
        batt.setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) { }
        }
        val blp = LinearLayout.LayoutParams(-1, -2)
        blp.topMargin = dpi(10f)
        bCard.addView(batt, blp)
        col.addView(bCard)

        val tip = bodyText(this, "Toque duas vezes num slider ou knob para zerar.", 12f)
        tip.gravity = Gravity.CENTER
        tip.setPadding(dpi(4f), dpi(6f), dpi(4f), 0)
        col.addView(tip, LinearLayout.LayoutParams(-1, -2))

        return page(col)
    }

    // ------------------------------------------------------------ estado

    private fun refreshFromState() {
        val s = EqState.s
        syncing = true
        sliders[0].value = s.preamp
        for (i in 0 until 10) sliders[i + 1].value = s.bands[i]
        bassKnob.value = s.bass
        trebleKnob.value = s.treble
        stylePill(bypassBtn, s.bypass)

        attackKnob.value = s.attack
        releaseKnob.value = s.release
        ratioKnob.value = s.ratio
        thresholdKnob.value = s.threshold
        gainKnob.value = s.gain
        balanceKnob.value = s.balance
        volumeKnob.value = s.volume
        limSwitch.isChecked = s.limOn
        limThrKnob.value = s.limThr
        limRelKnob.value = s.limRel

        masterSwitch.isChecked = s.master
        autoSwitch.isChecked = s.auto
        globalSwitch.isChecked = s.global
        syncing = false
        curve.invalidate()
    }

    private fun syncService() {
        val s = EqState.s
        val i = Intent(this, EqService::class.java)
        try {
            if (s.master || s.auto) startForegroundService(i) else stopService(i)
        } catch (_: Exception) { }
    }

    // -------------------------------------------------------- permissões

    private fun hasNotif(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasMic(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refreshPermsUi() {
        notifStatus.text = if (hasNotif()) "Concedida" else "Necessária para a notificação fixa"
        micStatus.text = if (hasMic()) "Concedida" else "Necessária para o espectro e o medidor"
    }

    private fun askPermissionsOnce() {
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        val need = ArrayList<String>()
        if (!hasNotif()) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (!hasMic()) need.add(Manifest.permission.RECORD_AUDIO)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 10)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermsUi()
        if (hasMic()) {
            // refaz o Visualizer agora que o microfone foi liberado
            val s = EqState.s
            if (s.master || s.auto) {
                try {
                    startForegroundService(Intent(this, EqService::class.java).setAction(EqService.ACTION_REFRESH))
                } catch (_: Exception) { }
            }
        }
    }
}
