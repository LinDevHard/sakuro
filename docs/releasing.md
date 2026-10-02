# Release pipeline

Sakuro has one product dependency graph (`foss`, Media3-only) and two release
outputs from it:

- an unsigned APK for F-Droid, which F-Droid rebuilds and signs itself;
- a signed APK and Android App Bundle for Google Play.

The `full` flavor contains libmpv and remains a benchmark/reference build. It is
not a store artifact.

## Version and optimization gates

The canonical version is stored in `gradle.properties`:

```properties
sakuro.versionName=0.1.0
sakuro.versionCode=1
sakuro.enableR8=false
```

Increase `versionCode` for every store release and keep `versionName` equal to
the Git tag without its `v` prefix. R8 and resource shrinking stay disabled
until the optimized APK passes the physical-device benchmark/parity suite. Once
that is true, set the GitHub Actions variable `SAKURO_ENABLE_R8=true` (and then
make `sakuro.enableR8=true` the repository default). Preserve the generated
`mapping.txt` for every optimized release.

## Signing secrets

Never commit the upload keystore. Configure these GitHub Actions secrets:

- `SAKURO_UPLOAD_KEYSTORE_BASE64`
- `SAKURO_UPLOAD_KEYSTORE_PASSWORD`
- `SAKURO_UPLOAD_KEY_ALIAS`
- `SAKURO_UPLOAD_KEY_PASSWORD`

For a local signed build, decode the keystore outside the repository and expose
its path as `SAKURO_UPLOAD_KEYSTORE_FILE` together with the other three values.

## Lanes

```bash
bundle exec fastlane android quality  # tests, detekt, dependency/license gate
bundle exec fastlane android fdroid   # unsigned audited APK
bundle exec fastlane android play     # signed APK + AAB
bundle exec fastlane android release  # clean exact tag, all gates + checksums
```

`android release` requires `HEAD` to have the exact tag `v<versionName>` and a
localized `<versionCode>.txt` changelog for every metadata locale. It creates
`dist/v<versionName>/` with F-Droid and Play artifacts, the dependency license
report, optional R8 mapping, and `SHA256SUMS`.

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
4. Create and push the exact tag, for example `v0.1.0`.
5. The tag job builds and verifies both store outputs and retains the artifact
   bundle for 30 days. Publishing to Play and submitting to `fdroiddata` remain
   explicit human-controlled steps.
