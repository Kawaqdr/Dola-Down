package com.kawaqdr.doladown.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        // Replace with the OAuth 2.0 Web application client ID for the site's backend.
        private const val GOOGLE_WEB_CLIENT_ID = "YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com"
        private const val HOME_URL = "https://dola.com"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
                darkColorScheme() else lightColorScheme()) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var address by remember { mutableStateOf(HOME_URL) }
                var webView by remember { mutableStateOf<WebView?>(null) }
                var canGoBack by remember { mutableStateOf(false) }
                var canGoForward by remember { mutableStateOf(false) }
                var loading by remember { mutableFloatStateOf(0f) }

                fun navigate(raw: String) {
                    val value = raw.trim()
                    if (value.isBlank()) return
                    val target = when {
                        value.startsWith("https://") || value.startsWith("http://") -> value
                        value.contains(".") && !value.contains(" ") -> "https://$value"
                        else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(value, "UTF-8")
                    }
                    address = target
                    webView?.loadUrl(target)
                }

                fun chooseGoogleAccount() {
                    if (GOOGLE_WEB_CLIENT_ID.startsWith("YOUR_")) {
                        Toast.makeText(context, "Set the Google OAuth Web Client ID before enabling Google sign-in.", Toast.LENGTH_LONG).show()
                        return
                    }
                    scope.launch {
                        try {
                            val option = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                                .setAutoSelectEnabled(false)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(option)
                                .build()
                            val result = CredentialManager.create(context).getCredential(context, request)
                            val credential = result.credential
                            if (credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                val google = GoogleIdTokenCredential.createFrom(credential.data)
                                Toast.makeText(
                                    context,
                                    "Google account selected: ${google.id}. The website backend must exchange this identity to create a session.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, "Unsupported Google credential.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: GetCredentialException) {
                            Toast.makeText(context, e.localizedMessage ?: "Google sign-in cancelled or unavailable.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.localizedMessage ?: "Google sign-in failed.", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                BackHandler(enabled = canGoBack) { webView?.goBack() }

                Scaffold(
                    topBar = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = address,
                                    onValueChange = { address = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    placeholder = { Text("Search or enter website") },
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(onGo = { navigate(address) })
                                )
                                IconButton(onClick = { navigate(address) }) {
                                    Icon(Icons.Rounded.ArrowForward, contentDescription = "Go")
                                }
                                IconButton(onClick = { chooseGoogleAccount() }) {
                                    Icon(Icons.Rounded.AccountCircle, contentDescription = "Choose Google account")
                                }
                            }
                            if (loading in 0.01f..0.99f) {
                                LinearProgressIndicator(progress = { loading }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = false,
                                onClick = { if (canGoBack) webView?.goBack() },
                                icon = { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") },
                                label = { Text("Back") },
                                enabled = canGoBack
                            )
                            NavigationBarItem(
                                selected = false,
                                onClick = { if (canGoForward) webView?.goForward() },
                                icon = { Icon(Icons.Rounded.ArrowForward, contentDescription = "Forward") },
                                label = { Text("Forward") },
                                enabled = canGoForward
                            )
                            NavigationBarItem(
                                selected = false,
                                onClick = { webView?.reload() },
                                icon = { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") },
                                label = { Text("Reload") }
                            )
                            NavigationBarItem(
                                selected = false,
                                onClick = { navigate(HOME_URL) },
                                icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                                label = { Text("Home") }
                            )
                        }
                    }
                ) { padding ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        factory = { viewContext ->
                            WebView(viewContext).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadsImagesAutomatically = true
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.setSupportMultipleWindows(false)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        url?.let { address = it }
                                        canGoBack = view.canGoBack()
                                        canGoForward = view.canGoForward()
                                    }
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        super.onPageFinished(view, url)
                                        url?.let { address = it }
                                        canGoBack = view.canGoBack()
                                        canGoForward = view.canGoForward()
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        loading = newProgress / 100f
                                    }
                                }
                                webView = this
                                loadUrl(HOME_URL)
                            }
                        },
                        update = { webView = it }
                    )
                }
            }
        }
    }
}
