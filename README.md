![logo-sm](https://github.com/user-attachments/assets/264da8ed-7ac7-48b5-b2da-ddd62eafd668)

# unprocess

A simple, open-source Android camera app that saves photos free of modern
devices' excessive computational photography. Uses the Camera2 API to capture
raw sensor data, optionally running it through the
[filmr](https://github.com/thetechgeekko/filmr) film-simulation engine to
apply authentic analog film looks.

## Features

- RAW (DNG) or JPEG capture
- 30+ film stock presets via filmr (Kodak Portra, Fujifilm Velvia, Ilford HP5+, …)
- Malvar-He-Cutler demosaic + DNG ColorMatrix colour correction on the RAW path
- Depth-aware DOF and object-motion blur (optional — requires depth model download)
- Tap-to-focus and pinch-to-zoom
- Per-capture progress overlay and Snackbar error reporting

## Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog (2023.1.1) or later |
| Android SDK | API 29+ (minSdk 29, targetSdk 34) |
| Rust toolchain | stable (1.75+) |
| cargo-ndk | 3.x (`cargo install cargo-ndk`) |
| Android NDK | r25c or r26 (`ANDROID_NDK_HOME` must be set) |

## Getting started

### 1 — Clone both repos as siblings

The filmr build script outputs `.so` files directly into `../unprocess/app/src/main/jniLibs/`,
so both repos must live in the same parent directory.

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

# Release build — film simulation only (no depth estimation)
./android/build-android.sh

# Release build — film simulation + Depth Anything V2 DOF/motion blur
./android/build-android.sh --with-depth

# Debug build (faster compile, slower at runtime)
./android/build-android.sh --debug
```

The script places `libfilmr.so` for each ABI into:
```
../unprocess/app/src/main/jniLibs/
  arm64-v8a/libfilmr.so
  armeabi-v7a/libfilmr.so
  x86_64/libfilmr.so
  x86/libfilmr.so
```

### 4 — Open and run in Android Studio

1. Open the `unprocess/` directory in Android Studio.
2. Let Gradle sync (it does **not** need to build filmr — the `.so` files are already in `jniLibs/`).
3. Connect a physical device or start an emulator.
4. Click **Run**.

If `libfilmr.so` is absent the app still launches; film simulation is silently
disabled and a one-time Snackbar informs the user.

## Architecture overview

```
CameraFragment
  ├── Camera2 dual-stream capture (JPEG + RAW_SENSOR)
  ├── DngCreator  →  FilmrEngine.processFromDng()   (RAW path, MHC demosaic)
  └── BitmapFactory  →  FilmrEngine.processChecked() (JPEG path)

FilmrEngine (Kotlin singleton)
  ├── System.loadLibrary("filmr")           — loads libfilmr.so at startup
  ├── processImage()          → JNI → Rust  — JPEG/bitmap film simulation
  ├── processImageWithDepth() → JNI → Rust  — as above + depth DOF/motion
  └── processRawDng()         → JNI → Rust  — DNG demosaic + film simulation

FilmrConfig (Kotlin data class)
  ├── Saved to SharedPreferences
  ├── Serialised to JSON for the Rust SimulationConfig
  └── Editable from SettingsFragment

libfilmr.so (Rust, cross-compiled with cargo-ndk)
  ├── src/android.rs   — JNI entry points + DNG decode pipeline
  ├── src/processor.rs — core film simulation
  └── src/depth/       — Depth Anything V2 (feature = "depth" only)
```

## Depth estimation (optional)

Depth-aware DOF simulation and object-motion blur require the
`depth_anything_v2_vits.rten` model (~95 MB). Download it from inside the
app:

1. Tap **Settings** (gear icon on the camera screen).
2. Scroll to **Depth Estimation** and tap **Download model**.
3. A progress bar shows download progress. Once complete, DOF and
   object-motion effects activate whenever their sliders are above zero.

The depth model is only used when `libfilmr.so` was compiled with
`--with-depth`. If it was built without that flag `isDepthSupported()`
returns false and depth settings are hidden.

## Feature flags

| Build flag | Effect |
|------------|--------|
| `android` (default) | Film simulation only. Smaller binary (~4 MB per ABI). |
| `android,depth` (`--with-depth`) | Adds Depth Anything V2 monocular depth estimation. Larger binary (~20 MB per ABI). |

## Support

Patches welcome — fork the repo and open a pull request.
