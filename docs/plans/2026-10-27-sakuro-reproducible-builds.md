# Sakuro Reproducible Builds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a reproducible, upstream-signed Sakuro FOSS APK from GitHub Actions and update the existing F-Droid submission to verify and distribute it.

**Architecture:** A unique Sakuro keystore is held outside Git and copied into the GitHub `release` environment as encrypted secrets. The Actions workflow checks out an immutable tag, runs the existing release lane with R8 enabled, publishes the signed APK to a GitHub Release, and exposes the certificate fingerprint needed by F-Droid. The F-Droid metadata is changed to upstream-signed RB only after the signed APK verifies against F-Droid's source build.

**Tech Stack:** Android Gradle Plugin, Gradle/Kotlin DSL, Fastlane, GitHub Actions, Android `keytool`/`apksigner`, F-Droid `apksigcopier`/verification tooling

**Spec:** `docs/superpowers/specs/2026-10-27-sakuro-reproducible-builds-design.md`

## Global Constraints

- Keep application ID `com.rinwave.sakuro` and all published tags unchanged.
- Use a unique RSA-4096 key with alias `sakuro-release` and do not reuse another Rinwave key.
- R8 and resource shrinking remain enabled for every release artifact.
- No keystore or credential may be committed or printed.
- F-Droid metadata switches to RB only after an actual reproducibility check passes.

## Review Focus

- A pull request or branch push must never receive signing secrets or publish a release.
- A manual dispatch with a non-version ref must fail before signing or publishing.
- A missing signing secret must fail clearly without falling back to a debug key.
- Re-running a release must update only the same tag's assets and must never move the tag.
- A signed APK whose unsigned contents differ from F-Droid's build must block the metadata switch.

---

### Task 1: Create and escrow the dedicated Sakuro key

**Files:**
- Create outside Git: `~/Library/Application Support/Rinwave/keys/sakuro-release.jks`
- Modify externally: macOS Keychain entries and GitHub environment secrets

**Interfaces:**
- Produces: `SAKURO_UPLOAD_KEYSTORE_BASE64`, `SAKURO_UPLOAD_KEYSTORE_PASSWORD`, `SAKURO_UPLOAD_KEY_ALIAS`, and `SAKURO_UPLOAD_KEY_PASSWORD` in GitHub environment `release`
- Produces: SHA-256 signing-certificate fingerprint for F-Droid metadata

- [x] **Step 1: Create the GitHub `release` environment**

Use the GitHub API for `LinDevHard/sakuro`; verify the environment is returned by the repository environments endpoint.

- [x] **Step 2: Generate the key without exposing credentials**

Generate random store/key passwords, create an RSA-4096 JKS key with alias `sakuro-release`, and store both passwords in macOS Keychain. Expected: `keytool -list` identifies exactly one private-key entry with that alias.

- [x] **Step 3: Upload environment-scoped secrets through stdin**

Set the four existing `SAKURO_UPLOAD_*` secrets in environment `release`; expected: `gh secret list --env release` lists all four names without values.

- [x] **Step 4: Record and independently verify the certificate fingerprint**

Run `keytool -list -v` and `apksigner verify --print-certs` after the first signed build; expected: both SHA-256 fingerprints are identical.

### Task 2: Add upstream-signed release publishing

**Files:**
- Modify: `.github/workflows/android.yml`
- Modify: `docs/releasing.md`
- Modify: `docs/fdroid.md`

**Interfaces:**
- Consumes: Task 1 environment secrets
- Produces: workflow input `release_tag` and release asset `sakuro-<version>-foss-release.apk`

- [x] **Step 1: Add safe workflow triggers and tag validation**

Add `workflow_dispatch.inputs.release_tag`; resolve it to the tag event ref for automatic runs or the explicit input for manual runs. Validate `^v[0-9]+\.[0-9]+\.[0-9]+$` and ensure it resolves to an existing tag before any secret-bearing step.

- [x] **Step 2: Add a release-environment signing job**

Checkout the resolved tag, use `environment: release`, set job-level `contents: write`, materialize the keystore under `runner.temp`, and run `bundle exec fastlane android release` with the four signing variables. Do not override the tag's R8 setting with a false default.

- [x] **Step 3: Publish deterministic asset names without moving tags**

Use `gh release create` only when the release is absent and `gh release upload --clobber` for assets. Publish the signed FOSS APK, AAB, mapping, license report, and checksums from `dist/<tag>/`.

- [x] **Step 4: Add workflow safety assertions**

Validate the YAML and inspect the event/job conditions. Expected: pull requests run only `verify`; tag/manual release jobs alone reference `environment: release` and `contents: write`.

- [x] **Step 5: Update release documentation**

Document that the historical `SAKURO_UPLOAD_*` names now carry Sakuro's dedicated app-signing key, how to restore it from backup, and how to dispatch an existing immutable tag without retagging.

- [x] **Step 6: Commit the workflow change**

Commit only the design, plan, workflow, and release documentation with message `Add upstream-signed release workflow`.

### Task 3: Produce and validate the signed release

Execution note: `v0.1.1` exposed nondeterministic AGP baseline-profile entries.
`v0.1.2` removed those profiles but F-Droid then exposed JDK-dependent Kotlin
tooling metadata and a disallowed AGP dependency signing block. The final
corrective release is `v0.1.3` (versionCode 4), with all three sources removed
or pinned.

**Files:**
- No repository source changes
- Create externally: GitHub Release assets for `v0.1.3`

**Interfaces:**
- Consumes: Task 2 workflow and Task 1 secrets
- Produces: public signed APK URL and verified certificate fingerprint

- [x] **Step 1: Push the release commit and publish `v0.1.3`**

Expected: the workflow checks out the exact immutable commit tagged `v0.1.3`;
the tag itself remains unchanged.

- [x] **Step 2: Wait for the complete release job**

Expected: tests, Detekt, dependency-license gate, R8 build, APK audit, signing, checksums, and release upload all pass.

- [x] **Step 3: Download and inspect the public APK**

Verify package `com.rinwave.sakuro`, versionName `0.1.3`, versionCode `4`, release signature, and absence of debug signing.

- [x] **Step 4: Verify upstream/F-Droid reproducibility**

Compare the public signed APK against the APK produced by fdroiddata pipeline `3004505129` using F-Droid signature-copy verification. Expected: only signing data differs and verification succeeds; otherwise stop before Task 4 and diagnose the exact differing entries.

### Task 4: Switch the existing F-Droid MR to upstream-signed RB

**Files:**
- Modify externally: `metadata/com.rinwave.sakuro.yml` on branch `LinDevHard/fdroiddata:com.rinwave.sakuro`

**Interfaces:**
- Consumes: Task 3 public APK URL and SHA-256 certificate fingerprint
- Produces: `Binaries`/`AllowedAPKSigningKeys` metadata for MR `!53715`

- [x] **Step 1: Add binary URL and allowed key**

Point the metadata at `https://github.com/LinDevHard/sakuro/releases/download/v%v/sakuro-%v-foss-release.apk` and set `AllowedAPKSigningKeys` to the verified lowercase SHA-256 certificate digest.

- [x] **Step 2: Validate metadata and build locally where available**

Run YAML parsing, `fdroid lint`, `fdroid rewritemeta`, and the app-specific build/verify commands available in the fdroiddata checkout. Expected: no metadata rewrite diff and successful binary verification.

- [x] **Step 3: Commit and push the existing MR branch**

Commit with message `Enable reproducible builds for Sakuro`; push without creating a second MR.

- [x] **Step 4: Wait for GitLab CI**

Expected: source checks, schema, lint, F-Droid build, and APK verification all pass.

- [x] **Step 5: Prepare reviewer response**

Report R8 enabled, upstream signing selected, the immutable source commit, public binary URL, and passing reproducibility verification. Obtain action-time confirmation before posting the message to GitLab.

