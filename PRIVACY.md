# Privacy Policy

Sakuro is a local-first video player. The current app does not include accounts, analytics SDKs, advertising SDKs, crash reporting SDKs, cloud sync, or telemetry.

## Summary

- Sakuro reads local video metadata to build the on-device library.
- Sakuro stores settings and preset preferences locally.
- Debug information is shown locally in the app.
- `sakuro-bench` captures and reports are generated locally by the developer running the tool.
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

The app manifest does not request the `INTERNET` permission in the current open-source build.

## Settings And Presets

Sakuro stores app settings locally through multiplatform settings. Stored values may include:

- selected playback engine;
- default upscale preset;
- debug overlay setting;
- adaptive mode setting;
- gesture settings;
- library sort preferences;
- user presets and pinned preset choices.

These settings remain local to the device or desktop sandbox environment.

## Debug Overlay

The debug overlay can show technical playback data such as engine, decoder, codec, resolution, FPS, dropped frames, bitrate, audio information, active preset, detected content class, adaptive state, and device signals.

This information is displayed locally. If you share screenshots or logs publicly, review them first for private file names, folder names, or media details.

## sakuro-bench

`tools/sakuro-bench` is a local developer tool. It can use Android `adb screencap`, local images, local video frames, ffmpeg, mpv, and vendored shader files to create benchmark reports.

Generated captures and reports can contain visual content from your videos and metadata about the benchmark scenario. They are local artifacts and are ignored by git by default. Do not share benchmark reports publicly unless you have the right to share the captured media.

## Third Parties

Sakuro uses open-source libraries and platform APIs for playback, UI, settings, media scanning, and benchmarking. The current app build does not use third-party analytics, ads, cloud sync, or crash reporting services.

Future optional services, if added, must be documented here before release.

## Security Reports

For privacy or security issues, follow [SECURITY.md](SECURITY.md). Please do not publish sensitive reports as public issues before triage.
