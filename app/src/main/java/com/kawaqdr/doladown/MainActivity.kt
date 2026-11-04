package com.kawaqdr.doladown

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var payloadInput: EditText
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLayout()
        handleIncomingIntent(intent)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) refreshStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(12, 16, 29))
            setPadding(dp(22), dp(28), dp(22), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "Dola Down"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Native Dola companion · automatic video link detection"
            textSize = 13f
            setTextColor(Color.rgb(166, 177, 202))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        })
        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(230, 235, 248))
            setBackgroundColor(Color.rgb(29, 38, 61))
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        root.addView(statusView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(actionButton("Open Dola") { launchDola() })
        root.addView(actionButton("Enable automatic detection") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(actionButton("Check detection status") { refreshStatus() })

        payloadInput = EditText(this).apply {
            hint = "Optional: paste a Dola fallback_api payload or direct video URL"
            setHintTextColor(Color.rgb(135, 146, 170))
            setTextColor(Color.WHITE)
            textSize = 12f
            minLines = 3
            maxLines = 5
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(24, 30, 47))
        }
        root.addView(payloadInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })
        root.addView(actionButton("Resolve shared / pasted data") {
            resolveText(payloadInput.text.toString())
        })
        root.addView(TextView(this).apply {
            text = "How it works: Dola Down opens the installed Dola app. With Accessibility enabled, it watches Dola's visible screen for a direct video URL and downloads a detected MP4. It cannot read Dola's private network traffic or hidden app data."
            textSize = 12f
            setTextColor(Color.rgb(145, 157, 181))
            setPadding(0, dp(18), 0, 0)
        })
        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

    private fun launchDola() {
        val launch = packageManager.getLaunchIntentForPackage(DOLA_PACKAGE)
        if (launch == null) {
            statusView.text = "Dola is not installed (package $DOLA_PACKAGE). Install the official Dola app first."
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        statusView.text = "Dola opened. Keep Dola Down's accessibility detection enabled."
    }

    private fun refreshStatus() {
        val enabled = DolaAccessibilityService.isEnabled(this)
        statusView.text = if (enabled) {
            "Automatic detection is ON. Open Dola and generate a video. A directly exposed MP4 link will be downloaded when it appears in Dola's visible interface."
        } else {
            "Automatic detection is OFF. Tap Enable automatic detection, then enable “Dola Down video detection” in Android Accessibility settings."
        }
    }

    private fun resolveText(text: String) {
        if (text.isBlank()) {
            statusView.text = "Paste a direct video URL or an actual captured fallback_api payload."
            return
        }
        statusView.text = "Checking shared data…"
        executor.execute {
            val result = DolaResolver.resolveSharedText(text, "", "https://www.dola.com/")
            runOnUiThread {
                when (result) {
                    is DolaResolveResult.Success -> {
                        DolaDownload.enqueue(this, result.url, result.filename)
                        statusView.text = "Video download queued: ${result.filename}"
                    }
                    is DolaResolveResult.Failure -> statusView.text = result.message
                }
            }
        }
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return
        val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedText.isNullOrBlank()) {
            payloadInput.setText(sharedText)
            resolveText(sharedText)
        } else {
            statusView.text = "Shared item received. If Dola shared a video file, Android will hand it to this app for saving."
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val DOLA_PACKAGE = "com.larus.wolf"
    }
}
