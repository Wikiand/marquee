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

A GitHub Actions workflow is also maintained under
`.github/workflows/ci.yml` for the GitHub-hosted repository. It uses
Java 17 and runs the same Maven verification command on pushes and
pull requests targeting `master` or `main`.

GitHub Actions can provide supplementary evidence of automated
verification, but it does not automatically enforce merge protection
on the Gitea repository.

## Consequences

- The same Maven verification lifecycle can be run locally and in CI.
- Pushes and pull requests can trigger automated checks on supported
  hosting platforms.
- CI results provide evidence of build and test status.
- Repository branch protection and required status checks must be
  configured separately on each hosting platform.
- A successful GitHub Actions run does not prove that Gitea Actions
  executed or that Gitea merge protection is enabled.
- The existence of a workflow file alone does not prove that a
  workflow has executed successfully.

## Verification

### Local verification

On 16 November 2026, the project completed:

`mvn --batch-mode clean verify`

Results:

- Java 17 project build completed successfully.
- 170 tests executed.
- 0 failures.
- 0 errors.
- 0 skipped.
- JaCoCo analysed 24 classes.

### GitHub Actions verification

On 16 November 2026, the GitHub Actions workflow
`Final Cut CI` completed successfully for GitHub pull request #1.
The successful workflow execution was run number 13:

https://github.com/Wikiand/marquee/pull/1

The workflow executed the Maven verification job using the configured
Java 17 environment.

### Gitea verification and merge protection

Gitea Actions execution remains unverified because Actions support
is disabled by the Gitea instance administrator.

Gitea branch protection and required status checks must also be
verified separately in repository settings. GitHub CI success must
not be presented as evidence of Gitea merge protection.
