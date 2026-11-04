package com.kawaqdr.doladown

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(22), dp(20))
            setBackgroundColor(Color.rgb(12, 16, 29))
        }
        root.addView(TextView(this).apply {
            text = "Dola Down"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Private virtual space for Dola"
            textSize = 14f
            setTextColor(Color.rgb(166, 177, 202))
            setPadding(0, dp(5), 0, dp(22))
        })
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(225, 232, 247))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(29, 38, 61))
        }
        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(makeButton("Install Dola into Sandbox") { installDola() })
        root.addView(makeButton("Launch Dola in Sandbox") {
            try {
                DolaSandbox.launchDola(this)
                status.text = "Launching the sandbox copy of Dola…"
            } catch (error: Throwable) {
                status.text = error.message ?: "Could not launch Dola."
            }
        })
        root.addView(TextView(this).apply {
            text = "Dola Down copies the installed Dola APK (including split APKs where available) into its own virtual app space. The original Dola installation remains unchanged. First launch may take longer while the virtual runtime prepares the guest app."
            textSize = 12f
            setTextColor(Color.rgb(145, 157, 181))
            setPadding(0, dp(18), 0, 0)
        })
        setContentView(root)
        refreshStatus()
    }

    private fun installDola() {
        if (!DolaDownApp.runtimeReady) {
            status.text = "Virtual runtime could not start: ${DolaDownApp.startupError?.message ?: "unknown error"}"
            return
        }
        status.text = "Preparing Dola APK and installing it into the private sandbox…"
        worker.execute {
            try {
                val message = DolaSandbox.installExistingDola(this)
                runOnUiThread {
                    status.text = message + " Tap Launch Dola in Sandbox."
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    status.text = error.message ?: "Sandbox installation failed."
                }
            }
        }
    }

    private fun refreshStatus() {
        status.text = when {
            !DolaDownApp.runtimeReady ->
                "Virtual runtime startup failed: ${DolaDownApp.startupError?.message ?: "initializing"}"
            DolaSandbox.isDolaInstalledInSandbox() ->
                "Virtual sandbox is ready. Dola is installed inside Dola Down."
            else ->
                "Virtual sandbox is ready. Dola has not been imported yet."
        }
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
