package com.estiloaudio.equalizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

/**
 * Cartão fino com a música do Spotify: capa, título, artista, play/pause, próxima e uma linha de progresso.
 * Fica abaixo do medidor de pico. As cores vêm da paleta [C] (no Layout Spotify, a cor da capa).
 */
class MiniPlayer(ctx: Context) : LinearLayout(ctx) {

    /** Chamado ao tocar no cartão quando o acesso à música ainda não foi liberado. */
    var onNeedAccess: (() -> Unit)? = null

    private val coverBox = FrameLayout(ctx)
    private val coverImage = ImageView(ctx)
    private val coverNote = TextView(ctx)
    private val titleView = TextView(ctx)
    private val artistView = TextView(ctx)
    private val playBtn = TransportButton(ctx, 0)
    private val nextBtn = TransportButton(ctx, 1)
    private val progress = ProgressStrip(ctx)

    private var access = false
    private var shownCover: Bitmap? = null

    init {
        orientation = VERTICAL
        background = rounded(C.CARD, 16f, ctx)

        val row = LinearLayout(ctx)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(4f))

        // capa com cantos arredondados (ou uma notinha quando não há capa)
        coverBox.background = rounded(C.CARD2, 8f, ctx)
        coverBox.clipToOutline = true
        coverBox.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpf(8f))
            }
        }
        coverImage.scaleType = ImageView.ScaleType.CENTER_CROP
        coverBox.addView(coverImage, FrameLayout.LayoutParams(-1, -1))
        coverNote.text = "\u266B"
        coverNote.gravity = Gravity.CENTER
        coverNote.setTextColor(C.PRI_DK)
        coverNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        coverBox.addView(coverNote, FrameLayout.LayoutParams(-1, -1))
        row.addView(coverBox, LayoutParams(dpi(44f), dpi(44f)))

        val texts = LinearLayout(ctx)
        texts.orientation = VERTICAL
        titleView.setTextColor(C.INK)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        titleView.typeface = FONT_MEDIUM
        titleView.maxLines = 1
        titleView.ellipsize = TextUtils.TruncateAt.END
        artistView.setTextColor(C.SUB)
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        artistView.maxLines = 1
        artistView.ellipsize = TextUtils.TruncateAt.END
        texts.addView(titleView)
        texts.addView(artistView)
        val tlp = LayoutParams(0, -2, 1f)
        tlp.marginStart = dpi(10f)
        tlp.marginEnd = dpi(6f)
        row.addView(texts, tlp)

        playBtn.contentDescription = "Tocar ou pausar"
        playBtn.setOnClickListener { togglePlay() }
        row.addView(playBtn, LayoutParams(dpi(40f), dpi(40f)))
        nextBtn.contentDescription = "Próxima música"
        nextBtn.setOnClickListener { skipNext() }
        val nlp = LayoutParams(dpi(40f), dpi(40f))
        nlp.marginStart = dpi(2f)
        row.addView(nextBtn, nlp)
        addView(row, LayoutParams(-1, -2))

        val plp = LayoutParams(-1, dpi(3f))
        plp.setMargins(dpi(10f), 0, dpi(10f), dpi(7f))
        addView(progress, plp)

        // toque no cartão: libera o acesso, ou abre o Spotify
        isClickable = true
        setOnClickListener {
            if (!access) {
                onNeedAccess?.invoke()
            } else if (NowPlaying.hasSession) {
                try {
                    NowPlaying.controller?.sessionActivity?.send()
                } catch (_: Exception) { }
            }
        }
        refresh(false)
    }

    /** Atualiza textos, capa e botões. [hasAccess] = o acesso a notificações está liberado. */
    fun refresh(hasAccess: Boolean) {
        access = hasAccess
        val live = hasAccess && NowPlaying.hasSession
        when {
            !hasAccess -> {
                titleView.text = "Mostrar a música do Spotify"
                artistView.text = "Toque aqui para liberar o acesso"
            }
            !NowPlaying.hasSession -> {
                titleView.text = "Nada tocando no Spotify"
                artistView.text = "Dê play numa música para ela aparecer aqui"
            }
            else -> {
                titleView.text = if (NowPlaying.title.isEmpty()) "Spotify" else NowPlaying.title
                artistView.text = NowPlaying.artist
            }
        }
        artistView.visibility = if (artistView.text.isNullOrEmpty()) GONE else VISIBLE

        val bmp = if (live) NowPlaying.cover else null
        if (bmp !== shownCover) {
            shownCover = bmp
            coverImage.setImageBitmap(bmp)
        }
        coverImage.visibility = if (bmp != null) VISIBLE else GONE
        coverNote.visibility = if (bmp != null) GONE else VISIBLE

        playBtn.visibility = if (live) VISIBLE else GONE
        nextBtn.visibility = if (live) VISIBLE else GONE
        playBtn.playing = NowPlaying.playing
        progress.visibility = if (live && NowPlaying.durationMs > 0L) VISIBLE else INVISIBLE
        progress.fraction = if (live) NowPlaying.fraction() else 0f
    }

    /** Chamado várias vezes por segundo pela tela: move a linha de progresso. */
    fun tick() {
        if (progress.visibility == VISIBLE) progress.fraction = NowPlaying.fraction()
    }

    private fun togglePlay() {
        val tc = NowPlaying.controller?.transportControls ?: return
        if (NowPlaying.playing) tc.pause() else tc.play()
    }

    private fun skipNext() {
        NowPlaying.controller?.transportControls?.skipToNext()
    }

    // ---------------------------------------------------------------- peças

    /** Botão redondo de play/pause (kind 0) ou ícone de próxima (kind 1), desenhados no código. */
    private class TransportButton(ctx: Context, private val kind: Int) : View(ctx) {
        var playing = false
            set(v) {
                if (field != v) {
                    field = v
                    invalidate()
                }
            }

        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val rect = RectF()

        init {
            isClickable = true
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            alpha = if (isPressed) 0.6f else 1f
        }

        override fun onDraw(c: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = min(width, height) / 2f
            p.style = Paint.Style.FILL
            if (kind == 0) {
                p.color = C.PRI
                c.drawCircle(cx, cy, r * 0.86f, p)
                p.color = C.ON_PRI
            } else {
                p.color = C.INK
            }
            val s = r * 0.42f
            path.reset()
            if (kind == 0 && !playing) {
                // triângulo de play (deslocado um pouco para a direita para parecer centralizado)
                path.moveTo(cx - s * 0.55f, cy - s)
                path.lineTo(cx - s * 0.55f, cy + s)
                path.lineTo(cx + s * 0.95f, cy)
                path.close()
                c.drawPath(path, p)
            } else if (kind == 0) {
                val bw = s * 0.55f
                val gap = s * 0.45f
                rect.set(cx - gap / 2f - bw, cy - s, cx - gap / 2f, cy + s)
                c.drawRoundRect(rect, bw * 0.25f, bw * 0.25f, p)
                rect.set(cx + gap / 2f, cy - s, cx + gap / 2f + bw, cy + s)
                c.drawRoundRect(rect, bw * 0.25f, bw * 0.25f, p)
            } else {
                path.moveTo(cx - s * 0.8f, cy - s * 0.8f)
                path.lineTo(cx - s * 0.8f, cy + s * 0.8f)
                path.lineTo(cx + s * 0.45f, cy)
                path.close()
                c.drawPath(path, p)
                rect.set(cx + s * 0.6f, cy - s * 0.8f, cx + s * 0.6f + s * 0.3f, cy + s * 0.8f)
                c.drawRect(rect, p)
            }
        }
    }

    /** Linha fina de progresso da faixa. */
    private class ProgressStrip(ctx: Context) : View(ctx) {
        var fraction = 0f
            set(v) {
                if (abs(field - v) > 0.0015f) {
                    field = v
                    invalidate()
                }
            }

        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            p.color = C.TRACK
            rect.set(0f, 0f, w, h)
            c.drawRoundRect(rect, h / 2f, h / 2f, p)
            p.color = C.PRI
            rect.set(0f, 0f, w * fraction.coerceIn(0f, 1f), h)
            c.drawRoundRect(rect, h / 2f, h / 2f, p)
        }
    }
}
