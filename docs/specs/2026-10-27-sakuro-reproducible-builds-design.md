# Sakuro Reproducible Builds Design

## Intent

Publish Sakuro APKs signed with a dedicated upstream key so GitHub and F-Droid
can distribute mutually compatible updates. Keep the key isolated from every
other Rinwave application and never commit signing material to Git.

## Chosen approach

GitHub Actions performs release signing. A dedicated `sakuro-release` keystore
and random credentials are stored as secrets in the GitHub `release`
environment. Release jobs run only for immutable version tags or an explicit
manual dispatch that checks out an existing version tag.

The workflow builds the exact tagged source, runs the existing release gates,
publishes a signed FOSS APK with a stable filename, and records checksums and
the signing-certificate fingerprint. The current `v0.1.1` tag is not moved;
the new workflow may be dispatched from `main` while checking out that tag.

## F-Droid contract

The signed GitHub APK is accepted for the existing F-Droid submission only
after its unsigned contents reproduce the APK built by F-Droid. The metadata
then uses `Binaries` plus `AllowedAPKSigningKeys`. If verification fails, the
MR remains on F-Droid signing until the mismatch is understood and fixed.

## Security constraints

- Use a unique RSA-4096 key named `sakuro-release`; do not reuse a Rinwave key.
- Store the keystore outside the repository and keep credentials in macOS
  Keychain and GitHub environment secrets.
- Never print or commit the keystore or credentials.
- Give the publishing job only `contents: write`; pull-request jobs receive no
  signing secrets.
- Preserve the key for the lifetime of `com.rinwave.sakuro` and keep an
  encrypted offline backup outside GitHub.

## Considered alternatives

- Local manual signing offers a smaller online attack surface but adds a manual
  step to every release; rejected in favor of the requested Actions workflow.
- Reusing the shared Rinwave key is operationally simple but expands the blast
  radius of compromise to several apps; rejected.

