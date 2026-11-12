# Security Policy

Sakuro is an early Android-first open-source project. Security reports are welcome, especially issues involving file handling, media parsing boundaries, unsafe native integration, or privacy-sensitive behavior.

## Supported Versions

Only the current `main` branch is actively reviewed before the first stable release.

| Version | Supported |
|---|---|
| `main` | Yes |
| Tagged pre-releases | Best effort |
| Older commits | No |

## Reporting A Vulnerability

Please do not open a public issue for a suspected vulnerability.

Preferred reporting path:

1. Use GitHub's private vulnerability reporting if it is enabled for the repository.
2. If private reporting is not available, contact the maintainer privately through the public Rinwave/Sakuro support channel listed for the repository.

Include:

- Affected commit, branch, or release.
- Device and Android version, when relevant.
- Clear reproduction steps.
- Impact and attack scenario.
- Any logs, crafted files, or proof-of-concept materials needed to reproduce.

Please avoid sending public exploit details before the issue is triaged.

## Scope

In scope:

- Unsafe handling of local media files or metadata.
- Native integration issues around libmpv, JNI, shaders, or bundled native libraries.
- Path traversal, arbitrary file access, or unsafe import/export behavior.
- Privacy leaks from local media scanning, settings, logs, debug overlay, or reports.
- Build/distribution issues that could lead to malicious artifacts.

Out of scope:

- Issues requiring physical access to an unlocked device without a meaningful escalation.
- Denial-of-service reports that only crash a debug build without broader impact.
- Vulnerabilities in third-party dependencies unless they affect Sakuro's shipped configuration.
- Social engineering or phishing against maintainers/users.

## Disclosure

The project aims to acknowledge security reports within 7 days and provide a triage update within 30 days. Timelines may be longer before stable releases, but the intent is to coordinate fixes before public disclosure.

Security fixes should include tests or reproduction notes whenever practical.
