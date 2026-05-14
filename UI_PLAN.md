# UI Plan: BeerCSS Integration for unprocess

## Overview

This document defines a concrete, phased plan to integrate [BeerCSS](https://www.beercss.com) (v4.0.21) into the **unprocess** Android camera app. BeerCSS is a Material Design 3 CSS framework (14.4 KB brotli, zero build-step) whose 37 color token names are structurally identical to Android Material You `colorScheme` attribute names — making cross-layer visual consistency straightforward.

### Integration Vectors

| Vector | Description | Cost | Value |
|---|---|---|---|
| **A — WebView Settings Panel** | Replace `SettingsFragment` with a `WebView` loading `assets/settings/index.html`, styled with BeerCSS, bridged to Kotlin via `JavascriptInterface` | Medium | High |
| **B — Companion Photo Viewer** | HTML page generated alongside each JPEG export; per-film accent color via `beercss.theme(hex)` | Low | Medium |
| **C — Design Reference Alignment** | Use BeerCSS tokens as canonical reference for Android XML `themes.xml` — no web layer | Low | Medium |

**Recommended order: A → C → B**, then Phase 4 (live histogram in panel).

---

## 1. BeerCSS Framework Capabilities

### Component Inventory

| Category | Components & Class Names |
|---|---|
| **Containers** | `article`, `article.border`, `dialog`, `dialog.bottom` (bottom sheet), `dialog.max` (full-screen) |
| **Navigation** | `nav.top`, `nav.bottom`, `nav.left`; `.tabs` + `a.active` (horizontal scrollable tab row) |
| **Buttons** | `button`, `button.fill`, `button.border`, `button.circle`, `button.circle.fill` (FAB), `button.extend` (extended FAB) |
| **Chips** | `.chip`, `.chip.fill`, `.chip.fill.active`, `.chip.small` |
| **Sliders** | `.slider`, `.slider.medium`; `input[type=range]`; `.tooltip.bottom` value readout |
| **Form Fields** | `.field`, `.field.label`, `.field.border`; styled `select`, `input`, `textarea` |
| **Selection** | `.checkbox`, `.radio`, `.switch` (toggle) |
| **Feedback** | `.snackbar`; `progress.circle.indeterminate`; `.badge.circle`; `.tooltip` |
| **Expansion** | Native `<details>`/`<summary>` — no custom class needed |
| **Layout** | `.grid`, `.row`, `.max`, `.absolute`, `.fixed`, `.center`, `.middle` |

### Key APIs
- **Dark mode:** `<body class="dark">` — single class, no JS
- **Dynamic color:** `beercss.theme("#c0876a")` — seeds all 37 tokens from one hex (equivalent to Android `DynamicColors`)
- **Amber palette helpers:** `amber1`–`amber10` named color helpers — directly applicable to film-stock aesthetic
- **Animation tokens:** `--speed1: 0.1s` through `--speed4: 0.4s` (maps to Android `motionDurationShort2`/`Medium1`)
- **Elevation:** `--elevate1`–`--elevate3` with correct shadow values
- **Safe-area insets:** `--top`, `--bottom` derived from `env(safe-area-inset-*)` — handles notches in WebView

---

## 2. Screen-by-Screen Component Mapping

### 2.1 PermissionsFragment — Design Reference (Vector C)

| Android widget | BeerCSS anatomy |
|---|---|
| Centered container | `article` with `--surface-container-low` background |
| Permission icon | `<i>` (Material Symbols Outlined), `font-size: 4rem; color: var(--primary)` |
| Rationale text | `<p>` + `<h5>` with `--on-surface` |
| Grant button | `button.fill` — `--primary` fill, `1rem` radius |
| Deny text button | `button` (no fill) — `--primary` text color |

### 2.2 SelectorFragment — Design Reference (Vector C)

```html
<!-- reference for Android RecyclerView item layout -->
<article class="border">
  <div class="row">
    <i class="large">camera_rear</i>
    <div class="max">
      <h6>Main Camera</h6>
      <p class="medium-text">f/1.8 · 50 MP · RAW</p>
    </div>
    <i>chevron_right</i>
  </div>
</article>
```

Selected state: `.active` → `--primary-container` bg, `--on-primary-container` text.

### 2.3 CameraFragment — Vector C (chrome only; SurfaceView stays native)

| UI Element | BeerCSS anatomy | Android implementation |
|---|---|---|
| Capture button | `button.circle.extra.fill` — 3.5rem, `--primary` | 72dp `MaterialButton`, `shapeAppearanceFull` |
| Film info bar (top) | `nav.top` with translucent `header` | `ConstraintLayout` + `RenderEffect` blur (API 31+) |
| Preset strip | `.tabs` horizontal scroll | `TabLayout`, `tabIndicatorColor = --primary` |
| Histogram overlay | `article.border` at 60% opacity | Custom `View`, `--surface` bg α=0.6 |
| Focus ring | `border-radius` + `scale` anim, `--speed2`/`--speed3` | `ObjectAnimator` at 200/300 ms |
| Settings FAB | `button.circle.fill.extend` | `ExtendedFloatingActionButton`, `--tertiary` |

### 2.4 SettingsFragment → WebView Settings Panel (Vector A — primary deliverable)

Replace `SettingsFragment` with a `BottomSheetDialogFragment` (or full-screen fragment) containing a `WebView` loading `file:///android_asset/settings/index.html`.

#### Kotlin Bridge

**`SettingsBridge.kt`:**
```kotlin
class SettingsBridge(
    private val webView: WebView,
    private val onConfigChanged: (FilmrConfig) -> Unit,
    private var config: FilmrConfig
) {
    /** Push current FilmrConfig values into HTML controls after page load. */
    fun pushConfigToJs() {
        val json = Gson().toJson(config)
        webView.post {
            webView.evaluateJavascript("loadConfig('${json.replace("'", "\\'")}');", null)
        }
    }

    @JavascriptInterface fun setFilmPreset(value: String) { update(config.copy(preset = FilmPreset.valueOf(value))) }
    @JavascriptInterface fun setStyle(value: String)      { update(config.copy(style = FilmStyle.valueOf(value))) }
    @JavascriptInterface fun setSimulationMode(value: String) { update(config.copy(simulationMode = SimulationMode.valueOf(value))) }
    @JavascriptInterface fun setGrain(value: String)     { update(config.copy(grainAmount = value.toFloatOrNull() ?: config.grainAmount)) }
    @JavascriptInterface fun setVignette(value: String)  { update(config.copy(vignetteAmount = value.toFloatOrNull() ?: config.vignetteAmount)) }
    @JavascriptInterface fun setDof(value: String)       { update(config.copy(dofAmount = value.toFloatOrNull() ?: config.dofAmount)) }
    @JavascriptInterface fun setMotionBlur(value: String){ update(config.copy(motionBlurAmount = value.toFloatOrNull() ?: config.motionBlurAmount)) }
    @JavascriptInterface fun setWhiteBalance(value: String) { update(config.copy(whiteBalanceMode = WhiteBalanceMode.valueOf(value))) }
    @JavascriptInterface fun setJpegQuality(value: String)  { update(config.copy(jpegQuality = value.toIntOrNull() ?: config.jpegQuality)) }
    @JavascriptInterface fun resetDefaults() { update(FilmrConfig()); pushConfigToJs() }
    @JavascriptInterface fun navigateBack()  { onConfigChanged(config) }

    private fun update(c: FilmrConfig) { config = c; onConfigChanged(c) }
}
```

**`WebViewSettingsFragment.kt`:**
```kotlin
@SuppressLint("SetJavaScriptEnabled") // Loads only file:///android_asset/ — no remote content
class WebViewSettingsFragment : Fragment() {
    private val viewModel: SettingsViewModel by activityViewModels()
    private lateinit var webView: WebView
    private lateinit var bridge: SettingsBridge

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        WebView(requireContext()).also {
            webView = it
            it.settings.javaScriptEnabled = true
            it.settings.allowFileAccess = true
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bridge = SettingsBridge(
            webView = webView,
            onConfigChanged = { viewModel.updateConfig(it) },
            config = viewModel.config.value ?: FilmrConfig()
        )
        webView.addJavascriptInterface(bridge, "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) = bridge.pushConfigToJs()
        }
        webView.loadUrl("file:///android_asset/settings/index.html")

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            bridge.navigateBack()
            viewModel.persistConfig()
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    /** Push live histogram (256-bin float array) into the panel canvas. Phase 4. */
    fun pushHistogram(bins: FloatArray) {
        val json = bins.joinToString(",", "[", "]")
        webView.post { webView.evaluateJavascript("updateHistogram($json)", null) }
    }
}
```

#### JS-side hydration (settings.js)

```javascript
function loadConfig(jsonString) {
  const cfg = JSON.parse(jsonString);
  setSelectValue('film-preset',  cfg.preset);
  setSelectValue('style-mode',   cfg.style);
  setSelectValue('sim-mode',     cfg.simulationMode);
  setRangeValue('grain',         cfg.grainAmount * 100);
  setRangeValue('vignette',      cfg.vignetteAmount * 100);
  setRangeValue('dof',           cfg.dofAmount * 100);
  setRangeValue('motion-blur',   cfg.motionBlurAmount * 100);
  setSelectValue('wb-mode',      cfg.whiteBalanceMode);
  setRangeValue('jpeg-quality',  cfg.jpegQuality);
}

function setSelectValue(id, value) {
  const el = document.getElementById(id);
  if (el) el.value = value;
}

function setRangeValue(id, value) {
  const el = document.getElementById(id);
  if (el) {
    el.value = value;
    el.dispatchEvent(new Event('input')); // BeerCSS needs this to repaint the track fill
  }
}

function updateHistogram(bins) { /* Phase 4 — draw on <canvas id="histogram"> */ }
```

#### Complete `assets/settings/index.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <link rel="stylesheet" href="vendor/beer.min.css">
  <link rel="stylesheet" href="theme.css">
  <script src="settings.js"></script>
</head>
<body class="dark">

<nav class="top">
  <button class="transparent circle" onclick="Android.navigateBack()">
    <i>arrow_back</i>
  </button>
  <h5 class="max">Settings</h5>
</nav>

<main class="responsive">

  <!-- Film Simulation -->
  <article class="no-padding">
    <h6 class="small padding">Film Simulation</h6>

    <div class="field label border margin">
      <select id="film-preset" onchange="Android.setFilmPreset(this.value)"></select>
      <label class="active">Film Preset</label>
    </div>

    <div class="field label border margin">
      <select id="style-mode" onchange="Android.setStyle(this.value)">
        <option value="ACCURATE">Accurate</option>
        <option value="ARTISTIC">Artistic</option>
        <option value="VINTAGE">Vintage</option>
        <option value="HIGH_CONTRAST">High Contrast</option>
        <option value="PASTEL">Pastel</option>
      </select>
      <label class="active">Style</label>
    </div>

    <div class="field label border margin">
      <select id="sim-mode" onchange="Android.setSimulationMode(this.value)">
        <option value="FAST">Fast (preview quality)</option>
        <option value="ACCURATE">Accurate (full spectral)</option>
      </select>
      <label class="active">Simulation Mode</label>
    </div>
  </article>

  <!-- Adjustments -->
  <article class="no-padding">
    <h6 class="small padding">Adjustments</h6>

    <div class="padding">
      <label>Grain</label>
      <div class="slider">
        <input type="range" min="0" max="100" id="grain"
               oninput="Android.setGrain(this.value / 100)">
        <span></span>
      </div>
    </div>

    <div class="padding">
      <label>Vignette</label>
      <div class="slider">
        <input type="range" min="0" max="100" id="vignette"
               oninput="Android.setVignette(this.value / 100)">
        <span></span>
      </div>
    </div>

    <div class="padding">
      <label>Depth of Field</label>
      <div class="slider">
        <input type="range" min="0" max="100" id="dof"
               oninput="Android.setDof(this.value / 100)">
        <span></span>
      </div>
    </div>

    <div class="padding">
      <label>Motion Blur</label>
      <div class="slider">
        <input type="range" min="0" max="100" id="motion-blur"
               oninput="Android.setMotionBlur(this.value / 100)">
        <span></span>
      </div>
    </div>
  </article>

  <!-- Camera -->
  <article class="no-padding">
    <h6 class="small padding">Camera</h6>

    <div class="field label border margin">
      <select id="wb-mode" onchange="Android.setWhiteBalance(this.value)">
        <option value="AUTO">Auto</option>
        <option value="DAYLIGHT">Daylight (5600 K)</option>
        <option value="CLOUDY">Cloudy (6500 K)</option>
        <option value="TUNGSTEN">Tungsten (3200 K)</option>
        <option value="FLUORESCENT">Fluorescent (4000 K)</option>
      </select>
      <label class="active">White Balance</label>
    </div>

    <div class="padding">
      <label>JPEG Quality</label>
      <div class="slider">
        <input type="range" min="50" max="100" id="jpeg-quality"
               oninput="Android.setJpegQuality(this.value)">
        <span></span>
      </div>
    </div>
  </article>

  <!-- Actions -->
  <article>
    <nav>
      <button class="border responsive" onclick="Android.resetDefaults()">
        <i>restart_alt</i>
        <span>Reset Defaults</span>
      </button>
    </nav>
  </article>

</main>

<div class="snackbar" id="saved-snack">Settings applied</div>

<script type="module" src="vendor/beer.min.js"></script>
<script>
window.addEventListener('load', () => {
  // Warm amber seed — Kodachrome warmth
  beercss.theme('#c0876a');

  // Populate preset dropdown dynamically (FilmPreset enum values from Kotlin)
  const presetValues = [
    ['KODAK_PORTRA_400','Kodak Portra 400'],['KODAK_PORTRA_160','Kodak Portra 160'],
    ['KODAK_PORTRA_800','Kodak Portra 800'],['KODAK_TRI_X_400','Kodak Tri-X 400'],
    ['KODAK_GOLD_200','Kodak Gold 200'],['KODAK_EKTAR_100','Kodak Ektar 100'],
    ['SUPERIA_400','Fuji Superia 400'],['VELVIA_50','Fuji Velvia 50'],
    ['PROVIA_100F','Fuji Provia 100F'],['HP5_PLUS_400','Ilford HP5 Plus'],
    ['FP4_PLUS_125','Ilford FP4 Plus'],['DELTA_400_PROFESSIONAL','Ilford Delta 400'],
    ['CINESTILL_800T','CineStill 800T'],['CINESTILL_50D','CineStill 50D'],
  ];
  const sel = document.getElementById('film-preset');
  presetValues.forEach(([val, label]) => {
    const opt = document.createElement('option');
    opt.value = val; opt.textContent = label;
    sel.appendChild(opt);
  });
  sel.onchange = () => Android.setFilmPreset(sel.value);
});
</script>

</body>
</html>
```

### 2.5 ImageViewerFragment — Vector B + Vector C

#### Native Chrome (Vector C)

| Element | BeerCSS anatomy | Android |
|---|---|---|
| Share FAB | `button.circle.fill` (`--tertiary`) | `FloatingActionButton` |
| Bottom action bar | `nav.bottom.fixed` with icon `button.circle` | `BottomAppBar` |
| EXIF chip row | `.chip.fill.small` per field | `ChipGroup` + `Chip` |
| Save progress | `progress.circle.indeterminate` | `CircularProgressIndicator` |

#### Companion Photo Viewer (Vector B)

A `photo.meta.json` sidecar is written alongside every JPEG export. A viewer template (`assets/viewer/index.html`) is copied to the export folder and opened via a browser intent:

```html
<!-- assets/viewer/index.html (abridged) -->
<body class="dark">
<div id="wrap">
  <img id="photo" src="photo.jpg" />
  <div id="chrome">
    <div class="row" style="gap:.5rem; flex-wrap:wrap;">
      <span class="chip fill small" id="c-film"></span>
      <span class="chip fill small" id="c-iso"></span>
      <span class="chip fill small" id="c-shutter"></span>
    </div>
  </div>
</div>
<button class="circle fill absolute bottom right"
        onclick="document.getElementById('dlg-exif').setAttribute('open','')"><i>info</i></button>
<dialog class="bottom medium" id="dlg-exif">
  <ul class="list medium-space" id="exif-list"></ul>
</dialog>
<script>
fetch('photo.meta.json').then(r=>r.json()).then(m => {
  beercss.theme(m.filmColor); // per-film accent color
  document.getElementById('c-film').textContent = m.filmPreset;
  document.getElementById('c-iso').textContent  = 'ISO ' + m.iso;
  document.getElementById('c-shutter').textContent = m.shutter;
});
</script>
</body>
```

**Per-preset `filmColor` hex:**

| Preset | Color |
|---|---|
| Kodachrome | `#c0876a` (warm amber) |
| Portra | `#b8956b` (warm neutral) |
| Velvia | `#3d8b6e` (saturated teal) |
| Tri-X / HP5 | `#7a7a8a` (neutral grey) |
| Ektar | `#c05050` (vivid red-orange) |
| CineStill 800T | `#8a6a9a` (tungsten violet) |

---

## 3. Design Token Alignment (Vector C)

| BeerCSS token | Android attribute | Dark value |
|---|---|---|
| `--primary` | `colorPrimary` | `#FFB300` (amber) |
| `--on-primary` | `colorOnPrimary` | `#1A0D00` |
| `--primary-container` | `colorPrimaryContainer` | `#3D2800` |
| `--on-primary-container` | `colorOnPrimaryContainer` | `#FFDEA0` |
| `--secondary` | `colorSecondary` | `#D4A843` |
| `--tertiary` | `colorTertiary` | `#6ECFBE` |
| `--background` | `colorBackground` | `#120D08` |
| `--surface` | `colorSurface` | `#1A1209` |
| `--on-surface` | `colorOnSurface` | `#EDE0D0` |
| `--on-surface-variant` | `colorOnSurfaceVariant` | `#C5B49A` |
| `--outline` | `colorOutline` | `#7A6A54` |
| `--speed2` (0.2 s) | `motionDurationShort2` | 200 ms |
| `--speed3` (0.3 s) | `motionDurationMedium1` | 300 ms |
| `1rem` corner radius | `shapeAppearanceMediumComponent` | `16dp` |
| `--elevate1` | card elevation | 1 dp |
| `--elevate3` | FAB elevation | 6 dp |

`res/values/colors.xml` for Phase 2:
```xml
<resources>
  <color name="md_theme_dark_primary">#FFB300</color>
  <color name="md_theme_dark_onPrimary">#1A0D00</color>
  <color name="md_theme_dark_primaryContainer">#3D2800</color>
  <color name="md_theme_dark_onPrimaryContainer">#FFDEA0</color>
  <color name="md_theme_dark_background">#120D08</color>
  <color name="md_theme_dark_surface">#1A1209</color>
  <color name="md_theme_dark_onSurface">#EDE0D0</color>
  <color name="md_theme_dark_surfaceVariant">#2C2010</color>
  <color name="md_theme_dark_outline">#7A6A54</color>
</resources>
```

---

## 4. File Structure

```
app/src/main/
  assets/
    settings/
      index.html          ← Phase 1+2 WebView settings page
      settings.js         ← JS hydration & slider wiring
      theme.css           ← unprocess dark amber theme overrides
      vendor/
        beer.min.css      ← pinned beercss@4.0.21 (committed, offline)
        beer.min.js
        material-symbols-outlined.woff2  ← icon font subset
    viewer/
      index.html          ← Phase 3 companion viewer template
      vendor/             ← same pinned files (copy or symlink)
  java/.../ui/
    settings/
      WebViewSettingsFragment.kt   ← new Phase 1
      SettingsBridge.kt            ← new Phase 1
  java/.../data/
    PhotoMeta.kt                   ← new Phase 3
  res/values/
    colors.xml                     ← updated Phase 2
    themes.xml
```

**Download vendor files (run once, commit to repo):**
```bash
mkdir -p app/src/main/assets/settings/vendor
curl -o app/src/main/assets/settings/vendor/beer.min.css \
  https://cdn.jsdelivr.net/npm/beercss@4.0.21/dist/cdn/beer.min.css
curl -o app/src/main/assets/settings/vendor/beer.min.js \
  https://cdn.jsdelivr.net/npm/beercss@4.0.21/dist/cdn/beer.min.js
```

APK size impact: **~40 KB** added (CSS + JS, brotli-compressed).

---

## 5. Theming (`theme.css`)

```css
/* assets/settings/theme.css
   Overrides BeerCSS MD3 tokens with unprocess film/darkroom aesthetic.
   Applied after beer.min.css. */

:root,
body.dark {
  /* Primary: warm amber */
  --primary: #FFB300;
  --on-primary: #1A0D00;
  --primary-container: #3D2800;
  --on-primary-container: #FFDEA0;

  /* Secondary: desaturated gold */
  --secondary: #D4A843;
  --on-secondary: #1F1200;
  --secondary-container: #2E1E00;
  --on-secondary-container: #F0C96A;

  /* Tertiary: cool teal (histogram overlays) */
  --tertiary: #6ECFBE;
  --on-tertiary: #003730;
  --tertiary-container: #004F47;

  /* Background: deep shadow */
  --background: #120D08;
  --on-background: #EDE0D0;

  /* Surface hierarchy */
  --surface: #1A1209;
  --on-surface: #EDE0D0;
  --surface-variant: #2C2010;
  --on-surface-variant: #C5B49A;
  --surface-container-low: #1A1209;
  --surface-container: #211709;
  --surface-container-high: #2C200F;
  --surface-container-highest: #372A18;

  /* Outline */
  --outline: #7A6A54;
  --outline-variant: #3D3022;
}

.slider input[type="range"] { accent-color: var(--primary); }

.chip.active, a.chip.active {
  background-color: var(--primary-container);
  color: var(--on-primary-container);
  border-color: var(--primary);
}

nav.top { background-color: var(--surface-container-high); }

@font-face {
  font-family: 'Material Symbols Outlined';
  font-style: normal;
  src: url('vendor/material-symbols-outlined.woff2') format('woff2');
}
```

---

## 6. Phased Roadmap

### Phase 1 — WebView Settings Panel (2 weeks)

| Task | Acceptance criteria |
|---|---|
| Commit `beer.min.css` + `beer.min.js` to `assets/settings/vendor/` | No network permission needed at runtime |
| `SettingsBridge.kt` with all `@JavascriptInterface` methods | `setParam("grainAmount", 0.5f)` reaches `FilmrConfig` within 50 ms |
| `WebViewSettingsFragment` as `BottomSheetDialogFragment` | Panel slides up on FAB press; back closes it |
| `index.html` with: preset dropdown, style/mode selects, grain/vignette/DOF/motionBlur sliders, WB select, JPEG quality slider, reset button | All controls hydrated from `FilmrConfig` on open |
| Dark amber theme `#c0876a` | Warm amber primary; no light flash on open |
| Pre-warm `WebView` in `CameraFragment.onViewCreated()` | Panel opens in < 150 ms |
| Works on API 26+ | Tested on emulator API 26 + 34 |

### Phase 2 — Design Token Alignment (2 weeks)

| Task | Acceptance criteria |
|---|---|
| `colors.xml` updated with amber/dark palette | All attributes traceable to §3 token table |
| Capture button: 72dp circle, `--primary` fill | Matches `button.circle.extra.fill` anatomy |
| Preset strip: `TabLayout` underline indicator at `--primary` | Smooth scroll; active tab highlighted |
| Card corners: `16dp` app-wide | Consistent with `1rem` BeerCSS standard |
| Enable Material You `DynamicColors` on API 31+ | Fallback to static amber palette on older devices |

### Phase 3 — Companion Photo Viewer (2 weeks)

| Task | Acceptance criteria |
|---|---|
| `PhotoMeta.kt` + write `photo.meta.json` alongside each export | Valid JSON next to every JPEG/DNG |
| `filmColor` hex for all presets | 6+ presets covered |
| `assets/viewer/index.html` bundled | Opens in Chrome; correct film chips and per-film accent color |
| "View in browser" intent from `ImageViewerFragment` | Works offline |

### Phase 4 — Live Histogram in Panel (1 week)

| Task | Acceptance criteria |
|---|---|
| `<canvas id="histogram" height="80">` above preset tabs | Canvas visible in panel |
| JS 3-channel (R/G/B) 256-bin histogram renderer | Correct on simulated data |
| `pushHistogram(FloatArray)` from `CameraFragment` at 4 fps | No viewfinder jank; dispatched on `Dispatchers.Default` |
| Histogram latency ≤ 250 ms | |

---

## 7. Risks and Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| WebView JS execution blocked on some OEMs | High | Explicitly set `javaScriptEnabled = true`; show error view on page load failure |
| `@JavascriptInterface` runs on WebView background thread | High | All config mutations via `SettingsViewModel` `LiveData`; never touch Views from bridge |
| Slider value lost on back press race condition | Medium | `OnBackPressedCallback` calls `bridge.navigateBack()` + `viewModel.persistConfig()` before popping |
| `file://` CSP restrictions in future WebView | Medium | Migrate to `WebViewAssetLoader` at `http://appassets.androidplatform.net/` — one-day change |
| BeerCSS version drift | Low | CSS/JS committed at pinned version; update manually with changelog review |
| Slider track fill not repainting on programmatic value set | Certain | Fixed in `setRangeValue()` via `el.dispatchEvent(new Event('input'))` |
| Material Symbols glyph missing from bundled WOFF2 | Low | Bundle generous subset (50+ icons); BeerCSS renders icon name as text fallback |
| APK size increase | Certain (Low risk) | ~40 KB compressed — negligible |

---

## 8. BeerCSS Quick Reference

```
Containers    article  article.border  dialog  dialog.bottom  dialog.max
Navigation    nav  nav.top  nav.bottom  header  .tabs  a.active
Buttons       button  button.fill  button.border  button.circle
              button.circle.fill  button.circle.extra  button.extend
Chips         .chip  .chip.fill  .chip.fill.active  .chip.small
Sliders       .slider  .slider.medium  input[type=range]  .tooltip.bottom
Fields        .field  .field.label  .field.border  .field.prefix
Selections    .checkbox  .radio  .switch
Feedback      .snackbar  .progress.circle.indeterminate  .badge.circle
Expansion     details  summary  (no custom class needed)
Layout        .grid  .row  .max  .absolute  .fixed  .center  .middle
Typography    .medium-text  .large-text  .bold  .truncate  h5  h6
Responsive    .s1--.s12  .m1--.m12  .l1--.l12
Utilities     .wave  .no-wave  .border  .fill  .active  .dark
```

---

*BeerCSS version: 4.0.21 — Material Design 3. Android target: API 26+. Last updated: 2026-05-14.*
