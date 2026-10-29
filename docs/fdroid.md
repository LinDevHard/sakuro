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

Suggested fdroiddata build block for version 0.1.3:

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
  - versionName: 0.1.3
    versionCode: 4
    commit: <full release commit SHA>
    gradle:
      - foss

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
UpdateCheckData: gradle.properties|sakuro.versionCode=(\d+)|.|sakuro.versionName=(.+)
CurrentVersion: 0.1.3
CurrentVersionCode: 4
```

Use the immutable commit SHA tagged as `v0.1.3` after the release commit is
ready.

For upstream-signed reproducible builds, the metadata also uses:

```yaml
Binaries: https://github.com/LinDevHard/sakuro/releases/download/v%v/sakuro-%v-foss-release.apk
AllowedAPKSigningKeys: <sakuro signing certificate SHA-256 digest>
```

The private key remains only in the GitHub `release` environment and encrypted
offline backups. It never belongs in this repository or in `fdroiddata`.
