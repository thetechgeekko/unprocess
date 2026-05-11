# FILMR Android — Roadmap

Branch: `claude/merge-camera-filmr-app-lNRaU`  
Repos: `thetechgeekko/unprocess` · `thetechgeekko/filmr`

---

## Milestone 1 · Stability — fix all blocking bugs

Everything here must land before the app is usable on a real device.

| Issue | Repo | What to do |
|-------|------|-----------|
| [unprocess #1](https://github.com/thetechgeekko/unprocess/issues/1) | unprocess | **DngCreator double-write** — write DNG to `ByteArrayOutputStream` once; reuse bytes for both archival file save and `processFromDng` call. Remove second `writeImage` call. |
| [unprocess #2](https://github.com/thetechgeekko/unprocess/issues/2) | unprocess | **INTERNET permission** — add `<uses-permission android:name="android.permission.INTERNET" />` to `AndroidManifest.xml` |
| [unprocess #3](https://github.com/thetechgeekko/unprocess/issues/3) | unprocess | **Android 13 media permissions** — add `READ_MEDIA_IMAGES` (minSdk 33), cap `READ/WRITE_EXTERNAL_STORAGE` with `maxSdkVersion` |
| [unprocess #9](https://github.com/thetechgeekko/unprocess/issues/9) | unprocess | **onCaptureFailed** — implement in `CaptureCallback`: complete both deferreds exceptionally, remove timeout runnable, clear listeners, resume coroutine with exception |
| [unprocess #16](https://github.com/thetechgeekko/unprocess/issues/16) | unprocess | **POLAROID_100_COLOR** — add missing enum entry to `FilmrConfig.kt` |
| [filmr #7](https://github.com/thetechgeekko/filmr/issues/7) | filmr | **build-android.sh preflight** — check `cargo-ndk` installed, `ANDROID_NDK_HOME` set, all 4 Rust targets added |

---

## Milestone 2 · Core UX — the app must feel complete

| Issue | Repo | What to do |
|-------|------|-----------|
| [unprocess #7](https://github.com/thetechgeekko/unprocess/issues/7) | unprocess | **Error feedback** — `Snackbar` when filmr falls back; banner if `FilmrEngine.isAvailable == false` |
| [unprocess #10](https://github.com/thetechgeekko/unprocess/issues/10) | unprocess | **JPEG quality** — add `jpegQuality: Int = 95` to `FilmrConfig`; expose as slider in settings; use in `saveJpeg()` |
| [unprocess #11](https://github.com/thetechgeekko/unprocess/issues/11) | unprocess | **Tap-to-focus** — `GestureDetector` on viewfinder; set `CONTROL_AF_MODE_AUTO` + `CONTROL_AF_REGIONS` on preview request; animate focus ring |
| [unprocess #12](https://github.com/thetechgeekko/unprocess/issues/12) | unprocess | **Pinch-to-zoom** — `ScaleGestureDetector`; update `SCALER_CROP_REGION` on preview request; clamp to `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM` |
| [unprocess #17](https://github.com/thetechgeekko/unprocess/issues/17) | unprocess | **Capture progress indicator** — show processing overlay between shutter tap and save completion; dismiss on success or error |
| [unprocess #18](https://github.com/thetechgeekko/unprocess/issues/18) | unprocess | **Depth model download progress** — replace "Downloading…" text with a `ProgressBar`; show MB downloaded / total |

---

## Milestone 3 · Image Quality — make the simulation physically correct

| Issue | Repo | What to do |
|-------|------|-----------|
| [filmr #6](https://github.com/thetechgeekko/filmr/issues/6) | filmr | **BitsPerSample multi-value** — call `into_u32_vec().ok().and_then(\|v\| v.first().copied())` as fallback when scalar read fails |
| [filmr #4](https://github.com/thetechgeekko/filmr/issues/4) | filmr | **Malvar-He-Cutler demosaic** — replace bilinear 3×3 with MHC 5×5 gradient-corrected kernels; add config flag to choose speed vs quality |
| [filmr #5](https://github.com/thetechgeekko/filmr/issues/5) | filmr | **Color correction matrices** — read `ColorMatrix1` (0xC621) from DNG IFD; invert to get camera→XYZ; multiply with XYZ→sRGB; apply 3×3 to demosaiced output |
| [filmr #3](https://github.com/thetechgeekko/filmr/issues/3) | filmr | **Depth in processRawDng** — add `model_path: JString` param; call `estimate_depth_if_available()` after demosaic; pass `DepthMap` to `process_image_with_depth` |
| [filmr #8](https://github.com/thetechgeekko/filmr/issues/8) | filmr | **Compressed DNG** — detect JPEG / lossless-JPEG / deflate compression from `Compression` tag; error clearly if unsupported rather than returning garbage |

---

## Milestone 4 · Developer Experience

| Issue | Repo | What to do |
|-------|------|-----------|
| [unprocess #4](https://github.com/thetechgeekko/unprocess/issues/4) | unprocess | **CI/CD** — `.github/workflows/android.yml`: checkout both repos, Rust + NDK setup, run `build-android.sh`, `./gradlew lint assembleDebug`, upload APK artifact |
| [filmr #1](https://github.com/thetechgeekko/filmr/issues/1) | filmr | **CI/CD** — `.github/workflows/ci.yml`: `cargo check --features android`, `cargo check --features android,depth`, `cargo test`; cache `~/.cargo` |
| [unprocess #5](https://github.com/thetechgeekko/unprocess/issues/5) | unprocess | **ProGuard rules** — `-keepclasseswithmembernames` for native methods; `-keep` for `FilmrEngine` |
| [unprocess #6](https://github.com/thetechgeekko/unprocess/issues/6) | unprocess | **Developer README** — prerequisites, clone-both-repos, `build-android.sh`, Android Studio run, architecture overview |
| [filmr #2](https://github.com/thetechgeekko/filmr/issues/2) | filmr | **Android integration README** — NDK setup, cargo-ndk, targets, build commands, JNI function reference |

---

## Milestone 5 · Polish & Maintenance

| Issue | Repo | What to do |
|-------|------|-----------|
| [unprocess #8](https://github.com/thetechgeekko/unprocess/issues/8) | unprocess | **IMAGE_BUFFER_SIZE → 3** — one-liner in `CameraFragment` |
| [unprocess #13](https://github.com/thetechgeekko/unprocess/issues/13) | unprocess | **targetSdkVersion 33 → 34** — align with `compileSdkVersion` |
| [unprocess #14](https://github.com/thetechgeekko/unprocess/issues/14) | unprocess | **Kotlin version 1.5.21 → 2.0** — update `build.gradle` |
| [unprocess #15](https://github.com/thetechgeekko/unprocess/issues/15) | unprocess | **utils module SDK** — align `compileSdkVersion`/`targetSdkVersion` with app module |
| [unprocess #19](https://github.com/thetechgeekko/unprocess/issues/19) | unprocess | **Remove duplicate `PREFS_NAME`** — delete unused private constant in `FilmrConfig.kt` |
| [unprocess #20](https://github.com/thetechgeekko/unprocess/issues/20) | unprocess | **Unit tests** — `FilmrConfig` serialisation round-trip; `FilmrEngine` ARGB↔RGB conversion with mocked JNI |

---

## Suggested work order for next session

```
Milestone 1 (all 6 items) → Milestone 2 (items 1-4) → Milestone 3 (items 1-3) → Milestone 4
```

Start with `unprocess #1` (DngCreator crash) and `unprocess #2` (INTERNET permission) as they take under 10 minutes each and unblock real-device testing.
