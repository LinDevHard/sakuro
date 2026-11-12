# F-Droid playback gaps implementation plan

> **For agentic workers:** Follow TDD; parallel workers own disjoint files, root integrates and verifies.

**Goal:** Close the eight playback/usability gaps accepted by the user's “закрой пробелы”.
**Architecture:** Persist local playback history and subtitle preferences; use Android intents and user-initiated library permission; keep playback navigation and error recovery in PlayerComponent; export an allowlisted diagnostic report only after preview and an explicit share action.
**Tech Stack:** Existing Kotlin Multiplatform, Compose, Media3, Settings and Decompose. No new network permissions or third-party SDKs.
**Spec:** The preceding feature assessment in this task, excluding the separately suggested A/B comparison feature.

## Constraints
- Existing library/picker and shader behavior remain available.
- Resume prompt must prevent playback until user chooses continue or restart.
- Queue stays inside one identified folder, naturally sorted; autoplay defaults off.
- Retry without effects is session-local and cannot be undone by the adaptive loop.
- Diagnostic reports exclude file paths, titles, raw logs and images; preview before system sharing.
- No release tags, commits or uploads without a separate request.

## Tasks
- [x] Persistent bounded resume history and natural folder queue; tests for restoration, completion, corrupt data and folder boundaries.
- [x] Cold/warm ACTION_VIEW/ACTION_SEND routing and optional library access; rejection and URI tests.
- [x] Persisted subtitle size/background/position/delay/language and actual renderer application.
- [x] Navigation integration: resume choices, library progress, next/previous and optional autoplay.
- [x] Session-local no-effects recovery, audio focus/noisy handling, private diagnostic preview/export.
- [x] Full tests, detekt, FOSS APK and packaging audit, independent review.

## Review focus
Repeated intent delivery; lost URI grants; last-position writes during resume prompt; end-of-file autoplay loops; same-label folders; shader retry reactivation; diagnostic path leakage; subtitle timing after seek.
