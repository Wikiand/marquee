# ADR-004: JUnit 5 and JaCoCo

- Status: Accepted
- Decision area: Automated testing

## Context

The application needs repeatable automated tests for HTTP behavior,
business rules, database operations, schema migrations, and error
handling.

Tests should run as part of the standard Maven build and provide
visibility into untested code paths.

## Decision

Use JUnit Jupiter 5 as the automated test framework and Maven Surefire
to discover and execute tests.

Use JaCoCo to collect coverage data and generate a report during the
Maven `verify` lifecycle.

The standard verification command is:

`mvn --batch-mode clean verify`

Tests are organized around application handlers, services, repositories,
database initialization, migrations, and logging behavior.

## Consequences

- Tests can be run consistently from local development and CI.
- The same framework supports unit and integration-style tests.
- JaCoCo identifies uncovered lines and branches.
- Coverage percentages are diagnostic measures, not proof of correctness.
- Meaningful assertions and behavior coverage take priority over
  artificially increasing coverage percentages.

## Verification

Run the Maven verification lifecycle and inspect:

`target/site/jacoco/index.html`

The test result and coverage report should be reviewed after meaningful
changes to the application.
