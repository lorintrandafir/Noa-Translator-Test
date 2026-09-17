package com.lorin.noatranslator

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "Noa Translator Test"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        val languages = TextView(this).apply {
            text = "Germană → Română"
            textSize = 22f
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "Pregătit pentru primul test"
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val startButton = Button(this).apply {
            text = "START"
            setOnClickListener {
                status.text = "Microfon activat"
                requestMicrophonePermission()
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)

            addView(title)
            addView(languages)
            addView(status)
            addView(startButton)
        }

        setContentView(layout)
    }

    private fun requestMicrophonePermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
    }
}
