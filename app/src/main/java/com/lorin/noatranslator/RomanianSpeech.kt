package com.lorin.noatranslator

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.ArrayDeque

/** Main-thread controller. Only finalized translations enter this queue. */
class RomanianSpeech(
    context: Context,
    private val status: (String) -> Unit,
    private val busy: (Boolean) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var serial = 0L
    private var current: String? = null
    private var timeout: Runnable? = null

    init {
        engine = TextToSpeech(context.applicationContext) { result ->
            handler.post {
                if (!closed) initialize(result)
            }
        }
    }

    private fun initialize(result: Int) {
        val tts = engine ?: return
        if (result != TextToSpeech.SUCCESS) {
            status("Motorul vocal nu a pornit. Verifică setările de sinteză vocală Android.")
            return
        }
        val locale = Locale.forLanguageTag("ro-RO")
        if (tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
            status("Lipsește vocea română. Instaleaz-o din setările de sinteză vocală Android, apoi redeschide NOA.")
            return
        }
        // Never silently send translations to a network-only TTS voice.
        val voice = tts.voices?.filter {
            it.locale.language == "ro" && !it.isNetworkConnectionRequired &&
                !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "RO" }
            .thenByDescending { it.quality }.thenBy { it.name })?.firstOrNull()
        if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            status("Instalează o voce română offline din setările de sinteză vocală Android, apoi redeschide NOA.")
            return
        }
        tts.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts.setSpeechRate(1.0f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { handler.post { finished(utteranceId, false) } }
            @Deprecated("Required by Android")
            override fun onError(utteranceId: String?) { handler.post { finished(utteranceId, true) } }
            override fun onError(utteranceId: String?, errorCode: Int) { handler.post { finished(utteranceId, true) } }
        })
        ready = true
        status("Voce română offline pregătită · ieșirea multimedia Android")
    }

    fun speak(text: String) {
        if (closed || text.isBlank()) return
        if (!ready) {
            status("Vocea română nu este pregătită. Verifică setările de sinteză vocală Android.")
            return
        }
        if (text.length >= TextToSpeech.getMaxSpeechInputLength()) {
            status("Text prea lung pentru redare. Alege o frază mai scurtă.")
            return
        }
        if (queue.size >= 5) {
            status("Coada audio este plină. Traducerea rămâne disponibilă pentru redare manuală.")
            return
        }
        queue.addLast(text)
        pump()
    }

    private fun pump() {
        if (closed || current != null) return
        if (queue.isEmpty()) {
            busy(false)
            return
        }
        current = "ro-${++serial}"
        val id = current!!
        val text = queue.removeFirst()
        busy(true)
        status("Redau traducerea în română…")
        // Guard against TTS engines that omit their terminal callback.
        timeout = Runnable { finished(id, true) }.also { handler.postDelayed(it, 60_000) }
        if (engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            finished(id, true)
        }
    }

    private fun finished(id: String?, failed: Boolean) {
        if (closed || id == null || id != current) return
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        current = null
        if (failed) {
            engine?.stop()
            queue.clear()
            status("Redarea vocală a eșuat. Textul este păstrat; poți reîncerca.")
        } else status("Redare încheiată · voce română")
        pump()
    }

    fun stop() {
        current = null
        queue.clear()
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        engine?.stop()
        busy(false)
    }

    fun close() {
        closed = true
        stop()
        handler.removeCallbacksAndMessages(null)
        engine?.shutdown()
        engine = null
    }
}
