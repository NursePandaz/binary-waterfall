# Binary Waterfall for Android — preview 0.1

Native Java/Android preview inspired by upstream issue nimaid/binary-waterfall#25.
Based on Binary Waterfall by Ella Jameson (nimaid), licensed under GPL v3; the repository LICENSE applies to this port too.

Open a local file or choose **Try a demo**, then press **Play**. Seek through the file and adjust raw PCM sample rate, 8/16-bit sample size, mono/stereo, image dimensions, BGRX/RGB/grayscale mapping, and vertical flip. Playback pauses when the app leaves the foreground. Volume starts at 15%.

Android 8.0 (API 26) or later. Files are copied into private temporary storage, limited to 256 MiB, and removed when the activity is destroyed. The system file picker grants access to the chosen file only. No network permission, analytics, microphone access, or broad storage permission is requested. A new session starts after activity recreation, including screen rotation; the original file is never modified.

This preview does not yet include desktop video/audio export, custom color format strings, 24/32-bit audio, background playback, or complete desktop rendering parity. Physical-device playback and long-session battery/memory behavior still need validation. Do not describe the port as production-ready before those checks.

## Build and test

Use JDK 17, Gradle 8.9 and Android SDK 35, or import this folder in Android Studio:

```sh
gradle --no-daemon lintDebug assembleDebug assembleDebugAndroidTest
# With an Android emulator/device connected:
gradle --no-daemon connectedDebugAndroidTest
```

The Android preview workflow builds an installable debug APK and runs emulator tests. Download the APK from its workflow artifacts. This is a development signing key, not a Play Store release; it may require uninstalling an older development build if the signing key changes between builds. A stable distribution key must be configured before public release.
