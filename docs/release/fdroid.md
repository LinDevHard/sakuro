# F-Droid release guide

The `foss` flavor is the only distribution intended for F-Droid. It uses the
Media3 product engine and excludes the optional `engine-mpv` module, its
prebuilt native AAR and the mpv/FFmpeg shared libraries.

## Local verification

Install Ruby 3.3 and Bundler, then run:

```bash
bundle install
bundle exec fastlane android ci
```

The pipeline runs unit tests, detekt and Android Lint, builds the unsigned F-Droid release
APK, checks the resolved runtime graph for common proprietary SDKs and libmpv,
inspects the APK for mpv/FFmpeg native libraries and verifies the application ID.
The APK requests Internet access only to play stream links the user enters
(see [privacy](../privacy.md#network-streams)).

The build artifact is:

```text
androidApp/build/outputs/apk/foss/release/androidApp-foss-release-unsigned.apk
```

The Android application module moved from `composeApp` to `androidApp`. The
fdroiddata recipe must switch `subdir: composeApp` to `subdir: androidApp`
(and any output path from `composeApp-foss-release-unsigned.apk` to
`androidApp-foss-release-unsigned.apk`) in the same release that ships this
layout.

The shared release and signing workflow is documented in
[`releasing.md`](releasing.md). Signing is forcibly disabled while producing
the comparison APK. GitHub Actions separately signs the matching upstream APK
with Sakuro's dedicated app-signing key.

## Release contract

1. Update `sakuro.versionName` and monotonically increase
   `sakuro.versionCode` in `gradle.properties`.
2. Add localized release notes named `<versionCode>.txt` below
   `fastlane/metadata/android/<locale>/changelogs/`.
3. Merge the release commit and tag that exact commit as `v<versionName>`.
4. Run `bundle exec fastlane android fdroid_release` from the clean tagged
   checkout.
5. Submit/update `com.rinwave.sakuro.yml` in the separate `fdroiddata`
   repository, selecting the `foss` Gradle flavor.

Suggested fdroiddata build block for version 0.2.0:

```yaml
Categories:
  - Local Media Player
  - Online Media Player
License: GPL-3.0-only
AuthorName: Rinwave
SourceCode: https://github.com/LinDevHard/sakuro
IssueTracker: https://github.com/LinDevHard/sakuro/issues

RepoType: git
Repo: https://github.com/LinDevHard/sakuro.git

Builds:
  - versionName: 0.2.0
    versionCode: 6
    commit: <full release commit SHA>
    subdir: androidApp
    submodules: true
    sudo:
      - apt-get update
      - apt-get install -y autoconf automake libtool autopoint gettext gperf meson nasm ninja-build pkg-config
    gradle:
      - foss
    ndk: 28.2.13676358

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
UpdateCheckData: gradle.properties|sakuro.versionCode=(\d+)|.|sakuro.versionName=(.+)
CurrentVersion: 0.2.0
CurrentVersionCode: 6
```

Categories follow fdroiddata's `config/categories.yml`: Sakuro plays files on
the device and stream links the user enters.

Use the immutable commit SHA tagged as `v0.2.0` after the release commit is
ready.

### Native code (libass)

From 0.2.0 the APK contains native libraries (libass) built from
source: `third_party/libass-android` is a git submodule (pinned tag) whose own
submodules pin libass, FreeType, HarfBuzz, FriBidi, Fontconfig, Expat and
libunibreak. The build block then needs:

```yaml
    submodules: true
    sudo:
      - apt-get update
      - apt-get install -y autoconf automake libtool autopoint gettext gperf meson nasm ninja-build pkg-config
    ndk: 28.2.13676358
```

`tools/native/prepare-libass.sh` (run automatically by Gradle before CMake)
generates the autotools `configure` scripts and pins Fontconfig's baked-in
config paths (by default they are the build directory). Release native code is
compiled with `-ffile-prefix-map` and `-g0` and linked without a GNU build-id:
the shipped `.so` files are stripped, and the build-id would otherwise hash
debug info that contains per-checkout paths. Two release builds from different
directories produced byte-identical APKs (checked on 2026-11-24); repeat that
check after changing the native stack.

For upstream-signed reproducible builds, the metadata also uses:

```yaml
Binaries: https://github.com/LinDevHard/sakuro/releases/download/v%v/sakuro-%v-foss-release.apk
AllowedAPKSigningKeys: <sakuro signing certificate SHA-256 digest>
```

The private key remains only in the GitHub `release` environment and encrypted
offline backups. It never belongs in this repository or in `fdroiddata`.
