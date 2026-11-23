# Privacy Policy

Sakuro is a local-first video player. The current app does not include accounts, analytics SDKs, advertising SDKs, crash reporting SDKs, cloud sync, or telemetry.

## Summary

- Sakuro reads local video metadata to build the on-device library.
- Sakuro stores settings, subtitle preferences and preset preferences locally.
- Playback progress for up to 200 recently watched video URIs (local files and stream links) is stored locally to offer resume playback.
- Debug information is shown locally in the app.
- `sakuro-bench` captures and reports are generated locally by the developer running the tool.
- Sakuro connects to the network only to play a stream link you enter, and only to that link's server.
- Sakuro does not send your media library, playback activity, settings, debug overlay data, benchmark captures, or reports to Sakuro/Rinwave servers.

## Android App Data

Sakuro may access local video files and video metadata through Android MediaStore and the system file picker.

This can include:

- video display name;
- content URI;
- duration;
- width and height;
- file size;
- date added;
- folder/bucket name;
- codec and playback information exposed by the selected playback engine.

This data is used to show the local library, open videos, choose presets, and display playback/debug information. It stays on the device.

## Permissions

The Android app currently requests local media read permissions:

- `READ_MEDIA_VIDEO` on modern Android versions;
- `READ_EXTERNAL_STORAGE` on Android 12 and older.

Library permission is requested only when you choose to connect the media library. You can instead open an individual file with the system picker or another app without granting access to the whole library.

The app also requests `INTERNET` and `ACCESS_NETWORK_STATE` for network streams (see below). Neither is used unless you open a stream link.

`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` keep playback alive in the background. While a video plays, Sakuro publishes a standard media session: the system media notification, lock screen, Bluetooth/headset buttons and companion devices (watch, car) can see the video title and control playback, as with any media app. Background playback can be turned off in Settings → Playback.

## Network Streams

You can paste a direct link to an HLS playlist, a DASH manifest or a video file. Sakuro then makes ordinary HTTP(S) requests to that server (and to any server it redirects to) with browser-like headers: Chrome's reduced user agent (a fixed Android version and no device model) tagged `Sakuro/<version>`, and your device language. It sends no accounts or identifiers of its own. Cookies a stream server sets are kept in memory for the app session and sent back only to that server, as a browser would; they are never written to disk. A User-Agent, Referer or Cookie you type under "Advanced" is sent with that stream's requests only.

Downloaded parts of streams are cached in the app's cache directory (default limit 1 GB, adjustable or off in Settings → Playback, where it can also be cleared). Playlists and manifests are not cached. Android may also clear this directory when storage runs low. Plain `http://` links are allowed because many streams use them; such traffic is not encrypted and can be seen by your network operator.

The stream server sees your IP address and what you request, as with any web request; its own privacy policy applies. Connectivity state is read only to pause reconnect attempts while the device is offline. Stream links are kept on the device in the bounded playback history, like local URIs, and are excluded from diagnostic reports.

## Settings And Presets

Sakuro stores app settings locally through multiplatform settings. Stored values may include:

- selected playback engine;
- default upscale preset;
- debug overlay setting;
- adaptive mode setting;
- gesture settings;
- library sort preferences;
- user presets and pinned preset choices;
- subtitle size, background, position, delay and preferred language;
- optional next-video autoplay (off by default);
- bounded playback history containing local URI or stream link, position and duration.

These settings remain local to the device or desktop sandbox environment.

## Debug Overlay

The debug overlay can show technical playback data such as engine, decoder, codec, resolution, FPS, dropped frames, bitrate, audio information, active preset, detected content class, adaptive state, and device signals.

This information is displayed locally. If you share screenshots or logs publicly, review them first for private file names, folder names, or media details.

## Diagnostic Reports

The player can prepare a technical report for you to preview. It contains the app version, device/OS information, engine, canonical codec labels, resolutions, frame statistics, processing state and an error code. It excludes video titles, file paths, media URIs, raw logs, images and custom preset names.

Nothing is exported until you explicitly copy the preview or choose Share and select a destination in the system chooser. The receiving app handles any further transmission under its own privacy policy.

## sakuro-bench

`tools/sakuro-bench` is a local developer tool. It can use Android `adb screencap`, local images, local video frames, ffmpeg, mpv, and vendored shader files to create benchmark reports.

Generated captures and reports can contain visual content from your videos and metadata about the benchmark scenario. They are local artifacts and are ignored by git by default. Do not share benchmark reports publicly unless you have the right to share the captured media.

## Third Parties

Sakuro uses open-source libraries and platform APIs for playback, UI, settings, media scanning, and benchmarking. The current app build does not use third-party analytics, ads, cloud sync, or crash reporting services.

Future optional services, if added, must be documented here before release.

## Security Reports

For privacy or security issues, follow [SECURITY.md](../.github/SECURITY.md). Please do not publish sensitive reports as public issues before triage.
