package com.lorin.noatranslator

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

/** Standalone input test, never simultaneous with SpeechRecognizer; PCM is not retained. */
internal class MicrophoneProbe(
    private val manager: AudioManager,
    private val bluetooth: Boolean,
    private val complete: (String) -> Unit
) {
    @Volatile private var cancelled = false

    fun cancel() { cancelled = true }

    fun start() {
        Thread({
            var recorder: AudioRecord? = null
            var report: String
            try {
                val devices = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val communication = if (bluetooth) manager.communicationDevice else null
                val input = devices.firstOrNull {
                    if (bluetooth) {
                        (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) &&
                            (communication?.address.isNullOrEmpty() || it.address == communication?.address)
                    } else it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                } ?: throw IllegalStateException("Intrarea selectată nu este disponibilă")
                val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0) { "Formatul audio de test nu este disponibil" }
                val audio = AudioRecord.Builder()
                    .setAudioSource(if (bluetooth) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(16_000)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(minimum, 6_400)).build()
                recorder = audio
                check(audio.state == AudioRecord.STATE_INITIALIZED) { "Microfonul nu poate fi inițializat" }
                check(audio.setPreferredDevice(input)) { "Intrarea audio cerută a fost refuzată" }
                if (!cancelled) audio.startRecording()
                val end = SystemClock.elapsedRealtime() + 4_000
                val buffer = ShortArray(1_600)
                var samples = 0L
                var squares = 0.0
                val routes = linkedSetOf<String>()
                while (!cancelled && SystemClock.elapsedRealtime() < end) {
                    val read = audio.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                    check(read >= 0) { "Citirea microfonului a eșuat: $read" }
                    for (i in 0 until read) squares += buffer[i].toDouble() * buffer[i]
                    samples += read
                    audio.routedDevice?.let { routes.add("${it.productName} (${deviceKind(it.type)})") }
                    Thread.sleep(20)
                }
                val level = if (samples > 0) sqrt(squares / samples) / 32768.0 else 0.0
                val db = if (level > 0) String.format(Locale.ROOT, "%.1f dBFS", 20 * log10(level)) else "tăcere / zero"
                report = if (cancelled) "Test întrerupt."
                    else "Test audio: ${routes.joinToString(" → ").ifEmpty { "intrare neconfirmată" }}\nNivel mediu: $db; $samples eșantioane.\nAcesta este microfonul testului, nu o confirmare a intrării serviciului vocal."
            } catch (error: Exception) {
                report = "Test audio eșuat: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                recorder?.let { audio ->
                    runCatching { audio.stop() }
                    runCatching { audio.release() }
                }
            }
            Handler(Looper.getMainLooper()).post { complete(report) }
        }, "NOA-microphone-test").start()
    }

    private fun deviceKind(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "microfon telefon"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "microfon Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "microfon Bluetooth LE"
        else -> "tip $type"
    }
}
