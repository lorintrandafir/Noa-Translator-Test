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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private val retryPolicy = RetryPolicy()
    private var finishing = false
    private var utteranceCount = 0
    private var utteranceStartedAt = 0L
    private var finalizedAt: Long? = null
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
    private var translationQueue = TranslationQueue()
    private var entries by mutableStateOf(emptyList<TranscriptEntry>())
    private var translationReady by mutableStateOf(false)
    private var downloading by mutableStateOf(false)
    private var translationStatus by mutableStateOf("Verific modelele de traducere…")
    private var closed = false
    private val translator by lazy {
        Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.GERMAN)
            .setTargetLanguage(TranslateLanguage.ROMANIAN).build())
    }
    private var partial by mutableStateOf("")
    private var status by mutableStateOf("Microfon pregătit")
    private var bluetoothSelected by mutableStateOf(false)
    private var routeStatus by mutableStateOf("Rutare audio implicită (telefon)")
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val preferences by lazy { getSharedPreferences("transcript", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreTranscript()
        checkTranslationModels()
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
            var showTranslationInfo by remember { mutableStateOf(false) }
            val scroll = rememberScrollState()
            MaterialTheme {
                if (showTranslationInfo) AlertDialog(
                    onDismissRequest = { showTranslationInfo = false },
                    title = { Text("Despre traducere") },
                    text = { Text("Traducere automată Google Translate, realizată pe telefon prin ML Kit după descărcarea modelelor. Recunoașterea vocală poate necesita internet. Traducerile pot conține erori.\n\nGoogle nu oferă garanții privind traducerile, explicite sau implicite, inclusiv privind exactitatea, fiabilitatea, vandabilitatea, adecvarea pentru un anumit scop sau neîncălcarea drepturilor terților.") },
                    confirmButton = { TextButton(onClick = { showTranslationInfo = false }) { Text("ÎNCHIDE") } },
                    dismissButton = { TextButton(onClick = {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://cloud.google.com/translate")))
                    }) { Text("GOOGLE TRANSLATE") } }
                )
                Column(
                    Modifier.fillMaxSize().systemBarsPadding().verticalScroll(scroll).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("NOA Translator · 0.5", style = MaterialTheme.typography.titleLarge)
                    Text("Germană → română · păstrează aplicația deschisă")
                    TextButton(onClick = { showTranslationInfo = true }) { Text("Despre traducerea Google Translate") }
                    Text(translationStatus)
                    if (!translationReady) {
                        Button(onClick = { prepareTranslation() }, enabled = !downloading) {
                            Text(if (downloading) "DESCĂRCARE · AȘTEAPTĂ WI-FI" else "TRADU CU GOOGLE · PREGĂTEȘTE PE WI-FI")
                        }
                    }
                    if (entries.any { it.failed }) {
                        Button(onClick = {
                            translationQueue.retryFailures()
                            publishTranscript()
                            pumpTranslation()
                        }, enabled = translationReady) { Text("REÎNCEARCĂ TRADUCERILE") }
                    }
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
                        translationQueue.clear()
                        partial = ""
                        publishTranscript()
                    }, enabled = !active && !probing && entries.isNotEmpty()) {
                        Text("ȘTERGE TEXTUL")
                    }
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (entries.isEmpty()) Text("Aici vor apărea frazele în germană și traducerile în română.")
                            entries.forEach { entry ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("DE: ${entry.german}")
                                    Text(when {
                                        entry.provisional -> "Text provizoriu · netradus"
                                        entry.romanian != null -> "RO: ${entry.romanian}"
                                        entry.failed -> "Traducere nereușită · poți reîncerca"
                                        !translationReady -> "RO: așteaptă pregătirea traducerii"
                                        else -> "RO: se traduce…"
                                    }, color = MaterialTheme.colorScheme.primary)
                                    if (entry.romanian != null) Text("Traducere automată · powered by Google Translate", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (partial.isNotBlank()) Text("În curs: $partial")
                        }
                    }
                    Button(onClick = {
                        val report = "NOA 0.5 | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE}\n$routeStatus\n$level\n$translationStatus\n$probeResult\n$diagnostics"
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

    private fun restoreTranscript() {
        val saved = preferences.getString("bilingual_history", null)
        val restored = saved?.let { json -> runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                TranscriptEntry(index.toLong() + 1, item.getString("de"),
                    if (item.isNull("ro")) null else item.getString("ro"),
                    item.optBoolean("provisional"), item.optBoolean("failed"))
            }
        }.getOrNull() }
        translationQueue = TranslationQueue(restored.orEmpty())
        if (restored == null) {
            preferences.getString("history", "").orEmpty().split("\n\n").forEach {
                translationQueue.add(it.removePrefix("[provizoriu] "), it.startsWith("[provizoriu] "))
            }
        }
        publishTranscript()
    }

    private fun publishTranscript() {
        entries = translationQueue.entries
        val json = JSONArray()
        entries.forEach {
            json.put(JSONObject().put("de", it.german).put("ro", it.romanian ?: JSONObject.NULL)
                .put("provisional", it.provisional).put("failed", it.failed))
        }
        // Keep a plain-text copy for compatibility with the previous version.
        val plain = entries.joinToString("\n\n") { (if (it.provisional) "[provizoriu] " else "") + it.german }
        preferences.edit().putString("bilingual_history", json.toString()).putString("history", plain).apply()
    }

    private fun appendText(text: String) {
        translationQueue.add(text.removePrefix("[provizoriu] "), text.startsWith("[provizoriu] "))
        publishTranscript()
        pumpTranslation()
    }

    private fun checkTranslationModels() {
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                if (closed || downloading) return@addOnSuccessListener
                val languages = models.map { it.language }.toSet()
                translationReady = languages.containsAll(listOf(TranslateLanguage.GERMAN, TranslateLanguage.ROMANIAN))
                translationStatus = if (translationReady) "Traducerea în română este pregătită · pe telefon"
                    else "Conectează Wi-Fi și pregătește traducerea o singură dată."
                pumpTranslation()
            }.addOnFailureListener {
                if (!closed && !downloading) translationStatus = "Pregătește traducerea prin Wi-Fi."
            }
    }

    private fun prepareTranslation() {
        if (closed || downloading) return
        downloading = true
        translationStatus = "Descarc modelele de traducere. Păstrează conexiunea Wi-Fi."
        translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
            .addOnSuccessListener {
                if (closed) return@addOnSuccessListener
                downloading = false
                translationReady = true
                translationStatus = "Traducerea în română este pregătită · pe telefon"
                logEvent("Modelele de traducere sunt pregătite")
                pumpTranslation()
            }.addOnFailureListener {
                if (closed) return@addOnFailureListener
                downloading = false
                translationStatus = "Modelele nu s-au descărcat. Verifică Wi-Fi și spațiul liber, apoi reîncearcă."
                logEvent("Descărcarea modelelor a eșuat")
            }
    }

    private fun pumpTranslation() {
        if (closed || !translationReady) return
        val entry = translationQueue.next() ?: return
        translator.translate(entry.german)
            .addOnSuccessListener { translated -> finishTranslation(entry.id, translated) }
            .addOnFailureListener { finishTranslation(entry.id, null) }
    }

    private fun finishTranslation(id: Long, translated: String?) {
        if (closed || !translationQueue.complete(id, translated)) return
        publishTranscript()
        logEvent(if (translated.isNullOrBlank()) "Traducere nereușită" else "Traducere în română primită")
        pumpTranslation()
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
        finalizedAt = null
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
            utteranceStartedAt = SystemClock.elapsedRealtime()
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
                    finalizedAt?.let {
                        logEvent("De la finalizarea precedentă la pregătire: ${SystemClock.elapsedRealtime() - it} ms")
                    }
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
                    finalizedAt = SystemClock.elapsedRealtime()
                    if (text.isNotBlank()) {
                        appendText(text)
                        partial = ""
                        retryPolicy.reset()
                        logEvent("Rezultat final primit (${text.length} caractere)")
                        scheduleNext(RetryPolicy.RESTART_DELAY_MS, "Reiau ascultarea…")
                    } else retryWithoutText("Rezultat gol")
                }

                override fun onError(error: Int) {
                    if (!isCurrent(ticket)) return
                    finalizedAt = SystemClock.elapsedRealtime()
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
                        level = "Nivel audio raportat de recunoaștere: %.1f dB".format(rmsdB)
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
        val duration = SystemClock.elapsedRealtime() - utteranceStartedAt
        val delay = retryPolicy.withoutText(duration)
        logEvent("$reason; sesiune ${duration} ms")
        if (delay == null) stopSession("Serviciul închide repetat sesiunile imediat. Oprit pentru a evita o buclă. Copiază diagnosticul.")
        else if (delay == RetryPolicy.RESTART_DELAY_MS) scheduleNext(delay, "Reiau ascultarea…")
        else scheduleNext(delay, "Serviciul a închis prea repede sesiunea. Reiau în $delay ms…")
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
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT).format(java.util.Date())
        diagnostics = (diagnostics.lines().filter { it.isNotBlank() } + "$time $message")
            .takeLast(100).joinToString("\n")
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
        closed = true
        translator.close()
        active = false
        probe?.cancel()
        releaseRecognizer()
        super.onDestroy()
    }
}
