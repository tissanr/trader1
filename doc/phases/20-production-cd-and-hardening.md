# Phase 20: Production CD And Hardening

## Status

Planned

## Goal

Turn releases into a controlled production deployment workflow with approvals, health checks, and a clear rollback story.

## Why Now

Production automation should only land after staging deployment has proven the packaging and environment model. At this point the focus shifts from convenience to operational safety.

## Scope

- [ ] Add a production deployment workflow
- [ ] Require approval before production rollout
- [ ] Add deployment health checks and rollback handling
- [ ] Document the production release path for maintainers

## Out Of Scope

- Major infrastructure redesign
- Multi-region deployment
- Advanced traffic shaping beyond the first safe rollout path

## Technical Areas

- production deployment workflow
- protected environments
- post-deploy verification
- rollback handling
- release documentation

## Tasks

- [ ] Define the production deployment trigger and approval model
- [ ] Reuse the staging artifact and deployment path where practical
- [ ] Add post-deploy checks that can fail the rollout clearly
- [ ] Define the rollback path and when it is invoked
- [ ] Add release documentation for operators
- [ ] Verify the flow with a non-destructive production-like rehearsal if possible

## Acceptance Criteria

- [ ] Production deployment requires explicit approval
- [ ] A production rollout surfaces success and failure clearly
- [ ] Operators know how to roll back to a known-good version
- [ ] Production deployment no longer depends on ad hoc manual steps

## Notes

- A manual approval gate is a feature, not a defect, for the first production rollout path.
