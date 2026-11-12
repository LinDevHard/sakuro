# Shader pipeline fixes implementation plan

> **For agentic workers:** Use superpowers:executing-plans. Implement and verify in the current checkout.

**Goal:** Fix the five shader pipeline defects from the review approved by the user's “Исправляй”.
**Architecture:** Register GL ownership immediately; scope parameters to source-document passes; normalize built-in shader output to the profile target; track source format changes independently of rendered output.
**Tech Stack:** Kotlin, Media3, GLES, existing JVM test harness, JDK 17.
**Spec:** The five findings in this task's preceding review. No additional product decisions are needed.

## Global constraints
- Preserve explicit user-chain sizing and existing profile serialization.
- Keep per-file parameter overrides independent, including RPN conditions and dimensions.
- Do not add dependencies or change the mpv engine.
- Keep changes uncommitted for review.

## Review focus
- Partial allocation and cleanup failures must release all acquired resources.
- Built-in scaling must work at 2160p and after shader passthrough.
- Repeated size notifications must not repeatedly prepare playback.
- Per-file defaults, overrides, DEFINE params and nested merges must stay independent.
- Shader graph final normalization does not eliminate intermediate CNN memory cost.

## Tasks
- [x] Add regression tests for partial resource cleanup, built-in dimensions, source changes and merged parameter scopes; observe failures.
- [x] Add an owned-resource scope and register programs, textures, FBOs and buffers before fallible initialization; release the old presenter on reconfigure.
- [x] Normalize Anime4K and parametric outputs to capped profile target, including when shaders degrade.
- [x] Reset source-size tracking on load and rebuild on changed decoded source height; ignore rendered output dimensions.
- [x] Preserve document-local parameters on merged passes and pass per-file overrides through loading, GLSL generation and RPN planning.
- [x] Run Media3 regressions, full tests and detekt; compile generated collision fixture with glslangValidator; perform independent final review.

## Verification and review
- Initial regressions reproduced the 4K presentation omission and merged GLSL/RPN parameter collisions.
- Media3: 129 JVM tests passed (21 added); core-upscale desktop: 47 passed.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew test detekt :core:core-upscale:desktopTest :composeApp:assembleFossDebug` passed after final changes.
- Generated GLSL for a merged duplicate-parameter fixture compiles and links with glslangValidator.
- Independent review found and resolved custom-chain fallback sizing, Media3 constructor allocation leaks, and shared GL_ARRAY_BUFFER state. The exception-safe compiler now covers both fragment and compute programs.
- Existing Compose/Decompose deprecation and opt-in warnings remain outside this change.
- No physical GPU/device validation was performed. Final output is capped; intermediate CNN textures may still exceed the presentation size.
