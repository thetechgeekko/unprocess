# Changelog

All work is on branch **`claude/merge-camera-filmr-app-lNRaU`** across two repos:
- `thetechgeekko/unprocess` — Android app
- `thetechgeekko/filmr` — Rust engine

---

## [Unreleased] — filmr × unprocess integration

### filmr repo — `src/android.rs` + `Cargo.toml`

| Commit | Change |
|--------|--------|
| `37b7be9` | `feat` — initial JNI bindings: `processImage`, `getAvailablePresets`, `getDefaultConfig`; `[lib] crate-type = ["cdylib","rlib"]`; `jni = "0.21"` optional dep; `android` feature flag |
| `023f1e5` | `fix` — add missing `POLAROID_100_COLOR` to JNI preset match (was falling through to Portra 400) |
| `7fb9263` | `feat` — depth JNI: `processImageWithDepth`, `isDepthSupported`; `--with-depth` flag in `build-android.sh` |
| `98ba8de` | `feat` — `processRawDng` JNI: accepts DNG bytes, TIFF-decodes Bayer data, bilinear demosaic → linear RGB → filmr simulation; returns `[w:i32LE][h:i32LE][RGB…]`; adds `tiff = "0.9"` dep (android-gated) |

### unprocess repo

| Commit | Change |
|--------|--------|
| `a87e71e` | `feat` — `FilmrConfig.kt` (47 presets, all enums, SharedPrefs, JSON serialisation); `FilmrEngine.kt` (JNI wrapper, ARGB↔RGB conversion, graceful fallback); `SettingsFragment.kt` + `fragment_settings.xml` (all engine params); `CameraFragment.kt` — settings nav, `applyFilmrProcessing`; `nav_graph.xml` — settings destination; `ic_back.xml` drawable |
| `6125a27` | `fix` — RAW mode respects `args.convertToJpeg`; `Switch` → `SwitchCompat` (API 33); `jniLibs/` dirs with `.gitkeep` + `.gitignore` |
| `820d7b7` | `feat` — depth port: `objectMotionAmount` field in `FilmrConfig`; model-download coroutine in `SettingsFragment`; `processWithDepth` + `isDepthEstimationSupported` in `FilmrEngine`; `CameraFragment` uses depth path when model present and DOF/motion > 0 |
| `29de19b` | `feat` — FILMr dark professional UI: `colors.xml` (FILMr palette from `theme.rs`); `styles.xml` DayNight; section-card layout with `labeled_slider` pattern; amber accent `#E69B32`; camera screen film-info bar; gear icon; landscape layout |
| `7af9125` | `fix` — replace broken `BitmapFactory.decodeFile(dng)` with **dual-stream capture**: `rawImageReader` (RAW_SENSOR) + `jpegImageReader` (JPEG); `CompletableDeferred<Image>` coordination; graceful fallback to JPEG-only on unsupported devices |
| `d14c8be` | `feat` — wire `processRawDng`: `FilmrEngine.processFromDng()` decodes JNI response header, builds Bitmap; `saveResult` RAW path writes DNG to `ByteArrayOutputStream` → `processFromDng` → linear Bayer pipeline; fallback to JPEG companion if library absent |

---

## Known open issues (as of this commit)

See full tracker:
- **unprocess**: https://github.com/thetechgeekko/unprocess/issues
- **filmr**: https://github.com/thetechgeekko/filmr/issues

### Critical bugs
- [unprocess #1] `DngCreator` double-write crash in RAW archive mode
- [unprocess #2] Missing `INTERNET` permission — depth model download fails
- [unprocess #3] Missing `READ_MEDIA_IMAGES` for Android 13+
- [unprocess #9] `onCaptureFailed` not implemented — deferred hangs

### High
- [filmr #7] `build-android.sh` no preflight checks
- [unprocess #16] `POLAROID_100_COLOR` missing from `FilmrConfig.kt` enum

### Medium
- [unprocess #7] No UI error feedback on filmr failure
- [unprocess #10] JPEG quality hardcoded to 100
- [unprocess #11] No tap-to-focus
- [unprocess #12] No pinch-to-zoom
- [filmr #4] Malvar demosaic (bilinear currently)
- [filmr #5] Camera color correction matrices not applied
- [filmr #6] `BitsPerSample` multi-value tag handling

### Enhancement / future
- [unprocess #4] CI/CD pipeline
- [unprocess #5] ProGuard rules
- [unprocess #6] Developer README
- [unprocess #8] `IMAGE_BUFFER_SIZE` → 3
- [filmr #1] CI/CD pipeline
- [filmr #2] Android integration README
- [filmr #3] Depth support in `processRawDng`
- [filmr #8] Compressed DNG support
