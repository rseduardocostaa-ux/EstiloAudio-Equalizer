package com.estiloaudio.equalizer

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Visualizer
import android.os.SystemClock
import kotlin.math.abs
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
    private var cutoffs = FloatArray(0)
    private var centers = FloatArray(0)

    private var grSmooth = 0f
    private var lastWave = 0L

    fun attach(id: Int) {
        release()
        session = id

        for (n in intArrayOf(32, 16, 10)) {
            try {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true, n,   // pré-EQ
                    true, 1,   // MBC (1 banda = compressor de banda larga)
                    false, 0,  // pós-EQ
                    true       // limiter
                ).build()
                dp = DynamicsProcessing(0, id, cfg)
                nb = n
                break
            } catch (e: Throwable) {
                dp = null
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
        EqState.grDb = 0f
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
        val pre = if (s.bypass) 0f else s.preamp
        val bal = s.balance / 100f
        val l = if (bal > 0f) gainDb(1f - bal) else 0f
        val r = if (bal < 0f) gainDb(1f + bal) else 0f
        d.setInputGainbyChannel(0, pre + l)
        d.setInputGainbyChannel(1, pre + r)

        for (i in 0 until nb) {
            val g = EqMath.responseDb(s, centers[i]).coerceIn(-24f, 24f)
            d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffs[i], g))
        }

        d.setMbcBandAllChannelsTo(
            0,
            DynamicsProcessing.MbcBand(
                true, 20000f, s.attack, s.release, s.ratio, s.threshold,
                3f, -90f, 1f, 0f, (s.gain + s.volume).coerceIn(-60f, 30f)
            )
        )

        d.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, s.limOn, 0, 1f, s.limRel, 10f, s.limThr, 0f)
        )
    }

    // ---------- Espectro e medidor ----------

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
            val f1 = 31.25f * 2f.pow(9f * (b + 1) / bars)
            val k0 = (f0 / binHz).roundToInt().coerceIn(1, half - 1)
            val k1 = (f1 / binHz).roundToInt().coerceIn(k0, half - 1)
            var m = 0f
            for (k in k0..k1) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                m = max(m, sqrt(re * re + im * im))
            }
            val fc = sqrt(f0 * f1)
            val db = 20f * log10(m + 1f) + 3f * max(0f, log2(fc / 100f))
            out[b] = ((db - 8f) / 40f).coerceIn(0f, 1f)
        }
        EqState.bars = out
    }

    private fun onWave(w: ByteArray) {
        var peak = 0
        for (x in w) {
            val v = abs((x.toInt() and 0xFF) - 128)
            if (v > peak) peak = v
        }
        val lvl = if (peak <= 0) -90f else 20f * log10(peak / 128f)
        val s = EqState.s

        // Estimativa do ganho reduzido (o Android não informa o valor real).
        var comp = 0f
        if (s.ratio > 1f && lvl > s.threshold) comp = (lvl - s.threshold) * (1f - 1f / s.ratio)
        val outLvl = lvl - comp + s.gain + s.volume
        val lim = if (s.limOn && outLvl > s.limThr) outLvl - s.limThr else 0f
        val target = comp + lim

        val now = SystemClock.uptimeMillis()
        val dt = if (lastWave == 0L) 50f else (now - lastWave).coerceIn(5L, 500L).toFloat()
        lastWave = now
        val tau = if (target > grSmooth) s.attack else s.release
        grSmooth += (target - grSmooth) * (1f - exp(-dt / max(1f, tau)))
        EqState.grDb = grSmooth
    }
}
