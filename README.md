# Dola Down — Android 720P Downloader

Android app porting the Dola extension's page-context network capture and video-resolution flow into an embedded Dola WebView.

## Extension techniques ported

- Document-start JavaScript injection into Dola WebView.
- Fetch response cloning and streaming-body inspection; XHR response interception.
- fallback_api extraction with nearby prompt, poster, video ID, and key-seed metadata.
- API parameters: channel=no, codec_type=8, logo_type=unwatermarked.
- video_list parsing and explicit 720P rendition selection.
- SHA-512 key/IV derivation and AES-CBC qAAB token decoding.
- Video-element association and a Download 720P button below each video.
- Native Android DownloadManager output to Downloads/Dola_Down/.

## Native Dola app limitation

Android's application sandbox prevents a normal companion app from reading another app's private HTTPS traffic or process memory. Automatic interception therefore runs inside the Dola WebView hosted by Dola Down, not inside the separately installed Dola Android APK.

The app accepts Android Share intents. Shared video files are copied to Downloads; shared text containing an actual captured fallback_api payload can be resolved. A normal conversation URL is not the same as the captured network payload.

## Build

Android Gradle Plugin 8.7.3, Gradle 8.9, Kotlin 2.0.21, JDK 17, Android SDK 35, minimum API 29.

Open in Android Studio or run gradle assembleDebug. APK output: app/build/outputs/apk/debug/app-debug.apk.

## Use

1. Install and open Dola Down.
2. Sign in to Dola inside the embedded WebView.
3. Generate a new video after the interceptor has loaded.
4. Wait for the status to report a resolved 720P rendition.
5. Tap Download 720P below the video.
6. The MP4 is saved to Downloads/Dola_Down/.

## Limitations

- A non-720P rendition is never relabeled as 720P.
- Dola may change its API response schema, token format, or session requirements.
- The fallback API call uses WebView cookies and can still fail if Dola requires additional signatures or headers.
- No TLS interception certificate, root access, or private-app memory inspection is used.
