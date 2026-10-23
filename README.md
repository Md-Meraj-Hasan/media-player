# Meraj Media — Offline Audio + Video Player (LibVLC)

This project uses LibVLC as the native Android playback engine for local audio and video. The existing HTML/CSS/JavaScript interface and Capacitor bridge are retained. The Android build script adds `org.videolan.android:libvlc-all:3.6.0` and removes Media3/ExoPlayer dependencies.

- Local audio and video only; the build workflow strips Android INTERNET permission.
- Video playback uses the selected Android content URI directly.
- Codec/container support depends on the LibVLC build and device/ABI compatibility.
- The JavaScript/browser preview still uses browser media APIs; native Android playback is LibVLC.
- Validate the GitHub Actions APK build and test playback on the target Android phone before release.

Note: this migration uses an in-process LibVLC audio player. Background playback/media notification integration and the former Android audio-session equalizer presets require separate LibVLC-specific implementation.
