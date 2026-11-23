# Changelog

All notable changes to Sakuro are documented here.

This project follows a simple pre-1.0 changelog style: changes are grouped by version, and breaking behavior can still happen while the app is in early alpha.

## Unreleased

- Live streams: Wi‑Fi stays awake for screen-off background streams; live
  segments skip the cache; the upscale chain is not rebuilt on resolution
  changes (ad breaks, discontinuities) and automatic chain changes are spaced
  60 s apart, while a rebuild keeps the DVR position; stuck playlists
  reconnect at the live edge; seek buttons, gestures and manual speed are
  hidden where they cannot work, DVR seeks are clamped to the window and a
  paused no-DVR stream resumes at the edge; event streams that end show a
  "broadcast has ended" screen; DVR positions show wall-clock time; the
  stats overlay shows live latency, target and catch-up speed; the media
  notification shows no progress bar for live without DVR.

- Added playback from a direct link (Library → link icon): HLS (`.m3u8`), DASH
  (`.mpd`) and progressive http(s) files such as MP4/MKV/WebM, with the same
  upscale presets as local video. Plain `http://` links are allowed.
- Network failures retry automatically with backoff and wait for connectivity
  to return; the player shows reconnect progress. Remaining failures are
  explained (connection, server, dead link, TLS, unsupported format), and
  DRM-protected streams are reported as unsupported instead of a black screen.
- Background playback through a Media3 `MediaSessionService`: media
  notification, lock-screen and headset controls; video decoding pauses while
  the app is hidden and resumes on return. Closing picture-in-picture stops
  playback. Can be turned off in Settings → Playback.
- Stream cache (LRU, 1 GB by default, adjustable/off/clearable in settings);
  live playlists and manifests always come from the network. Follows Reddit's
  ExoPlayer cache notes: dedicated internal directory, a single SimpleCache
  opened off the main thread, and cache keys that ignore URL signatures and
  expiry tokens so re-signed CDN links still hit the cache.
- Playback tuning from the same Reddit write-up: playback starts after 1 s of
  buffer (2 s after a stall) instead of 2.5 s/5 s, a busy hardware decoder
  falls back to another decoder instead of failing, and the last measured
  bandwidth per network type seeds the next session's first quality choice.
- Background playback keeps the upscale chain idle (no video decoded) and
  defers any chain change until the app is visible again; the adaptive
  controller is frozen while hidden and ignores the decoder warm-up drops for
  3 s after returning, so returning does not trigger a false downgrade.
- Quality picker for adaptive streams (Auto, 4K, 1080p, 720p …) and a LIVE
  badge with "behind live" time and a jump-to-live action.
- Browser-like request headers, a session cookie jar and optional per-link
  User-Agent / Referer / Cookie.
- Playback failures are shown as a full-screen state with an illustration and
  actions (retry, copy link, back), with dedicated copy for 404/403/401.
- The libmpv reference engine stays in compatibility mode: it plays links but
  has no cache, quality picker or media session.
- The app now requests `INTERNET`, `ACCESS_NETWORK_STATE` and the
  media-playback foreground service permissions (see docs/privacy.md).

## 0.1.4

- Rebuilt the Anime SD and Anime HD presets on Anime4K Upscale CNN x2 S and
  dropped their Sharpen pass (Restore CNN). On-device benchmarks scored plain
  Upscale S highest; the old Restore + Upscale chains mostly added
  oversharpening. Anime chains now always use the small Upscale CNN; the
  medium model stays available in custom shader chains.
- Restructured the project into feature modules (library, player, settings,
  benchmark) over a flat core layer, with entry points in `androidApp` and
  `desktopApp`. The Android module moved from `composeApp` to `androidApp`, so
  F-Droid builds now use `subdir: androidApp`. The module graph is enforced by
  `checkModuleGraph`.
- CI now runs Android Lint, strict detekt and a translation check.
- Added missing Japanese translations for the playback session and subtitle
  settings.

## 0.1.3

- Removed AGP dependency metadata from release signing blocks for F-Droid
  policy compatibility.
- Pinned Kotlin tooling metadata to JVM 17 so release APKs remain reproducible
  when F-Droid builds them on JDK 21.
- Hardened the signing workflow with protected release refs, immutable Action
  SHAs, certificate verification and complete stable-asset checksums.

## 0.1.2

- Added upstream-signed reproducible GitHub/F-Droid releases with a dedicated
  Sakuro signing key.
- Disabled AGP's nondeterministic compiled release baseline profiles so clean
  R8 builds produce byte-identical unsigned APKs.

## 0.1.1

- Added a gated release pipeline for unsigned F-Droid APKs and signed Play APK/AAB artifacts, including tag/version validation, dependency-license auditing, R8/resource shrinking, mapping retention, and SHA-256 manifests.

### Added

- Local resume history with library progress, natural-order folder navigation and optional autoplay.
- Opening videos from other Android apps, with library access requested only on demand.
- Subtitle appearance, timing and preferred-language settings for Media3.
- Session-only retry without processing and previewable privacy-filtered diagnostic reports with explicit sharing.
- Media3 audio focus and pause on disconnected headphones.

- Open-source project documentation: license, contributing guide, security policy, code of conduct, issue templates, pull request template, privacy policy, funding metadata, and code ownership.
- README badges and clearer public project status.
- Fastlane quality/release lanes, localized F-Droid store metadata, and CI packaging checks.

### Changed

- New app icon: eclipse ring, play button and sakura blossom, with a matching monochrome variant for Android 13+ themed icons. All icon assets are generated from one script (`tools/branding/generate_app_icon.py`).
- Media3 debug statistics now report the configured shader graph's actual output resolution and retained passes, including pending and passthrough degradation states.
- Adaptive quality now degrades explicit shader chains safely and keeps a localized player status visible while processing is reduced, pending, partially applied, or unavailable; transition logs include the requested/effective chains and runtime reason.
- Public README now reflects the current Android-first Media3/mpv architecture and links to benchmark methodology.
- Internal worklog/research notes are excluded from the public repository surface.
- The `foss` flavor excludes the prebuilt libmpv AAR and native mpv/FFmpeg libraries; the reference engine remains in `full` only.

## 0.1.0

Initial early-alpha Android-first build.

### Added

- Kotlin Multiplatform and Compose Multiplatform project structure.
- Android app target and Desktop/JVM UI sandbox.
- Local video library backed by Android MediaStore.
- Manual video opening through the system picker.
- Shared `PlayerEngine` abstraction and engine registry.
- Media3/ExoPlayer playback engine with GLSL ES video effects.
- libmpv playback engine with vendored Anime4K shader support.
- Fake player engine for desktop and tests.
- Built-in upscale presets and preset import/export primitives.
- Media3 Anime4K shader parser, graph planner, and multi-pass render runtime.
- Debug overlay for playback, engine, codec, resolution, FPS, dropped frames, bitrate, audio, detection, adaptive state, and active preset information.
- Filename and frame-sample content detection.
- Adaptive controller for thermal, battery, power-save, and dropped-frame conditions.
- Gesture controls for playback, seek, speed, brightness, volume, and scale mode.
- Settings backed by multiplatform settings.
- `foss` and `full` Android flavors.
- `tools/sakuro-bench` for frame-based upscale comparison and benchmark reports.
- Inter font and Sakuro branding assets.
- Unit tests for core logic, media library behavior, settings, detection, adaptive control, gestures, mpv mapping, and Media3 Anime4K planning/parsing.
