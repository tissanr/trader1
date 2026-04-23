# Security Gates

This document records the repository security controls for Phase 17 and the GitHub settings that must be enabled by a repository admin.

## Repo-managed checks

- `CI` in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) validates install, build, and tests on pull requests and pushes to `main`.
- `Dependency Review` in [`.github/workflows/dependency-review.yml`](../.github/workflows/dependency-review.yml) blocks pull requests that introduce dependencies with known vulnerabilities at `moderate` severity or higher.
- `CodeQL` in [`.github/workflows/codeql.yml`](../.github/workflows/codeql.yml) scans supported repository surfaces, with this repo explicitly analyzing GitHub Actions workflows.
- [`.github/dependabot.yml`](../.github/dependabot.yml) keeps GitHub Actions and npm dependencies on a weekly update cadence.

## Required status checks

Protect `main` and require these checks before merge:

- `build`
- `dependency-review`
- `analyze-actions`

Keep job names unique across workflows so branch protection resolves them unambiguously.

## GitHub settings to enable

The following settings cannot be changed from this workspace because GitHub CLI authentication is currently invalid. A repository admin should enable them in the GitHub UI:

1. In `Settings` -> `Security` -> `Advanced Security`:
   - Enable Dependabot alerts.
   - Enable Dependabot security updates.
   - Enable secret scanning if the repository is eligible.
   - Enable push protection if the repository is eligible.
2. In `Settings` -> `Branches` or `Settings` -> `Rules`:
   - Protect `main`.
   - Require a pull request before merge.
   - Require the `build`, `dependency-review`, and `analyze-actions` status checks to pass.
   - Block force pushes and branch deletion.

## Validation notes

- `Dependency Review` only evaluates pull requests, so branch protection is what turns it into a merge gate.
- CodeQL support in this repository is intentionally limited to GitHub Actions workflows because the application code is Clojure/ClojureScript, which is outside CodeQL's supported language set.
- Secret scanning and push protection availability depends on repository visibility and GitHub plan entitlements.
