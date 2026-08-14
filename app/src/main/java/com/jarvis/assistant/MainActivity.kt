package com.jarvis.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var iris: IrisView
    private lateinit var statusWord: TextView
    private lateinit var statusSub: TextView
    private lateinit var transcript: TextView
    private lateinit var logView: TextView
    private lateinit var toggleBtn: Button
    private lateinit var accessibilityBtn: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        iris = findViewById(R.id.irisView)
        statusWord = findViewById(R.id.statusWord)
        statusSub = findViewById(R.id.statusSub)
        transcript = findViewById(R.id.transcript)
        logView = findViewById(R.id.logView)
        toggleBtn = findViewById(R.id.toggleBtn)
        accessibilityBtn = findViewById(R.id.accessibilityBtn)

        requestNeededPermissions()

        JarvisListenerService.statusListener = { mode, word, sub, log ->
            runOnUiThread {
                iris.mode = mode
                statusWord.text = word
                statusSub.text = sub
                if (log != null) {
                    transcript.text = log
                    logView.append("\n$log")
                }
            }
        }

        val prefs = getSharedPreferences("jarvis", MODE_PRIVATE)
        updateToggleLabel(prefs.getBoolean("service_enabled", false))

        toggleBtn.setOnClickListener {
            val enabled = prefs.getBoolean("service_enabled", false)
            if (enabled) {
                stopService(Intent(this, JarvisListenerService::class.java))
                prefs.edit().putBoolean("service_enabled", false).apply()
            } else {
                requestNeededPermissions()
                val svc = Intent(this, JarvisListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
                prefs.edit().putBoolean("service_enabled", true).apply()
            }
            updateToggleLabel(!enabled)
        }

        accessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun updateToggleLabel(running: Boolean) {
        toggleBtn.text = if (running) "Stop Jarvis (background service)" else "Start Jarvis (background service)"
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    override fun onDestroy() {
        JarvisListenerService.statusListener = null
        super.onDestroy()
    }
}
