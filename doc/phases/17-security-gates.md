# Phase 17: Security Gates

## Status

Planned

## Goal

Add repository-level and CI-level security checks that catch vulnerable dependency changes, unsafe workflow patterns, and exposed secrets before merge.

## Why Now

Once baseline CI exists, the next highest-value guardrail is preventing dependency and workflow risk from entering the codebase quietly.

## Scope

- [ ] Add pull-request dependency review
- [ ] Add code scanning where GitHub-native tooling is useful for this repo
- [ ] Enable repository security features that should block obvious mistakes
- [ ] Make the new checks enforceable through branch protection or rulesets

## Out Of Scope

- Deep static analysis for Clojure code that GitHub-native tooling does not support
- Runtime application security testing
- Deployment-time secrets rotation

## Technical Areas

- GitHub Actions security workflows
- dependency review
- CodeQL or equivalent GitHub-native scanning
- branch protection or repository rulesets
- repository security settings

## Tasks

- [ ] Add a dependency review workflow for pull requests
- [ ] Add code scanning for supported languages and workflow files
- [ ] Enable Dependabot alerts and security updates
- [ ] Enable secret scanning and push protection where available
- [ ] Reduce workflow token permissions to the minimum needed
- [ ] Define which checks must pass before merge
- [ ] Verify the new checks fail cleanly on introduced sample issues

## Acceptance Criteria

- [ ] Vulnerable dependency changes are visible and can block merge
- [ ] Supported code and workflow surfaces are scanned automatically
- [ ] Repository security settings are enabled for common leak and supply-chain risks
- [ ] Protected branches require the new security checks to pass

## Notes

- GitHub-native code scanning is still useful here, but it will not replace Clojure-specific review discipline.
- Prefer checks that are understandable and actionable over noisy scanners that get ignored.
