# Dola Down Browser

A lightweight Android browser built on the Android System WebView.

## Features
- Android System WebView for website rendering.
- Address/search field with URL and Google search handling.
- Back, forward, reload, and home controls.
- Google Credential Manager account-selection scaffold using Google Identity Services.

## Google sign-in configuration

The Google account picker requires an OAuth 2.0 **Web application** client ID belonging to the relying-party website/backend. In `MainActivity.kt`, replace:

```kotlin
private const val GOOGLE_WEB_CLIENT_ID = "YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com"
```

with the Web client ID configured for the website's backend.

Selecting an account returns a Google ID token to the native app. This alone does **not** sign the user into an arbitrary website. The website/backend must accept the ID token, validate its signature, issuer, audience, expiry and nonce as applicable, then establish its own secure session (normally via a server endpoint and an HttpOnly, Secure cookie). Do not put the client secret in the Android app.

For a complete `dola.com` sign-in integration, the site owner must provide or implement the supported token-exchange endpoint and its expected request/response contract. The browser deliberately does not inject a Google token into arbitrary web pages.

## Build
Requires JDK 17 and Android SDK platform 35.

```bash
gradle assembleDebug
```
