# Sakuro Design System

> Project: **Sakuro** (`com.rinwave.sakuro`)
> Date: 2026-07-02
> Status: design direction and decisions.
> Related: [ARCHITECTURE.md](ARCHITECTURE.md).

## 1. Direction

Sakuro should feel like a premium media player: dark plum twilight, sakura accents, chrome-pink highlights, careful typography, generous spacing, soft shadows/blur, and smooth transitions. The reference is a modern premium player or streaming app, not a hobbyist fork.

Principles:

- **Content over chrome:** UI does not compete with video and fades back during playback.
- **One product runtime:** users should experience Sakuro as a polished Media3 player; libmpv comparison tooling stays out of the normal UI.
- **Tactility:** gestures, micro-animations, and haptics should make actions feel responsive.

## 2. Icons

- Use **Lucide** as the single icon family.
- Compose Multiplatform uses **Compose Icons** (`br.com.devsrsouza.compose.icons:lucide`).
- Keep one outline style, consistent stroke weight, and context-appropriate sizes such as 24 dp in bars and 20 dp inline. Do not mix with Material Icons.

## 3. Typography

- Bundle variable or static app fonts instead of relying on system fonts.
- Primary UI/text font: Inter, because it is neutral and highly readable.
- Optional display accent: Geist or another free grotesk for titles and timeline numbers.
- Use a complete type scale and tabular figures for timecodes.
- Keep font licenses in the repository; OFL/free licensing matters for F-Droid.

## 4. Color And Theme

- Base: dark cinematic plum/twilight palette from [BRAND.md](BRAND.md).
- Material 3 is the token foundation, extended with brand colors:
  - `background #120A17`, `surface #1E1226`, `surfaceElevated #2A1A34`, `twilight #4A2A5A`
  - `accentSakura #EC8FC0`, `accentLavender #A57FD6`, `glowMagenta #C94F9C`
  - text `#F3E9F2`, muted text `#B9A7C4`
- Accents are used sparingly for active preset, upscale-on state, and focus.
- Glow is restrained and should not read as neon.
- Video controls use translucent overlays and scrims with WCAG-minded contrast over arbitrary frames.

## 5. Key Screens

- **Library:** grid/list of local files, thumbnails, metadata, quick search.
- **Player:** minimal chrome, gestures, upscale indicator, track/subtitle selection.
- **Settings:** default upscale preset, content auto-detect, gestures, theme, auto-hide behavior, and optional debug controls.
- **Preset manager:** built-in and user presets, create/edit, import/export.
- **Upscale overlay:** quick preset and status access.
- **Debug overlay:** unobtrusive monospace "stats for nerds".

UI iteration happens on the Desktop target with `FakePlayerEngine`, Compose Multiplatform, and Compose Hot Reload.

## 6. Motion

- Transitions are smooth, short, and stable.
- Controls fade out automatically during playback.
- Paused state can add a subtle dim.
- Switching upscale should include a small feedback animation so the feature feels real.

## 7. Adaptivity

The app must work across phones, tablets, foldables, resizable/multi-window, split-screen, and desktop windows. Avoid fixed sizes and portrait-only assumptions.

- Use `WindowSizeClass` branches, not "phone/tablet" checks or hardcoded dp thresholds.
- Use `calculateWindowSizeClass()` from Material3 Adaptive for shared Android/desktop/iOS behavior.
- Navigation can move from bottom bar to rail to wider drawer/rail as size grows.
- Library/player can use `ListDetailPaneScaffold` on larger screens and separate Decompose screens on compact screens.
- Library grids use adaptive columns, not fixed counts.
- Video surfaces fit the window and support portrait, landscape, and fullscreen.
- Controls scale with room: spacious on tablets/expanded layouts, compact on phones, with bottom sheets when needed.
- Respect safe areas, cutouts, rounded corners, and system bars through `WindowInsets`.
- Foldable and resizable changes must preserve state.
- The lower supported width should be around 320-360 dp; content scrolls, long text wraps, and critical actions remain reachable.
- Check layouts under larger system font sizes and high density.

## 8. Assets And Licenses

- Fonts must be free and bundled with the repository.
- Lucide icons are ISC licensed and compatible.
- The `foss` flavor must not include proprietary assets or fonts.
