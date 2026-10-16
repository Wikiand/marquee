# Marquee API

Marquee is a Java 17 HTTP API for managing films, genres, film–genre relationships, and cinema screenings. It uses Java's built-in `HttpServer`, SQLite, Jackson, and Maven.

## Features

- Film CRUD with genre, title, and year filters.
- Genre CRUD with duplicate-name protection.
- Screening CRUD with film, venue, and date filters.
- Pagination for film and screening lists.
- SQLite foreign keys and cascading deletes.
- Automated tests for handlers and services.

## Requirements

- JDK 17 or newer
- Maven
- SQLite CLI (`sqlite3`) for loading extended seed data

## Build and test

```bash
mvn test
mvn compile
```

## Run the application

```bash
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" com.marquee.Main
```

The server listens on port `8080`. The database defaults to `data/marquee.db`. Override it with `MARQUEE_DB`:

```bash
MARQUEE_DB=/tmp/marquee.db java -cp "target/classes:$(cat target/classpath.txt)" com.marquee.Main
```

Check the service:

```bash
curl http://localhost:8080/health
```

Expected response:

```json
{"status":"ok","service":"Marquee"}
```

## Seed data

On an empty database, startup creates the schema and seeds 8 genres, 5 films, their associations, and 5 screenings.

The root `seed.sql` provides an extended catalogue. To load it:

1. Start the application once to initialize the database.
2. Stop the application.
3. Run `sqlite3 data/marquee.db < seed.sql`.
4. Restart the application.

The script uses explicit IDs and conflict handling to avoid duplicating existing records.

## API conventions

Base URL: `http://localhost:8080`

| Status | Meaning |
|---|---|
| 200 | Successful retrieval or update |
| 201 | Resource created |
| 204 | Resource deleted; no response body |
| 400 | Invalid input |
| 404 | Resource or route not found |
| 405 | HTTP method not supported |
| 409 | Duplicate genre name |

Errors use this envelope:

```json
{"error":{"code":400,"message":"Invalid JSON request body"}}
```

The current implementation does not include a `details` array.

## Films

| Method | Endpoint | Description |
|---|---|---|
| GET | `/films` | List and filter films |
| POST | `/films` | Create a film |
| GET | `/films/{filmId}` | Get a film |
| PUT | `/films/{filmId}` | Update a film |
| DELETE | `/films/{filmId}` | Delete a film |

List filters: `genre`, `title`, `year`, `page`, `per_page`.

Pagination defaults to page `1` and `20` items per page; the maximum page size is `100`.

Example:

```bash
curl "http://localhost:8080/films?genre=Drama&year=2019&page=1&per_page=10"
```

Create a film:

```bash
curl -X POST http://localhost:8080/films \
  -H "Content-Type: application/json" \
  -d '{"title":"Example Film","year":2024,"description":"A sample film.","genreIds":[1,3]}'
```

Film `title` and `year` are required. `description` is optional. `genreIds` may be omitted or set to an array of existing genre IDs. During updates, omitted or null `genreIds` preserves existing associations; an empty array clears them.

Film lists omit descriptions; retrieving a film by ID includes the description.

## Genres

| Method | Endpoint | Description |
|---|---|---|
| GET | `/genres` | List genres |
| POST | `/genres` | Create a genre |
| GET | `/genres/{genreId}` | Get a genre |
| PUT | `/genres/{genreId}` | Update a genre |
| DELETE | `/genres/{genreId}` | Delete a genre |

Create a genre:

```bash
curl -X POST http://localhost:8080/genres \
  -H "Content-Type: application/json" \
  -d '{"name":"Adventure"}'
```

Genre names must not be blank. Duplicate names return `409 Conflict`.

## Screenings

| Method | Endpoint | Description |
|---|---|---|
| GET | `/screenings` | List and filter screenings |
| POST | `/screenings` | Create a screening |
| GET | `/screenings/{screeningId}` | Get a screening |
| PUT | `/screenings/{screeningId}` | Update a screening |
| DELETE | `/screenings/{screeningId}` | Delete a screening |

List filters: `film_id`, `venue`, `date`, `page`, `per_page`. Date filters use `YYYY-MM-DD`. Pagination uses the same defaults and maximum as films.

Example:

```bash
curl "http://localhost:8080/screenings?film_id=1&date=2026-10-20"
```

Create a screening:

```bash
curl -X POST http://localhost:8080/screenings \
  -H "Content-Type: application/json" \
  -d '{"filmId":1,"venue":"Main Hall","screeningTime":"2026-12-01T19:30:00Z"}'
```

The `filmId`, `venue`, and `screeningTime` fields are required. The referenced film must exist. `screeningTime` is checked for non-blank content but is not currently validated against a strict timestamp format.

## Pagination response

Film list responses contain `page`, `per_page`, `total`, and `films`. Screening list responses contain `page`, `per_page`, `total`, and `screenings`. The `total` is the number of matching records before pagination; empty results return an empty array.

## Database design

The schema contains:

- `films` — film details.
- `genres` — unique genre names.
- `film_genres` — junction table for the many-to-many film/genre relationship.
- `screenings` — screenings associated with a film.

See [`docs/erd.mmd`](docs/erd.mmd) for the ER diagram and [`docs/design-decisions.md`](docs/design-decisions.md) for design rationale.

## Project structure

```text
src/main/java/com/marquee/
├── Main.java
├── database/
├── handler/
├── model/
├── repository/
└── service/
src/test/java/com/marquee/
├── handler/
└── service/
docs/
seed.sql
```

## Known follow-ups

- Add a `details` array to error responses to meet the full benchmark.
- Keep startup seed data and the extended SQL seed data synchronized.
- Add a saved API client collection and dedicated API testing instructions.

