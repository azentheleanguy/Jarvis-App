package com.jarvis.assistant

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class JarvisListenerService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "jarvis_listener"
        const val NOTIF_ID = 1
        var statusListener: ((mode: IrisMode, statusWord: String, statusSub: String, log: String?) -> Unit)? = null
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var awake = false
    private var justWoke = false
    private var restartPending = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createChannel()
        startForeground(NOTIF_ID, buildNotification("Sleeping", "Say \"Jarvis\" to wake"))
        startListening()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.85f)
            tts?.setSpeechRate(0.95f)
            selectBestVoice()
        }
    }

    private fun selectBestVoice() {
        val engine = tts ?: return
        val voices = engine.voices ?: return
        val candidate = voices
            .filter { it.locale == Locale.US && !it.isNetworkConnectionRequired }
            .maxByOrNull { it.quality }
        candidate?.let { engine.voice = it }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateStatus(IrisMode.SLEEP, "UNAVAILABLE", "speech recognition not supported", null)
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val heard = matches?.firstOrNull()?.lowercase() ?: ""
                handleFinalResult(heard)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val heard = matches?.firstOrNull()?.lowercase() ?: ""
                if (!awake && heard.contains("jarvis")) {
                    awake = true
                    justWoke = true
                    updateStatus(IrisMode.LISTENING, "LISTENING", "go ahead", "Wake word detected")
                    speak("Yes?")
                    updateNotification("Listening", "Go ahead")
                }
            }
            override fun onError(error: Int) { restartListening() }
            override fun onEndOfSpeech() {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onReadyForSpeech(params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000)
        }
        recognizer?.startListening(intent)
    }

    private fun restartListening() {
        if (restartPending) return
        restartPending = true
        recognizer?.destroy()
        android.os.Handler(mainLooper).postDelayed({
            restartPending = false
            startListening()
        }, 1200)
    }

    private fun handleFinalResult(heard: String) {
        if (justWoke) {
            justWoke = false
            val idx = heard.indexOf("jarvis")
            val after = if (idx >= 0) heard.substring(idx + "jarvis".length).trim() else ""
            if (after.isBlank()) {
                restartListening()
            } else {
                awake = false
                processCommand(after)
                restartListening()
            }
            return
        }
        if (awake) {
            awake = false
            processCommand(heard)
            restartListening()
            return
        }
        restartListening()
    }

    private fun processCommand(heard: String) {
        updateStatus(IrisMode.THINKING, "THINKING", heard, "You: $heard")
        val result = CommandRouter.handle(this, heard)
        val reply = if (result.handled) result.spokenReply else "I heard: $heard. That's not a command I know yet."
        updateStatus(IrisMode.SPEAKING, "SPEAKING", reply, "Jarvis: $reply")
        speak(reply)
        android.os.Handler(mainLooper).postDelayed({
            updateStatus(IrisMode.SLEEP, "SLEEPING", "say \"jarvis\" to wake", null)
            updateNotification("Sleeping", "Say \"Jarvis\" to wake")
        }, 1500)
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
    }

    private fun updateStatus(mode: IrisMode, word: String, sub: String, log: String?) {
        statusListener?.invoke(mode, word, sub, log)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Jarvis Listener", NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis — $title")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(title, text))
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
