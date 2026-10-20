# Marquee: Design Decisions

## 1. Built-in Java HTTP Server

Marquee uses Java's `com.sun.net.httpserver.HttpServer` instead of a larger web framework. This keeps the application lightweight and makes HTTP routing and response handling explicit.

**Trade-off:** Routing, query parsing, JSON serialization, and error handling require more code than they would with a framework.

## 2. Layered Architecture

The application separates handlers, services, repositories, models, and database initialization.

- **Handlers:** Parse HTTP requests, invoke application operations, and construct HTTP responses.
- **Services:** Validate inputs and enforce business rules.
- **Repositories:** Execute SQL queries and map database rows to model objects.
- **Models:** Represent domain records and request payloads.
- **Database initialization:** Creates the schema, configures database connections, and initializes starter data.

This separation improves maintainability and makes service behavior easier to test independently of HTTP and SQL implementation details.

## 3. SQLite Persistence

SQLite stores application data in a local database file without requiring a separate database server. This is practical for local development, automated testing, and project demonstrations.

The database defaults to `data/marquee.db`. The `MARQUEE_DB` environment variable can override the database path.

**Trade-off:** High-concurrency or multi-instance production deployments may require a server-based database and a deployment-specific connection strategy.

## 4. Many-to-Many Film and Genre Relationship

A film can belong to multiple genres, and a genre can be associated with multiple films. The `film_genres` junction table represents this many-to-many relationship.

Its composite primary key, `(film_id, genre_id)`, prevents duplicate associations between the same film and genre. Foreign keys maintain referential integrity between the related records.

## 5. Foreign Keys and Cascading Deletes

The application enables `PRAGMA foreign_keys = ON` for each database connection.

Foreign-key constraints prevent associations and screenings from referring to nonexistent records. `ON DELETE CASCADE` removes a film's associated screenings and genre associations when the film is deleted. Deleting a genre also removes its associated film-genre records.

These constraints help maintain database consistency and prevent orphaned records.

## 6. Parameterized SQL

Repository queries use parameterized SQL for data values. User input is passed separately from SQL syntax, reducing the risk of SQL injection in CRUD operations and filtering queries.

Input validation complements parameterized queries by checking that incoming values meet the application's expected types, formats, and business rules.

## 7. Transactions for Film Changes

Film creation and updates coordinate film records with their genre associations. Database transactions group related changes so they can be committed together or rolled back if an operation fails.

This helps prevent partially completed film operations and keeps film records and genre associations consistent.

## 8. Pagination

Film and screening collection endpoints support page-based pagination.

- **Default page:** `1`
- **Default page size:** `20`
- **Maximum page size:** `100`

Paginated responses include pagination metadata and the results for the requested page. Pagination limits the number of records returned in a single response and allows clients to retrieve larger catalogues incrementally.

Filtering and pagination can be combined to retrieve a specific subset of films or screenings.

## 9. HTTP Status Codes and Error Responses

Marquee uses conventional HTTP status codes to communicate request outcomes.

- `200 OK`: Successful retrieval or update.
- `201 Created`: Successful resource creation.
- `204 No Content`: Successful deletion without a response body.
- `400 Bad Request`: Invalid request data or query parameters.
- `404 Not Found`: Requested resource does not exist.
- `405 Method Not Allowed`: HTTP method is unsupported for the route.
- `409 Conflict`: Duplicate resource or association where the application defines a conflict.

Errors use a consistent JSON envelope containing `error.code`, `error.message`, and `error.details`.

The `details` array is currently empty for all errors. Field-specific validation details could be added in a future improvement to help clients identify individual input problems.

## 10. Seed Data

On first startup, the application initializes the database schema and loads a small starter catalogue when the films table is empty.

The root-level `seed.sql` script provides an extended dataset for demonstrations and testing, including films, genres, film-genre associations, and screenings.

The startup catalogue and SQL seed dataset contain different numbers of records. The README explains how to initialize the database and load the extended dataset.

The SQL seed uses explicit IDs and conflict handling to avoid inserting matching records again. It does not overwrite existing records with the same IDs.

## 11. Configuration and Local Development

The application uses a local SQLite database by default, while the `MARQUEE_DB` environment variable allows developers to select another database path.

This keeps local setup simple while supporting isolated test databases and alternative development environments.

The project uses Maven for dependency management, compilation, and automated testing. Continuous integration runs the Maven verification workflow to check that the project builds and its tests pass.

## 12. Testing Strategy

Marquee uses automated tests to verify behavior at different application layers.

- **Handler tests:** Check HTTP routes, request parsing, response status codes, and error responses.
- **Service tests:** Check validation rules and business behavior.
- **Repository and integration tests:** Check persistence and interactions with the database.
- **API collection tests:** Exercise endpoints through HTTP requests and verify expected responses.

This layered approach helps identify regressions while keeping business logic, persistence, and HTTP behavior independently testable.

## 13. Known Limitations and Future Improvements

The current implementation prioritizes a lightweight, locally runnable API and the assignment's core requirements.

Potential improvements include:

- Returning meaningful field-specific validation details in the error envelope.
- Introducing explicit schema migration tracking as the database schema evolves.
- Expanding operational logging and structured security-event reporting where needed.
- Evaluating authentication, authorization, and production deployment requirements before exposing the API beyond a local development environment.

These items should be treated as future work rather than existing functionality unless they are implemented and verified.
