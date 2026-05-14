# UI Plan: BeerCSS Integration for unprocess

## Overview

This document defines a concrete, phased plan to integrate the [BeerCSS](https://www.beercss.com) framework (v4.0.21) into the **unprocess** Android camera app ecosystem. BeerCSS is a Material Design 3 CSS framework (14.4 KB brotli, zero build-step dependencies) whose design token system maps directly onto Android's Material You color scheme — making it the most compatible web framework available for a companion or embedded web layer in a native MD3 Android app.

The plan covers three integration vectors ordered by implementation value:

| Vector | Description | Cost | Value |
|---|---|---|---|
| **A — WebView Settings Panel** | Replace/augment SettingsFragment with a local HTML page in an Android `WebView`, styled with BeerCSS, communicating with Kotlin via `JavascriptInterface` | Medium | High |
| **B — Companion Photo Viewer** | A static HTML file generated alongside each export, viewable in any browser, showing the photo with film metadata and dynamic theming | Low | Medium |
| **C — Design Reference Alignment** | Use BeerCSS token values and component anatomy as the canonical reference for Android XML Material You themes — no web layer, purely a design discipline | Low | Medium |

**Recommended order: A → C → B**, then Phase 4 (live histogram in panel).

---

## 1. BeerCSS Framework Capabilities

### 1.1 Installation (no build step)

```html
<link href="beer.min.css" rel="stylesheet" />
<script type="module" src="beer.min.js"></script>
<!-- Optional: Material You dynamic color seeding -->
<script type="module" src="material-dynamic-colors.min.js"></script>
```

Both files are committed to `app/src/main/assets/` so no network permission is needed at runtime.

### 1.2 Full Component Inventory

| Category | Components & Class Names |
|---|---|
| **Containers** | `article` (card), `article.border`, `article.small/.medium/.large`; `dialog`, `dialog.bottom` (bottom sheet), `dialog.left/.right` (drawer), `dialog.max` (full-screen); `.grid` (12-col responsive) |
| **Navigation** | `nav.top`, `nav.bottom`, `nav.left`, `nav.right`; `nav.tabbed`, `nav.toolbar`; `header`, `footer`; `.tabs` + `a.active` (tab row) |
| **Buttons** | `button`, `button.fill`, `button.border`, `button.circle`, `button.square`; sizes `.small/.large/.extra`; `button.extend` (expandable FAB); `button.circle.fill` (standard FAB) |
| **Chips** | `.chip`, `.chip.fill`, `.chip.fill.active`; `.chip.small/.medium/.large` |
| **Sliders** | `.slider`, `.slider.medium`, `.slider.vertical`; sizes `.tiny/.small/.medium/.large/.extra`; dual-input range support; `.tooltip.bottom` value readout |
| **Form Fields** | `.field`, `.field.fill`, `.field.border`, `.field.label` (floating label), `.field.prefix/.suffix`, `.field.invalid`; `select`, `input`, `textarea` all styled; `output` for helper/error text |
| **Selection** | `.checkbox`, `.radio`, `.switch` (toggle); sizes `.small/.large/.extra` |
| **Feedback** | `.snackbar`, `.snackbar.top`; `progress`, `progress.circle.indeterminate`, `progress.wavy`; `.badge`, `.badge.circle`, `.badge.min`; `.tooltip` with directional variants |
| **Data** | `table.border`, `table.stripes`, `table.fixed`; `.list`, `.list.border`, spacing variants |
| **Expansion** | Native `<details>`/`<summary>` — no custom class needed |
| **Shapes** | `.shape` + 40+ mask presets (`.circle`, `.square`, `.pill`, `.diamond`, `.flower`, etc.) |
| **Overlay** | `.overlay`, `.overlay.active`; native `dialog::backdrop` handled automatically |
| **Pages** | `.page`, `.page.active` with directional slide-in animations (`.top/.bottom/.left/.right`) |
| **Helpers** | Responsive grid `.s1–.s12 / .m1–.m12 / .l1–.l12`; visibility `.s/.m/.l`; positions `.absolute .fixed .center .middle .front .back`; typography `.h1–.h6`, `.small-text/.medium-text/.large-text`, `.bold`, `.truncate`; 22 color palettes × 10 shades; wave/ripple on any element via `.wave` |

### 1.3 Theming System

BeerCSS implements the full MD3 color token set as CSS custom properties (37 tokens total):

```css
:root {
  --primary: #cfbcff;
  --on-primary: #381e72;
  --primary-container: #4f378a;
  --on-primary-container: #e9ddff;
  --secondary: #cbc2db;
  --surface: #141316;
  --background: #1c1b1e;
  --error: #ffb4ab;
  /* ...and 29 more */
}
```

- **Dark mode:** `<body class="dark">` — no JS required
- **Light mode:** `<body class="light">`
- **Material You dynamic color:** `beercss.theme("#c0876a")` — seeds the entire 37-token palette from one hex color (equivalent to Android 12+ `DynamicColors`)

### 1.4 Animation Tokens

```css
--speed1: 0.1s   /* micro-interactions */
--speed2: 0.2s   /* press feedback, focus ring snap-in */
--speed3: 0.3s   /* panel transitions, card hover */
--speed4: 0.4s   /* page transitions */
```

These map to Android's `motionDurationShort2` (200 ms) and `motionDurationMedium1` (300 ms).

### 1.5 Elevation System

```css
--elevate1: 0 0.125rem 0.125rem 0 rgb(0 0 0 / 0.32);   /* cards, chips */
--elevate2: 0 0.25rem 0.5rem 0 rgb(0 0 0 / 0.4);        /* dialogs */
--elevate3: 0 0.375rem 0.75rem 0 rgb(0 0 0 / 0.48);     /* FABs, snackbars */
```

### 1.6 Responsive Breakpoints

| Suffix | Viewport | Use case |
|---|---|---|
| `.s` | ≤ 600 px | Phone portrait (primary unprocess target) |
| `.m` | 601–992 px | Phone landscape / small tablet |
| `.l` | ≥ 993 px | Tablet / desktop companion app |

Safe-area insets (`--top`, `--bottom`, `--left`, `--right`) derived from `env(safe-area-inset-*)` — correctly handles Android notches in WebView.

---

## 2. Screen-by-Screen Component Mapping

### 2.1 PermissionsFragment → Design Reference (Vector C)

| Android widget | BeerCSS equivalent | Token |
|---|---|---|
| Centered container | `article` | `--surface-container-low` bg, `--elevate1` |
| Permission icon | `i` (Material Symbols Outlined) | `font-size: 4rem; color: var(--primary)` |
| Rationale text | `p` + `h5` | `--on-surface`, `0.875rem` body |
| Grant button | `button.fill` | `--primary`, `1rem` radius |
| Deny text button | `button` (no fill) | `--primary` text color |

**Acceptance criteria:** Card uses `--surface-container-low` background equivalent; button uses `--primary` fill; icons from Material Symbols Outlined.

---

### 2.2 SelectorFragment (Camera Picker) → Design Reference (Vector C)

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

**Selected state:** `.active` → `--primary-container` bg, `--on-primary-container` text.

---

### 2.3 CameraFragment (Viewfinder) → Vector A + Vector C

The `SurfaceView`/`TextureView` stays native. BeerCSS informs the chrome overlaid on top.

#### Native UI Chrome (Vector C reference)

| UI Element | BeerCSS anatomy | Android implementation |
|---|---|---|
| **Capture button** | `button.circle.extra.fill` — 3.5rem, `--primary`, ripple | 72dp `MaterialButton`, `shapeAppearanceFull` |
| **Film info bar (top)** | `nav.top` with blurred `header` | `ConstraintLayout` + `RenderEffect` blur (API 31+) |
| **Preset strip** | `.tabs` horizontal scroll | `TabLayout` with `tabIndicatorColor = --primary` |
| **Histogram overlay** | `article.border` at 60% opacity | Custom `View` + `--surface` bg at α=0.6 |
| **Focus ring** | CSS: `border-radius` + `scale`, `--speed2` snap-in, `--speed3` decay | `ObjectAnimator` at 200 ms/300 ms |
| **Settings FAB** | `button.circle.fill.extend` expandable | `ExtendedFloatingActionButton`, `--tertiary` color |
| **Flash/timer badge** | `.badge.circle` on icon button | `BadgeDrawable` on icon |
| **ISO/shutter readout** | Chip with `--inverse-surface` bg | `MaterialTextView` in `ChipGroup` |

#### WebView Settings Bottom Sheet (Vector A — primary deliverable)

When the settings FAB is pressed, a `BottomSheetDialogFragment` containing a `WebView` slides up, loading `file:///android_asset/settings_panel/index.html`.

**`SettingsBridge.kt`:**
```kotlin
class SettingsBridge(private val onParam: (String, Float) -> Unit) {
    @JavascriptInterface
    fun setParam(key: String, value: Float) = onParam(key, value)

    @JavascriptInterface
    fun getPresets(): String = Gson().toJson(FilmPresetRepository.allPresets())
}
```

**`SettingsPanelFragment.kt`:**
```kotlin
class SettingsPanelFragment : BottomSheetDialogFragment() {
    private lateinit var webView: WebView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        WebView(requireContext()).also {
            webView = it
            it.settings.apply {
                javaScriptEnabled = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = false
                domStorageEnabled = true
            }
            it.addJavascriptInterface(SettingsBridge { k, v -> engine.set(k, v) }, "Android")
            it.loadUrl("file:///android_asset/settings_panel/index.html")
        }

    fun pushHistogram(bins: FloatArray) {
        val json = bins.joinToString(",", "[", "]")
        webView.post { webView.evaluateJavascript("updateHistogram('$json')", null) }
    }
}
```

**`assets/settings_panel/index.html` (abridged):**
```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <link href="beer.min.css" rel="stylesheet" />
  <style>
    body { padding-bottom: env(safe-area-inset-bottom); }
    .slider { margin: 0.25rem 0 1rem; }
    details summary { padding: 0.75rem 1rem; cursor: pointer; }
  </style>
</head>
<body class="dark">

<!-- Film preset tabs -->
<div class="tabs" id="preset-tabs"></div>

<!-- Film style chips -->
<div class="row" id="style-chips" style="gap:0.5rem; padding:0.75rem 1rem; flex-wrap:wrap;">
  <button class="chip fill active" data-style="ACCURATE">Accurate</button>
  <button class="chip"            data-style="ARTISTIC">Artistic</button>
  <button class="chip"            data-style="VINTAGE">Vintage</button>
  <button class="chip"            data-style="HIGH_CONTRAST">High Contrast</button>
  <button class="chip"            data-style="PASTEL">Pastel</button>
</div>

<main style="padding:0 1rem;">
  <!-- Simulation mode -->
  <div class="field label border" style="margin:1rem 0;">
    <select id="mode-select">
      <option value="FAST">Fast (preview quality)</option>
      <option value="ACCURATE">Accurate (full spectral)</option>
    </select>
    <label>Simulation Mode</label>
    <i>tune</i>
  </div>

  <!-- Grain intensity -->
  <label class="medium-text">Grain Intensity</label>
  <div class="slider medium">
    <input type="range" id="sl-grain" min="0" max="100" step="1" value="25" />
    <span></span>
    <div class="tooltip bottom">25</div>
  </div>

  <!-- Vignette -->
  <label class="medium-text">Vignette</label>
  <div class="slider medium">
    <input type="range" id="sl-vignette" min="0" max="100" step="1" value="20" />
    <span></span>
    <div class="tooltip bottom">20</div>
  </div>

  <!-- Halation -->
  <label class="medium-text">Halation</label>
  <div class="slider medium">
    <input type="range" id="sl-halation" min="0" max="100" step="1" value="10" />
    <span></span>
    <div class="tooltip bottom">10</div>
  </div>

  <!-- Color Grade accordion -->
  <details>
    <summary><span class="medium-text bold">Color Grade</span></summary>
    <label class="medium-text">Shadow Tint (Split Tone Hue)</label>
    <div class="slider medium">
      <input type="range" id="sl-shadow-tint" min="-1" max="1" step="0.01" value="0" />
      <span></span>
    </div>
    <label class="medium-text">Saturation</label>
    <div class="slider medium">
      <input type="range" id="sl-saturation" min="0" max="2" step="0.01" value="1" />
      <span></span>
    </div>
  </details>

  <!-- Advanced accordion -->
  <details>
    <summary><span class="medium-text bold">Advanced</span></summary>
    <label class="medium-text">DOF Amount</label>
    <div class="slider medium">
      <input type="range" id="sl-dof" min="0" max="1" step="0.01" value="0" />
      <span></span>
    </div>
    <label class="medium-text">JPEG Quality</label>
    <div class="slider medium">
      <input type="range" id="sl-quality" min="60" max="100" step="1" value="95" />
      <span></span>
      <div class="tooltip bottom">95</div>
    </div>
  </details>
</main>

<script type="module" src="beer.min.js"></script>
<script>
window.addEventListener('load', () => {
  // Warm amber seed — Kodachrome warmth
  beercss.theme('#c0876a');

  // Populate preset tabs from Kotlin
  const presets = JSON.parse(Android.getPresets());
  const tabs = document.getElementById('preset-tabs');
  presets.forEach((p, i) => {
    const a = document.createElement('a');
    a.textContent = p.name || p;
    if (i === 0) a.classList.add('active');
    a.onclick = () => {
      tabs.querySelectorAll('a').forEach(t => t.classList.remove('active'));
      a.classList.add('active');
      Android.setParam('preset_index', i);
    };
    tabs.appendChild(a);
  });

  // Generic slider wiring
  function wireSlider(id, key) {
    const el = document.getElementById(id);
    const tip = el.closest('.slider')?.querySelector('.tooltip');
    el.oninput = () => {
      if (tip) tip.textContent = el.value;
      Android.setParam(key, parseFloat(el.value));
    };
  }
  wireSlider('sl-grain',       'grain_intensity');
  wireSlider('sl-vignette',    'vignette_amount');
  wireSlider('sl-halation',    'halation');
  wireSlider('sl-shadow-tint', 'split_toning_hue');
  wireSlider('sl-saturation',  'saturation');
  wireSlider('sl-dof',         'dof_amount');
  wireSlider('sl-quality',     'jpeg_quality');

  // Style chips
  document.getElementById('style-chips').onclick = e => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    document.querySelectorAll('#style-chips .chip').forEach(c => c.classList.remove('active'));
    chip.classList.add('active');
    Android.setParam('film_style', chip.dataset.style);
  };

  // Mode
  document.getElementById('mode-select').onchange = e =>
    Android.setParam('sim_mode', e.target.value);
});

// Called by Kotlin with live histogram (256-bin float array JSON)
function updateHistogram(json) { /* Phase 4 */ }
</script>
</body>
</html>
```

---

### 2.4 SettingsFragment (Full-Screen) → Vector A extended

Extend `index.html` with a top app bar and export controls:

```html
<header class="fixed">
  <nav>
    <button class="circle" onclick="Android.navigateBack()"><i>arrow_back</i></button>
    <h5 class="max">Film Settings</h5>
    <button class="circle" onclick="document.getElementById('dlg-reset').setAttribute('open','')">
      <i>restart_alt</i>
    </button>
  </nav>
</header>

<!-- Export format -->
<div class="row" style="gap:0.5rem; padding:1rem; flex-wrap:wrap;">
  <label class="medium-text" style="width:100%;">Export Format</label>
  <button class="chip fill active" data-fmt="jpeg">JPEG</button>
  <button class="chip"            data-fmt="dng">DNG</button>
  <button class="chip"            data-fmt="both">Both</button>
</div>

<!-- Toggles -->
<div class="list medium-space" style="padding:0 1rem;">
  <li>
    <div class="max"><p>Save Original DNG</p></div>
    <label class="switch">
      <input type="checkbox" id="sw-raw" checked
             onchange="Android.setParam('save_raw', this.checked ? 1 : 0)" />
      <span></span>
    </label>
  </li>
  <li>
    <div class="max"><p>Convert to JPEG</p></div>
    <label class="switch">
      <input type="checkbox" id="sw-convert" checked
             onchange="Android.setParam('convert_to_jpeg', this.checked ? 1 : 0)" />
      <span></span>
    </label>
  </li>
</div>

<!-- Reset confirmation dialog -->
<dialog id="dlg-reset">
  <h5>Reset All Settings?</h5>
  <p>Film preset, grade, and grain return to defaults.</p>
  <nav class="right-align">
    <button onclick="document.getElementById('dlg-reset').removeAttribute('open')">Cancel</button>
    <button class="fill"
            onclick="Android.setParam('reset_all', 1); document.getElementById('dlg-reset').removeAttribute('open')">
      Reset
    </button>
  </nav>
</dialog>
```

---

### 2.5 ImageViewerFragment → Vector B + Vector C

#### Native Fragment (Vector C)

| Element | BeerCSS anatomy | Android |
|---|---|---|
| Action bar (bottom) | `nav.bottom.fixed` with icon `button.circle` | `BottomAppBar` |
| Share FAB | `button.circle.fill` (`--tertiary` color) | `FloatingActionButton` |
| Delete | `button.circle.border` with error color | Icon button |
| EXIF chip row | `.chip.fill.small` per field | `ChipGroup` + `Chip` |
| Save progress | `progress.circle.indeterminate.max` (full-screen overlay) | `CircularProgressIndicator` |
| Film name badge | `.snackbar` persistent at top | Custom `TextView`, `--inverse-surface` bg |

#### Companion Photo Viewer Web App (Vector B)

`assets/viewer/index.html` is copied alongside each JPEG export and opened via browser intent:

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <link href="beer.min.css" rel="stylesheet" />
  <style>
    body { margin:0; background:#000; }
    #wrap { width:100vw; height:100svh; display:flex; align-items:center; justify-content:center; position:relative; }
    #photo { max-width:100%; max-height:100%; object-fit:contain; }
    #chrome { position:absolute; bottom:0; left:0; right:0; padding:1rem;
              background:linear-gradient(transparent,rgba(0,0,0,.85)); }
  </style>
</head>
<body class="dark">
<div id="wrap">
  <img id="photo" src="photo.jpg" alt="Processed photo" />
  <div id="chrome">
    <div class="row" style="gap:.5rem; flex-wrap:wrap; margin-bottom:.5rem;">
      <span class="chip fill small" id="c-film"></span>
      <span class="chip fill small" id="c-iso"></span>
      <span class="chip fill small" id="c-shutter"></span>
    </div>
    <p class="medium-text" id="c-time" style="color:var(--on-surface-variant); margin:0;"></p>
  </div>
</div>

<button class="circle fill absolute bottom right" style="margin:1.5rem;"
        onclick="document.getElementById('dlg-exif').setAttribute('open','')"><i>info</i></button>

<dialog class="bottom medium" id="dlg-exif">
  <h6 style="padding:1rem 1rem 0;">Film Metadata</h6>
  <ul class="list medium-space" id="exif-list"></ul>
</dialog>

<script type="module" src="beer.min.js"></script>
<script>
fetch('photo.meta.json').then(r=>r.json()).then(m => {
  beercss.theme(m.filmColor);
  document.getElementById('c-film').textContent    = m.filmPreset;
  document.getElementById('c-iso').textContent     = 'ISO ' + m.iso;
  document.getElementById('c-shutter').textContent = m.shutter;
  document.getElementById('c-time').textContent    = new Date(m.capturedAt).toLocaleString();
  const ul = document.getElementById('exif-list');
  const labels = { filmPreset:'Preset', iso:'ISO', shutter:'Shutter',
                   grainIntensity:'Grain', simMode:'Mode', colorProfile:'Profile' };
  Object.entries(labels).forEach(([k,label]) => {
    const li = document.createElement('li');
    li.innerHTML = `<div class="max"><p>${label}</p></div><span>${m[k]}</span>`;
    ul.appendChild(li);
  });
});
</script>
</body>
</html>
```

**`PhotoMeta.kt` (new):**
```kotlin
@Serializable
data class PhotoMeta(
    val filmPreset: String,
    val filmColor: String,    // hex — seeds beercss.theme()
    val iso: Int,
    val shutter: String,
    val capturedAt: Long,
    val grainIntensity: Int,
    val halation: Int,
    val simMode: String,
    val colorProfile: String
)
```

**Per-preset `filmColor` hex:**

| Film preset | `filmColor` |
|---|---|
| Kodachrome | `#c0876a` (warm amber) |
| Portra | `#b8956b` (warm neutral) |
| Velvia | `#3d8b6e` (saturated teal) |
| Tri-X | `#7a7a8a` (neutral grey-blue) |
| HP5 | `#8a8a96` (cool grey) |
| Ektar | `#c05050` (vivid red-orange) |

---

## 3. Design Token Alignment (Vector C)

Map BeerCSS dark-mode tokens to Android `themes.xml`:

| BeerCSS token | Android attribute | Dark value |
|---|---|---|
| `--primary` | `colorPrimary` | `#cfbcff` |
| `--on-primary` | `colorOnPrimary` | `#381e72` |
| `--primary-container` | `colorPrimaryContainer` | `#4f378a` |
| `--on-primary-container` | `colorOnPrimaryContainer` | `#e9ddff` |
| `--secondary` | `colorSecondary` | `#cbc2db` |
| `--secondary-container` | `colorSecondaryContainer` | `#4a4458` |
| `--tertiary` | `colorTertiary` | `#efb8c8` |
| `--tertiary-container` | `colorTertiaryContainer` | `#633b48` |
| `--error` | `colorError` | `#ffb4ab` |
| `--background` | `colorBackground` | `#1c1b1e` |
| `--surface` | `colorSurface` | `#141316` |
| `--on-surface` | `colorOnSurface` | `#e6e1e6` |
| `--on-surface-variant` | `colorOnSurfaceVariant` | `#cac4cf` |
| `--speed2` (0.2 s) | `motionDurationShort2` | 200 ms |
| `--speed3` (0.3 s) | `motionDurationMedium1` | 300 ms |
| `1rem` radius | `shapeAppearanceMediumComponent` | `16dp` |
| `0.5rem` radius | `shapeAppearanceSmallComponent` | `8dp` |
| `--elevate1` | elevation on cards | 1 dp |
| `--elevate3` | elevation on FABs | 6 dp |

Enable Material You dynamic colors as the runtime equivalent of `beercss.theme()`:
```kotlin
// Application.onCreate()
DynamicColors.applyToActivitiesIfAvailable(this)
```

---

## 4. File Structure

```
app/src/main/
  assets/
    settings_panel/
      index.html          ← Phase 1 + 2
      beer.min.css        ← pinned beercss@4.0.21, committed to repo
      beer.min.js
    viewer/
      index.html          ← Phase 3 companion viewer template
      beer.min.css        ← same pinned copy (symlink or duplicate)
      beer.min.js
  kotlin/ui/
    settings/
      SettingsBridge.kt       ← new Phase 1
      SettingsPanelFragment.kt ← new Phase 1
  kotlin/data/
    PhotoMeta.kt              ← new Phase 3
  res/values/
    colors.xml                ← updated Phase 2 to BeerCSS dark palette
    themes.xml
```

APK size impact: **~35 KB** added (BeerCSS CSS + JS, brotli-compressed).

---

## 5. Phased Roadmap

### Phase 1 — WebView Settings Panel (2 weeks)

| Task | Acceptance criteria |
|---|---|
| Commit `beer.min.css` + `beer.min.js` to `assets/settings_panel/` | Files present; no network permission needed |
| Implement `SettingsBridge` with `setParam()` + `getPresets()` | `setParam("grain_intensity", 50f)` reaches `FilmrConfig` within 50 ms |
| Implement `SettingsPanelFragment` as `BottomSheetDialogFragment` with `WebView` | Panel slides up on FAB press; back gesture closes it |
| Write `index.html` with preset tabs, style chips, mode select, grain/vignette/halation/DOF sliders, accordions | All sliders fire `Android.setParam()` on touch release |
| Dark amber theme seed `#c0876a` | Primary color is warm amber; no light-mode flash on open |
| Pre-warm WebView in `CameraFragment.onViewCreated()` | Panel opens in < 150 ms |
| Works on API 26+ (Android 8.0) | Tested on emulator API 26, 34 |

### Phase 2 — Full Settings Screen + Native Token Alignment (2 weeks)

| Task | Acceptance criteria |
|---|---|
| Extend `index.html` to full-screen with `<header>` app bar + back button | Replaces XML `SettingsFragment`; `Android.navigateBack()` pops the back stack |
| Add export format chips (JPEG/DNG/Both) and switch toggles (Save RAW, Convert to JPEG) | All controls persist via `Android.setParam()`; survive app restart |
| Add reset `<dialog>` with confirm/cancel | Blocks accidental reset |
| Update `res/values/colors.xml` with token table from §3 | All hex values traceable to named BeerCSS token |
| Capture button: 72dp `MaterialButton`, `shapeAppearanceFull`, `--primary` fill | Matches `button.circle.extra.fill` anatomy |
| Preset strip: `TabLayout` with underline indicator at `--primary` | Smooth horizontal scroll; active tab highlighted |
| Card corners app-wide: `16dp` | Consistent with `1rem` BeerCSS standard |

### Phase 3 — Companion Photo Viewer (2 weeks)

| Task | Acceptance criteria |
|---|---|
| Define `PhotoMeta` + write `photo.meta.json` alongside each export | Valid JSON next to every JPEG/DNG |
| Add `filmColor` hex per preset | 6 presets covered (table in §2.5) |
| Write + bundle `assets/viewer/index.html` | Opens in Chrome/Firefox; correct film chips and accent color |
| "View in browser" intent from `ImageViewerFragment` | Share sheet includes the option; works offline |
| EXIF bottom-sheet populated from JSON sidecar | All `PhotoMeta` fields visible in list |

### Phase 4 — Live Histogram in Panel (1 week)

| Task | Acceptance criteria |
|---|---|
| Add `<canvas id="histogram" height="80">` to settings panel | Canvas visible above preset tabs |
| JS 3-channel (R/G/B) histogram renderer (256 bins) | Draws correctly on simulated data |
| Push histogram from `CameraFragment` at 4 fps via `pushHistogram(FloatArray)` | No jank on viewfinder; dispatched on `Dispatchers.Default` |
| Latency ≤ 250 ms | No dropped viewfinder frames |

---

## 6. Security & Performance Notes

| Concern | Mitigation |
|---|---|
| XSS via `evaluateJavascript` | Only pass serialized primitives and pre-validated JSON; never inject raw user strings |
| `allowUniversalAccessFromFileURLs` | Keep `false`; only `file://` → same-asset requests needed |
| WebView memory leak | Call `webView.destroy()` in `onDestroyView()`; do not hold reference after fragment detach |
| Settings panel startup latency | Pre-warm WebView in `CameraFragment.onViewCreated()`; target < 120 ms to first paint |
| Frame rate impact | All `evaluateJavascript` calls on `histogramScope` coroutine (`Dispatchers.Default`); never on camera callback thread |
| BeerCSS version drift | CSS/JS committed to repo at pinned version; update manually with changelog review |
| `JavascriptInterface` surface area | Expose minimum methods; `@JavascriptInterface` annotations only; no reflection or filesystem access exposed |

---

## 7. Why BeerCSS

| Criterion | BeerCSS | Bootstrap / Tailwind / MUI |
|---|---|---|
| Material Design 3 native | First MD3 CSS framework; identical token names | No MD3 support |
| Bundle size (WebView) | 14.4 KB brotli | 30–300 KB |
| Dark mode | `<body class="dark">`, 37 tokens, zero JS | Custom CSS variables required |
| No build step | Single CSS + optional JS | Tailwind requires PostCSS; MUI requires React |
| Android token mapping | 1:1 token-to-attribute (§3 table) | Approximate only |
| Dynamic color | `beercss.theme(hex)` companion package | Not available |
| Semantic HTML | `<button>`, `<dialog>`, `<details>` natively styled | Requires wrapper divs |
| Maintenance | Active (v4.0.21, 2025) | Varies |

The critical advantage: BeerCSS token names (`--primary`, `--surface-container-low`, `--on-primary-container`) are structurally identical to Android Material You `colorScheme` attribute names. A WebView panel and a native XML screen can be kept visually indistinguishable by aligning both to the same hex values.

---

## 8. BeerCSS Class Quick Reference

```
Containers    article  article.border  dialog  dialog.bottom  dialog.max
Navigation    nav  nav.top  nav.bottom  header  .tabs  a.active
Buttons       button  button.fill  button.border  button.circle
              button.circle.fill  button.circle.extra  button.extend
Chips         .chip  .chip.fill  .chip.fill.active  .chip.small
Sliders       .slider  .slider.medium  input[type=range]  .tooltip.bottom
Fields        .field  .field.label  .field.border  .field.prefix  .field.suffix
Selections    .checkbox  .radio  .switch
Feedback      .snackbar  .progress.circle.indeterminate  .badge.circle  .tooltip
Expansion     details  summary  (no custom class needed)
Layout        .grid  .row  .max  .absolute  .fixed  .center  .middle  .front
Typography    .medium-text  .large-text  .bold  .truncate  h5  h6
Responsive    .s1–.s12  .m1–.m12  .l1–.l12  .s  .m  .l  .responsive
Utilities     .wave  .no-wave  .border  .fill  .active  .dark
```

---

*BeerCSS version: 4.0.21 — Material Design 3. Android target: API 26+. Last updated: 2026-05-14.*
