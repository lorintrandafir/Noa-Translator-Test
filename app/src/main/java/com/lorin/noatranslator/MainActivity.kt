package com.lorin.noatranslator

import android.Manifest
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
