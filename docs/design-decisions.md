# Marquee: Design Decisions

## 1. Built-in Java HTTP server

Marquee uses Java's `com.sun.net.httpserver.HttpServer` rather than a larger web framework. This keeps the application lightweight and makes HTTP routing and response behavior explicit.

**Trade-off:** routing, query parsing, serialization, and error handling require more code than they would with a framework.

## 2. Layered architecture

The application separates handlers, services, repositories, models, and database initialization.

- **Handlers:** translate HTTP requests into application calls and build HTTP responses.
- **Services:** validate inputs and enforce application rules.
- **Repositories:** execute SQL and map database rows to model objects.
- **Models:** represent domain records and request payloads.
- **Database:** initializes the schema and provides SQLite connections.

This separation improves maintainability and allows service behavior to be tested independently of HTTP and SQL details.

## 3. SQLite persistence

SQLite stores data in a local database file and does not require a separate database server. This is practical for local development, automated tests, and project demonstrations.

The database defaults to `data/marquee.db`. The `MARQUEE_DB` environment variable can override the path.

**Trade-off:** a high-concurrency or multi-instance production deployment may require a server database and a deployment-specific connection strategy.

## 4. Many-to-many film and genre relationship

A film can have several genres, and a genre can belong to several films. The `film_genres` junction table represents this many-to-many relationship.

Its composite primary key, `(film_id, genre_id)`, prevents duplicate associations between the same film and genre.

## 5. Foreign keys and cascading deletes

The application enables `PRAGMA foreign_keys = ON` for each database connection.

Foreign keys prevent associations and screenings from referring to nonexistent records. `ON DELETE CASCADE` automatically removes a film's screening and association records when the film is deleted. Deleting a genre removes its association records.

This helps prevent orphaned database rows.

## 6. Parameterized SQL

Repository queries use parameterized SQL for data values. This keeps user input separate from SQL syntax and helps prevent SQL injection in CRUD operations and filters.

## 7. Transactions for film changes

Film creation and updates coordinate film records with their genre associations. Transactions keep these related changes consistent if an operation fails partway through.

## 8. Pagination

Film and screening lists use page-based pagination.

- Default page: `1`
- Default page size: `20`
- Maximum page size: `100`

Responses include the requested page, page size, total matching records, and the current page's results. This avoids requiring clients to retrieve the entire catalogue for every request.

## 9. HTTP status codes

The API uses conventional status codes: `200` for successful retrieval or update, `201` for creation, `204` for deletion, `400` for invalid input, `404` for missing resources, `409` for duplicate genre names, and `405` for unsupported methods.

Errors currently use an `error.code` and `error.message` envelope. Adding a `details` array remains a follow-up to meet the full project benchmark.

## 10. Seed data

Startup seeds a small catalogue when the films table is empty. The root `seed.sql` provides an extended dataset for demonstrations and testing.

The startup seed and SQL seed currently contain different numbers of records. This is documented so that developers know which dataset to expect.
