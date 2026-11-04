package com.kawaqdr.doladown

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var payloadInput: EditText
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resolverExecutor = Executors.newSingleThreadExecutor()
    private val seenFallbacks = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLayout()
        configureWebView()
        handleIncomingIntent(intent)
        if (intent?.action != Intent.ACTION_SEND) webView.loadUrl(DOLA_CHAT_URL)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 16, 29))
            setPadding(dp(12), dp(12), dp(12), 0)
        }
        val title = TextView(this).apply {
            text = "Dola Down"; textSize = 22f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title)
        val subtitle = TextView(this).apply {
            text = "720P video capture · native Android downloader"
            textSize = 12f; setTextColor(Color.rgb(166, 177, 202))
            setPadding(0, dp(3), 0, dp(9))
        }
        root.addView(subtitle)
        statusView = TextView(this).apply {
            text = "Opening Dola… Sign in here, then generate a new video."
            textSize = 12f; setTextColor(Color.rgb(214, 222, 242))
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setBackgroundColor(Color.rgb(29, 38, 61))
        }
        root.addView(statusView)
        payloadInput = EditText(this).apply {
            hint = "Optional: paste fallback_api payload or direct MP4 URL"
            setHintTextColor(Color.rgb(135, 146, 170)); setTextColor(Color.WHITE)
            textSize = 12f; minLines = 2; maxLines = 4
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackgroundColor(Color.rgb(24, 30, 47))
        }
        root.addView(payloadInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val openButton = Button(this).apply {
            text = "Open Dola"; isAllCaps = false
            setOnClickListener { webView.loadUrl(DOLA_CHAT_URL) }
        }
        val resolveButton = Button(this).apply {
            text = "Resolve shared data"; isAllCaps = false
            setOnClickListener { resolveSharedText(payloadInput.text.toString()) }
        }
        actions.addView(openButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(resolveButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions)

        webView = WebView(this).apply { setBackgroundColor(Color.rgb(12, 16, 29)) }
        root.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(6) })
        setContentView(root)
    }

    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false; allowContentAccess = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        webView.addJavascriptInterface(DolaBridge(), "DolaAndroid")
        webView.webChromeClient = WebChromeClient()

        val documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        val script = assets.open("dola_interceptor.js").bufferedReader().use { it.readText() }
        if (documentStartSupported) {
            WebViewCompat.addDocumentStartJavaScript(
                webView, script,
                setOf("https://dola.com", "https://www.dola.com", "https://*.dola.com")
            )
        } else {
            statusView.text = "Update Android System WebView for early script injection."
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                statusView.text = "Loading Dola page…"
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                val host = Uri.parse(url ?: "").host.orEmpty()
                if (isDolaHost(host)) {
                    if (!documentStartSupported) view?.evaluateJavascript(script, null)
                    statusView.text = "Dola ready. Generate a new video; the interceptor is active."
                    view?.evaluateJavascript("window.DolaDownloader && window.DolaDownloader.scanVideos();", null)
                } else {
                    statusView.text = "Page loaded. Capture is active only on Dola pages."
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "https" && isDolaHost(uri.host.orEmpty())) return false
                if (uri.scheme == "https" || uri.scheme == "http") {
                    return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true }
                    catch (_: Exception) { true }
                }
                return true
            }
        }
    }

    private inner class DolaBridge {
        @JavascriptInterface
        fun onFallbackApi(payload: String) {
            mainHandler.post {
                if (!isCurrentPageDola()) return@post
                val json = try { JSONObject(payload) } catch (_: Exception) {
                    statusView.text = "Captured an unreadable API event."; return@post
                }
                val fallbackUrl = json.optString("fallbackUrl")
                if (fallbackUrl.isBlank() || !seenFallbacks.add(fallbackUrl)) return@post
                val metadata = DolaMetadata(
                    prompt = json.optString("prompt"), poster = json.optString("poster"),
                    videoId = json.optString("vid"), keySeed = json.optString("keySeed"),
                    messageId = json.optString("messageId")
                )
                val cookies = CookieManager.getInstance().getCookie(fallbackUrl).orEmpty()
                val referer = webView.url ?: DOLA_CHAT_URL
                statusView.text = "Captured fallback_api. Resolving actual 720P rendition…"
                resolverExecutor.execute {
                    val result = DolaResolver.resolveFallback(fallbackUrl, metadata, cookies, referer)
                    mainHandler.post {
                        when (result) {
                            is DolaResolveResult.Success -> {
                                statusView.text = "720P resolved (" + result.width + "×" + result.height + "). Button added below video."
                                val media = JSONObject().apply {
                                    put("url", result.url); put("filename", result.filename)
                                    put("prompt", result.prompt); put("poster", result.poster)
                                    put("width", result.width); put("height", result.height)
                                    put("bitrate", result.bitrate)
                                }
                                webView.evaluateJavascript(
                                    "window.DolaDownloader && window.DolaDownloader.addResolvedMedia(" + media.toString() + ");", null
                                )
                            }
                            is DolaResolveResult.Failure -> statusView.text = "Resolver: " + result.message
                        }
                    }
                }
            }
        }

        @JavascriptInterface
        fun download(url: String, filename: String) {
            mainHandler.post {
                if (!url.startsWith("https://")) {
                    statusView.text = "Blocked invalid or non-HTTPS media URL."; return@post
                }
                enqueueDownload(url, filename)
            }
        }
    }

    private fun resolveSharedText(text: String) {
        if (text.isBlank()) {
            statusView.text = "Nothing to resolve. Share a captured payload or paste a video URL."; return
        }
        val cookies = CookieManager.getInstance().getCookie(DOLA_CHAT_URL).orEmpty()
        val referer = webView.url ?: DOLA_CHAT_URL
        statusView.text = "Resolving shared data…"
        resolverExecutor.execute {
            val result = DolaResolver.resolveSharedText(text, cookies, referer)
            mainHandler.post {
                when (result) {
                    is DolaResolveResult.Success -> {
                        statusView.text = "Video URL resolved. Download started."
                        enqueueDownload(result.url, result.filename)
                    }
                    is DolaResolveResult.Failure -> statusView.text = result.message
                }
            }
        }
    }

    private fun enqueueDownload(url: String, requestedName: String) {
        try {
            val safeName = requestedName.replace(Regex("""[/\\:*?"<>|#%]"""), "_")
                .replace(Regex("""\s+"""), "_").take(90)
            val filename = if (safeName.endsWith(".mp4", true)) safeName else safeName + ".mp4"
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename).setDescription("Dola Down · 720P")
                .setMimeType("video/mp4")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Dola_Down/" + filename)
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
                ?.let { request.addRequestHeader("Cookie", it) }
            request.addRequestHeader("Referer", webView.url ?: DOLA_CHAT_URL)
            request.addRequestHeader("User-Agent", webView.settings.userAgentString ?: "Mozilla/5.0")
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            statusView.text = "Download queued: " + filename
            Toast.makeText(this, "720P download started", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            statusView.text = "Download could not start: " + (error.message ?: "unknown error")
        }
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return
        val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedText.isNullOrBlank()) {
            payloadInput.setText(sharedText)
            statusView.text = "Shared text received. Tap Resolve shared data."
            return
        }
        val sharedUri = if (Build.VERSION.SDK_INT >= 33) {
            incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (sharedUri != null) importSharedVideo(sharedUri)
    }

    private fun importSharedVideo(uri: Uri) {
        Thread {
            try {
                val filename = "Dola_shared_" + System.currentTimeMillis() + ".mp4"
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val destination = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create Downloads entry")
                contentResolver.openInputStream(uri).use { source ->
                    requireNotNull(source) { "Shared file is not readable" }
                    contentResolver.openOutputStream(destination).use { output ->
                        requireNotNull(output) { "Downloads destination is not writable" }
                        source.copyTo(output)
                    }
                }
                val completed = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                contentResolver.update(destination, completed, null, null)
                mainHandler.post { statusView.text = "Shared video saved to Downloads." }
            } catch (error: Exception) {
                val detail = error.message ?: "unknown error"
                mainHandler.post { statusView.text = "Video import failed: " + detail }
            }
        }.start()
    }

    private fun isCurrentPageDola(): Boolean =
        isDolaHost(Uri.parse(webView.url ?: "").host.orEmpty())

    private fun isDolaHost(host: String): Boolean =
        host.equals("dola.com", true) || host.endsWith(".dola.com", true)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("DolaAndroid")
            webView.destroy()
        }
        resolverExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val DOLA_CHAT_URL = "https://www.dola.com/chat"
    }
}
