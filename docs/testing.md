# Final Cut — Testing and Demonstration Guide

## 1. Prerequisites

- Java Development Kit 17
- Apache Maven
- Git
- Postman or curl for manual HTTP testing
- SQLite command-line tools for optional database inspection

## 2. Run the automated verification suite

From the repository root:

```bash
mvn --batch-mode clean verify
```

This compiles the application, executes the automated tests, and
generates the JaCoCo coverage report.

Open the report at:

```text
target/site/jacoco/index.html
```

To run tests without generating the full verification report:

```bash
mvn test
```

To run a specific test class:

```bash
mvn -Dtest=MigrationRunnerTest test
```

## 3. Start the API locally

Build the runtime dependency classpath:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

Start the application:

```bash
java -cp "target/classes:$(cat target/classpath.txt)" com.marquee.Main
```

The API listens on port 8080 by default.

Check its health:

```bash
curl -i http://localhost:8080/health
```

## 4. Fresh database and migration demonstration

Use a new database path so the demonstration does not modify an
existing development database:

```bash
rm -f /tmp/final-cut-fresh.db

MARQUEE_DB=/tmp/final-cut-fresh.db \
  java -cp "target/classes:$(cat target/classpath.txt)" com.marquee.Main
```

The application initializes the database through its versioned
migration resources.

In another terminal, inspect the migration history:

```bash
sqlite3 /tmp/final-cut-fresh.db \
  'SELECT version, description FROM schema_migrations ORDER BY version;'
```

Expected migration versions are 1 through 5.

Stop the application and start it again using the same database path.
The migrations should not be applied a second time.

## 5. SQL injection demonstration

Use a harmless input containing SQL syntax as data:

```bash
curl -i --get \
  --data-urlencode "title=' OR 1=1 --" \
  http://localhost:8080/films
```

The input should be treated as a filter value, not executable SQL.
The response should follow the endpoint's normal filtering behavior.

This demonstration supplements automated tests; it does not replace
reviewing repository queries to confirm that user-controlled values
are bound through prepared statements.

## 6. Structured request logging demonstration

Send a request:

```bash
curl -i http://localhost:8080/health
```

Inspect the application output. The request log should contain a JSON
object with:

- `timestamp`
- `method`
- `path`
- `status`
- `duration_ms`

The request body should not appear in the request log.

## 7. Unexpected server-error demonstration

Unexpected failures should produce a generic HTTP 500 response rather
than exposing SQL messages, stack traces, or internal implementation
details to the client.

The automated handler tests exercise this behavior and verify that
internal diagnostic details are logged server-side.

Use the automated test as the repeatable demonstration:

```bash
mvn -Dtest=FilmHandlerTest#unexpectedDatabaseFailureReturnsGeneric500AndLogsDetails test
```

Do not damage a development database to simulate a production failure.

## 8. Rollback and migration failure tests

Run the migration tests:

```bash
mvn -Dtest=MigrationRunnerTest test
```

These tests cover migration initialization, version history,
schema evolution, data preservation, and failure rollback.

The rollback scenarios should verify database state after failure,
not merely that an exception was thrown.

## 9. Postman collection

Import:

`postman/Marquee.postman_collection.json`

Set the collection's `baseUrl` variable to:

`http://localhost:8080`

Start the API and run the collection using Postman's Collection Runner.

Review the actual Collection Runner output when recording request and
assertion counts. Do not rely on historical counts in documentation.

## 10. Continuous integration

The Gitea workflow is located at:

`.gitea/workflows/ci.yaml`

It is configured to run the Maven verification lifecycle for pushes
and pull requests targeting `main`.

Verify actual execution in the Gitea Actions interface. A successful
local build does not establish that Gitea CI has run.

Branch protection and required status checks must be verified
separately in repository settings.

## 11. Evidence to retain

For a project demonstration or review, retain:

1. Output from `mvn --batch-mode clean verify`.
2. The JaCoCo HTML coverage report.
3. The migration history query from a fresh database.
4. A structured JSON request log entry.
5. Automated test output for generic 500 handling.
6. Automated test output for migration rollback.
7. A successful Gitea Actions run.

Record actual results and dates. Do not claim a CI run, branch
protection setting, or Postman assertion count without verifying it.
