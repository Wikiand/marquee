# Final Cut Retrospective

## Project outcome

Final Cut was merged into the `main` branch through pull request #1.

- Merge commit: `354769f`
- Pull request: #1
- Files changed: 21
- Insertions: 1,025
- Deletions: 602
- Automated tests: 169 passed
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: successful
- JaCoCo report: generated

## What went well

- Versioned SQL migrations were extracted into separate numbered SQL resources.
- Migration history validation and rollback behavior received dedicated tests.
- Database operations use prepared statements for user-supplied values.
- SQL injection regression coverage was added at the HTTP handler level.
- Unexpected server errors return generic responses while internal details are logged.
- Structured JSON request logging captures request metadata.
- Documentation was improved through four Architecture Decision Records.
- Changes were organized into focused commits and reviewed through a pull request.
- The complete test suite passed both before and after merging.

## Challenges

- Migration handling required careful preservation of existing database schemas and data.
- SQL parsing needed to account for quoted values, comments and statement delimiters.
- Error handling had to protect internal details without changing established HTTP error semantics.
- JaCoCo coverage analysis required investigating uncovered branches and edge cases.
- Gitea Actions were disabled by the repository administrator, preventing automated CI execution.

## Lessons learned

- Database migrations should be versioned, repeatable and tested against existing data.
- Prepared statements must be used for user-controlled SQL values.
- Error responses and internal error logging should have separate responsibilities.
- Regression tests should demonstrate security behavior rather than only inspect implementation.
- Focused commits and pull requests make changes easier to review.
- A configured CI workflow is not proof that CI is executing; infrastructure availability must also be verified.

## Follow-up action items

| Action | Status |
|---|---|
| Ask repository administrator about enabling Gitea Actions | Pending |
| Verify CI execution when Actions become available | Pending |
| Confirm branch protection and required status checks | Pending |
| Preserve successful Maven and JaCoCo output as project evidence | Pending |
| Review remaining JaCoCo coverage gaps and prioritize meaningful tests | Pending |
| Demonstrate fresh database migration and existing-data preservation | Pending |
| Demonstrate structured request logs and generic HTTP 500 behavior | Pending |

## Final assessment

The implementation has been merged into `main` and passes all 169 automated tests locally.

Automated CI execution and merge-blocking status checks remain unverified because Gitea Actions are disabled by the repository administrator.
