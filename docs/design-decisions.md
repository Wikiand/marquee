# Marquee: Architecture Decision Records

This document records the main architectural and technical decisions made
during the development of Marquee, including the reasoning, trade-offs,
and implementation choices.

## 1. Built-in Java HTTP Server

Marquee uses Java's `com.sun.net.httpserver.HttpServer` instead of a larger
web framework. This keeps the application lightweight and makes HTTP
routing and response handling explicit.

**Trade-off:** Routing, query parsing, JSON serialization, and error
handling require more code than they would with a framework.

## 2. Layered Architecture

The application separates handlers, services, repositories, models,
and database initialization.

- **Handlers:** Parse HTTP requests, invoke application operations, and
  construct HTTP responses.
- **Services:** Validate inputs and enforce business rules.
- **Repositories:** Execute SQL queries and map database rows to model
  objects.
- **Models:** Represent domain records and request payloads.
- **Database initialization:** Configures database connections, runs
  migrations, and initializes starter data.

This separation improves maintainability and makes service behavior
easier to test independently of HTTP and SQL implementation details.

## 3. SQLite Persistence

SQLite stores application data in a local database file without requiring
a separate database server. This is practical for local development,
automated testing, and project demonstrations.

The database defaults to `data/marquee.db`. The `MARQUEE_DB` environment
variable can override the database path.

**Trade-off:** High-concurrency or multi-instance production deployments
may require a server-based database and a deployment-specific connection
strategy.

## 4. Many-to-Many Film and Genre Relationship

A film can belong to multiple genres, and a genre can be associated with
multiple films. The `film_genres` junction table represents this
many-to-many relationship.

Its composite primary key, `(film_id, genre_id)`, prevents duplicate
associations between the same film and genre. Foreign keys maintain
referential integrity between related records.

## 5. Foreign Keys and Cascading Deletes

The application enables `PRAGMA foreign_keys = ON` for each database
connection.

Foreign-key constraints prevent associations and screenings from
referring to nonexistent records. `ON DELETE CASCADE` removes a film's
associated screenings and genre associations when the film is deleted.
Deleting a genre also removes its associated film-genre records.

These constraints help maintain database consistency and prevent orphaned
records.

## 6. Parameterized SQL

Repository queries use parameterized SQL for data values. User input is
passed separately from SQL syntax, reducing the risk of SQL injection in
CRUD operations and filtering queries.

Input validation complements parameterized queries by checking that
incoming values meet the application's expected types, formats, and
business rules.

## 7. Transactions for Film Changes

Film creation and updates coordinate film records with their genre
associations. Database transactions group related changes so they can
be committed together or rolled back if an operation fails.

This helps prevent partially completed film operations and keeps film
records and genre associations consistent.

## 8. Pagination

Film and screening collection endpoints support page-based pagination.

- **Default page:** `1`
- **Default page size:** `20`
- **Maximum page size:** `100`

Paginated responses include pagination metadata and the results for the
requested page. Pagination limits the number of records returned in a
single response and allows clients to retrieve larger catalogues
incrementally.

Filtering and pagination can be combined to retrieve a specific subset
of films or screenings.

## 9. HTTP Status Codes and Error Responses

Marquee uses conventional HTTP status codes to communicate request
outcomes.

- `200 OK`: Successful retrieval or update.
- `201 Created`: Successful resource creation.
- `204 No Content`: Successful deletion without a response body.
- `400 Bad Request`: Invalid request data or query parameters.
- `404 Not Found`: Requested resource does not exist.
- `405 Method Not Allowed`: HTTP method is unsupported for the route.
- `409 Conflict`: Duplicate resource or association where the application
  defines a conflict.
- `500 Internal Server Error`: Unexpected server-side failure.

Errors use a consistent JSON envelope containing `error.code`,
`error.message`, and `error.details`.

The `details` array may be empty for general errors. Some validation
errors include field-specific messages to help clients identify
individual input problems.

Unexpected internal errors should not expose sensitive implementation
details to API clients. The current request logger records request
metadata but does not capture exception details or stack traces.

## 10. Seed Data

On first startup, the application initializes the database schema and
loads a small starter catalogue when the films table is empty.

The root-level `seed.sql` script provides an extended dataset for
demonstrations and testing, including films, genres, film-genre
associations, and screenings.

The startup catalogue and SQL seed dataset contain different numbers
of records. The README explains how to initialize the database and load
the extended dataset.

The SQL seed uses explicit IDs and conflict handling to avoid inserting
matching records again. It does not overwrite existing records with the
same IDs.

## 11. Configuration and Local Development

The application uses a local SQLite database by default, while the
`MARQUEE_DB` environment variable allows developers to select another
database path.

This keeps local setup simple while supporting isolated test databases
and alternative development environments.

The project uses Maven for dependency management, compilation, and
automated testing.

## 12. Testing Strategy

Marquee uses automated tests to verify behavior at different application
layers.

- **Handler tests:** Check HTTP routes, request parsing, response status
  codes, and error responses.
- **Service tests:** Check validation rules and business behavior.
- **Repository and integration tests:** Check persistence and interactions
  with the database.
- **Migration tests:** Check migration history, schema evolution,
  compatibility, and preservation of existing records.
- **API collection tests:** Exercise endpoints through HTTP requests and
  verify expected responses.

JUnit Jupiter is used as the test framework, with Maven Surefire executing
the test suite.

This layered approach helps identify regressions while keeping business
logic, persistence, migrations, and HTTP behavior independently testable.

## 13. Schema Migration Strategy

Marquee uses an ordered, versioned migration approach implemented by
`MigrationRunner`.

Each migration has a version, a description, and a collection of SQL
statements. Applied versions are recorded in the `schema_migrations`
table so that previously completed migrations are not applied again.

The migration sequence currently establishes the films and genres tables,
the film-genre relationship, screenings, database indexes, and a nullable
`room` column on screenings. The `room` column is currently a schema-level
extension; the screening model, repository operations, and HTTP API do not
yet expose or use it.

Before applying pending migrations, the runner validates the existing
database against the original schema. After applying migrations, it
validates the resulting schema against the latest expected structure.

This approach supports fresh database initialization and the evolution
of existing databases without requiring existing application data to
be discarded.

**Trade-off:** Migrations require careful version ordering and should be
treated as permanent schema history. Changes to an already-applied
migration should instead be introduced through a new migration.

## 14. Structured Request Logging

Marquee uses a custom HTTP `Filter` named `RequestLoggingFilter` to
record request information after each request is processed.

Each request produces a JSON log entry containing:

- Timestamp
- HTTP method
- Request path
- Response status
- Request duration in milliseconds

The logger writes one JSON object per line, making the output suitable
for terminal inspection and structured log processing.

Request bodies are not included in the request log, avoiding unnecessary
exposure of submitted data.

**Trade-off:** The current implementation writes logs to standard output.
A production deployment may require configurable log destinations,
retention, rotation, and centralized collection.

## 15. Continuous Integration Platform

The repository contains a GitHub Actions workflow at
`.github/workflows/ci.yml`.

The workflow uses Ubuntu, sets up Temurin Java 17, enables Maven
dependency caching, and runs:

`mvn --batch-mode clean verify`

It is configured to run for pushes to `master` and pull requests
targeting `master`.

This provides an automated build and test check for the GitHub repository
that hosts this workflow.

**Deployment consideration:** The Final Cut project is also hosted on
Gitea. The presence of this GitHub Actions workflow does not establish
that CI is running on the Gitea repository. Gitea Actions availability
and workflow execution must be verified separately before claiming
that the Gitea repository is protected by automated CI.

## 16. Test Framework

Marquee uses JUnit Jupiter 5.13.4 for automated tests.

Maven Surefire Plugin 3.5.3 discovers and executes the test suite during
the Maven test lifecycle.

JUnit provides test isolation, lifecycle support, and assertions for
checking expected application behavior. The same framework is used
across handler, service, database, migration, and logging tests.

**Trade-off:** Tests still need deliberate integration coverage to verify
behavior across application layers; passing isolated unit tests alone
does not establish that every end-to-end workflow is correct.

## 17. Future Improvements

Potential areas for future development include:

- Extending field-specific validation details consistently across all
  endpoints.
- Evaluating authentication and authorization requirements before
  exposing the API beyond a local development environment.
- Evaluating production deployment, monitoring, and operational
  requirements.
- Introducing configurable log destinations and retention for
  deployments that require centralized logging.
- Adding server-side exception logging for diagnosing unexpected
  application failures without exposing internal details to API clients.

These are potential enhancements, not claims about existing functionality.
