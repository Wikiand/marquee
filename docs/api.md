# Marquee API Reference

Marquee is a REST-style HTTP API for managing films, genres, film-genre
associations, and cinema screenings.

## Base URL

```text
http://localhost:8080
```

All request and response bodies use JSON where applicable. Successful
responses and errors include `Cache-Control: no-store`.

## Common conventions

### Pagination

The film-list and screening-list endpoints support pagination.

| Parameter | Default | Rules |
|---|---:|---|
| `page` | `1` | Must be a positive integer |
| `per_page` | `20` | Must be a positive integer; maximum `100` |

Paginated responses include `page`, `per_page`, `total`, and the relevant
resource array.

### Error format

Errors use the following JSON structure:

```json
{
  "error": {
    "code": 400,
    "message": "Invalid request",
    "details": []
  }
}
```

The `code` field matches the HTTP status. The `details` array is currently
empty.

## Health

### Check API health

`GET /health`

Example response — `200 OK`:

```json
{
  "status": "ok",
  "service": "Marquee"
}
```

## Films

### List films

`GET /films`

Optional query parameters:

| Parameter | Description |
|---|---|
| `genre` | Filter by genre name, case-insensitively |
| `title` | Filter by title substring, case-insensitively |
| `year` | Filter by exact release year |
| `page` | Page number; default `1` |
| `per_page` | Results per page; default `20`, maximum `100` |

Example request:

```http
GET /films?genre=Drama&year=2019&page=1&per_page=10
```

Example response — `200 OK`:

```json
{
  "page": 1,
  "per_page": 10,
  "total": 1,
  "films": [
    {
      "filmId": 4,
      "title": "Parasite",
      "year": 2019,
      "createdAt": "2026-10-14T12:38:42Z",
      "genres": [
        {"genreId": 3, "name": "Drama"},
        {"genreId": 8, "name": "Thriller"}
      ]
    }
  ]
}
```

Film-list responses omit `description`. A valid filter with no matches
returns `200 OK` with an empty `films` array.

Status codes:
- `200 OK` — request successful.
- `400 Bad Request` — invalid query parameters, year, or pagination values.
- `500 Internal Server Error` — database operation failed.

### Get a film

`GET /films/{id}`

Example response — `200 OK`:

```json
{
  "filmId": 4,
  "title": "Parasite",
  "year": 2019,
  "description": "A family becomes involved with a wealthy household.",
  "createdAt": "2026-10-14T12:38:42Z",
  "genres": [
    {"genreId": 3, "name": "Drama"},
    {"genreId": 8, "name": "Thriller"}
  ]
}
```

The description above is illustrative; the returned value depends on the
stored record.

Status codes:
- `200 OK` — film found.
- `400 Bad Request` — invalid film ID.
- `404 Not Found` — film does not exist.
- `500 Internal Server Error` — database operation failed.

### Create a film

`POST /films`

Example request body:

```json
{
  "title": "Arrival",
  "year": 2016,
  "description": "A linguist works to communicate with visitors from space.",
  "genreIds": [3, 4]
}
```

`genreIds` is optional. When provided, it assigns the listed existing genres
to the film.

Example response — `201 Created`: returns the created film, including its
ID, description, creation timestamp, and genre associations.

Status codes:
- `201 Created` — film created.
- `400 Bad Request` — malformed JSON, invalid fields, or invalid genre IDs.
- `500 Internal Server Error` — database operation failed.

### Update a film

`PUT /films/{id}`

Example request body:

```json
{
  "title": "Arrival",
  "year": 2016,
  "description": "Updated film description.",
  "genreIds": [3, 4]
}
```

Supply the film's mutable fields. `genreIds` is optional; when provided,
it replaces the film's existing genre associations.

Status codes:
- `200 OK` — film updated; returns the updated film.
- `400 Bad Request` — invalid ID, malformed JSON, or invalid fields.
- `404 Not Found` — film does not exist.
- `500 Internal Server Error` — database operation failed.

### Delete a film

`DELETE /films/{id}`

Status codes:
- `204 No Content` — film deleted successfully.
- `400 Bad Request` — invalid film ID.
- `404 Not Found` — film does not exist.
- `500 Internal Server Error` — database operation failed.

Deleting a film also removes its genre associations and screenings through
the database's cascading foreign-key constraints.

### Assign a genre to a film

`POST /films/{id}/genres`

Example request body:

```json
{
  "genre_id": 3
}
```

Example response — `201 Created`:

```json
{
  "filmId": 4,
  "genreId": 3,
  "message": "Genre assigned to film"
}
```

Status codes:
- `201 Created` — association created.
- `400 Bad Request` — invalid ID or request body.
- `404 Not Found` — film or genre does not exist.
- `409 Conflict` — genre is already assigned to the film.
- `500 Internal Server Error` — database operation failed.

### Remove a genre from a film

`DELETE /films/{id}/genres/{genre_id}`

Status codes:
- `204 No Content` — association removed.
- `400 Bad Request` — invalid ID.
- `404 Not Found` — film, genre, or association does not exist.
- `500 Internal Server Error` — database operation failed.

## Genres

### List genres

`GET /genres`

Example response — `200 OK`:

```json
[
  {"genreId": 1, "name": "Action"},
  {"genreId": 3, "name": "Drama"}
]
```

The response contains the stored genres, ordered by name.

Status codes:
- `200 OK` — request successful.
- `500 Internal Server Error` — database operation failed.

### Get a genre

`GET /genres/{id}`

Status codes:
- `200 OK` — genre found; returns the genre object.
- `400 Bad Request` — invalid genre ID.
- `404 Not Found` — genre does not exist.
- `500 Internal Server Error` — database operation failed.

### Create a genre

`POST /genres`

Example request body:

```json
{
  "name": "Documentary"
}
```

Example response — `201 Created`: returns the created genre object.

Status codes:
- `201 Created` — genre created.
- `400 Bad Request` — malformed JSON or invalid name.
- `409 Conflict` — genre name already exists.
- `500 Internal Server Error` — database operation failed.

### Update a genre

`PUT /genres/{id}`

Example request body:

```json
{
  "name": "Documentary"
}
```

Status codes:
- `200 OK` — genre updated; returns the updated genre object.
- `400 Bad Request` — invalid ID, malformed JSON, or invalid name.
- `404 Not Found` — genre does not exist.
- `409 Conflict` — another genre already uses that name.
- `500 Internal Server Error` — database operation failed.

### Delete a genre

`DELETE /genres/{id}`

Status codes:
- `204 No Content` — genre deleted successfully.
- `400 Bad Request` — invalid genre ID.
- `404 Not Found` — genre does not exist.
- `500 Internal Server Error` — database operation failed.

Deleting a genre also removes its film-genre associations.

## Screenings

### List screenings

`GET /screenings`

Optional query parameters:

| Parameter | Description |
|---|---|
| `film_id` | Filter by film ID |
| `venue` | Filter by venue |
| `date` | Filter by screening date |
| `page` | Page number; default `1` |
| `per_page` | Results per page; default `20`, maximum `100` |

Example request:

```http
GET /screenings?film_id=3&page=1&per_page=10
```

Example response — `200 OK`:

```json
{
  "page": 1,
  "per_page": 10,
  "total": 1,
  "screenings": [
    {
      "screeningId": 3,
      "filmId": 3,
      "filmTitle": "Inception",
      "venue": "Main Hall",
      "screeningTime": "2026-10-22T19:00:00Z"
    }
  ]
}
```

A valid filter with no matches returns `200 OK` with an empty `screenings`
array.

Status codes:
- `200 OK` — request successful.
- `400 Bad Request` — invalid or unsupported query parameters.
- `500 Internal Server Error` — database operation failed.

### Get a screening

`GET /screenings/{id}`

Status codes:
- `200 OK` — screening found; returns the screening object.
- `400 Bad Request` — invalid screening ID.
- `404 Not Found` — screening does not exist.
- `500 Internal Server Error` — database operation failed.

### Create a screening

`POST /screenings`

Example request body:

```json
{
  "filmId": 3,
  "venue": "Main Hall",
  "screeningTime": "2026-10-22T19:00:00Z"
}
```

Example response — `201 Created`: returns the created screening object,
including its ID and associated film title.

Status codes:
- `201 Created` — screening created.
- `400 Bad Request` — malformed JSON or invalid fields.
- `404 Not Found` — referenced film does not exist.
- `500 Internal Server Error` — database operation failed.

### Update a screening

`PUT /screenings/{id}`

Example request body:

```json
{
  "filmId": 3,
  "venue": "Studio A",
  "screeningTime": "2026-10-22T20:00:00Z"
}
```

Status codes:
- `200 OK` — screening updated; returns the updated screening object.
- `400 Bad Request` — invalid ID, malformed JSON, or invalid fields.
- `404 Not Found` — screening or referenced film does not exist.
- `500 Internal Server Error` — database operation failed.

### Delete a screening

`DELETE /screenings/{id}`

Status codes:
- `204 No Content` — screening deleted successfully.
- `400 Bad Request` — invalid screening ID.
- `404 Not Found` — screening does not exist.
- `500 Internal Server Error` — database operation failed.

## Additional notes

- IDs in URL paths must be valid positive integers.
- JSON property names are case-sensitive.
- Unknown or unsupported query parameters may return `400 Bad Request`.
- The API does not currently provide authentication or authorization.
- Refer to `testing.md` for test execution and Postman collection instructions.
