package com.lorin.noatranslator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private val retryPolicy = RetryPolicy()
    private var finishing = false
    private var utteranceCount = 0
    private var lastRmsUpdate = 0L
    private var level by mutableStateOf("Nivel vocal: încă neraportat")
    private var diagnostics by mutableStateOf("")
    private var probeResult by mutableStateOf("")
    private var probing by mutableStateOf(false)
    private var probe: MicrophoneProbe? = null
    private var probeEpoch = 0
    private var permissionForProbe = false
    private var routeDeviceId: Int? = null
    private var previousAudioMode: Int? = null
    private var watchdog: Runnable? = null
    private var active by mutableStateOf(false)
    private var history by mutableStateOf("")
    private var partial by mutableStateOf("")
    private var status by mutableStateOf("Microfon pregătit")
    private var bluetoothSelected by mutableStateOf(false)
    private var routeStatus by mutableStateOf("Rutare audio implicită (telefon)")
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val preferences by lazy { getSharedPreferences("transcript", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = preferences.getString("history", "").orEmpty()
        setContent {
            val microphonePermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    if (permissionForProbe) startProbe() else startSession()
                }
                else status = "Permite accesul la microfon pentru a porni."
            }
            val bluetoothPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) toggleBluetooth()
                else routeStatus = "Accesul Bluetooth nu a fost permis."
            }
            val scroll = rememberScrollState()
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().systemBarsPadding().verticalScroll(scroll).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("NOA Translator · 0.3", style = MaterialTheme.typography.titleLarge)
                    Text("Transcriere germană · păstrează aplicația deschisă")
                    Text(status)
                    Button(onClick = {
                        if (active) stopSession("Ascultare oprită. Textul este păstrat.")
                        else if (hasPermission(Manifest.permission.RECORD_AUDIO)) startSession()
                        else {
                            permissionForProbe = false
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }, enabled = !probing) {
                        Text(if (active) "OPREȘTE ASCULTAREA" else "PORNEȘTE ASCULTAREA")
                    }
                    Button(onClick = {
                        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) toggleBluetooth()
                        else bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }, enabled = !active && !probing) {
                        Text(if (bluetoothSelected) "REVINO LA TELEFON" else "FOLOSEȘTE CĂȘTILE BLUETOOTH")
                    }
                    Text(routeStatus)
                    Text(level)
                    Button(onClick = {
                        if (hasPermission(Manifest.permission.RECORD_AUDIO)) startProbe()
                        else {
                            permissionForProbe = true
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }, enabled = !active && !probing) {
                        Text(if (probing) "VORBEȘTE ACUM…" else "TEST MICROFON · 4 SECUNDE")
                    }
                    if (probeResult.isNotBlank()) Text(probeResult)
                    Button(onClick = {
                        history = ""
                        partial = ""
                        saveHistory()
                    }, enabled = !active && !probing && history.isNotEmpty()) {
                        Text("ȘTERGE TEXTUL")
                    }
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(history.ifEmpty { "Aici vor apărea frazele recunoscute." })
                            if (partial.isNotBlank()) Text("În curs: $partial")
                        }
                    }
                    Button(onClick = {
                        val report = "NOA 0.3 | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE}\n$routeStatus\n$level\n$probeResult\n$diagnostics"
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostic NOA", report))
                        status = "Diagnostic copiat."
                    }, enabled = !active && !probing) { Text("COPIAZĂ DIAGNOSTICUL") }
                    SelectionContainer { Text(diagnostics, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    private fun hasPermission(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun saveHistory() {
        preferences.edit().putString("history", history).apply()
    }

    private fun appendText(text: String) {
        if (text.isBlank()) return
        history = if (history.isBlank()) text.trim() else "$history\n\n${text.trim()}"
        saveHistory()
    }

    private fun preservePartial() {
        if (partial.isNotBlank()) appendText("[provizoriu] $partial")
        partial = ""
    }

    private fun startSession() {
        if (active || probing) return
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            status = "Accesul la microfon este necesar."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "Nu există un serviciu de recunoaștere vocală disponibil pe telefon."
            return
        }
        active = true
        retryPolicy.reset()
        utteranceCount = 0
        diagnostics = ""
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prepareRoute { scheduleNext(700, "Pornesc microfonul…") }
    }

    // Invalidate callbacks BEFORE cancel/destroy; old callbacks must never restart a session.
    private fun releaseRecognizer() {
        generation++
        handler.removeCallbacksAndMessages(null)
        watchdog = null
        val old = recognizer
        recognizer = null
        if (old != null) {
            runCatching { old.cancel() }
            runCatching { old.destroy() }
        }
    }

    private fun scheduleNext(delay: Long, message: String, recreate: Boolean = false) {
        // Normal terminal callbacks allow reusing the service. Only faults tear it down.
        if (recreate) releaseRecognizer()
        else {
            generation++
            handler.removeCallbacksAndMessages(null)
            watchdog = null
        }
        if (!active) return
        status = message
        logEvent(message)
        val ticket = generation
        handler.postDelayed({
            if (active && ticket == generation) beginUtterance()
        }, delay)
    }

    private fun isCurrent(ticket: Int) = active && ticket == generation

    private fun armWatchdog(ticket: Int, delay: Long) {
        watchdog?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            if (isCurrent(ticket)) {
                if (finishing) recover("Nu a venit rezultatul final")
                else finishUtterance(ticket)
            }
        }
        watchdog = task
        handler.postDelayed(task, delay)
    }

    private fun beginUtterance() {
        val ticket = generation
        try {
            finishing = false
            level = "Nivel vocal: încă neraportat de serviciu"
            utteranceCount++
            logEvent("Sesiune $utteranceCount")
            val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrent(ticket)) return
                    status = "🎙 Ascult în germană…"
                    logEvent("Serviciul este pregătit")
                    if (!finishing) armWatchdog(ticket, 12_000)
                }

                override fun onBeginningOfSpeech() {
                    if (!isCurrent(ticket)) return
                    status = "Serviciul a detectat începutul vorbirii…"
                    logEvent("Început de vorbire")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isCurrent(ticket)) return
                    val text = bestText(partialResults)
                    if (text.isNotBlank() && text != partial) {
                        partial = text
                        // Keep the fixed utterance deadline even if partial text changes.
                    }
                }

                override fun onEndOfSpeech() {
                    if (!isCurrent(ticket)) return
                    status = "Procesez fraza…"
                    finishing = true
                    logEvent("Sfârșit de vorbire; aștept rezultatul")
                    armWatchdog(ticket, 10_000)
                }

                override fun onResults(results: Bundle?) {
                    if (!isCurrent(ticket)) return
                    val text = bestText(results)
                    if (text.isNotBlank()) {
                        appendText(text)
                        partial = ""
                        retryPolicy.reset()
                        logEvent("Rezultat final primit (${text.length} caractere)")
                        scheduleNext(800, "Reiau ascultarea…")
                    } else retryWithoutText("Rezultat gol")
                }

                override fun onError(error: Int) {
                    if (!isCurrent(ticket)) return
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            retryWithoutText("${errorDescription(error)} (cod $error)")
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            stopSession("Accesul la microfon a fost refuzat. Verifică permisiunile.")
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            stopSession("Limba germană nu este disponibilă în serviciul vocal (eroare $error).")
                        else -> recover("${errorDescription(error)} (cod $error)",
                            error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS)
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (!isCurrent(ticket) || !rmsdB.isFinite()) return
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRmsUpdate >= 250) {
                        lastRmsUpdate = now
                        level = "Nivel raportat de serviciu: %.1f dB (nu identifică microfonul)".format(rmsdB)
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            armWatchdog(ticket, 15_000)
            engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: SecurityException) {
            stopSession("Accesul la microfon a fost refuzat. Verifică permisiunile.")
        } catch (_: RuntimeException) {
            recover("Serviciul vocal nu a pornit")
        }
    }

    private fun bestText(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()

    private fun finishUtterance(ticket: Int) {
        if (!isCurrent(ticket) || finishing) return
        finishing = true
        status = "Cer rezultatul frazei…"
        logEvent("Cer finalizarea după limita sesiunii")
        armWatchdog(ticket, 10_000)
        try { recognizer?.stopListening() }
        catch (_: RuntimeException) { recover("Nu pot finaliza sesiunea") }
    }

    private fun retryWithoutText(reason: String) {
        preservePartial()
        val delay = retryPolicy.withoutText()
        if (delay == null) stopSession("$reason. Pauză după 4 sesiuni fără text. Folosește TEST MICROFON.")
        else scheduleNext(delay, "$reason. Reiau în ${delay / 1000} s…")
    }

    private fun recover(reason: String, rateLimited: Boolean = false) {
        preservePartial()
        val delay = retryPolicy.failure(rateLimited)
        if (delay == null) stopSession("$reason. Oprit după erori repetate. Copiază diagnosticul.")
        else scheduleNext(delay, "$reason. Reîncerc în ${delay / 1000} s…", recreate = true)
    }

    private fun stopSession(message: String) {
        active = false
        releaseRecognizer()
        preservePartial()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = message
        logEvent(message)
        clearAudioRoute()
    }

    private fun logEvent(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())
        diagnostics = (diagnostics.lines().filter { it.isNotBlank() } + "$time $message")
            .takeLast(35).joinToString("\n")
    }

    private fun errorDescription(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Eroare la captarea audio"
        SpeechRecognizer.ERROR_NETWORK -> "Eroare de rețea"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Rețeaua nu răspunde"
        SpeechRecognizer.ERROR_SERVER -> "Eroare a serviciului vocal"
        SpeechRecognizer.ERROR_CLIENT -> "Sesiune vocală întreruptă"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Serviciul vocal este ocupat"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Serviciul a limitat cererile"
        SpeechRecognizer.ERROR_NO_MATCH -> "Fără text recunoscut"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nu s-a detectat vorbire"
        else -> "Eroare vocală"
    }

    // setCommunicationDevice accepts a request; wait for the actual communication route.
    // This route still does not prove which input a separate speech service captures.
    private fun prepareRoute(ready: () -> Unit) {
        try {
            if (!bluetoothSelected) {
                clearAudioRoute()
                routeStatus = "Telefon selectat; serviciul vocal își alege intrarea"
                ready()
                return
            }
            val device = audioManager.availableCommunicationDevices.firstOrNull { it.id == routeDeviceId }
            if (device == null || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                probing = false
                stopSession("Căștile nu sunt disponibile. Reconectează-le sau selectează telefonul.")
                return
            }
            previousAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (!audioManager.setCommunicationDevice(device)) {
                probing = false
                stopSession("Android a refuzat conectarea microfonului Bluetooth.")
                return
            }
            status = "Aștept conectarea audio a căștilor…"
            val deadline = SystemClock.elapsedRealtime() + 6_000
            val ticket = generation
            fun checkRoute() {
                if ((!active && !probing) || ticket != generation) return
                val actual = runCatching { audioManager.communicationDevice }.getOrElse {
                    probing = false
                    stopSession("Nu pot verifica ruta Bluetooth. Verifică permisiunile.")
                    return
                }
                if (actual?.id == device.id) {
                    routeStatus = "Rută de comunicație: ${device.productName}. Intrarea vocală se verifică separat."
                    logEvent("Ruta Bluetooth confirmată de Android")
                    handler.postDelayed({ if ((active || probing) && ticket == generation) ready() }, 800)
                } else if (SystemClock.elapsedRealtime() >= deadline) {
                    probing = false
                    stopSession("Conexiunea audio Bluetooth nu s-a stabilit în 6 secunde.")
                } else handler.postDelayed({ checkRoute() }, 200)
            }
            checkRoute()
        } catch (_: RuntimeException) {
            probing = false
            stopSession("Nu pot pregăti ruta audio. Verifică permisiunile și conexiunea căștilor.")
        }
    }

    private fun startProbe() {
        if (active || probing) return
        probing = true
        probeResult = "Pregătesc testul…"
        releaseRecognizer()
        prepareRoute {
            status = "Vorbește timp de 4 secunde. Testul nu salvează sunetul."
            val epoch = probeEpoch
            probe = MicrophoneProbe(audioManager, bluetoothSelected) { report ->
                probe = null
                probing = false
                if (epoch == probeEpoch) {
                    probeResult = report
                    status = "Test microfon încheiat."
                    logEvent(report)
                    clearAudioRoute()
                }
            }.also { it.start() }
        }
    }

    private fun toggleBluetooth() {
        if (active || probing) return
        try {
            if (bluetoothSelected) {
                resetAudioRoute()
                return
            }
            val device = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (device == null) {
                routeStatus = "Nu am găsit căști cu microfon. Conectează-le și încearcă din nou."
                return
            }
            bluetoothSelected = true
            routeDeviceId = device.id
            routeStatus = "Căști selectate: ${device.productName}"
        } catch (_: SecurityException) {
            resetAudioRoute()
            routeStatus = "Permite accesul la dispozitivele Bluetooth din setări."
        }
    }

    private fun resetAudioRoute() {
        clearAudioRoute()
        routeDeviceId = null
        bluetoothSelected = false
        routeStatus = "Telefon selectat; verifică intrarea cu TEST MICROFON"
    }

    private fun clearAudioRoute() {
        runCatching { audioManager.clearCommunicationDevice() }
        previousAudioMode?.let { mode -> runCatching { audioManager.mode = mode } }
        previousAudioMode = null
    }

    override fun onStop() {
        probeEpoch++
        if (active) stopSession("Pauză: aplicația a trecut în fundal. Apasă PORNEȘTE pentru a continua.")
        probe?.cancel()
        handler.removeCallbacksAndMessages(null)
        if (probing && probe == null) probing = false
        resetAudioRoute()
        super.onStop()
    }

    override fun onDestroy() {
        active = false
        probe?.cancel()
        releaseRecognizer()
        super.onDestroy()
    }
}
