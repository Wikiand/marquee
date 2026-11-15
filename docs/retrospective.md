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

## Marquee Retrospective: Original Action and Final Cut Follow-up

The Marquee project had a retrospective before Final Cut development.
One of the improvements identified was to make HTTP request-body handling
shared, consistent and safe.

### Original action

Introduce a shared request-body reader with the following requirements:

- Enforce a maximum request-body size of 1 MiB.
- Return HTTP 413 when a request body exceeds the limit.
- Return HTTP 400 when the request contains malformed JSON.
- Apply the shared handling consistently across HTTP handlers.

### What happened in Final Cut

The action was revisited and implemented through
`RequestBodyReader`.

The implementation:

- Provides shared request-body handling.
- Enforces the 1 MiB request-body limit.
- Rejects oversized requests with HTTP 413.
- Preserves HTTP 400 handling for malformed JSON.
- Is covered by dedicated request-body reader tests.

Implementation commit: `ae40f26` — `feat: add shared request body reader with size limits`.

### Retrospective outcome

The original Marquee improvement was carried forward into Final Cut.
The shared reader and its validation behaviour were implemented and tested,
addressing the outstanding request-body handling work.

### Historical record and chronology

The Marquee retrospective took place before Final Cut implementation.
The original retrospective minutes have not been recovered, and the
available Git history does not contain a separate pre-Final-Cut commit
containing those notes.

The following implementation chronology is verifiable from Git history:

| Event | Commit | Date |
|---|---|---|
| Shared request-body reader implemented | `ae40f26` | 6 November 2026 |
| First identified Final Cut migration implementation | `dfb4ac7` | 7 November 2026 |
| Final Cut retrospective document created | `f9253f4` | 14 November 2026 |
| Marquee retrospective follow-up documented | `d7e74b8` | 15 November 2026 |

The request-body reader implementation therefore predates the identified
Final Cut migration work. This provides evidence that the improvement
was carried forward from the earlier Marquee work.

The Marquee retrospective section in this document is a reconstruction
based on the available project history and the retrospective action
previously discussed. It is not represented as the original meeting
minutes or as a document committed before Final Cut began.
