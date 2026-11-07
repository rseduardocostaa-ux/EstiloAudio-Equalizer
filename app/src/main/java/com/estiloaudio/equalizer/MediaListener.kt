package com.estiloaudio.equalizer

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lê a música do Spotify (título, artista, capa e play/pause) pela sessão de mídia.
 * Exige o acesso a notificações, liberado pelo usuário nos ajustes do Android.
 */
class MediaListener : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var current: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> safe { pick(list) } }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            safe { readMetadata(metadata) }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            safe {
                NowPlaying.setState(state)
                NowPlaying.notifyChanged()
            }
        }

        override fun onSessionDestroyed() {
            safe { drop() }
        }
    }

    /** Qualquer erro aqui é guardado em Ajustes em vez de derrubar o app. */
    private fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            CrashLog.report(e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NowPlaying.connected = true
        try {
            val cn = ComponentName(this, MediaListener::class.java)
            val m = getSystemService(MediaSessionManager::class.java)
            manager = m
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            pick(m.getActiveSessions(cn))
        } catch (e: Throwable) {
            CrashLog.report(e)
        }
        NowPlaying.notifyChanged()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NowPlaying.connected = false
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) { }
        drop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        safe { handlePosted(sbn) }
    }

    private fun handlePosted(sbn: StatusBarNotification?) {
        // plano B da capa: a imagem grande da notificação do Spotify (só se a sessão não trouxe a capa)
        if (sbn == null || sbn.packageName != NowPlaying.SPOTIFY) return
        if (NowPlaying.cover != null) return
        val t = notifTitle(sbn.notification)
        if (t != null && t == NowPlaying.title) {
            val bmp = largeIcon(sbn.notification)
            if (bmp != null) {
                setCover(bmp)
                NowPlaying.notifyChanged()
            }
        }
    }

    private fun pick(list: List<MediaController>?) {
        val c = list?.firstOrNull { it.packageName == NowPlaying.SPOTIFY }
        if (c == null) {
            drop()
            return
        }
        if (current?.sessionToken == c.sessionToken) return
        try {
            current?.unregisterCallback(callback)
        } catch (_: Exception) { }
        current = c
        NowPlaying.controller = c
        NowPlaying.hasSession = true
        c.registerCallback(callback, main)
        NowPlaying.setState(c.playbackState)
        readMetadata(c.metadata)
        NowPlaying.notifyChanged()
    }

    private fun drop() {
        try {
            current?.unregisterCallback(callback)
        } catch (_: Exception) { }
        current = null
        NowPlaying.controller = null
        NowPlaying.hasSession = false
        NowPlaying.playing = false
        NowPlaying.notifyChanged()
    }

    private fun readMetadata(m: MediaMetadata?) {
        if (m == null) return
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        var bmp: Bitmap? = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp == null) bmp = coverFromNotifications(title)

        val newTrack = title != NowPlaying.title || artist != NowPlaying.artist
        NowPlaying.title = title
        NowPlaying.artist = artist
        NowPlaying.durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (bmp != null) {
            if (bmp !== NowPlaying.cover) setCover(bmp)
        } else if (newTrack) {
            NowPlaying.cover = null
        }
        NowPlaying.notifyChanged()
    }

    private fun setCover(bmp: Bitmap) {
        NowPlaying.cover = bmp
        val base = CoverColor.extract(bmp)
        if (base != 0) ThemePrefs.saveCover(this, base)
    }

    private fun notifTitle(n: Notification): String? =
        n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun coverFromNotifications(title: String): Bitmap? {
        if (title.isEmpty()) return null
        try {
            val all = getActiveNotifications() ?: return null
            for (sbn in all) {
                if (sbn.packageName != NowPlaying.SPOTIFY) continue
                if (notifTitle(sbn.notification) != title) continue
                val b = largeIcon(sbn.notification)
                if (b != null) return b
            }
        } catch (_: Exception) { }
        return null
    }

    private fun largeIcon(n: Notification): Bitmap? {
        try {
            val icon = n.getLargeIcon() ?: return null
            val d = icon.loadDrawable(this) ?: return null
            if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
            val w = d.intrinsicWidth.coerceAtLeast(1)
            val h = d.intrinsicHeight.coerceAtLeast(1)
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, w, h)
            d.draw(Canvas(b))
            return b
        } catch (_: Exception) {
            return null
        }
    }
}
