package com.estiloaudio.equalizer

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Visualizer
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Liga os efeitos do Android (DynamicsProcessing, BassBoost) e o Visualizer
 * a uma sessão de áudio (a do Spotify, ou a global 0 no modo experimental).
 */
class AudioEngine {
    var session = -1
        private set

    private var dp: DynamicsProcessing? = null
    private var bb: BassBoost? = null
    private var viz: Visualizer? = null

    private var nb = 0
    private var mbcN = 1          // faixas do compressor que o efeito realmente tem
    private var mbcReq = false     // se o efeito foi criado pedindo 3 faixas
    private var cutoffs = FloatArray(0)
    private var centers = FloatArray(0)

    private var grSmooth = 0f
    private var lastWave = 0L

    fun attach(id: Int) {
        release()
        session = id

        val want3 = EqState.s.multiband
        mbcReq = want3
        mbcN = 1
        outer@ for (m in (if (want3) intArrayOf(3, 1) else intArrayOf(1))) {
            for (n in intArrayOf(64, 32, 16, 10)) {
                try {
                    val cfg = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2,
                        true, n,   // pré-EQ
                        true, m,   // MBC (1 banda = compressor de banda larga; 3 = graves/médios/agudos)
                        false, 0,  // pós-EQ
                        true       // limiter
                    ).build()
                    dp = DynamicsProcessing(0, id, cfg)
                    nb = n
                    mbcN = m
                    break@outer
                } catch (e: Throwable) {
                    dp = null
                }
            }
        }
        if (dp != null) {
            cutoffs = FloatArray(nb) { 20f * 1000f.pow((it + 1f) / nb) }
            centers = FloatArray(nb) { 20f * 1000f.pow((it + 0.5f) / nb) }
            cutoffs[nb - 1] = 20000f
        }

        try {
            bb = BassBoost(0, id)
        } catch (e: Throwable) {
            bb = null
        }

        createViz(id)
    }

    /** Recria só o Visualizer (por exemplo, depois de a permissão do microfone ser concedida). */
    fun restartViz() {
        if (session < 0) return
        releaseViz()
        createViz(session)
    }

    private fun createViz(id: Int) {
        try {
            val v = Visualizer(id)
            v.setEnabled(false)
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform != null) onWave(waveform)
                }

                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) onFft(fft, samplingRate)
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true)
            viz = v
            EqState.vizOk = true
        } catch (e: Throwable) {
            viz = null
            EqState.vizOk = false
        }
    }

    private fun releaseViz() {
        try { viz?.setEnabled(false) } catch (_: Throwable) { }
        try { viz?.release() } catch (_: Throwable) { }
        viz = null
        EqState.vizOk = false
        EqState.bars = FloatArray(EqState.BARS)
        barsSm = FloatArray(0)
        EqState.grDb = 0f
        EqState.peakDb = -90f
        EqState.inDb = -90f
        EqState.peakState = 0
        grSmooth = 0f
    }

    fun release() {
        releaseViz()
        try { dp?.setEnabled(false) } catch (_: Throwable) { }
        try { dp?.release() } catch (_: Throwable) { }
        dp = null
        try { bb?.setEnabled(false) } catch (_: Throwable) { }
        try { bb?.release() } catch (_: Throwable) { }
        bb = null
        session = -1
    }

    fun apply(s: EqSettings, active: Boolean) {
        // trocar entre 1 e 3 faixas exige recriar o efeito
        if (session >= 0 && s.multiband != mbcReq) attach(session)
        val d = dp
        if (d != null) {
            try {
                d.setEnabled(active)
                if (active) applyDp(d, s)
            } catch (_: Throwable) { }
        }

        val b = bb
        if (b != null) {
            try {
                val str = if (!s.bypass && s.bass > 0f) (s.bass / 12f * 600f).toInt() else 0
                if (b.getStrengthSupported()) b.setStrength(str.coerceIn(0, 1000).toShort())
                b.setEnabled(active && str > 0)
            } catch (_: Throwable) { }
        }

        val v = viz
        if (v != null) {
            try {
                val want = active && EqState.uiVisible
                if (v.getEnabled() != want) v.setEnabled(want)
            } catch (_: Throwable) { }
        }
    }

    private fun gainDb(x: Float): Float = 20f * log10(max(x, 0.001f))

    private fun applyDp(d: DynamicsProcessing, s: EqSettings) {
        val pre = if (s.bypass) 0f else (s.preamp + if (s.autoPreamp) EqMath.autoPreampDb(s) else 0f).coerceIn(-40f, 20f)
        val bal = s.balance / 100f
        val l = if (bal > 0f) gainDb(1f - bal) else 0f
        val r = if (bal < 0f) gainDb(1f + bal) else 0f
        d.setInputGainbyChannel(0, pre + l)
        d.setInputGainbyChannel(1, pre + r)

        for (i in 0 until nb) {
            val g = EqMath.responseDb(s, centers[i]).coerceIn(-24f, 24f)
            d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffs[i], g))
        }

        if (s.multiband && mbcN == 3) {
            val cuts = floatArrayOf(s.x1, s.x2, 20000f)
            for (i in 0 until 3) {
                d.setMbcBandAllChannelsTo(
                    i,
                    DynamicsProcessing.MbcBand(
                        true, cuts[i], s.mbAtk[i], s.mbRel[i], s.mbRatio[i], s.mbThr[i],
                        3f, -90f, 1f, 0f, (s.mbGain[i] + s.volume).coerceIn(-60f, 30f)
                    )
                )
            }
        } else {
            d.setMbcBandAllChannelsTo(
                0,
                DynamicsProcessing.MbcBand(
                    true, 20000f, s.attack, s.release, s.ratio, s.threshold,
                    3f, -90f, 1f, 0f, (s.gain + s.volume).coerceIn(-60f, 30f)
                )
            )
        }

        d.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, s.limOn, 0, 1f, s.limRel, 10f, s.limThr, 0f)
        )
    }

    // ---------- Espectro e medidor ----------

    private var barsSm = FloatArray(0)

    private fun binMag(fft: ByteArray, k: Int): Float {
        val re = fft[2 * k].toFloat()
        val im = fft[2 * k + 1].toFloat()
        return sqrt(re * re + im * im)
    }

    private fun onFft(fft: ByteArray, srMilli: Int) {
        val n = fft.size
        val sr = srMilli / 1000f
        if (n < 8 || sr <= 0f) return
        val binHz = sr / n
        val half = n / 2
        val bars = EqState.BARS
        val out = FloatArray(bars)
        for (b in 0 until bars) {
            val f0 = 31.25f * 2f.pow(9f * b / bars)
            val f1 = 31.25f * 2f.pow(9f * (b + 1f) / bars)
            val fc = sqrt(f0 * f1)
            val x0 = f0 / binHz
            val x1 = f1 / binHz
            val m: Float
            if (x1 - x0 < 1f) {
                // faixa mais estreita que um bin (graves): interpola entre os bins vizinhos,
                // em vez de todas as barras repetirem o mesmo valor
                val x = (fc / binHz).coerceIn(1f, (half - 2).toFloat())
                val k = x.toInt()
                val fr = x - k
                var v = binMag(fft, k) * (1f - fr) + binMag(fft, k + 1) * fr
                if (fc < binHz) v *= fc / binHz   // abaixo do primeiro bin quase não há dado: desce suave
                m = v
            } else {
                // faixa larga: média de energia dos bins (mais estável que pegar só o maior)
                val k0 = ceil(x0).toInt().coerceIn(1, half - 1)
                val k1 = x1.toInt().coerceIn(k0, half - 1)
                var sum = 0f
                for (k in k0..k1) {
                    val v = binMag(fft, k)
                    sum += v * v
                }
                m = sqrt(sum / (k1 - k0 + 1))
            }
            val db = 20f * log10(m + 1f) + 2f * max(0f, log2(fc / 100f))
            out[b] = ((db - 12f) / 38f).coerceIn(0f, 1f)
        }
        // suaviza no tempo para não ficar tremendo com música lenta ou com poucos instrumentos
        if (barsSm.size != bars) barsSm = FloatArray(bars)
        for (b in 0 until bars) barsSm[b] += (out[b] - barsSm[b]) * 0.35f
        EqState.bars = barsSm.copyOf()
    }

    private fun onWave(w: ByteArray) {
        var peak = 0
        for (x in w) {
            val v = abs((x.toInt() and 0xFF) - 128)
            if (v > peak) peak = v
        }
        val lvl = if (peak <= 0) -90f else 20f * log10(peak / 128f)
        val s = EqState.s
        EqState.inDb = lvl

        // Estimativa do ganho reduzido (o Android não informa o valor real).
        // Em 3 faixas não dá para estimar por faixa: considera sem compressão e o maior ganho.
        val mb = s.multiband && mbcN == 3
        val gainSum = if (mb) (s.mbGain.maxOrNull() ?: 0f) + s.volume else s.gain + s.volume
        var comp = 0f
        if (!mb && s.ratio > 1f && lvl > s.threshold) comp = (lvl - s.threshold) * (1f - 1f / s.ratio)
        val outLvl = lvl - comp + gainSum
        val lim = if (s.limOn && outLvl > s.limThr) outLvl - s.limThr else 0f
        val target = comp + lim

        // Pico de saída estimado (antes do limiter): nível medido + preamp + ganhos - compressão.
        val pre = if (s.bypass) 0f else (s.preamp + if (s.autoPreamp) EqMath.autoPreampDb(s) else 0f).coerceIn(-40f, 20f)
        val est = lvl + pre + gainSum - comp
        EqState.peakDb = est
        EqState.peakState = when {
            est > 0f && !s.limOn -> 2          // passaria de 0 dBFS sem limiter: clipping
            s.limOn && est > s.limThr + 2f -> 1   // o limiter está segurando o pico com folga (a medida é estimada)
            else -> 0
        }

        val now = SystemClock.uptimeMillis()
        val dt = if (lastWave == 0L) 50f else (now - lastWave).coerceIn(5L, 500L).toFloat()
        lastWave = now
        val tau = if (target > grSmooth) s.attack else s.release
        grSmooth += (target - grSmooth) * (1f - exp(-dt / max(1f, tau)))
        EqState.grDb = grSmooth
    }
}
