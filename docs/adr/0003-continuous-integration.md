# ADR-003: Continuous integration

- Status: Accepted
- Decision area: Build and test automation

## Context

Changes should be checked automatically so that compilation failures
and failing tests are detected before changes are integrated into the
main development branch.

The Final Cut repository is hosted on Gitea. A workflow in a different
hosting platform does not establish that the Gitea repository is running
the same checks.

## Decision

Maintain a Gitea Actions workflow at:

`.gitea/workflows/ci.yaml`

The workflow is configured for pushes and pull requests targeting
`main`. It checks out the repository, installs Temurin Java 17, enables
Maven dependency caching, and runs:

`mvn --batch-mode clean verify`

A GitHub Actions workflow is also present under
`.github/workflows/ci.yml` for the GitHub-hosted repository.

## Consequences

- The same Maven verification lifecycle can be run locally and in CI.
- Pushes and pull requests targeting `main` can trigger automated checks.
- CI results provide evidence of build and test status.
- Repository branch protection and required status checks must be
  configured separately in Gitea.
- The existence of a workflow file alone does not prove that a workflow
  has executed successfully.

## Verification

Verify the workflow in the Gitea Actions interface after a push or
pull request. Confirm that the run uses Java 17 and completes
`mvn --batch-mode clean verify` successfully.

Verify branch protection separately in repository settings.
