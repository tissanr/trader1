# Phase 19: Staging CD

## Status

Planned

## Goal

Create a repeatable staging deployment path so merged changes can be verified in a realistic environment before production release decisions are made.

## Why Now

Once CI is trustworthy, staging deployment becomes the next practical step for catching environment-specific breakage and verifying operator workflows outside local machines.

## Scope

- [ ] Define a staging deployment target and artifact shape
- [ ] Automate deployment to staging from approved branch activity
- [ ] Add post-deploy smoke checks
- [ ] Gate staging access through GitHub Environments or equivalent controls

## Out Of Scope

- Production deployment
- Progressive rollout strategies
- Full browser regression suites

## Technical Areas

- deployment workflow
- application packaging
- staging environment configuration
- smoke checks
- environment secrets and approvals

## Tasks

- [ ] Decide how `trader1` is packaged for deployment
- [ ] Define staging-specific configuration and secret handling
- [ ] Implement a deployment workflow that depends on green CI
- [ ] Add post-deploy health or smoke checks
- [ ] Configure a protected staging environment in GitHub
- [ ] Verify deployment, failure, and redeploy behavior

## Acceptance Criteria

- [ ] A green main-branch build can be deployed to staging in a repeatable way
- [ ] Staging deploys do not expose production secrets to pull request workflows
- [ ] Basic post-deploy health checks confirm the app is reachable
- [ ] Maintainers can see whether staging deployment succeeded or failed

## Notes

- Do not automate deployment until the deployment target is defined clearly enough to support rollback thinking later.
