# unprocess

**unprocess** is an Android camera app that shoots RAW (DNG) and applies analog film simulation in real time, powered by the [filmr](https://github.com/thetechgeekko/filmr) Rust engine via JNI.

> "Remove the digital look. Get the film feeling."

---

## Download

Grab the latest release APK from the [Releases page](https://github.com/thetechgeekko/unprocess/releases).

**Requirements:** Android 8.0+ (API 26) · RAW-capable camera

---

## Features

### Film Simulation
- **50+ film presets** — Kodak Portra 400/160/800, Ektar 100, Gold 200, ColorPlus 200, UltraMax 400; Fujifilm Provia, Velvia, Superia, X-Pro2; Ilford HP5, Delta 3200, FP4; Agfa Vista, Futura; Polaroid 600, Originals Color; CineStill 800T, 50D; and more
- **Fast / Accurate pipeline** — Fast for preview-speed processing, Accurate for maximum quality with full Bayer demosaic in Rust
- **Filmic S-curve** tone mapping for natural highlight roll-off
- **Grain** — film-accurate luminance grain with per-preset intensity and global multiplier
- **Vignette** — optical vignetting with adjustable strength
- **Chromatic aberration** — lateral CA for lens character
- **Split toning** — independent highlight and shadow hue shifts
- **White balance** — Auto, Daylight, Cloudy, Shade, Tungsten, Fluorescent, Flash; adjustable strength and warmth
- **Saturation** control
- **Output mode** — Positive (colour) or Negative (inverted)

### Camera
- **RAW / DNG capture** via Android Camera2 API — full Bayer sensor data, no in-camera processing
- **JPEG fallback** for cameras without RAW support
- Saves to `DCIM/Camera` (MediaStore on API 29+)
- Camera selector lists only RAW-capable cameras

### Depth & Motion (optional)
- **Depth-of-field** blur, focus point, swirl — powered by Depth Anything V2 ViT-S (~95 MB model, download in Settings)
- **Motion blur** and **object motion** simulation
- **Rotational blur**
- Depth model is never loaded when DoF and object motion are both 0

### Image Viewer
- Pinch-to-zoom (1×–5×) with MATRIX scale type
- Share photo via system share sheet (FileProvider)
- Metadata overlay

### Settings
- All parameters persist immediately (SharedPreferences, keyed by enum `.name`)
- **Debug logcat toggle** — captures app-only logcat to `files/logs/`, shareable as attachment for crash diagnosis
- Depth model download with progress indicator

---

## Architecture

```
PermissionsFragment
    └─► SelectorFragment          (enumerates RAW-capable cameras)
            └─► CameraFragment    ◄──► SettingsFragment
                    └─► ImageViewerFragment
```

### Capture Flow

```
takePhoto()  [suspendCancellableCoroutine]
    ├─ jpegImageReader + rawImageReader listeners
    ├─ CaptureRequest fired
    └─ Awaits jpegDeferred + rawDeferred [CompletableDeferred]
            │
            ▼  (imageReaderThread HandlerThread)
        CombinedCaptureResult
            │
            ▼  saveResult() [Dispatchers.IO]
                ├─ RAW → DngCreator → FilmrEngine.processFromDng()  (Rust Bayer demosaic)
                │         fallback → JPEG companion + processChecked()
                └─ JPEG → BitmapFactory → FilmrEngine.processChecked()
                │
                ▼  saveJpeg() → MediaStore (API 29+) or DCIM/Camera
```

### Engine Layer

| Component | Purpose |
|---|---|
| `FilmrEngine.kt` | JNI singleton wrapping `libfilmr.so`; degrades gracefully when absent |
| `FilmrConfig.kt` | Data class for all film parameters; serialises to Rust `SimulationConfig` JSON |
| `FilmPreset` | Enum of 50+ film stocks |
| `filmr` (Rust) | Cross-compiled to `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` |

### Tech Stack

- **Language:** Kotlin
- **UI:** Android Views + ViewBinding (Jetpack Compose migration planned)
- **Navigation:** Navigation Component with Safe Args
- **Async:** Kotlin Coroutines + `HandlerThread`
- **Native:** Rust (filmr) via JNI, compiled with `cargo-ndk`
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 36 (Android 16)

---

## Building

`filmr/` and `unprocess/` must be **sibling directories**.

```bash
# 1. Build the native library (requires Rust + cargo-ndk + Android NDK r25c)
cd filmr && ./android/build-android.sh
# Copies libfilmr.so into ../unprocess/app/src/main/jniLibs/<abi>/

# 2. Build the app
export ANDROID_HOME="$HOME/android-sdk"
cd unprocess
./gradlew assembleDebug              # debug APK
./gradlew assembleRelease           # release APK
./gradlew assembleDebug -PskipFilmrBuild=true   # skip Rust step (uses pre-built .so)

# Lint & tests
./gradlew lint
./gradlew testDebugUnitTest
```

CI runs lint + unit tests + debug APK build on every push. Release APKs are built and uploaded automatically on `v*` tags.

---

## Roadmap

### In Progress
- [ ] Jetpack Compose + Material 3 UI migration ([#39](https://github.com/thetechgeekko/unprocess/issues/39))
- [ ] Force-close diagnosis on photo capture (debug log → share flow)

### Planned
- [ ] EV compensation slider in CameraFragment ([#40](https://github.com/thetechgeekko/unprocess/issues/40))
- [ ] Capture timer (3 s / 10 s self-timer) ([#41](https://github.com/thetechgeekko/unprocess/issues/41))
- [ ] Live histogram overlay in CameraFragment ([#42](https://github.com/thetechgeekko/unprocess/issues/42))
- [ ] Preset browser with before/after preview ([#43](https://github.com/thetechgeekko/unprocess/issues/43))
- [ ] Custom preset creation and export ([#44](https://github.com/thetechgeekko/unprocess/issues/44))
- [ ] Focus peaking overlay ([#45](https://github.com/thetechgeekko/unprocess/issues/45))
- [ ] Burst mode capture ([#46](https://github.com/thetechgeekko/unprocess/issues/46))
- [ ] Gallery view of processed photos ([#47](https://github.com/thetechgeekko/unprocess/issues/47))
- [ ] filmr Rust engine — GTK/Wayland CI build fix ([filmr#26](https://github.com/thetechgeekko/filmr/issues/26))
- [ ] filmr — additional pipeline stages: bloom, halation, dust/scratch overlay ([filmr#27](https://github.com/thetechgeekko/filmr/issues/27))

### Done in v2.0.0
- [x] filmr Rust engine integration (JNI)
- [x] 50+ film presets
- [x] RAW/DNG capture pipeline
- [x] Grain, vignette, chromatic aberration parameters
- [x] Split toning (highlight/shadow hue shift)
- [x] Filmic S-curve from filmrandroidlibrary fork
- [x] Depth estimation (DoF, motion blur) via Depth Anything V2
- [x] Pinch-to-zoom image viewer
- [x] Photo sharing via FileProvider
- [x] Debug logcat toggle in Settings
- [x] Target SDK 36 (Android 16), minSdk 26
- [x] Edge-to-edge immersive mode (modern API)
- [x] Predictive back gesture support

---

## License

```
Copyright 2024 thetechgeekko

Licensed under the Apache License, Version 2.0
```

Portions derived from the [Android Camera2 Basic sample](https://github.com/android/camera-samples) — Apache License 2.0.
