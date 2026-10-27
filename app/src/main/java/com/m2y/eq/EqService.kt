package com.m2y.eq

import helium314.keyboard.latin.R

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock

/**
 * Foreground service that owns the audio effects; all effect work runs on one background thread.
 * During phone / VoIP calls the effects are removed, so the hands-free echo canceller sees the plain
 * loudspeaker signal (an EQ curve in that path make echo cancellation worse) and voice stays natural.
 */
class EqService : Service() {
    private val thread = HandlerThread("m2y-eq").apply { start() }
    private val worker = Handler(thread.looper)
    private val engine = EqEngine()
    private lateinit var store: EqStore
    private lateinit var audio: AudioManager
    @Volatile private var globalOk = false // written on the worker, read by the session receiver on the main thread
    @Volatile private var inCall = false
    private var lastApplied: EqSettings? = null // non-null only while the EQ is meant to be running (enabled, no call)
    private var retryAt = 0L // elapsedRealtime before which a refused global session is not tried again by the poll
    private val playerSessions = mutableSetOf<Int>() // worker thread only; followed when session 0 is refused
    private var inForeground = false
    private var shownText: String? = null
    private var builder: Notification.Builder? = null

    // Players announce their sessions; we only follow them when the global mix (session 0) was refused.
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR)
            if (session <= 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> if (!globalOk) worker.post {
                    if (globalOk) return@post // the global mix was set up meanwhile
                    playerSessions += session
                    store.load().takeIf { it.enabled && !inCall }?.let { engine.attach(session, it) }
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> worker.post {
                    playerSessions -= session
                    engine.detach(session)
                }
            }
        }
    }

    private val playbackWatch = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) = checkCall()
    }
    private val recordingWatch = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) = checkCall()
    }

    override fun onCreate() {
        super.onCreate()
        store = EqStore(this)
        audio = getSystemService(AudioManager::class.java)
        registerReceiver(sessionReceiver, IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        })
        audio.registerAudioPlaybackCallback(playbackWatch, worker)
        audio.registerAudioRecordingCallback(recordingWatch, worker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!inForeground) { // later starts only re-apply the settings, without notification churn
            startForeground(NOTIFICATION_ID, notification(getString(R.string.eq_notification_on, "…")))
            inForeground = true
        }
        worker.post {
            inCall = callActive()
            applyStored()
            checkCall()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        audio.unregisterAudioPlaybackCallback(playbackWatch)
        audio.unregisterAudioRecordingCallback(recordingWatch)
        unregisterReceiver(sessionReceiver)
        worker.post { engine.releaseAll() }
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Runs on the worker thread. */
    private fun applyStored() {
        val s = store.load()
        val text = when {
            // Referans: no effect stays in the audio path, so the factory Infinity chain gets the stream untouched
            !s.enabled -> { release(); getString(R.string.eq_notification_on, "Referans (kapalı)") }
            inCall -> { release(); getString(R.string.eq_notification_on, "görüşme sürüyor, EQ beklemede") }
            else -> {
                lastApplied?.takeIf { globalOk && it != s }?.let { from ->
                    val (a, z) = from.toEngine() to s.toEngine() // both FINE layout, so 10↔25 mode switches fade too
                    if (bigJump(a, z)) for (step in 1 until RAMP_STEPS) { // short fade so a profile change does not click
                        engine.applyAll(a.lerp(z, step / RAMP_STEPS.toFloat()))
                        SystemClock.sleep(RAMP_STEP_MS)
                    }
                }
                globalOk = engine.attach(GLOBAL_SESSION, s)
                lastApplied = s
                if (globalOk) { // the global mix now carries the EQ; player effects on top would apply it twice
                    playerSessions.forEach(engine::detach)
                    playerSessions.clear()
                } else {
                    retryAt = SystemClock.elapsedRealtime() + RETRY_MS
                    playerSessions.forEach { engine.attach(it, s) } // follow players, also after a call
                }
                if (globalOk) getString(R.string.eq_notification_on, "Hi-Fi DSP") else getString(R.string.eq_error_effect, "genel ses oturumu reddedildi")
            }
        }
        if (text != shownText) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    /** Slider steps (0.5 dB) cannot click audibly; only profile-sized jumps need the fade (fewer binder calls). */
    private fun bigJump(a: EqSettings, z: EqSettings): Boolean =
        maxOf(a.gains.zip(z.gains).maxOfOrNull { (x, y) -> kotlin.math.abs(x - y) } ?: 0f,
            kotlin.math.abs(a.bassDb - z.bassDb), kotlin.math.abs(a.loudnessDb - z.loudnessDb)) >= RAMP_MIN_JUMP_DB

    // Android 10 has no audio-mode listener (API 31+) and modem / Bluetooth hands-free calls raise no playback event,
    // so the mode is also polled on a short timer (one cheap AudioManager query).
    private val callPoll = Runnable { checkCall() }

    private fun release() {
        engine.releaseAll()
        lastApplied = null
    }

    private fun checkCall() {
        worker.removeCallbacks(callPoll)
        val now = callActive()
        if (now != inCall) {
            inCall = now
            applyStored()
        } else if (!inCall && lastApplied != null && effectLost()) {
            applyStored()
        }
        worker.postDelayed(callPoll, CALL_POLL_MS)
    }

    // The global effect dies with the audio server (or was refused at boot, before the server was ready): rebuild it.
    private fun effectLost(): Boolean =
        if (globalOk) !engine.probe(GLOBAL_SESSION) else SystemClock.elapsedRealtime() >= retryAt

    // Wake-word / assistant capture (VOICE_RECOGNITION) is deliberately ignored: BYD's assistant listens often,
    // and bypassing the EQ for it would leave music unprocessed most of the time.
    private fun callActive(): Boolean =
        audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_VOICE_COMMUNICATION } ||
            audio.activeRecordingConfigurations.any { it.clientAudioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION }

    private fun notification(text: String): Notification {
        shownText = text
        val b = builder ?: run {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.eq_channel_name), NotificationManager.IMPORTANCE_MIN))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_eq)
                .setContentTitle(getString(R.string.tools_app_name))
                .setContentIntent(open)
                .setOngoing(true)
                .also { builder = it }
        }
        return b.setContentText(text).build()
    }

    companion object {
        private const val GLOBAL_SESSION = 0
        private const val CHANNEL = "eq"
        private const val NOTIFICATION_ID = 1
        private const val CALL_POLL_MS = 2000L
        private const val RETRY_MS = 20_000L // pause between attempts while the platform keeps refusing the global session
        private const val RAMP_STEPS = 4      // 4 × 10 ms ≈ 40 ms: long enough to avoid a click, short enough to feel instant
        private const val RAMP_MIN_JUMP_DB = 3f
        private const val RAMP_STEP_MS = 10L

        /** (Re)applies the stored settings; safe to call repeatedly. */
        fun start(context: Context) = context.startForegroundService(Intent(context, EqService::class.java))
    }
}
