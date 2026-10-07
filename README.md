# Yaaro Android Test APK

**Purpose:** Installable Android package for testing the existing Yaaro web app.  
**Web app:** https://reliable-tarsier-fdbc17.netlify.app/ (unchanged)

This project does **NOT** modify Yaaro web code, Firebase, WebRTC, or Netlify.

## What this APK does

- Opens the live Yaaro site inside a full-screen WebView
- Enables JavaScript, DOM storage, media playback
- Bridges WebRTC `getUserMedia` permissions (camera + microphone)
- Requests `CAMERA`, `RECORD_AUDIO`, `INTERNET` at runtime

## Build (Android Studio — recommended)

1. Install [Android Studio](https://developer.android.com/studio)
2. Open folder `YaaroAndroid`
3. Let Gradle sync
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK path:
   `app/build/outputs/apk/debug/app-debug.apk`

## Build (command line)

```bash
cd YaaroAndroid
chmod +x gradlew
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install on phone

1. Copy `app-debug.apk` to the phone
2. Enable **Install unknown apps** for Files/Chrome
3. Tap the APK → Install
4. Open **Yaaro**
5. Allow **Camera** and **Microphone** when asked (needed for calls)

## Multi-phone testing

Install the same APK on 2–3 phones, login with different Yaaro accounts, then test voice/video calls against the existing Netlify backend.

## Notes

- Debug APK is for **testing only** (not Play Store release)
- Call quality depends on the existing web WebRTC implementation + network
- The APK does not improve or rewrite WebRTC
