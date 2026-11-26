# Marquee

**A Java-based cinema management application with a browser interface and a REST-style JSON API.**

Marquee is an application for managing films, genres, film–genre relationships, and cinema screenings. It brings together a browser-based experience and a JSON API, allowing people to explore cinema information through a web browser while also providing programmatic access to application resources.

The project is built with Java 17, SQLite, JDBC, Maven, and Thymeleaf. Its architecture separates HTTP handling, business logic, database access, and presentation. It also incorporates database migrations, input validation, structured request logging, automated testing, and continuous integration.

## Table of contents

- [Overview](#overview)
- [Features](#features)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Getting started](#getting-started)
- [Configuration and database](#configuration-and-database)
- [Using the application](#using-the-application)
- [API reference](#api-reference)
- [Validation and error handling](#validation-and-error-handling)
- [Logging and reliability](#logging-and-reliability)
- [Testing and code quality](#testing-and-code-quality)
- [Continuous integration](#continuous-integration)
- [Project documentation](#project-documentation)

## Overview

Marquee is designed around three main areas:

1. **Film catalogue:** Create, browse, search, update, and delete film records.
2. **Genre management:** Maintain genres and associate them with films.
3. **Screening management:** Manage cinema screenings and retrieve them using useful filters.

The application supports both HTML and JSON responses on supported routes. The browser interface uses server-rendered Thymeleaf templates, while API clients can interact with resources using HTTP requests.

The browser-facing experience developed as part of the Encore work complements the existing management API. Both use the same application services and database.

## Features

### Film management

- Create, retrieve, update, and delete films.
- Browse the film catalogue through the browser.
- Filter films by title, genre, and release year.
- View individual film details.
- Associate films with multiple genres.
- Retrieve paginated film collections through the API.

### Genre management

- Create, retrieve, update, and delete genres.
- List available genres.
- Assign genres to films.
- Remove genre associations.
- Prevent duplicate genre names and duplicate film–genre associations.

### Screening management

- Create, retrieve, update, and delete screenings.
- Browse screenings through the browser interface.
- Filter screenings by film, venue, and date.
- Retrieve paginated screening collections through the API.
- Associate screenings with existing films.

### Browser interface

- Server-rendered pages using Thymeleaf.
- Homepage and application navigation.
- Film catalogue.
- Individual film detail pages.
- Screening listings.
- Static stylesheets and image resources.
- HTML and JSON content negotiation on supported routes.

### Engineering and reliability

- Layered application architecture.
- Prepared SQL statements for database operations.
- Version-controlled database migrations.
- Request-body validation and size limits.
- Consistent JSON error responses.
- Structured JSON request logging.
- Centralised handling of unexpected server errors.
- Automated unit and integration tests.
- JaCoCo test coverage reporting.
- Checkstyle code-quality checks.
- Maven-based build and verification.
- Continuous integration through GitHub Actions.

## Technology stack

| Technology | Purpose |
|---|---|
| Java 17 | Application language |
| Maven | Build and dependency management |
| SQLite | Relational database |
| JDBC | Database connectivity |
| Jackson | JSON processing |
| Thymeleaf | Server-side HTML rendering |
| JUnit 5 | Automated testing |
| JaCoCo | Test coverage reporting |
| Checkstyle | Code style and static checks |
| GitHub Actions | Continuous integration |

## Architecture

Marquee uses a layered architecture to keep responsibilities separate and make the application easier to maintain and test.

```text
src/
├── main/
│   ├── java/com/marquee/
│   │   ├── database/       # SQLite setup and migration runner
│   │   ├── handler/        # HTTP route handlers
│   │   ├── http/           # Request parsing, logging, and error handling
│   │   ├── model/          # Application data models
│   │   ├── repository/     # SQL queries and persistence
│   │   ├── service/        # Application rules and validation
│   │   ├── web/            # HTML rendering and static resources
│   │   └── Main.java       # HTTP server entry point
│   │
│   └── resources/
│       ├── static/
│       └── templates/
│
└── test/
    └── java/com/marquee/   # Automated tests

migrations/
├── 001_create_films_and_genres.sql
├── 002_create_film_genres.sql
├── 003_create_screenings.sql
├── 004_create_database_indexes.sql
└── 005_add_screening_room.sql

config/
└── checkstyle/
    └── checkstyle.xml

docs/
├── adr/
├── api.md
├── design-decisions.md
├── erd.mmd
├── retrospective.md
└── testing.md

postman/
└── Marquee.postman_collection.json
```

### Layer responsibilities

| Layer | Responsibility |
|---|---|
| Handler | Receives HTTP requests and builds HTTP responses. |
| Service | Applies business rules and validates application operations. |
| Repository | Executes database queries and persistence operations. |
| Model | Represents application data. |
| Database | Manages SQLite connections and schema migrations. |
| HTTP | Handles request-body reading, request logging, and server errors. |
| Web | Handles HTML templates, content negotiation, and static resources. |

### Request lifecycle

A typical request follows this sequence:

1. The HTTP server receives a request.
2. The relevant handler interprets the route, parameters, and request body.
3. The service layer validates input and applies business rules.
4. The repository performs the required database operation.
5. The handler returns an appropriate HTML or JSON response.
6. The request logging filter records the request outcome and duration.

This separation allows individual application components to be tested without requiring every test to exercise the entire HTTP server.

## Requirements

Before running the project, install:

- Java Development Kit (JDK) 17.
- Apache Maven.
- Git.

Verify the installations:

```bash
java -version
mvn -version
git --version
```

## Getting started

### 1. Open the project directory

If you already have the source code:

```bash
cd ~/marquee
```

If you are starting from a repository checkout, clone the repository using its configured remote URL and enter the project directory.

### 2. Build and verify the application

Run:

```bash
mvn clean verify
```

This compiles the application, runs the configured tests and code-quality checks, and generates the JaCoCo coverage report.

### 3. Build the runtime classpath

Run:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

### 4. Start the application

Run:

```bash
java -cp "target/classes:$(cat target/classpath.txt)" com.marquee.Main
```

The HTTP server listens on port `8080`.

Open the browser:

```text
http://localhost:8080
```

Check the application health:

```bash
curl http://localhost:8080/health
```

To stop the application, press `Ctrl+C` in the terminal running the server.

## Configuration and database

Marquee uses SQLite for persistent storage.

The default database location is:

```text
data/marquee.db
```

The database location can be changed using the `MARQUEE_DB` environment variable.

For example:

```bash
MARQUEE_DB=/tmp/marquee-test.db \
java -cp "target/classes:$(cat target/classpath.txt)" com.marquee.Main
```

### Database migrations

Database schema changes are managed through versioned SQL migrations.

The migration runner:

- Tracks migrations that have already been applied.
- Applies outstanding migrations in version order.
- Maintains a migration history table.
- Supports controlled schema evolution.
- Helps detect incompatible database states.

This approach avoids relying on manually recreating the database whenever the application schema changes.

## Using the application

The browser interface provides the following main pages:

| Page | Route | Description |
|---|---|---|
| Homepage | `/` | Application entry point and navigation. |
| Film catalogue | `/films` | Browse and filter films. |
| Film details | `/films/{id}` | View a film, its genres, and associated screenings. |
| Screenings | `/screenings` | Browse and filter cinema screenings. |

The film collection and film detail routes support content negotiation.

For example, a browser can request HTML:

```bash
curl -H "Accept: text/html" http://localhost:8080/films
```

An API client can request JSON:

```bash
curl -H "Accept: application/json" http://localhost:8080/films
```

The response format depends on the route's supported content negotiation behaviour.

## API reference

The API base URL for local development is:

```text
http://localhost:8080
```

### Main endpoints

| Resource | HTTP method | Endpoint |
|---|---|---|
| Health | GET | `/health` |
| Films | GET, POST | `/films` |
| Individual film | GET, PUT, DELETE | `/films/{id}` |
| Add film genre | POST | `/films/{id}/genres` |
| Remove film genre | DELETE | `/films/{id}/genres/{genre_id}` |
| Genres | GET, POST | `/genres` |
| Individual genre | GET, PUT, DELETE | `/genres/{id}` |
| Screenings | GET, POST | `/screenings` |
| Individual screening | GET, PUT, DELETE | `/screenings/{id}` |

### Example requests

Check application health:

```bash
curl http://localhost:8080/health
```

Retrieve films:

```bash
curl -H "Accept: application/json" \
http://localhost:8080/films
```

Search films by title:

```bash
curl -G \
-H "Accept: application/json" \
--data-urlencode "title=matrix" \
http://localhost:8080/films
```

Filter films by release year:

```bash
curl -G \
--data-urlencode "year=2024" \
http://localhost:8080/films
```

Retrieve genres:

```bash
curl http://localhost:8080/genres
```

Filter screenings by venue:

```bash
curl -G \
--data-urlencode "venue=IMAX" \
http://localhost:8080/screenings
```

### Pagination

Film and screening collection endpoints support pagination.

| Parameter | Description |
|---|---|
| `page` | Page number; defaults to 1. |
| `per_page` | Number of records per page; defaults to 20 and has a maximum of 100. |

Paginated responses include pagination metadata and the relevant resource collection.

For complete request bodies, response examples, supported filters, and endpoint behaviour, see [`docs/api.md`](docs/api.md).

## Validation and error handling

The application validates incoming data before performing operations.

Examples include:

- Required fields must be provided.
- Resource identifiers must be valid positive integers.
- Referenced films and genres must exist.
- Duplicate genre names are rejected.
- Duplicate film–genre associations are rejected.
- Invalid query parameters are rejected where the API defines a supported parameter set.

Errors use a consistent JSON structure:

```json
{
  "error": {
    "code": 400,
    "message": "Invalid request",
    "details": []
  }
}
```

The error code corresponds to the HTTP status code.

The application uses appropriate HTTP responses for invalid requests, missing resources, conflicts, and unexpected server failures.

Internal exception details are not exposed in generic server-error responses.

## Logging and reliability

Marquee includes structured request logging through its HTTP logging filter.

Each request log records:

- `timestamp`
- `method`
- `path`
- `status`
- `duration_ms`

Logs are written in JSON format to make them easier to inspect and process.

Request bodies are not included in the request log.

Unexpected server errors are handled separately so that useful diagnostic information can be recorded without exposing internal implementation details to API clients.

Database operations use prepared statements rather than building SQL queries through direct string concatenation.

## Testing and code quality

Automated tests help verify application behaviour across the database, services, handlers, migrations, and HTTP-related functionality.

Run the complete verification lifecycle:

```bash
mvn clean verify
```

Run tests only:

```bash
mvn test
```

Run a specific test class:

```bash
mvn -Dtest=MigrationRunnerTest test
```

Run a specific test method:

```bash
mvn -Dtest=FilmHandlerTest#unexpectedDatabaseFailureReturnsGeneric500AndLogsDetails test
```

### JaCoCo coverage report

After running verification, open:

```text
target/site/jacoco/index.html
```

The report provides coverage information for the tested application code.

### Checkstyle

The Checkstyle configuration is located at:

```text
config/checkstyle/checkstyle.xml
```

It is integrated into the Maven build and checks both application and test source code.

### Postman collection

The project includes a Postman collection:

```text
postman/Marquee.postman_collection.json
```

Import it into Postman to exercise the API.

The collection uses the following default base URL:

```text
http://localhost:8080
```

## Continuous integration

The GitHub Actions workflow is located at:

```text
.github/workflows/ci.yml
```

The workflow runs Maven verification on pushes and pull requests targeting the configured main branches.

It also:

- Uses Java 17.
- Runs the clean Maven verification lifecycle.
- Produces JaCoCo coverage information.
- Adds coverage information to the workflow summary.
- Uploads the HTML coverage report as a build artifact.

This provides an automated verification process for changes made to the project.

## Project documentation

Additional technical information is available in the `docs/` directory.

| Document | Description |
|---|---|
| `docs/api.md` | API endpoints, parameters, requests, and responses. |
| `docs/testing.md` | Testing instructions and verification guidance. |
| `docs/design-decisions.md` | Application design decisions. |
| `docs/erd.mmd` | Entity relationship diagram. |
| `docs/retrospective.md` | Project retrospective. |
| `docs/adr/` | Architecture decision records. |

The architecture decision records document important engineering decisions, including database migrations, structured logging, continuous integration, and automated testing.

## Project summary

Marquee brings together:

- A browser-based cinema catalogue and screening interface.
- A JSON API for managing cinema resources.
- A layered Java application architecture.
- SQLite persistence and versioned schema migrations.
- Input validation and consistent error handling.
- Structured request and server-error logging.
- Automated testing, code-quality checks, and CI verification.
---
