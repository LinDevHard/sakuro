# Release pipeline

Sakuro has one product dependency graph (`foss`, Media3-only) and three release
outputs from it:

- an unsigned APK that F-Droid rebuilds for reproducibility verification;
- an upstream-signed APK shared by GitHub Releases and F-Droid;
- a signed APK and Android App Bundle for Google Play.

The `full` flavor contains libmpv and remains a benchmark/reference build. It is
not a store artifact.

## Version and optimization gates

The canonical version is stored in `gradle.properties`:

```properties
sakuro.versionName=0.1.3
sakuro.versionCode=4
sakuro.enableR8=true
```

Increase `versionCode` for every store release and keep `versionName` equal to
the Git tag without its `v` prefix. R8 and resource shrinking are enabled for
release artifacts, including the unsigned F-Droid APK. Preserve the generated
`mapping.txt` for every optimized release.

## App-signing secrets

Never commit the Sakuro app-signing keystore. The dedicated key has alias
`sakuro-release`; it must not be reused by another application. Configure these
secrets in the protected GitHub Actions environment named `release`:

- `SAKURO_UPLOAD_KEYSTORE_BASE64`
- `SAKURO_UPLOAD_KEYSTORE_PASSWORD`
- `SAKURO_UPLOAD_KEY_ALIAS`
- `SAKURO_UPLOAD_KEY_PASSWORD`

Protect that environment with the `LinDevHard` required reviewer and selected
deployment refs `main` and `v*`. Keep the repository ruleset `Protect Sakuro
release tags` active so only repository administrators can create, move, or
delete release tags. Manual releases are accepted only when the workflow itself
is launched from `main`; the job still checks out the requested immutable tag.

The historical `SAKURO_UPLOAD_*` names are retained for compatibility with the
tagged release scripts, but they contain Sakuro's permanent app-signing key,
not a replaceable Play upload key. Store the local keystore outside the
repository, keep its passwords in a password manager, and maintain an encrypted
offline backup. Loss of this key prevents compatible GitHub/F-Droid updates.

For a local signed build, decode the keystore outside the repository and expose
its path as `SAKURO_UPLOAD_KEYSTORE_FILE` together with the other three values.

## Lanes

```bash
bundle exec fastlane android quality  # tests, detekt, dependency/license gate
bundle exec fastlane android fdroid   # unsigned audited APK
bundle exec fastlane android fdroid_release # clean tag + F-Droid release gates
bundle exec fastlane android play     # signed APK + AAB
bundle exec fastlane android release  # clean exact tag, all gates + checksums
```

Both release lanes require `HEAD` to have the exact tag `v<versionName>` and a
localized `<versionCode>.txt` changelog for every metadata locale. The full
`android release` lane creates `dist/v<versionName>/` with F-Droid and Play
artifacts, the dependency license report, optional R8 mapping, and
`SHA256SUMS`. GitHub Actions regenerates that manifest after adding the stable
`sakuro-<version>-foss-release.*` filenames, and verifies the APK certificate
against Sakuro's expected SHA-256 fingerprint before publication. All external
Actions in the signing job are pinned to immutable commit SHAs.

## Dependency and asset licenses

`scripts/verify-release-licenses.sh` resolves the actual FOSS release runtime
graph. Every external component group must match an explicitly reviewed SPDX
entry in `licenses/dependency-policy.tsv`; new dependency groups fail CI until
reviewed. The same gate checks that all vendored shader/font notices are
present. The generated report is stored at
`build/reports/licenses/foss-release.tsv` and included with release artifacts.

This is an engineering gate, not a substitute for legal review.

## Tag release

1. Complete device benchmark/parity and smoke tests for the exact release APK.
2. Update the version and localized changelogs.
3. Merge the clean release commit.
4. Create and push the exact tag, for example `v0.1.3`.
5. The tag job builds the unsigned comparison APK plus signed APK/AAB, runs all
   release gates, uploads CI artifacts, and creates or updates the matching
   GitHub Release without moving the tag.
6. To publish an existing immutable tag after introducing the workflow, run the
   `Android` workflow from `main` and set `release_tag` to that tag. For an
   existing release, provide its exact immutable tag; the workflow checks out
   that tag instead of retagging it. Approve the protected `release`
   environment only after confirming the requested tag and commit.
7. Updating `fdroiddata` remains an explicit human-controlled step and is done
   only after F-Droid verifies that the upstream-signed APK is reproducible.
