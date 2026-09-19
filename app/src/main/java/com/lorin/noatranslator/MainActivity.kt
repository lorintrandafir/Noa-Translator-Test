package com.lorin.noatranslator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var failures = 0
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
                if (granted) startSession()
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
                    Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("NOA Translator · 0.2", style = MaterialTheme.typography.titleLarge)
                    Text("Test de transcriere în germană")
                    Text(status)
                    Button(onClick = {
                        if (active) stopSession("Ascultare oprită. Textul este păstrat.")
                        else if (hasPermission(Manifest.permission.RECORD_AUDIO)) startSession()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) {
                        Text(if (active) "OPREȘTE ASCULTAREA" else "PORNEȘTE ASCULTAREA")
                    }
                    Button(onClick = {
                        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) toggleBluetooth()
                        else bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }, enabled = !active) {
                        Text(if (bluetoothSelected) "REVINO LA TELEFON" else "FOLOSEȘTE CĂȘTILE BLUETOOTH")
                    }
                    Text(routeStatus)
                    Text("Ține aplicația deschisă în timpul testului. Textul se păstrează între porniri.")
                    Button(onClick = {
                        history = ""
                        partial = ""
                        saveHistory()
                    }, enabled = !active && history.isNotEmpty()) {
                        Text("ȘTERGE TEXTUL")
                    }
                    SelectionContainer(Modifier.weight(1f).verticalScroll(scroll)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(history.ifEmpty { "Aici vor apărea frazele recunoscute." })
                            if (partial.isNotBlank()) Text("În curs: $partial")
                        }
                    }
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
        if (active) return
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            status = "Accesul la microfon este necesar."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "Nu există un serviciu de recunoaștere vocală disponibil pe telefon."
            return
        }
        active = true
        failures = 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scheduleNext(0, "Pornesc microfonul…")
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

    private fun scheduleNext(delay: Long, message: String) {
        releaseRecognizer()
        if (!active) return
        status = message
        val ticket = generation
        handler.postDelayed({
            if (active && ticket == generation) beginUtterance()
        }, delay)
    }

    private fun isCurrent(ticket: Int) = active && ticket == generation

    private fun armWatchdog(ticket: Int, delay: Long) {
        watchdog?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            if (isCurrent(ticket)) recover("Recunoașterea nu mai răspunde")
        }
        watchdog = task
        handler.postDelayed(task, delay)
    }

    private fun beginUtterance() {
        val ticket = generation
        try {
            val engine = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrent(ticket)) return
                    status = "🎙 Ascult în germană…"
                    armWatchdog(ticket, 20_000)
                }

                override fun onBeginningOfSpeech() {
                    if (!isCurrent(ticket)) return
                    status = "🎙 Se aude vorbire…"
                    armWatchdog(ticket, 30_000)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isCurrent(ticket)) return
                    val text = bestText(partialResults)
                    if (text.isNotBlank() && text != partial) {
                        partial = text
                        armWatchdog(ticket, 20_000)
                    }
                }

                override fun onEndOfSpeech() {
                    if (!isCurrent(ticket)) return
                    status = "Procesez fraza…"
                    armWatchdog(ticket, 10_000)
                }

                override fun onResults(results: Bundle?) {
                    if (!isCurrent(ticket)) return
                    val text = bestText(results)
                    if (text.isNotBlank()) {
                        appendText(text)
                        partial = ""
                    } else preservePartial()
                    failures = 0
                    scheduleNext(350, "Reiau ascultarea…")
                }

                override fun onError(error: Int) {
                    if (!isCurrent(ticket)) return
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            preservePartial()
                            failures = 0
                            scheduleNext(700, "Nu am auzit o frază clară. Reiau ascultarea…")
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            stopSession("Accesul la microfon a fost refuzat. Verifică permisiunile.")
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            stopSession("Limba germană nu este disponibilă în serviciul vocal (eroare $error).")
                        else -> recover("Eroare vocală $error")
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {}
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

    private fun recover(reason: String) {
        preservePartial()
        failures++
        if (failures >= 5) {
            stopSession("$reason. Oprit după 5 încercări; verifică internetul și serviciul vocal, apoi pornește din nou.")
        } else {
            val delay = (1_000L shl (failures - 1)).coerceAtMost(8_000L)
            scheduleNext(delay, "$reason. Reîncerc în ${delay / 1000} s ($failures/5)…")
        }
    }

    private fun stopSession(message: String) {
        active = false
        releaseRecognizer()
        preservePartial()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = message
    }

    private fun toggleBluetooth() {
        if (active) return
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
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (audioManager.setCommunicationDevice(device)) {
                bluetoothSelected = true
                routeStatus = "Rutare către căști solicitată: ${device.productName}. Serviciul vocal poate folosi alt microfon."
            } else {
                resetAudioRoute()
                routeStatus = "Telefonul nu a acceptat rutarea către căști."
            }
        } catch (_: SecurityException) {
            resetAudioRoute()
            routeStatus = "Permite accesul la dispozitivele Bluetooth din setări."
        }
    }

    private fun resetAudioRoute() {
        runCatching { audioManager.clearCommunicationDevice() }
        runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
        bluetoothSelected = false
        routeStatus = "Rutare audio implicită (telefon)"
    }

    override fun onStop() {
        if (active) stopSession("Pauză: aplicația a trecut în fundal. Apasă PORNEȘTE pentru a continua.")
        resetAudioRoute()
        super.onStop()
    }

    override fun onDestroy() {
        active = false
        releaseRecognizer()
        super.onDestroy()
    }
}
