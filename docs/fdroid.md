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

The pipeline runs unit tests and detekt, builds the unsigned F-Droid release
APK, checks the resolved runtime graph for common proprietary SDKs and libmpv,
inspects the APK for mpv/FFmpeg native libraries, verifies the application ID,
and confirms that the offline flavor does not request Internet access.

The build artifact is:

```text
composeApp/build/outputs/apk/foss/release/composeApp-foss-release-unsigned.apk
```

The shared release and signing workflow is documented in
[`releasing.md`](releasing.md). Signing is forcibly disabled in the F-Droid
lane even when Play signing secrets are available.

## Release contract

1. Update `versionName` and monotonically increase `versionCode` in
   `composeApp/build.gradle.kts`.
2. Add localized release notes named `<versionCode>.txt` below
   `fastlane/metadata/android/<locale>/changelogs/`.
3. Merge the release commit and tag that exact commit as `v<versionName>`.
4. Run `bundle exec fastlane android release` from the clean tagged checkout.
5. Submit/update `com.rinwave.sakuro.yml` in the separate `fdroiddata`
   repository, selecting the `foss` Gradle flavor.

Suggested fdroiddata build block for version 0.1.0:

```yaml
Categories:
  - Multimedia
License: GPL-3.0-only
AuthorName: Rinwave
SourceCode: https://github.com/LinDevHard/sakuro
IssueTracker: https://github.com/LinDevHard/sakuro/issues

RepoType: git
Repo: https://github.com/LinDevHard/sakuro.git

Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    gradle:
      - foss

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```

The tag does not exist yet; create it only when the release commit is ready.
F-Droid signs its own builds, so no signing key or secret belongs in this
repository or in the F-Droid lane.
