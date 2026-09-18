package com.lorin.noatranslator

import android.Manifest
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var hasPermission by remember {
                mutableStateOf(
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                )
            }

            var listening by remember { mutableStateOf(false) }
            
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            var useBluetoothMic by remember {
    mutableStateOf(false)
}

            val bluetoothMic = audioManager.availableCommunicationDevices.firstOrNull {
    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
}
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasPermission = granted
                    if (!granted) listening = false
                }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("NOA Translator 🇩🇪 ↔ 🇷🇴")
                Spacer(modifier = Modifier.height(20.dp))

Button(
    onClick = {
        if (bluetoothMic != null) {
            useBluetoothMic = !useBluetoothMic

            if (useBluetoothMic) {
                audioManager.setCommunicationDevice(bluetoothMic)
            } else {
                audioManager.clearCommunicationDevice()
            }
        }
    },
    enabled = bluetoothMic != null
) {
    Text(
        if (bluetoothMic == null)
            "🎧 Căști Bluetooth nedetectate"
        else if (useBluetoothMic)
            "🎧 Microfon căști Bluetooth"
        else
            "📱 Microfon telefon"
    )
}

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    when {
                        !hasPermission -> "Microfonul are nevoie de permisiune"
                        listening -> "🎙️ Ascult..."
                        else -> "Microfon pregătit"
                    }
                )

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = {
                        if (!hasPermission) {
                            permissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        } else {
                            listening = !listening
                        }
                    }
                ) {
                    Text(
                        when {
                            !hasPermission -> "PERMITE MICROFONUL"
                            listening -> "OPREȘTE ASCULTAREA"
                            else -> "PORNEȘTE ASCULTAREA"
                        }
                    )
                }
            }
        }
    }
}
