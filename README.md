# Marquee

[![Marquee CI](https://github.com/Wikiand/marquee/actions/workflows/ci.yml/badge.svg)](https://github.com/Wikiand/marquee/actions/workflows/ci.yml)

**A lightweight REST API for managing films, genres, and cinema screenings.**

Marquee is a Java 17 backend project built with the JDK's built-in HTTP server, SQLite, and Maven. It provides CRUD operations for films, genres, and screenings, alongside filtering, pagination, input validation, and film–genre association management.

The project focuses on clean backend architecture, relational data integrity, and automated testing without requiring a separate application server or database server.

## Features

- **Film management** — create, retrieve, update, and delete films.
- **Genre management** — manage genres and prevent duplicate genre names.
- **Screening management** — manage screening schedules and associate screenings with films.
- **Film–genre associations** — assign and remove genres from films.
- **Filtering and pagination** — filter film and screening listings using query parameters.
- **Input validation** — validate required fields, identifiers, dates, and pagination values.
- **Relational integrity** — use SQLite foreign keys and cascading deletes to maintain relationships.
- **Safer database access** — use parameterized SQL for query values.
- **Automated testing** — verify application behaviour with JUnit tests and a Postman collection.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 17 | Application language and runtime |
| JDK HttpServer | HTTP request handling |
| SQLite | Relational database |
| JDBC | Database connectivity |
| Maven | Build and dependency management |
| Jackson | JSON parsing and serialization |
| JUnit 5 | Automated testing |
| Postman | API testing |


## Architecture

Marquee uses a layered architecture to separate HTTP handling, business logic, and database operations.

- **Handlers** — receive HTTP requests and return JSON responses.
- **Services** — validate input and enforce business rules.
- **Repositories** — execute database queries and manage persistence.
- **Models** — represent films, genres, screenings, and input data.
- **Database** — initialize SQLite and manage database connections.

This separation makes the application easier to maintain, test, and extend.

## Prerequisites

Before running Marquee, make sure you have:

- Java Development Kit (JDK) 17 or later
- Apache Maven
- Git (to clone the repository)
- Postman or `curl` for testing the API

## Installation and Setup

Clone the repository and enter the project directory:

    git clone https://github.com/Wikiand/marquee.git
    cd marquee

Build the project and run the automated tests:

    mvn clean test

Start the API:

    mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
    java -cp "target/classes:$(cat target/classpath.txt)" com.marquee.Main

The API listens on port `8080` by default. Check its health using:

    curl -i http://localhost:8080/health

### Database and Seed Data

On first startup, Marquee creates the database schema but leaves the
catalogue empty. Load sample data separately using the `seed.sql` instructions
below when you want a populated catalogue.

To load the complete catalogue from `seed.sql`:

1. Start Marquee once to initialize the database.
2. Stop the API with `Ctrl+C`.
3. Run the seed script against the initialized database:

       sqlite3 data/marquee.db < seed.sql

4. Restart Marquee.

The SQL seed includes 24 films, 8 genres, their genre associations, and
30 screenings. It uses explicit IDs and conflict handling so existing
matching records are not inserted again. It does not overwrite existing
records with the same IDs.

If you configure a different database path with the `MARQUEE_DB`
environment variable, use that path in the `sqlite3` command instead.

## API Reference

Base URL: `http://localhost:8080`

### Health

| Method | Endpoint | Description |
|---|---|---|
| GET | `/health` | Check API health |

### Films

| Method | Endpoint | Description |
|---|---|---|
| GET | `/films` | List films with filtering and pagination |
| POST | `/films` | Create a film |
| GET | `/films/{id}` | Retrieve a film |
| PUT | `/films/{id}` | Update a film |
| DELETE | `/films/{id}` | Delete a film |
| POST | `/films/{id}/genres` | Assign a genre to a film |
| DELETE | `/films/{id}/genres/{genreId}` | Remove a genre from a film |

### Genres

| Method | Endpoint | Description |
|---|---|---|
| GET | `/genres` | List genres |
| POST | `/genres` | Create a genre |
| GET | `/genres/{id}` | Retrieve a genre |
| PUT | `/genres/{id}` | Update a genre |
| DELETE | `/genres/{id}` | Delete a genre |

### Screenings

| Method | Endpoint | Description |
|---|---|---|
| GET | `/screenings` | List screenings with filtering and pagination |
| POST | `/screenings` | Create a screening |
| GET | `/screenings/{id}` | Retrieve a screening |
| PUT | `/screenings/{id}` | Update a screening |
| DELETE | `/screenings/{id}` | Delete a screening |

### Query Parameters

The `GET /films` endpoint supports:

| Parameter | Description |
|---|---|
| `title` | Filter films by title |
| `genre` | Filter films by genre |
| `year` | Filter films by release year |
| `page` | Page number (default: `1`) |
| `per_page` | Results per page (default: `20`, maximum: `100`) |

The `GET /screenings` endpoint supports:

| Parameter | Description |
|---|---|
| `film_id` | Filter screenings by film ID |
| `venue` | Filter screenings by venue |
| `date` | Filter screenings by date |
| `page` | Page number (default: `1`) |
| `per_page` | Results per page (default: `20`, maximum: `100`) |

Example requests:

    GET /films?genre=Drama&page=1&per_page=10
    GET /screenings?venue=Nairobi&date=2026-10-19

## Example Requests

Check API health:

    curl http://localhost:8080/health

List films:

    curl http://localhost:8080/films

Filter films by title and paginate results:

    curl "http://localhost:8080/films?title=Matrix&page=1&per_page=10"

List genres:

    curl http://localhost:8080/genres

List screenings at a venue:

    curl "http://localhost:8080/screenings?venue=Nairobi"

Create a genre:

    curl -X POST http://localhost:8080/genres \
      -H "Content-Type: application/json" \
      -d '{"name":"Science Fiction"}'

For complete request examples, see the Postman collection in `postman/Marquee.postman_collection.json`.

## Database Configuration

Marquee uses SQLite, so a separate database server is not required.

By default, the application uses `data/marquee.db`. You can override this location with the `MARQUEE_DB` environment variable.

Example:

    MARQUEE_DB=/tmp/marquee.db java -cp "target/classes:$(cat target/classpath.txt)" com.marquee.Main

Ensure the database directory exists and is writable before starting the application.

## Automated Testing

Marquee uses JUnit 5 to test HTTP handlers and service-layer logic.

The suite covers CRUD operations, filtering, pagination, input validation, film-genre associations, and error handling.

Run the complete verification suite:

```bash
mvn --batch-mode clean verify
```

Latest verified local result: **121 tests passed, 0 failures, 0 errors, 0 skipped.**

See the [Testing Guide](docs/testing.md) for additional instructions.

## Project Documentation

- [API Reference](docs/api.md) — detailed endpoint documentation.

- [Testing Guide](docs/testing.md) — instructions for running and testing the API.
- [Design Decisions](docs/design-decisions.md) — architectural and implementation decisions.
- [Entity Relationship Diagram](docs/erd.mmd) — database relationships.
- [Postman Collection](postman/Marquee.postman_collection.json) — API requests for manual testing.

## Limitations

Marquee is a backend API project. It does not currently include a frontend or built-in authentication and authorization.

