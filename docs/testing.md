# API Testing

## Prerequisites

- Java 17 or compatible JDK
- Maven
- Postman

## Run automated tests

From the repository root:

```bash
mvn test
```

## Start the API

Build the runtime classpath:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

Start Marquee:

```bash
java -cp "target/classes:$(cat target/classpath.txt)" com.marquee.Main
```

The API listens at `http://localhost:8080`.

Check health:

```bash
curl -i http://localhost:8080/health
```

## Run the Postman collection

1. Open Postman and choose **Import**.
2. Import `postman/Marquee.postman_collection.json`.
3. Keep the `baseUrl` collection variable set to `http://localhost:8080`, unless the server uses another address.
4. Start Marquee.
5. Run the collection using Postman's Collection Runner.

The collection contains 24 requests covering the API endpoints and their expected responses. Run the collection against a running instance of Marquee.

## API conventions

- Film endpoints use `/films`; genre endpoints use `/genres`; screening endpoints use `/screenings`.
- Film filtering supports `genre`, `title`, `year`, `page`, and `per_page`.
- Screening filtering supports `film_id`, `venue`, `date`, `page`, and `per_page`.
- Film genre associations can be managed through `POST /films/{id}/genres` with a `genre_id` request field and `DELETE /films/{id}/genres/{genre_id}`. Film creation and updates also accept `genreIds`.
- Error responses use an `error` object containing `code`, `message`, and `details`.

## Troubleshooting

- If Postman reports a connection error, check that Marquee is running on the configured port.
- If a response differs from an assertion, compare it with the handler implementation and update the test to match the intended API contract.
