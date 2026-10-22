fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android quality

```sh
[bundle exec] fastlane android quality
```

Run unit tests and static analysis

### android fdroid

```sh
[bundle exec] fastlane android fdroid
```

Build and audit the unsigned F-Droid APK

### android full

```sh
[bundle exec] fastlane android full
```

Build the developer/reference flavor with libmpv

### android play

```sh
[bundle exec] fastlane android play
```

Build and verify signed Play APK/AAB; signing values come only from the environment

### android artifacts

```sh
[bundle exec] fastlane android artifacts
```

Collect release artifacts, R8 mapping (when present), license report and checksums

### android ci

```sh
[bundle exec] fastlane android ci
```

CI entry point: quality gates plus the audited F-Droid artifact

### android fdroid_release

```sh
[bundle exec] fastlane android fdroid_release
```

Validate a clean tag and build the audited unsigned F-Droid artifact

### android release

```sh
[bundle exec] fastlane android release
```

Validate a clean tag and build audited F-Droid plus signed Play artifacts

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
