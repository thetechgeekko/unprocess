![logo-sm](https://github.com/user-attachments/assets/264da8ed-7ac7-48b5-b2da-ddd62eafd668)

# unprocess

An open-source Android camera app that captures photos free of computational photography processing. unprocess uses the Camera2 API to capture raw sensor data (DNG) and feeds it through [filmr](https://github.com/thetechgeekko/filmr) — a physics-based Rust film simulation engine — to produce images with the characteristic grain, tone, and color of specific analog film stocks.

## Features

- **RAW (DNG) or JPEG capture** — dual-stream (JPEG + RAW_SENSOR simultaneously); RAW path uses Rust Bayer demosaic for maximum quality
- **30+ film stock presets** — Kodak Portra, Fujifilm Velvia, Ilford HP5+, Kodachrome, and more across 5 style variants
- **Depth-aware effects** (optional) — shallow DOF with Petzval swirl and object-motion blur powered by Depth Anything V2
- **Tap-to-focus** and **pinch-to-zoom** via Camera2 metering rectangles and SCALER_CROP_REGION
- **Full settings panel** — sliders for exposure, white balance, saturation, grain, motion blur, DOF, JPEG quality; real-time SharedPreferences persistence
- **EXIF preservation** — sensor orientation correctly embedded in all output files
- **Graceful degradation** — app runs without `libfilmr.so`; film simulation is silently disabled with a one-time Snackbar notice

## Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog (2023.1.1) or later |
| Android SDK | API 34 (minSdk 29) |
| Rust toolchain | stable (1.75+) |
| cargo-ndk | 3.x (`cargo install cargo-ndk`) |
| Android NDK | r25c or r26 (`ANDROID_NDK_ROOT` must be set) |
| JDK | 17+ |

## Getting Started

### 1 — Clone both repos as siblings

The filmr build script outputs `.so` files directly into `../unprocess/app/src/main/jniLibs/`, so both repos must live in the same parent directory.

```bash
git clone https://github.com/thetechgeekko/filmr
git clone https://github.com/thetechgeekko/unprocess
```

### 2 — Add Rust Android targets

```bash
rustup target add \
    aarch64-linux-android \
    armv7-linux-androideabi \
    x86_64-linux-android \
    i686-linux-android
```

### 3 — Build `libfilmr.so`

```bash
cd filmr

# Film simulation only (~4 MB per ABI)
./android/build-android.sh

# With Depth Anything V2 DOF/motion blur (~20 MB per ABI)
./android/build-android.sh --with-depth

# Debug build (faster compile, slower runtime)
./android/build-android.sh --debug
```

Output:
```
../unprocess/app/src/main/jniLibs/
  arm64-v8a/libfilmr.so
  armeabi-v7a/libfilmr.so
  x86_64/libfilmr.so
  x86/libfilmr.so
```

### 4 — Open and run in Android Studio

> **⚠️ Physical device required.** Camera2 RAW capture does not work on emulators.

1. Open the `unprocess/` directory in Android Studio
2. Let Gradle sync (the `.so` files are already in `jniLibs/` — no Rust build needed)
3. Connect a physical Android device with a RAW-capable camera
4. Click **Run**

## Architecture

The app follows a linear fragment flow: `PermissionsFragment` → `SelectorFragment` (enumerates RAW-capable cameras) → `CameraFragment` ↔ `SettingsFragment`, with `ImageViewerFragment` for reviewing captures. Navigation uses Safe Args; `SelectorFragment` passes `camera_id`, `pixel_format`, and `convert_to_jpeg` to `CameraFragment`.

On capture, `CameraFragment` fires a dual-stream `CaptureRequest` (JPEG + RAW_SENSOR simultaneously), awaits both `CompletableDeferred` results on a background `HandlerThread` (5 s timeout), then calls `saveResult()` on `Dispatchers.IO`. The RAW path runs `DngCreator.writeImage()` → `FilmrEngine.processFromDng()` (Bayer demosaic + film simulation in Rust), falling back to the JPEG companion if unavailable. The JPEG path runs `BitmapFactory.decodeByteArray()` → `FilmrEngine.processChecked()`. See [CLAUDE.md](CLAUDE.md) for the full capture flow and thread model.

### Key Classes

| Class | Responsibility |
|-------|---------------|
| `CameraFragment` | Camera2 session management, dual-stream capture, focus/zoom gestures, file saving |
| `FilmrEngine` | JNI singleton wrapping `libfilmr.so`; no-op if library absent |
| `FilmrConfig` | Film parameters data class; 49-entry `FilmPreset` enum; serialises to Rust JSON |
| `SettingsFragment` | Film settings UI; all controls write to SharedPreferences immediately |
| `ImageViewerFragment` | EXIF-corrected image display; depth/confidence map overlay via ViewPager2 |
| `OrientationLiveData` | Device orientation LiveData; computes relative rotation to camera sensor |

### Settings Persistence

All settings are stored immediately in SharedPreferences key `"filmr_settings"`. Enums are persisted by `.name` (not `.ordinal`) for safety across refactors. `FilmrConfig.load()` / `FilmrConfig.save()` are the only persistence entry points.

## Depth Estimation (Optional)

Requires `libfilmr.so` compiled with `--with-depth`. Download the ~95 MB `depth_anything_v2_vits.rten` model inside the app:

1. Tap **Settings** (gear icon)
2. Scroll to **Depth Estimation** → tap **Download model**
3. A progress bar tracks the download. Once complete, DOF and object-motion sliders become depth-aware

If `isDepthSupported()` returns false (library built without depth feature), depth settings are hidden. The model is loaded only when `dofAmount > 0` or `objectMotionAmount > 0`.

## Feature Flags

| Build flag | Binary size | Effect |
|------------|-------------|--------|
| `android` (default) | ~4 MB/ABI | Film simulation only |
| `android,depth` (`--with-depth`) | ~20 MB/ABI | + Depth Anything V2 monocular depth |

## CI

The Android workflow (`.github/workflows/android.yml`) checks out filmr and unprocess as siblings, cross-compiles all four ABIs with NDK r25c, runs lint, unit tests, and assembles a debug APK. Release APKs are built and uploaded to GitHub Releases on `v*` tags.

## Contributing

Preset calibration and new film stocks belong in the [filmr](https://github.com/thetechgeekko/filmr) repository. Camera, UI, and Android-specific changes belong here. Patches welcome — open a pull request.
