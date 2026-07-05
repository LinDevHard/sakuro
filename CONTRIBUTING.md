# Contributing To Sakuro

Thanks for taking the time to improve Sakuro. The project is still early, so the most useful contributions are focused, reproducible, and easy to review.

## Good First Contributions

- Documentation corrections.
- Build or setup fixes.
- Small UI polish with screenshots.
- Reproducible playback bugs with device details.
- Test-backed fixes in `core/*`.
- `sakuro-bench` methodology, report, or metric improvements.

Large engine changes, dependency swaps, architecture changes, and new product surfaces should start as an issue or discussion before a pull request.

## Development Setup

Requirements:

- JDK 17 or newer.
- Android SDK with `local.properties` pointing to `sdk.dir`.
- Python 3, ffmpeg with libvmaf, and mpv only when working on `tools/sakuro-bench`.

Useful commands:

```bash
./gradlew :composeApp:assembleFossDebug
./gradlew :composeApp:assembleFossRelease
./gradlew test
./gradlew detekt
```

Desktop UI sandbox:

```bash
./gradlew :composeApp:desktopRun
```

Benchmark tooling:

```bash
cd tools/sakuro-bench
./sakuro-bench --version
```

## Pull Request Expectations

- Keep the scope narrow.
- Describe the user-visible change and the risk.
- Include tests when changing core logic, parsing, presets, detection, settings, or benchmark metrics.
- Include screenshots or short screen recordings for UI changes.
- Include device, Android version, engine, codec, resolution, and preset details for playback changes.
- Update docs when behavior, commands, supported modes, or public workflows change.
- Do not include generated artifacts, local captures, APKs, reports, `.venv`, `.gradle`, or IDE files.

## Code Style

- Follow the existing Kotlin and Compose style.
- Prefer small functions and explicit state over cleverness.
- Keep platform-specific behavior in the correct source set.
- Keep `core/*` free of UI dependencies.
- Use `PlayerEngine` and shared abstractions instead of reaching into concrete engines from UI.
- For shader and benchmark work, document assumptions and limitations.

## Testing Guidance

Run the smallest useful check first, then broaden when the change touches shared behavior.

- Pure Kotlin logic: relevant module test plus `./gradlew test` before submitting.
- Android engine or app wiring: at least `./gradlew :composeApp:assembleFossDebug`.
- Release-sensitive changes: `./gradlew :composeApp:assembleFossRelease`.
- Static/style cleanup: `./gradlew detekt`.
- Upscale quality changes: include `sakuro-bench` output or explain why it is not applicable.

## Licensing

By contributing, you agree that your contribution is provided under the repository license. Do not submit code, assets, shaders, models, or generated files unless you have the right to license them for inclusion in this project.

Third-party assets must include their source, license, and any required attribution.

## Conduct

All participation is covered by the [Code of Conduct](CODE_OF_CONDUCT.md).
