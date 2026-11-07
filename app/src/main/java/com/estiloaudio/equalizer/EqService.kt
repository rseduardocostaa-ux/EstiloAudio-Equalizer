package com.estiloaudio.equalizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class EqService : Service() {

    companion object {
        const val SPOTIFY = "com.spotify.music"
        const val ACTION_TOGGLE = "com.estiloaudio.equalizer.TOGGLE"
        const val ACTION_REFRESH = "com.estiloaudio.equalizer.REFRESH"
        private const val CHANNEL = "eq_service"
        private const val NOTIF_ID = 42
    }

    private lateinit var engine: AudioEngine
    private val handler = Handler(Looper.getMainLooper())
    private var spotifySession = -1
    private var lastText = ""

    private val applyRunnable = Runnable { reconcile() }
    private val listener: () -> Unit = {
        handler.removeCallbacks(applyRunnable)
        handler.postDelayed(applyRunnable, 30)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (EqState.s.global) return
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            if (pkg != SPOTIFY) return
            val sid = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (sid < 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    spotifySession = sid
                    EqState.sessionOpen = true
                    engine.attach(sid)
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    if (sid == spotifySession) {
                        spotifySession = -1
                        EqState.sessionOpen = false
                        engine.release()
                    }
                }
            }
            reconcile()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        EqState.ensure(this)
        engine = AudioEngine()
        createChannel()
        if (!goForeground()) {
            stopSelf()
            return
        }

        EqState.listeners.add(listener)

        val f = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, f)
        }
        reconcile()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // O Android exige chamar startForeground() a CADA startForegroundService(), mesmo com o serviço já rodando.
        // Sem isso, o app fecha sozinho com "ForegroundServiceDidNotStartInTime".
        val ok = goForeground()
        if (!ok || !::engine.isInitialized) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_TOGGLE -> EqState.update { it.master = !it.master }
            ACTION_REFRESH -> {
                engine.restartViz()
                reconcile()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        EqState.listeners.remove(listener)
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(receiver) } catch (_: Exception) { }
        if (::engine.isInitialized) engine.release()
        EqState.running = false
        EqState.sessionOpen = false
        super.onDestroy()
    }

    private fun reconcile() {
        val s = EqState.s

        if (!s.master && !s.auto) {
            engine.release()
            EqState.sessionOpen = false
            EqState.running = false
            stopSelf()
            return
        }

        if (s.global) {
            if (s.master && engine.session != 0) engine.attach(0)
            if (!s.master && engine.session == 0) engine.release()
            EqState.sessionOpen = engine.session == 0
        } else if (engine.session == 0) {
            engine.release()
            EqState.sessionOpen = false
        }

        val active = engine.session >= 0 && (s.master || (s.auto && EqState.sessionOpen))
        EqState.running = active
        engine.apply(s, active)

        val text = EqState.status()
        if (text != lastText) {
            lastText = text
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        }
    }

    /** Põe o serviço em primeiro plano (com a notificação). Devolve false se o Android não deixou. */
    private fun goForeground(): Boolean {
        return try {
            val n = buildNotification(EqState.status())
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, n)
            }
            true
        } catch (e: Exception) {
            CrashLog.report(e)
            false
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Equalizador em segundo plano", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleIntent = Intent(this, EqService::class.java).setAction(ACTION_TOGGLE)
        val toggle = PendingIntent.getService(
            this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val label = if (EqState.s.master) "Desligar" else "Ligar"
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat), label, toggle
        ).build()

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Estilo Áudio Equalizer")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action)
            .build()
    }
}
