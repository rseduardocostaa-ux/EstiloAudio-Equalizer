package com.estiloaudio.equalizer

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
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
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : Activity() {

    private companion object {
        const val REQ_EXPORT = 21
        const val REQ_IMPORT = 22
    }

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
    private lateinit var preampKnob: KnobView
    private lateinit var freqKnob: KnobView
    private lateinit var bandGainKnob: KnobView
    private lateinit var qKnob: KnobView
    private lateinit var bandTitle: TextView
    private lateinit var bandControls: LinearLayout
    private lateinit var bandHint: TextView
    private lateinit var typeBtns: List<TextView>
    private lateinit var delBandBtn: TextView
    private lateinit var autoPreSwitch: M3Switch
    private lateinit var autoPreDesc: TextView
    private lateinit var bassKnob: KnobView
    private lateinit var trebleKnob: KnobView
    private lateinit var bypassBtn: TextView
    private lateinit var presetBtn: TextView

    private lateinit var grMeter: GrMeterView
    private lateinit var peakMeter: PeakMeterView
    private lateinit var miniPlayer: MiniPlayer
    private lateinit var listenerStatus: TextView
    private lateinit var compCurve: CompCurveView
    private lateinit var mbSwitch: M3Switch
    private lateinit var singleBox: LinearLayout
    private lateinit var multiBox: LinearLayout
    private lateinit var x1Knob: KnobView
    private lateinit var x2Knob: KnobView
    private lateinit var mbPills: List<TextView>
    private lateinit var mbRange: TextView
    private lateinit var mbAtkKnob: KnobView
    private lateinit var mbRelKnob: KnobView
    private lateinit var mbRatioKnob: KnobView
    private lateinit var mbThrKnob: KnobView
    private lateinit var mbGainKnob: KnobView
    private var selBand = 0
    private lateinit var graphPanel: FrameLayout
    private lateinit var graphLp: LinearLayout.LayoutParams
    private lateinit var slidersPanel: FrameLayout
    private lateinit var slidersRow: LinearLayout
    private var preSlider: LedSlider? = null
    private var sliderBands: List<ParamBand> = emptyList()
    private val sliderViews = ArrayList<LedSlider>()
    private var sliderMode = true   // sliders em cima, gráfico embaixo (fixo)
    private lateinit var lowCutSwitch: M3Switch
    private lateinit var highCutSwitch: M3Switch
    private lateinit var lowCutBox: LinearLayout
    private lateinit var highCutBox: LinearLayout
    private lateinit var lowCutKnob: KnobView
    private lateinit var highCutKnob: KnobView
    private lateinit var lowCutPills: List<TextView>
    private lateinit var highCutPills: List<TextView>
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
    private var curTab = 0
    private var touching = false
    private var lastTouch = 0L

    private val nowPlayingListener: () -> Unit = { onNowPlayingChanged() }

    /** Layout Spotify: quando a cor da capa muda, refaz a tela com a nova cor (sem interromper um toque). */
    private val themeRunnable = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (!ThemePrefs.spotify(this@MainActivity)) return
            if (CoverColor.similar(ThemePrefs.spotifyBase(this@MainActivity), C.SP_BASE)) return
            if (touching || SystemClock.uptimeMillis() - lastTouch < 1500L) {
                handler.postDelayed(this, 700L)
                return
            }
            recreate()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val live = EqState.vizOk && EqState.running
            spectrum.feed(EqState.bars, live)
            grMeter.value = if (EqState.running) EqState.grDb else 0f
            compCurve.feed(EqState.inDb, EqState.vizOk && EqState.running)
            peakMeter.feed(EqState.peakDb, EqState.peakState, EqState.vizOk && EqState.running)
            statusView.text = EqState.status()
            miniPlayer.tick()
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
        ThemePrefs.apply(this)
        window.setBackgroundDrawable(C.backgroundDrawable())

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        setContentView(buildUi())
        applySystemBars()
        refreshFromState()
        selectTab(savedInstanceState?.getInt("tab", 0) ?: 0)
        val settingsScroll = savedInstanceState?.getInt("settingsScroll", 0) ?: 0
        if (settingsScroll > 0) pages[2].post { pages[2].scrollTo(0, settingsScroll) }
        val eqScroll = savedInstanceState?.getInt("eqScroll", 0) ?: 0
        if (eqScroll > 0) pages[0].post { pages[0].scrollTo(0, eqScroll) }
        val compScroll = savedInstanceState?.getInt("compScroll", 0) ?: 0
        if (compScroll > 0) pages[1].post { pages[1].scrollTo(0, compScroll) }

        askPermissionsOnce()
        syncService()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", curTab)
        outState.putInt("settingsScroll", pages[2].scrollY)
        outState.putInt("eqScroll", pages[0].scrollY)
        outState.putInt("compScroll", pages[1].scrollY)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> touching = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
        lastTouch = SystemClock.uptimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    /** Ícones claros ou escuros na barra de status e na de navegação, conforme o tema. */
    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        val light = !C.DARK
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            var f = window.decorView.systemUiVisibility
            val bits = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            f = if (light) f or bits else f and bits.inv()
            window.decorView.systemUiVisibility = f
        }
    }

    override fun onResume() {
        super.onResume()
        EqState.uiVisible = true
        EqState.notifyChanged()
        refreshPermsUi()
        NowPlaying.addListener(nowPlayingListener)
        refreshNowPlaying()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        EqState.uiVisible = false
        EqState.notifyChanged()
        NowPlaying.removeListener(nowPlayingListener)
        handler.removeCallbacks(themeRunnable)
        handler.removeCallbacks(ticker)
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = C.backgroundDrawable()
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
        if (off) titleView.setShadowLayer(0f, 0f, 0f, 0) else titleView.setShadowLayer(dpf(2f), 0f, dpf(1f), C.GLOW)
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
        curTab = i
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

        // barra de presets: escolher (abre a lista) e salvar o atual
        presetBtn = pill(this, "Preset")
        presetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        presetBtn.setPadding(dpi(14f), dpi(8f), dpi(14f), dpi(8f))
        presetBtn.maxLines = 1
        presetBtn.ellipsize = android.text.TextUtils.TruncateAt.END
        presetBtn.setOnClickListener { showPresetList() }
        val saveBtn = pill(this, "Salvar")
        saveBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        saveBtn.setPadding(dpi(14f), dpi(8f), dpi(14f), dpi(8f))
        saveBtn.setOnClickListener { showSavePreset() }
        val presetRow = LinearLayout(this)
        presetRow.orientation = LinearLayout.HORIZONTAL
        presetRow.addView(presetBtn, LinearLayout.LayoutParams(0, -2, 1f))
        val svlp = LinearLayout.LayoutParams(-2, -2)
        svlp.marginStart = dpi(8f)
        presetRow.addView(saveBtn, svlp)
        val prlp = LinearLayout.LayoutParams(-1, -2)
        prlp.bottomMargin = dpi(6f)
        col.addView(presetRow, prlp)

        // painel de sliders: Preamp + uma faixa por banda (em ordem de frequência)
        slidersPanel = FrameLayout(this)
        slidersPanel.background = rounded(C.PANEL, 16f, this)
        slidersRow = LinearLayout(this)
        slidersRow.orientation = LinearLayout.HORIZONTAL
        slidersRow.setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(4f))
        slidersPanel.addView(slidersRow, FrameLayout.LayoutParams(-1, -1))
        val splp = LinearLayout.LayoutParams(-1, dpi(300f))
        splp.bottomMargin = dpi(8f)
        col.addView(slidersPanel, splp)

        // gráfico: espectro (barras) + curva e pontos por cima (arraste os pontos)
        val panel = FrameLayout(this)
        graphPanel = panel
        panel.background = rounded(C.PANEL, 16f, this)
        panel.clipToOutline = true
        spectrum = SpectrumView(this)
        curve = CurveView(this)
        panel.addView(spectrum, FrameLayout.LayoutParams(-1, -1))
        panel.addView(curve, FrameLayout.LayoutParams(-1, -1))
        curve.onSelect = { refreshBandEditor() }
        curve.onEdit = { bandEdited() }
        curve.onAdd = { f, g -> addBand(f, g) }
        val plp = LinearLayout.LayoutParams(-1, dpi(150f))
        plp.bottomMargin = dpi(8f)
        graphLp = plp
        col.addView(panel, plp)

        // medidor de pico de saída (estimado) com aviso de clipping
        peakMeter = PeakMeterView(this)
        val pmlp = LinearLayout.LayoutParams(-1, dpi(44f))
        pmlp.bottomMargin = dpi(8f)
        pmlp.marginStart = dpi(4f)
        pmlp.marginEnd = dpi(4f)
        col.addView(peakMeter, pmlp)

        // cartão fino do Spotify: capa, música, play/pause e próxima
        miniPlayer = MiniPlayer(this)
        miniPlayer.onNeedAccess = { openListenerSettings() }
        val mplp = LinearLayout.LayoutParams(-1, -2)
        mplp.bottomMargin = dpi(8f)
        col.addView(miniPlayer, mplp)

        // editor da banda selecionada
        val bCard = card(this)
        bandTitle = TextView(this)
        bandTitle.setTextColor(C.INK)
        bandTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        bandTitle.typeface = FONT_MEDIUM
        bandTitle.maxLines = 1
        bandTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        val addBtn = pill(this, "+ Banda")
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        addBtn.setPadding(dpi(12f), dpi(7f), dpi(12f), dpi(7f))
        addBtn.setOnClickListener { addBand(nextBandFreq(), 0f) }
        delBandBtn = pill(this, "Remover")
        delBandBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        delBandBtn.setPadding(dpi(12f), dpi(7f), dpi(12f), dpi(7f))
        delBandBtn.setOnClickListener { removeSelectedBand() }
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(bandTitle, LinearLayout.LayoutParams(0, -2, 1f))
        val hl1 = LinearLayout.LayoutParams(-2, -2)
        hl1.marginStart = dpi(8f)
        head.addView(addBtn, hl1)
        val hl2 = LinearLayout.LayoutParams(-2, -2)
        hl2.marginStart = dpi(6f)
        head.addView(delBandBtn, hl2)
        bCard.addView(head)

        bandControls = LinearLayout(this)
        bandControls.orientation = LinearLayout.VERTICAL
        val typeRow = LinearLayout(this)
        typeRow.orientation = LinearLayout.HORIZONTAL
        val typeNames = listOf("Pico", "Grave", "Agudo")
        typeBtns = typeNames.mapIndexed { i, n ->
            val p = pill(this, n)
            p.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            p.setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
            p.setOnClickListener { setBandType(i) }
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.marginStart = if (i == 0) 0 else dpi(6f)
            typeRow.addView(p, lp)
            p
        }
        val trlp = LinearLayout.LayoutParams(-1, -2)
        trlp.topMargin = dpi(10f)
        bandControls.addView(typeRow, trlp)

        freqKnob = KnobView(this, "Freq", log10(ParamBand.MIN_F), log10(ParamBand.MAX_F), log10(1000f), false, 0.005f, false, true) {
            fmtFreq(10f.pow(it))
        }
        bandGainKnob = KnobView(this, "Gain", -ParamBand.MAX_GAIN, ParamBand.MAX_GAIN, 0f, true, 0.1f, true, true) {
            fmtDb(it) + " dB"
        }
        qKnob = KnobView(this, "Q", log10(ParamBand.MIN_Q), log10(ParamBand.MAX_Q), log10(ParamBand.DEFAULT_Q), false, 0.01f, false, true) {
            String.format(Locale.US, "%.2f", 10f.pow(it))
        }
        freqKnob.onChange = { v -> editBand { it.freq = 10f.pow(v).coerceIn(ParamBand.MIN_F, ParamBand.MAX_F) } }
        bandGainKnob.onChange = { v -> editBand { it.gain = v } }
        qKnob.onChange = { v -> editBand { it.q = 10f.pow(v).coerceIn(ParamBand.MIN_Q, ParamBand.MAX_Q) } }
        bandControls.addView(weightRow(this, freqKnob, bandGainKnob, qKnob))
        bCard.addView(bandControls)

        bandHint = bodyText(this, "Nenhuma banda. Toque em \"+ Banda\" para adicionar.", 13f)
        bandHint.setPadding(0, dpi(10f), 0, 0)
        bCard.addView(bandHint)
        bCard.addView(
            bodyText(this, "Ajuste o ganho pelos sliders. Toque duas vezes num slider para zerar.", 12f).also {
                it.setPadding(0, dpi(6f), 0, 0)
            }
        )
        col.addView(bCard)

        // preamp, Bass e Treble
        preampKnob = KnobView(this, "Preamp", -12f, 12f, 0f, true, 0.5f, true, true) { fmtDb(it) + " dB" }
        bassKnob = KnobView(this, "Bass", -12f, 12f, 0f, true, 0.5f, true, true) { fmtDb(it) + " dB" }
        trebleKnob = KnobView(this, "Treble", -12f, 12f, 0f, true, 0.5f, true, true) { fmtDb(it) + " dB" }
        preampKnob.onChange = { v -> EqState.update { it.preamp = v }; updatePresetLabel(); updateAutoPre() }
        bassKnob.onChange = { v -> EqState.update { it.bass = v }; curve.invalidate(); updatePresetLabel(); updateAutoPre() }
        trebleKnob.onChange = { v -> EqState.update { it.treble = v }; curve.invalidate(); updatePresetLabel(); updateAutoPre() }
        val kw = weightRow(this, preampKnob, bassKnob, trebleKnob)
        val kwlp = LinearLayout.LayoutParams(-1, -2)
        kwlp.bottomMargin = dpi(8f)
        col.addView(kw, kwlp)

        // preamp automático (opcional)
        val apCard = card(this)
        val apRow = LinearLayout(this)
        apRow.orientation = LinearLayout.HORIZONTAL
        apRow.gravity = Gravity.CENTER_VERTICAL
        val apTexts = LinearLayout(this)
        apTexts.orientation = LinearLayout.VERTICAL
        apTexts.addView(bodyText(this, "Preamp automático", 15f, C.INK))
        autoPreDesc = bodyText(this, "", 12f)
        apTexts.addView(autoPreDesc)
        apRow.addView(apTexts, LinearLayout.LayoutParams(0, -2, 1f))
        autoPreSwitch = newSwitch()
        autoPreSwitch.setOnCheckedChangeListener { _, on ->
            if (!syncing) {
                EqState.update { it.autoPreamp = on }
                updateAutoPre()
            }
        }
        val aplp = LinearLayout.LayoutParams(-2, -2)
        aplp.marginStart = dpi(12f)
        apRow.addView(autoPreSwitch, aplp)
        apCard.addView(apRow)
        col.addView(apCard)
        col.addView(buildCutCard())

        // Bypass e Reset
        bypassBtn = pill(this, "Bypass")
        bypassBtn.setOnClickListener {
            EqState.update { it.bypass = !it.bypass }
            stylePill(bypassBtn, EqState.s.bypass)
            curve.invalidate()
            updateAutoPre()
        }
        val resetBtn = pill(this, "Reset")
        resetBtn.setOnClickListener {
            EqState.update { s ->
                s.resetBands()
                s.preamp = 0f
                s.bass = 0f
                s.treble = 0f
            }
            curve.selected = 0
            refreshFromState()
        }
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        val bl1 = LinearLayout.LayoutParams(0, -2, 1f)
        bl1.marginEnd = dpi(4f)
        val bl2 = LinearLayout.LayoutParams(0, -2, 1f)
        bl2.marginStart = dpi(4f)
        btnRow.addView(bypassBtn, bl1)
        btnRow.addView(resetBtn, bl2)
        col.addView(btnRow, LinearLayout.LayoutParams(-1, -2))

        return page(col)
    }

    // ----- Cortes de graves e agudos

    private fun buildCutCard(): LinearLayout {
        val cc = card(this)
        cc.addView(sectionTitle(this, "Cortes", 0f))

        lowCutSwitch = newSwitch()
        lowCutSwitch.setOnCheckedChangeListener { _, on ->
            if (!syncing) { EqState.update { it.lowCutOn = on }; refreshCuts(); afterCut() }
        }
        switchRow(cc, "Corte de graves", "Tira os graves abaixo da frequência. Ajuda a proteger o alto-falante do celular.", lowCutSwitch)
        lowCutKnob = KnobView(this, "Freq", log10(20f), log10(500f), log10(80f), false, 0.005f, false, true) {
            fmtFreq(10f.pow(it))
        }
        lowCutKnob.onChange = { v -> EqState.update { it.lowCutF = 10f.pow(v) }; afterCut() }
        val lo = cutControls(lowCutKnob) { sl -> EqState.update { it.lowCutSlope = sl }; refreshCuts(); afterCut() }
        lowCutBox = lo.first
        lowCutPills = lo.second
        cc.addView(lowCutBox)

        highCutSwitch = newSwitch()
        highCutSwitch.setOnCheckedChangeListener { _, on ->
            if (!syncing) { EqState.update { it.highCutOn = on }; refreshCuts(); afterCut() }
        }
        switchRow(cc, "Corte de agudos", "Tira os agudos acima da frequência. Suaviza som muito brilhante ou chiado.", highCutSwitch)
        highCutKnob = KnobView(this, "Freq", log10(2000f), log10(20000f), log10(16000f), false, 0.005f, false, true) {
            fmtFreq(10f.pow(it))
        }
        highCutKnob.onChange = { v -> EqState.update { it.highCutF = 10f.pow(v) }; afterCut() }
        val hi = cutControls(highCutKnob) { sl -> EqState.update { it.highCutSlope = sl }; refreshCuts(); afterCut() }
        highCutBox = hi.first
        highCutPills = hi.second
        cc.addView(highCutBox)

        cc.addView(bodyText(this, "O gráfico mostra o corte. O efeito do Android limita a atenuação a cerca de 24 dB.", 12f))
        return cc
    }

    /** Knob de frequência ao lado dos botões de inclinação (12 ou 24 dB por oitava). */
    private fun cutControls(knob: KnobView, onSlope: (Int) -> Unit): Pair<LinearLayout, List<TextView>> {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(knob, LinearLayout.LayoutParams(0, -2, 1f))
        val colp = LinearLayout.LayoutParams(0, -2, 1f)
        colp.marginStart = dpi(10f)
        val pillCol = LinearLayout(this)
        pillCol.orientation = LinearLayout.VERTICAL
        val pills = listOf("12 dB/oit", "24 dB/oit").mapIndexed { i, n ->
            val p = pill(this, n)
            p.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            p.setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
            p.setOnClickListener { onSlope(i) }
            val lp = LinearLayout.LayoutParams(-1, -2)
            if (i > 0) lp.topMargin = dpi(6f)
            pillCol.addView(p, lp)
            p
        }
        row.addView(pillCol, colp)
        return Pair(row, pills)
    }

    private fun afterCut() {
        curve.invalidate()
        updateAutoPre()
    }

    private fun refreshCuts() {
        val s = EqState.s
        lowCutSwitch.isChecked = s.lowCutOn
        highCutSwitch.isChecked = s.highCutOn
        lowCutKnob.value = log10(s.lowCutF)
        highCutKnob.value = log10(s.highCutF)
        lowCutBox.visibility = if (s.lowCutOn) View.VISIBLE else View.GONE
        highCutBox.visibility = if (s.highCutOn) View.VISIBLE else View.GONE
        for (k in lowCutPills.indices) stylePill(lowCutPills[k], k == s.lowCutSlope)
        for (k in highCutPills.indices) stylePill(highCutPills[k], k == s.highCutSlope)
    }

    // ----- Editor de bandas (EQ paramétrico)

    /** Aplica a mudança na banda selecionada e atualiza o que depende dela. */
    private fun editBand(block: (ParamBand) -> Unit) {
        val i = curve.selected
        val list = EqState.s.bands
        if (i < 0 || i >= list.size) return
        EqState.update { block(list[i]) }
        curve.invalidate()
        updateBandTitle()
        updatePresetLabel()
        updateAutoPre()
    }

    /** Chamado quando o gráfico mexeu numa banda (arrastar ou zerar): salva e atualiza os knobs. */
    private fun bandEdited() {
        EqState.update { }
        val i = curve.selected
        val list = EqState.s.bands
        if (i in 0 until list.size) {
            freqKnob.value = log10(list[i].freq)
            bandGainKnob.value = list[i].gain
        }
        updateBandTitle()
        updatePresetLabel()
        updateAutoPre()
    }

    private fun typeName(t: Int) = when (t) {
        ParamBand.LOW_SHELF -> "prateleira de graves"
        ParamBand.HIGH_SHELF -> "prateleira de agudos"
        else -> "pico"
    }

    private fun updateBandTitle() {
        val i = curve.selected
        val list = EqState.s.bands
        bandTitle.text = if (i in 0 until list.size) {
            val rank = list.count { it.freq < list[i].freq } + 1
            "Banda $rank de ${list.size} \u00B7 ${typeName(list[i].type)}"
        } else {
            "Sem bandas"
        }
    }

    /** Mostra a banda selecionada nos controles (ou o aviso, se não houver bandas). */
    private fun refreshBandEditor() {
        val list = EqState.s.bands
        var i = curve.selected
        if (i >= list.size) i = list.size - 1
        if (list.isNotEmpty() && i < 0) i = 0
        if (curve.selected != i) curve.selected = i
        val has = i >= 0
        bandControls.visibility = if (has) View.VISIBLE else View.GONE
        bandHint.visibility = if (has) View.GONE else View.VISIBLE
        delBandBtn.alpha = if (has) 1f else 0.4f
        if (has) {
            val b = list[i]
            freqKnob.value = log10(b.freq)
            bandGainKnob.value = b.gain
            qKnob.value = log10(b.q)
            for (k in typeBtns.indices) stylePill(typeBtns[k], k == b.type)
        }
        updateBandTitle()
    }

    private fun setBandType(t: Int) {
        editBand { b ->
            if (b.type != t) {
                b.q = if (t == ParamBand.PEAK) ParamBand.DEFAULT_Q else ParamBand.SHELF_Q
                b.type = t
            }
        }
        refreshBandEditor()
    }

    /** Frequência para uma banda nova: o meio (em escala logarítmica) do maior vão entre as bandas. */
    private fun nextBandFreq(): Float {
        val fs = listOf(ParamBand.MIN_F) + EqState.s.bands.map { it.freq }.sorted() + listOf(ParamBand.MAX_F)
        var best = 1000f
        var bestGap = -1f
        for (k in 0 until fs.size - 1) {
            val gap = ln(fs[k + 1] / fs[k])
            if (gap > bestGap) {
                bestGap = gap
                best = sqrt(fs[k] * fs[k + 1])
            }
        }
        return best.toInt().toFloat()
    }

    private fun addBand(f: Float, g: Float) {
        val list = EqState.s.bands
        if (list.size >= ParamBand.MAX_BANDS) {
            Toast.makeText(this, "Máximo de ${ParamBand.MAX_BANDS} bandas", Toast.LENGTH_SHORT).show()
            return
        }
        EqState.update { it.bands.add(ParamBand(f.coerceIn(ParamBand.MIN_F, ParamBand.MAX_F), g)) }
        curve.selected = list.size - 1
        refreshBandEditor()
        curve.invalidate()
        updatePresetLabel()
        updateAutoPre()
    }

    private fun removeSelectedBand() {
        val i = curve.selected
        if (i < 0 || i >= EqState.s.bands.size) return
        EqState.update { it.bands.removeAt(i) }
        refreshBandEditor()
        curve.invalidate()
        updatePresetLabel()
        updateAutoPre()
    }

    /** Texto do preamp automático: quanto está sendo reduzido agora. */
    private fun updateAutoPre() {
        if (!::autoPreDesc.isInitialized) return
        val s = EqState.s
        autoPreDesc.text = if (s.autoPreamp) {
            val d = EqMath.autoPreampDb(s)
            if (d > -0.05f) "Ligado. Nenhuma redução necessária agora."
            else String.format(Locale.US, "Ligado. Reduzindo %.1f dB para evitar distorção.", -d)
        } else {
            "Quando ligado, baixa o ganho de entrada sozinho ao reforçar muito."
        }
    }

    // ----- Presets

    /** Mostra o nome do preset igual aos ajustes atuais, ou "Personalizado". */
    private fun updatePresetLabel() {
        if (!::presetBtn.isInitialized) return
        val name = Presets.current(this, EqState.s)?.name ?: "Personalizado"
        presetBtn.text = "Preset: $name  \u25BE"
        syncSliders()
    }

    // ----- Modo sliders

    private fun applyMode() {
        if (!::slidersPanel.isInitialized) return
        slidersPanel.visibility = View.VISIBLE
        graphLp.height = dpi(150f)
        graphPanel.layoutParams = graphLp
        syncSliders()
    }

    private fun shortFreq(f: Float): String =
        if (f >= 1000f) {
            val k = f / 1000f
            if (Math.abs(k - Math.round(k)) < 0.05f) "${Math.round(k)}K" else String.format(Locale.US, "%.1fK", k)
        } else String.format(Locale.US, "%.0f", f)

    /** Deixa os sliders iguais às bandas: só atualiza valores e rótulos, ou recria se as bandas mudaram. */
    private fun syncSliders() {
        if (!sliderMode || !::slidersRow.isInitialized) return
        val s = EqState.s
        val list = s.bands.sortedBy { it.freq }
        val same = sliderBands.size == list.size && sliderViews.size == list.size &&
            list.indices.all { sliderBands[it] === list[it] }
        if (!same) {
            slidersRow.removeAllViews()
            sliderViews.clear()
            val dm = resources.displayMetrics
            val avail = dm.widthPixels - dpi(12f) - dpi(12f) - dpi(14f)
            val n = list.size + 1
            val w = maxOf(dpi(14f), avail / n)

            val ps = LedSlider(this, "Preamp")
            ps.onChange = { v ->
                EqState.update { it.preamp = v }
                preampKnob.value = v
                updatePresetLabel()
                updateAutoPre()
            }
            preSlider = ps
            slidersRow.addView(ps, LinearLayout.LayoutParams(w, -1))
            val sep = View(this)
            sep.setBackgroundColor(C.LINE)
            val seplp = LinearLayout.LayoutParams(dpi(1f), -1)
            seplp.topMargin = dpi(10f)
            seplp.bottomMargin = dpi(10f)
            slidersRow.addView(sep, seplp)

            for (b in list) {
                val sl = LedSlider(this, shortFreq(b.freq))
                sl.onChange = { v ->
                    val idx = EqState.s.bands.indexOf(b)
                    EqState.update { b.gain = v }
                    if (idx >= 0 && curve.selected != idx) {
                        curve.selected = idx
                        refreshBandEditor()
                    } else if (idx >= 0) {
                        bandGainKnob.value = v
                        updateBandTitle()
                    }
                    curve.invalidate()
                    updatePresetLabel()
                    updateAutoPre()
                }
                sliderViews.add(sl)
                slidersRow.addView(sl, LinearLayout.LayoutParams(w, -1))
            }
            sliderBands = list
        }
        preSlider?.let { if (it.value != s.preamp) it.value = s.preamp }
        for (i in list.indices) {
            val sl = sliderViews[i]
            val g = list[i].gain
            if (sl.value != g) sl.value = g
            sl.label = shortFreq(list[i].freq)
        }
    }

    /** Janela com cantos arredondados no padrão do app; devolve a janela e a caixa de conteúdo. */
    private fun newDialogBox(titleText: String): Pair<Dialog, LinearLayout> {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = rounded(C.BG, 24f, this)
        box.setPadding(dpi(16f), dpi(18f), dpi(16f), dpi(16f))
        val title = TextView(this)
        title.text = titleText
        title.setTextColor(C.INK)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        title.typeface = FONT_MEDIUM
        box.addView(title)
        val wrap = FrameLayout(this)
        wrap.setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        wrap.addView(box, FrameLayout.LayoutParams(-1, -2))
        dlg.setContentView(wrap)
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return Pair(dlg, box)
    }

    private fun applyPreset(p: Preset) {
        EqState.update { p.applyTo(it) }
        refreshFromState()
    }

    private fun presetRow(p: Preset, current: Preset?, dlg: Dialog, reopen: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val selected = current != null && current.name == p.name && current.builtIn == p.builtIn
        row.background =
            if (selected) rounded(C.CARD2, 14f, this, C.PRI_DK, 2f) else rounded(C.CARD, 14f, this)
        row.setPadding(dpi(14f), 0, dpi(6f), 0)
        val name = TextView(this)
        name.text = p.name
        name.setTextColor(C.INK)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        name.typeface = if (selected) FONT_MEDIUM else Typeface.DEFAULT
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        if (!p.builtIn) {
            val del = TextView(this)
            del.text = "\u2715"
            del.contentDescription = "Apagar " + p.name
            del.gravity = Gravity.CENTER
            del.setTextColor(C.SUB)
            del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            del.setOnClickListener {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Apagar preset")
                    .setMessage("Apagar \"" + p.name + "\"?")
                    .setPositiveButton("Apagar") { _, _ ->
                        Presets.delete(this, p.name)
                        updatePresetLabel()
                        dlg.dismiss()
                        reopen()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            row.addView(del, LinearLayout.LayoutParams(dpi(44f), dpi(44f)))
        }
        row.minimumHeight = dpi(44f)
        row.setOnClickListener {
            applyPreset(p)
            dlg.dismiss()
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, dpi(3f), 0, dpi(3f))
        row.layoutParams = lp
        return row
    }

    private fun showPresetList() {
        val (dlg, box) = newDialogBox("Presets")
        val current = Presets.current(this, EqState.s)
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL

        val mine = Presets.listUser(this)
        if (mine.isNotEmpty()) {
            list.addView(sectionTitle(this, "MEUS PRESETS", 6f))
            for (p in mine) list.addView(presetRow(p, current, dlg) { showPresetList() })
        }
        list.addView(sectionTitle(this, "PADRÃO", if (mine.isEmpty()) 6f else 14f))
        for (p in Presets.BUILT_IN) list.addView(presetRow(p, current, dlg) { showPresetList() })

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, ViewGroup.LayoutParams(-1, -2))
        val maxH = (resources.displayMetrics.heightPixels * 0.6f).toInt()
        box.addView(scroll, LinearLayout.LayoutParams(-1, minOf(maxH, dpi(420f))))

        val save = pill(this, "Salvar o ajuste atual como preset")
        save.setOnClickListener {
            dlg.dismiss()
            showSavePreset()
        }
        val slp = LinearLayout.LayoutParams(-1, -2)
        slp.topMargin = dpi(10f)
        box.addView(save, slp)
        dlg.show()
    }

    private fun showSavePreset() {
        val (dlg, box) = newDialogBox("Salvar preset")
        val sub = bodyText(this, "Guarda as bandas do equalizador, o preamp, o Bass e o Treble de agora.", 13f)
        sub.setPadding(0, dpi(2f), 0, dpi(10f))
        box.addView(sub)

        val input = EditText(this)
        input.hint = "Nome do preset"
        input.setSingleLine()
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.filters = arrayOf(InputFilter.LengthFilter(Presets.MAX_NAME))
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        input.setTextColor(C.INK)
        input.setHintTextColor(C.SUB)
        input.background = rounded(C.CARD, 14f, this)
        input.setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
        box.addView(input, LinearLayout.LayoutParams(-1, -2))

        val ok = pill(this, "Salvar")
        ok.setOnClickListener {
            val name = input.text.toString().trim()
            when {
                name.isEmpty() ->
                    Toast.makeText(this, "Digite um nome", Toast.LENGTH_SHORT).show()
                Presets.isBuiltInName(name) ->
                    Toast.makeText(this, "Já existe um preset padrão com esse nome", Toast.LENGTH_SHORT).show()
                else -> {
                    val replaced = Presets.exists(this, name)
                    Presets.saveCurrent(this, name, EqState.s)
                    updatePresetLabel()
                    Toast.makeText(this, if (replaced) "Preset atualizado" else "Preset salvo", Toast.LENGTH_SHORT).show()
                    dlg.dismiss()
                }
            }
        }
        val cancel = pill(this, "Cancelar")
        cancel.setOnClickListener { dlg.dismiss() }
        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        val b1 = LinearLayout.LayoutParams(0, -2, 1f)
        b1.marginEnd = dpi(4f)
        val b2 = LinearLayout.LayoutParams(0, -2, 1f)
        b2.marginStart = dpi(4f)
        btns.addView(cancel, b1)
        btns.addView(ok, b2)
        val blp = LinearLayout.LayoutParams(-1, -2)
        blp.topMargin = dpi(12f)
        box.addView(btns, blp)
        dlg.show()
        input.requestFocus()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
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
        mbSwitch = newSwitch()
        mbSwitch.setOnCheckedChangeListener { _, on ->
            if (!syncing) {
                EqState.update { it.multiband = on }
                refreshMbUi()
            }
        }
        switchRow(cCard, "Compressor em 3 faixas", "Graves, médios e agudos comprimidos separadamente.", mbSwitch)
        grMeter = GrMeterView(this)
        cCard.addView(grMeter, LinearLayout.LayoutParams(-1, -2))
        compCurve = CompCurveView(this)
        val cclp = LinearLayout.LayoutParams(-1, dpi(190f))
        cclp.topMargin = dpi(10f)
        cCard.addView(compCurve, cclp)
        cCard.addView(
            bodyText(this, "Entrada x saída do compressor (sem o ganho de saída). O ponto é o nível do áudio agora.", 12f).also {
                it.setPadding(0, dpi(6f), 0, dpi(8f))
            }
        )
        attackKnob = knob("Attack", 1f, 200f, 10f, 1f, { String.format(Locale.US, "%.0f ms", it) }) { s, v -> s.attack = v }
        releaseKnob = knob("Release", 10f, 2000f, 120f, 10f, { String.format(Locale.US, "%.0f ms", it) }) { s, v -> s.release = v }
        ratioKnob = knob("Ratio", 1f, 20f, 1f, 0.5f, { if (it <= 1.01f) "Off" else String.format(Locale.US, "%.1f:1", it) }) { s, v -> s.ratio = v }
        thresholdKnob = knob("Threshold", -60f, 0f, -18f, 1f, { String.format(Locale.US, "%.0f dB", it) }) { s, v -> s.threshold = v }
        gainKnob = knob("Gain", -12f, 24f, 0f, 0.5f, { fmtDb(it) + " dB" }) { s, v -> s.gain = v }
        singleBox = LinearLayout(this)
        singleBox.orientation = LinearLayout.VERTICAL
        singleBox.addView(weightRow(this, attackKnob, releaseKnob, ratioKnob))
        singleBox.addView(weightRow(this, thresholdKnob, gainKnob))
        cCard.addView(singleBox)

        multiBox = LinearLayout(this)
        multiBox.orientation = LinearLayout.VERTICAL
        x1Knob = KnobView(this, "Corte 1", log10(60f), log10(1000f), log10(200f), false, 0.005f, false, true) {
            fmtFreq(10f.pow(it))
        }
        x2Knob = KnobView(this, "Corte 2", log10(1000f), log10(12000f), log10(3000f), false, 0.005f, false, true) {
            fmtFreq(10f.pow(it))
        }
        x1Knob.onChange = { v -> setCrossover(0, 10f.pow(v)) }
        x2Knob.onChange = { v -> setCrossover(1, 10f.pow(v)) }
        multiBox.addView(weightRow(this, x1Knob, x2Knob))

        val pillRow = LinearLayout(this)
        pillRow.orientation = LinearLayout.HORIZONTAL
        mbPills = listOf("Graves", "Médios", "Agudos").mapIndexed { i, n ->
            val p = pill(this, n)
            p.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            p.setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
            p.setOnClickListener { selBand = i; refreshMbBand() }
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.marginStart = if (i == 0) 0 else dpi(6f)
            pillRow.addView(p, lp)
            p
        }
        val prl = LinearLayout.LayoutParams(-1, -2)
        prl.topMargin = dpi(8f)
        multiBox.addView(pillRow, prl)
        mbRange = bodyText(this, "", 12f)
        mbRange.setPadding(0, dpi(6f), 0, 0)
        multiBox.addView(mbRange)

        mbAtkKnob = mbKnob("Attack", 1f, 200f, 10f, 1f, { String.format(Locale.US, "%.0f ms", it) }) { s, b, v -> s.mbAtk[b] = v }
        mbRelKnob = mbKnob("Release", 10f, 2000f, 120f, 10f, { String.format(Locale.US, "%.0f ms", it) }) { s, b, v -> s.mbRel[b] = v }
        mbRatioKnob = mbKnob("Ratio", 1f, 20f, 1f, 0.5f, { if (it <= 1.01f) "Off" else String.format(Locale.US, "%.1f:1", it) }) { s, b, v -> s.mbRatio[b] = v }
        mbThrKnob = mbKnob("Threshold", -60f, 0f, -18f, 1f, { String.format(Locale.US, "%.0f dB", it) }) { s, b, v -> s.mbThr[b] = v }
        mbGainKnob = mbKnob("Gain", -12f, 24f, 0f, 0.5f, { fmtDb(it) + " dB" }) { s, b, v -> s.mbGain[b] = v }
        multiBox.addView(weightRow(this, mbAtkKnob, mbRelKnob, mbRatioKnob))
        multiBox.addView(weightRow(this, mbThrKnob, mbGainKnob))
        cCard.addView(multiBox)
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
                s.resetMultiband()
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

    private fun mbKnob(
        label: String, min: Float, max: Float, def: Float, step: Float,
        fmt: (Float) -> String, apply: (EqSettings, Int, Float) -> Unit
    ): KnobView {
        val k = KnobView(this, label, min, max, def, false, step, false, false, fmt)
        k.onChange = { v ->
            val b = selBand
            EqState.update { apply(it, b, v) }
            compCurve.invalidate()
        }
        return k
    }

    /** Muda um dos cortes entre faixas, mantendo os dois separados. */
    private fun setCrossover(i: Int, f: Float) {
        EqState.update { s ->
            if (i == 0) s.x1 = f.coerceIn(60f, minOf(1000f, s.x2 / 1.5f))
            else s.x2 = f.coerceIn(maxOf(1000f, s.x1 * 1.5f), 12000f)
        }
        val s = EqState.s
        x1Knob.value = log10(s.x1)
        x2Knob.value = log10(s.x2)
        updateMbRange()
    }

    private fun updateMbRange() {
        val s = EqState.s
        mbRange.text = when (selBand) {
            0 -> "Graves: até ${fmtFreq(s.x1)}"
            1 -> "Médios: de ${fmtFreq(s.x1)} a ${fmtFreq(s.x2)}"
            else -> "Agudos: acima de ${fmtFreq(s.x2)}"
        }
    }

    /** Mostra os controles da faixa escolhida. */
    private fun refreshMbBand() {
        val s = EqState.s
        val b = selBand
        mbAtkKnob.value = s.mbAtk[b]
        mbRelKnob.value = s.mbRel[b]
        mbRatioKnob.value = s.mbRatio[b]
        mbThrKnob.value = s.mbThr[b]
        mbGainKnob.value = s.mbGain[b]
        for (k in mbPills.indices) stylePill(mbPills[k], k == b)
        updateMbRange()
        compCurve.band = if (s.multiband) b else -1
        compCurve.invalidate()
    }

    /** Alterna entre o compressor de banda larga e o de 3 faixas. */
    private fun refreshMbUi() {
        val mb = EqState.s.multiband
        singleBox.visibility = if (mb) View.GONE else View.VISIBLE
        multiBox.visibility = if (mb) View.VISIBLE else View.GONE
        grMeter.visibility = if (mb) View.GONE else View.VISIBLE
        refreshMbBand()
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

    // ----- Aparência: cor principal e modo claro/escuro

    /** Salva a escolha e recria a tela para repintar tudo com a nova paleta. */
    private fun applyTheme(accent: Int? = null, dark: Boolean? = null) {
        ThemePrefs.saveSpotify(this, false)
        if (accent != null) ThemePrefs.saveAccent(this, accent)
        if (dark != null) ThemePrefs.saveDark(this, dark)
        recreate()
    }

    private fun swatch(color: Int, selected: Boolean): FrameLayout {
        val cell = FrameLayout(this)
        val dot = View(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        if (selected) g.setStroke(dpi(3f), C.INK) else g.setStroke(dpi(1f), C.LINE)
        dot.background = g
        val m = dpi(4f)
        val dlp = FrameLayout.LayoutParams(-1, -1)
        dlp.setMargins(m, m, m, m)
        cell.addView(dot, dlp)
        if (selected) {
            val chk = TextView(this)
            chk.text = "\u2713"
            chk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            chk.typeface = Typeface.DEFAULT_BOLD
            chk.setTextColor(if (isLightColor(color)) 0xFF1B1D16.toInt() else 0xFFFFFFFF.toInt())
            chk.gravity = Gravity.CENTER
            cell.addView(chk, FrameLayout.LayoutParams(-1, -1))
        }
        cell.contentDescription = "Cor " + ThemePrefs.toHex(color)
        return cell
    }

    private fun buildAppearanceCard(): LinearLayout {
        val a = card(this)
        a.addView(sectionTitle(this, "Aparência", 0f))

        // Layout Spotify: fundo preto com degradê da cor da capa
        val spSwitch = newSwitch()
        spSwitch.isChecked = ThemePrefs.spotify(this)
        spSwitch.setOnCheckedChangeListener { _, on ->
            ThemePrefs.saveSpotify(this, on)
            recreate()
        }
        switchRow(
            a, "Layout Spotify",
            "Fundo preto com degradê da cor da capa. O app usa a cor da música que está tocando. " +
                "Escolher o fundo ou a cor abaixo desliga o Layout Spotify.",
            spSwitch
        )
        a.addView(View(this), LinearLayout.LayoutParams(-1, dpi(10f)))

        // claro / escuro
        a.addView(bodyText(this, "Fundo do app", 15f, C.INK))
        val modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.HORIZONTAL
        val lightBtn = pill(this, "Claro")
        val darkBtn = pill(this, "Escuro")
        stylePill(lightBtn, !C.DARK && !C.SPOTIFY)
        stylePill(darkBtn, C.DARK && !C.SPOTIFY)
        lightBtn.setOnClickListener { if (C.DARK || C.SPOTIFY) applyTheme(dark = false) }
        darkBtn.setOnClickListener { if (!C.DARK || C.SPOTIFY) applyTheme(dark = true) }
        val l1 = LinearLayout.LayoutParams(0, -2, 1f)
        l1.marginEnd = dpi(4f)
        val l2 = LinearLayout.LayoutParams(0, -2, 1f)
        l2.marginStart = dpi(4f)
        modeRow.addView(lightBtn, l1)
        modeRow.addView(darkBtn, l2)
        val mlp = LinearLayout.LayoutParams(-1, -2)
        mlp.topMargin = dpi(8f)
        mlp.bottomMargin = dpi(14f)
        a.addView(modeRow, mlp)

        // cor principal
        a.addView(bodyText(this, "Cor principal", 15f, C.INK))
        a.addView(bodyText(this, "Os destaques usam essa cor e os cartões, um tom mais leve dela.", 12f))

        val current = ThemePrefs.accent(this) and 0xFFFFFF
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        val perRow = 6
        var start = 0
        while (start < ACCENT_PALETTE.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0 until perRow) {
                val idx = start + j
                val lp = LinearLayout.LayoutParams(0, dpi(46f), 1f)
                if (idx < ACCENT_PALETTE.size) {
                    val color = ACCENT_PALETTE[idx]
                    val cell = swatch(color, (color and 0xFFFFFF) == current)
                    cell.setOnClickListener { applyTheme(accent = color) }
                    row.addView(cell, lp)
                } else {
                    row.addView(View(this), lp)
                }
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
            start += perRow
        }
        val glp = LinearLayout.LayoutParams(-1, -2)
        glp.topMargin = dpi(8f)
        a.addView(grid, glp)

        // código da cor (hex)
        val input = EditText(this)
        input.hint = "#B9FC00"
        input.setText(ThemePrefs.toHex(ThemePrefs.accent(this)))
        input.setSingleLine()
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.filters = arrayOf(InputFilter.LengthFilter(9))
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        input.setTextColor(C.INK)
        input.setHintTextColor(C.SUB)
        input.background = rounded(C.BG, 14f, this)
        input.setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
        val useBtn = pill(this, "Aplicar")
        useBtn.setOnClickListener {
            val c = ThemePrefs.parseHex(input.text.toString())
            if (c == null) {
                Toast.makeText(this, "Código inválido. Use 6 dígitos, por exemplo B9FC00", Toast.LENGTH_SHORT).show()
            } else {
                applyTheme(accent = c)
            }
        }
        val hexRow = LinearLayout(this)
        hexRow.orientation = LinearLayout.HORIZONTAL
        hexRow.gravity = Gravity.CENTER_VERTICAL
        hexRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val ulp = LinearLayout.LayoutParams(-2, -2)
        ulp.marginStart = dpi(8f)
        hexRow.addView(useBtn, ulp)
        val hlp = LinearLayout.LayoutParams(-1, -2)
        hlp.topMargin = dpi(10f)
        a.addView(hexRow, hlp)

        val resetBtn = pill(this, "Restaurar cor padrão")
        resetBtn.setOnClickListener { applyTheme(accent = ThemePrefs.DEFAULT_ACCENT) }
        val rlp = LinearLayout.LayoutParams(-1, -2)
        rlp.topMargin = dpi(8f)
        a.addView(resetBtn, rlp)
        return a
    }

    private fun buildSettingsPage(): ScrollView {
        val col = LinearLayout(this)

        col.addView(buildAppearanceCard())

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

        val bkCard = card(this)
        bkCard.addView(sectionTitle(this, "Backup", 0f))
        bkCard.addView(bodyText(this, "Salva tudo num arquivo (ajustes, presets, cor e ícone) para usar em outro celular.", 13f))
        val exportBtn = pill(this, "Exportar configurações")
        exportBtn.setOnClickListener { startExport() }
        val importBtn = pill(this, "Importar configurações")
        importBtn.setOnClickListener { startImport() }
        val bk1 = LinearLayout.LayoutParams(-1, -2)
        bk1.topMargin = dpi(10f)
        bkCard.addView(exportBtn, bk1)
        val bk2 = LinearLayout.LayoutParams(-1, -2)
        bk2.topMargin = dpi(6f)
        bkCard.addView(importBtn, bk2)
        col.addView(bkCard)

        val pCard = card(this)
        pCard.addView(sectionTitle(this, "Permissões", 0f))
        notifStatus = permRow(pCard, "Notificações", "") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
        micStatus = permRow(pCard, "Microfone (só para o espectro)", "") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 12)
        }
        listenerStatus = permRow(pCard, "Acesso à música (Spotify)", "") { openListenerSettings() }
        pCard.addView(
            bodyText(
                this,
                "Mostra a música no cartão e dá a cor ao Layout Spotify. No Android 13 ou mais novo, se a opção " +
                    "aparecer em cinza: Ajustes do app › ⋮ › Permitir configurações restritas.",
                12f
            ).also { it.setPadding(0, 0, 0, dpi(6f)) }
        )
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

        val lastError = CrashLog.last(this)
        if (lastError != null) {
            val eCard = card(this)
            eCard.addView(sectionTitle(this, "Último erro", 0f))
            eCard.addView(bodyText(this, "Se o app fechou sozinho, toque em Copiar e me envie o texto.", 13f))
            val errText = bodyText(this, lastError, 10.5f)
            errText.typeface = Typeface.MONOSPACE
            errText.setTextIsSelectable(true)
            errText.setPadding(0, dpi(8f), 0, dpi(8f))
            eCard.addView(errText)
            val copyBtn = pill(this, "Copiar erro")
            copyBtn.setOnClickListener {
                try {
                    val cm = getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("erro", lastError))
                    Toast.makeText(this, "Erro copiado", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { }
            }
            val clearBtn = pill(this, "Limpar")
            clearBtn.setOnClickListener {
                CrashLog.clear(this)
                recreate()
            }
            eCard.addView(copyBtn, LinearLayout.LayoutParams(-1, -2))
            val clp = LinearLayout.LayoutParams(-1, -2)
            clp.topMargin = dpi(6f)
            eCard.addView(clearBtn, clp)
            col.addView(eCard)
        }

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
        preampKnob.value = s.preamp
        autoPreSwitch.isChecked = s.autoPreamp
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

        refreshCuts()
        mbSwitch.isChecked = s.multiband
        x1Knob.value = log10(s.x1)
        x2Knob.value = log10(s.x2)
        masterSwitch.isChecked = s.master
        autoSwitch.isChecked = s.auto
        globalSwitch.isChecked = s.global
        syncing = false
        applyMode()
        refreshMbUi()
        curve.invalidate()
        refreshBandEditor()
        updateAutoPre()
        updatePresetLabel()
    }

    private fun syncService() {
        val s = EqState.s
        val i = Intent(this, EqService::class.java)
        try {
            if (s.master || s.auto) startForegroundService(i) else stopService(i)
        } catch (_: Exception) { }
    }

    // ------------------------------------------------------------ backup

    private fun startExport() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        i.putExtra(Intent.EXTRA_TITLE, "estilo-audio-backup.json")
        try {
            startActivityForResult(i, REQ_EXPORT)
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir o seletor de arquivos", Toast.LENGTH_LONG).show()
        }
    }

    private fun startImport() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        try {
            startActivityForResult(i, REQ_IMPORT)
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir o seletor de arquivos", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == REQ_EXPORT) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(Backup.export(this).toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Configurações exportadas", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Não foi possível salvar o arquivo", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == REQ_IMPORT) {
            val text = try {
                contentResolver.openInputStream(uri)?.use { s ->
                    val bytes = s.readBytes()
                    if (bytes.size > Backup.MAX_BYTES) null else String(bytes, Charsets.UTF_8)
                }
            } catch (_: Exception) { null }
            if (text == null) {
                Toast.makeText(this, "Não foi possível ler o arquivo", Toast.LENGTH_LONG).show()
                return
            }
            val err = Backup.import(this, text)
            if (err != null) {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Configurações importadas", Toast.LENGTH_SHORT).show()
                syncService()
                recreate()   // reaplica o tema e atualiza todas as telas
            }
        }
    }

    // -------------------------------------------------------- permissões

    private fun hasNotif(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasMic(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasListenerAccess(): Boolean = try {
        getSystemService(android.app.NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(this, MediaListener::class.java))
    } catch (_: Exception) {
        false
    }

    private fun openListenerSettings() {
        if (Build.VERSION.SDK_INT >= 33) {
            Toast.makeText(
                this,
                "Se a opção estiver cinza: Ajustes do app › ⋮ › Permitir configurações restritas",
                Toast.LENGTH_LONG
            ).show()
        }
        try {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir os ajustes de acesso", Toast.LENGTH_LONG).show()
        }
    }

    /** Atualiza o cartão do Spotify e, se for o caso, pede ao sistema para reconectar o leitor de música. */
    private fun refreshNowPlaying() {
        val ok = NowPlaying.connected || hasListenerAccess()
        if (ok && !NowPlaying.connected) {
            try {
                NotificationListenerService.requestRebind(ComponentName(this, MediaListener::class.java))
            } catch (_: Exception) { }
        }
        miniPlayer.refresh(ok)
        listenerStatus.text = if (ok) "Liberado" else "Necessário para o cartão da música e o Layout Spotify"
        checkSpotifyTheme()
    }

    private fun onNowPlayingChanged() {
        miniPlayer.refresh(NowPlaying.connected || hasListenerAccess())
        checkSpotifyTheme()
    }

    private fun checkSpotifyTheme() {
        if (!ThemePrefs.spotify(this)) return
        if (CoverColor.similar(ThemePrefs.spotifyBase(this), C.SP_BASE)) return
        handler.removeCallbacks(themeRunnable)
        handler.postDelayed(themeRunnable, 500L)
    }

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
