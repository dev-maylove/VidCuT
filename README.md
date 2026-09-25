# VidCut — Phase 6–10 + Optimized Splash & Modern Media Picker

Cumulative continuation of Phase 5 with deep bug fixes, latest toolchain, optimized animated splash, and system Photo Picker integration.

## What’s new in this build (v1.9.0)

- **Optimized splash animation**
  - Hardware-accelerated logo (fade + overshoot scale)
  - Coordinated AnimatorSet
  - Smooth decelerated progress bar (0–1000 precision)
  - Staggered label fade
  - Proper cancel on destroy (no leak)
- **Modern media picker** — `ActivityResultContracts.PickVisualMedia` (VideoOnly)
  - Uses system Photo Picker on supported devices
  - Automatic fallback to document picker
- **Latest toolchain**
  - Android Gradle Plugin 9.4.1
  - Kotlin 2.4.20
  - Compose BOM 2026.09.00
  - Media3 1.11.1
  - Gradle 9.6.1
  - compileSdk / targetSdk 36
  - Java 17

## Phase overview

### Phase 6 — Auto Caption Foundation
- CaptionEngine interface for Whisper/Vosk/on-device transcription adapters.
- CaptionPipeline normalizes and wraps caption text.
- Existing SRT/manual subtitle flow remains available.

### Phase 7 — Voice / TTS
- Android TextToSpeech engine (Indonesian locale default).
- Generates WAV narration in app cache.

### Phase 8 — Vertical Render Pipeline
- 1080×1920 / 1080×1080 / 1920×1080 profiles.
- FPS, CRF, subtitle, watermark and audio-volume configuration.

### Phase 9 — MediaStore Export
- User-visible MP4 destination: Movies/VidCut.
- MediaStore + IS_PENDING for safe publication.

### Phase 10 — Release Preparation
- Release checklist model.
- Production signing via CI secrets only.

## Build

```bash
chmod +x gradlew
./gradlew assembleDebug
```

## GitHub Actions

### Android CI
`.github/workflows/android-ci.yml` — lint, unit tests, debug APK.

### Release
`.github/workflows/android-release.yml` — signed APK/AAB on `v*` tags.

Required Secrets:
- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Visual Branding

- `vidcut_app_icon.png` — launcher
- `vidcut_logo.png` — Studio UI
- `vidcut_splash.png` — animated splash artwork (transparent over black)

## Important
Process only video/audio you own or are authorized/licensed to use.
