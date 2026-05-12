# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Requires ANDROID_HOME set (session-start hook installs SDK if absent)
export ANDROID_HOME="$HOME/android-sdk"

# Build
./gradlew assembleDebug                            # debug APK (uses pre-built libfilmr.so)
./gradlew assembleRelease                          # release APK (minifyEnabled)
./gradlew assembleDebug -PskipFilmrBuild=true      # skip Rust cross-compile

# Test
./gradlew testDebugUnitTest                        # all unit tests
./gradlew testDebugUnitTest --tests "*.FilmrConfigTest"  # single test class
./gradlew lint                                     # Android lint

# Rebuild native library (run from repo root, filmr must be a sibling directory)
cd ../filmr && android/build-android.sh
# Copies libfilmr.so into ../unprocess/app/src/main/jniLibs/{abi}/
```

## Repo Layout

`filmr/` and `unprocess/` must be **sibling directories** — the Gradle build and CI both clone them side-by-side. The pre-built `.so` files live in `app/src/main/jniLibs/<abi>/`.

## Architecture

### Fragment Navigation

```
PermissionsFragment
    └─► SelectorFragment          (enumerates RAW-capable cameras)
            └─► CameraFragment    ◄──► SettingsFragment
                    └─► ImageViewerFragment
```

Navigation uses Safe Args. `SelectorFragment` passes `camera_id`, `pixel_format` (RAW_SENSOR or JPEG), and `convert_to_jpeg` to `CameraFragment`.

### Capture Flow (`CameraFragment.kt`)

```
takePhoto()   [suspendCancellableCoroutine]
    │
    ├─ Sets OnImageAvailableListener on jpegImageReader + rawImageReader
    ├─ Fires CaptureRequest
    └─ Awaits jpegDeferred + rawDeferred  [CompletableDeferred]
            │
            ▼ (results arrive on imageReaderThread HandlerThread)
        CombinedCaptureResult
            │
            ▼ saveResult()  [withContext(Dispatchers.IO)]
                │
                ├─ RAW path:  DngCreator → bytes → FilmrEngine.processFromDng()
                │             [high-quality Bayer demosaic in Rust]
                │             fallback → JPEG companion + processChecked()
                │
                └─ JPEG path: BitmapFactory.decodeByteArray()
                              → FilmrEngine.processChecked()
                │
                ▼ saveJpeg() → MediaStore (API 30+) or DCIM/Camera
```

**Thread model**: `cameraThread` + `imageReaderThread` are `HandlerThread`s started in `onResume` and stopped (`quitSafely()`) in `onPause`. Camera2 callbacks run on `cameraThread`; image dequeue runs on `imageReaderThread`. Coroutine continuations bridge to the IO dispatcher.

### Engine Layer

**`FilmrEngine.kt`** — JNI singleton wrapping `libfilmr.so`. Degrades gracefully if the library is absent.

Key methods:
- `processChecked(bitmap, config)` → `Pair<Bitmap?, String?>` — JPEG/bitmap path
- `processFromDng(dngBytes, config, modelPath?)` → `Bitmap?` — RAW/DNG path
- `isDepthEstimationSupported()` — returns false when built without `depth` feature

**`FilmrConfig.kt`** — All film parameters as a data class. Enums (`FilmPreset`, `FilmStyle`, `SimulationMode`, `OutputMode`, `WhiteBalanceMode`) are persisted by `.name` (not `.ordinal`) in SharedPreferences key `"filmr_settings"`. `toSimConfigJson()` serialises to the Rust `SimulationConfig` JSON format. All `SettingsFragment` controls write immediately on change — there is no explicit save button. `FilmrConfig.load()` / `FilmrConfig.save()` are the only persistence entry points.

### Depth Estimation

Optional feature requiring the ~95 MB `depth_anything_v2_vits.rten` model (download button in `SettingsFragment`). The model path is passed to `processFromDng` / `processWithDepth`. When `dofAmount` and `objectMotionAmount` are both 0 the depth model is not loaded even if present. The download coroutine uses a 30 s connect / 60 s read timeout and checks `isActive` each iteration.

