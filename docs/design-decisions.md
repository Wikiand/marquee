# Final Cut — Architecture Decision Records

This document indexes the architectural decisions governing Final Cut.
Each decision records its context, the selected approach, its consequences,
and its implementation location.

## Accepted decisions

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](adr/0001-versioned-sql-migrations.md) | Versioned SQL migrations | Accepted |
| [ADR-002](adr/0002-structured-request-and-error-logging.md) | Structured request and server-error logging | Accepted |
| [ADR-003](adr/0003-continuous-integration.md) | Continuous integration with Gitea Actions | Accepted |
| [ADR-004](adr/0004-junit-testing-and-coverage.md) | JUnit 5 and JaCoCo | Accepted |

## Related documentation

- [README](../README.md)
- [API reference](api.md)
- [Testing guide](testing.md)
- [Entity relationship diagram](erd.mmd)

Decisions describe the intended architecture. Their implementation and
operational status must be verified against the source code and CI results.
