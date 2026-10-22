package com.marquee.handler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.database.Database;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.GenreRepository;
import com.marquee.repository.ScreeningRepository;
import com.marquee.service.FilmService;
import com.marquee.service.GenreService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class FilmHandlerTest {

    @TempDir
    Path tempDir;

    private Database database;
    private HttpServer server;
    private HttpClient client;
    private ObjectMapper mapper;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(tempDir.resolve("films.db"));
        database.initialize();

        FilmService service = new FilmService(new FilmRepository(database));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/films", new FilmHandler(service));
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();

        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = HttpClient.newHttpClient();
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void createFilmReturns201AndCreatedFilm() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/films",
            "{\"title\":\"Arrival\",\"year\":2016,\"description\":\"Sci-fi film\"}"
        );

        assertEquals(201, response.statusCode());
        assertEquals("Arrival", json(response).get("title").asText());
        assertEquals(2016, json(response).get("year").asInt());
        assertTrue(json(response).get("filmId").asInt() > 0);
    }

    @Test
    void createFilmTrimsTitle() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/films", "{\"title\":\"  Arrival  \",\"year\":2016}"
        );

        assertEquals(201, response.statusCode());
        assertEquals("Arrival", json(response).get("title").asText());
    }

    @Test
    void createFilmRejectsMalformedJson() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/films", "{\"title\":"
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void createFilmRejectsMissingTitle() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/films", "{\"year\":2016}"
        );

        assertEquals(400, response.statusCode());
        JsonNode error = json(response).get("error");
        assertEquals("Validation failed", error.get("message").asText());
        assertTrue(error.get("details").toString().contains(
            "title is required and cannot be blank"
        ));
    }

    @Test
    void createFilmRejectsInvalidYear() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/films", "{\"title\":\"Arrival\",\"year\":0}"
        );

        assertEquals(400, response.statusCode());
        JsonNode error = json(response).get("error");
        assertEquals("Validation failed", error.get("message").asText());
        assertTrue(error.get("details").toString().contains(
            "year must be a positive integer"
        ));
    }

    @Test
    void listFilmsReturnsPaginationMetadata() throws Exception {
        createFilm("Arrival", 2016);
        createFilm("Dune", 2021);

        HttpResponse<String> response = request(
            "GET", "/films?page=1&per_page=1", null
        );

        assertEquals(200, response.statusCode());
        JsonNode body = json(response);
        assertEquals(1, body.get("page").asInt());
        assertEquals(1, body.get("per_page").asInt());
        assertEquals(2, body.get("total").asInt());
        assertEquals(1, body.get("films").size());
    }

    @Test
    void listFilmsFiltersByYear() throws Exception {
        createFilm("Arrival", 2016);
        createFilm("Dune", 2021);

        HttpResponse<String> response = request(
            "GET", "/films?year=2021", null
        );

        assertEquals(200, response.statusCode());
        JsonNode films = json(response).get("films");
        assertEquals(1, films.size());
        assertEquals("Dune", films.get(0).get("title").asText());
    }

    @Test
    void listFilmsFiltersByTitle() throws Exception {
        createFilm("Arrival", 2016);
        createFilm("Dune", 2021);

        HttpResponse<String> response = request(
            "GET", "/films?title=arri", null
        );

        assertEquals(200, response.statusCode());
        JsonNode films = json(response).get("films");
        assertEquals(1, films.size());
        assertEquals("Arrival", films.get(0).get("title").asText());
    }

    @Test
    void listFilmsRejectsInvalidPagination() throws Exception {
        HttpResponse<String> response = request(
            "GET", "/films?page=0", null
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void listFilmsRejectsUnknownQueryParameter() throws Exception {
        HttpResponse<String> response = request(
            "GET", "/films?unknown=value", null
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void getFilmReturns200ForExistingFilm() throws Exception {
        JsonNode created = createFilm("Arrival", 2016);
        int id = created.get("filmId").asInt();

        HttpResponse<String> response = request("GET", "/films/" + id, null);

        assertEquals(200, response.statusCode());
        assertEquals("Arrival", json(response).get("title").asText());
    }

    @Test
    void getFilmReturns404WhenMissing() throws Exception {
        HttpResponse<String> response = request("GET", "/films/9999", null);

        assertEquals(404, response.statusCode());
        assertTrue(json(response).get("error").get("details").isArray());
    }

    @Test
    void updateFilmChangesFilm() throws Exception {
        JsonNode created = createFilm("Arrival", 2016);
        int id = created.get("filmId").asInt();

        HttpResponse<String> response = request(
            "PUT", "/films/" + id,
            "{\"title\":\"Arrival Updated\",\"year\":2017,\"description\":\"Updated\"}"
        );

        assertEquals(200, response.statusCode());
        assertEquals("Arrival Updated", json(response).get("title").asText());
        assertEquals(2017, json(response).get("year").asInt());
    }

    @Test
    void updateMissingFilmReturns404() throws Exception {
        HttpResponse<String> response = request(
            "PUT", "/films/9999", "{\"title\":\"Missing\",\"year\":2020}"
        );

        assertEquals(404, response.statusCode());
    }

    @Test
    void deleteFilmReturns204AndRemovesFilm() throws Exception {
        JsonNode created = createFilm("Arrival", 2016);
        int id = created.get("filmId").asInt();

        HttpResponse<String> deleted = request("DELETE", "/films/" + id, null);
        HttpResponse<String> fetched = request("GET", "/films/" + id, null);

        assertEquals(204, deleted.statusCode());
        assertTrue(deleted.body().isEmpty());
        assertEquals(404, fetched.statusCode());
    }

    @Test
    void deletingFilmRemovesGenreAssociationsAndScreenings()
            throws Exception {
        int filmId = createFilmId("Cascade Film", 2024);
        int genreId = createGenreId("Cascade Genre");

        HttpResponse<String> assigned = request(
            "POST", "/films/" + filmId + "/genres",
            "{\"genre_id\":" + genreId + "}"
        );
        assertEquals(201, assigned.statusCode());

        new ScreeningRepository(database).create(
            filmId, "Test Cinema", "2026-11-01T19:00:00"
        );

        HttpResponse<String> deleted = request(
            "DELETE", "/films/" + filmId, null
        );
        assertEquals(204, deleted.statusCode());

        try (var connection = database.getConnection();
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM film_genres WHERE film_id = " + filmId)) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }

            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM screenings WHERE film_id = " + filmId)) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test
    void deleteMissingFilmReturns404() throws Exception {
        HttpResponse<String> response = request("DELETE", "/films/9999", null);

        assertEquals(404, response.statusCode());
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        HttpResponse<String> response = request("PATCH", "/films", "{}");

        assertEquals(405, response.statusCode());
    }

    private JsonNode createFilm(String title, int year) throws Exception {
        HttpResponse<String> response = request(
            "POST", "/films",
            mapper.createObjectNode()
                .put("title", title)
                .put("year", year)
                .toString()
        );

        assertEquals(201, response.statusCode());
        return json(response);
    }

    @Test
    void createFilmWithGenresReturnsAssignedGenres() throws Exception {
        GenreService genres = new GenreService(new GenreRepository(database));
        var sciFi = genres.createGenre("Sci-Fi");
        var drama = genres.createGenre("Drama");

        HttpResponse<String> response = request(
            "POST", "/films",
            """
            {"title":"Arrival","year":2016,"genreIds":[%d,%d]}
            """.formatted(sciFi.genreId(), drama.genreId())
        );

        assertEquals(201, response.statusCode());
        JsonNode film = json(response);
        assertEquals(2, film.get("genres").size());
        assertTrue(hasGenre(film, sciFi.genreId(), "Sci-Fi"));
        assertTrue(hasGenre(film, drama.genreId(), "Drama"));
    }

    @Test
    void createFilmWithNonexistentGenreReturnsBadRequest()
            throws Exception {
        HttpResponse<String> response = request(
            "POST", "/films",
            """
            {"title":"Unknown Genre","year":2024,"genreIds":[99999]}
            """
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void createFilmWithDuplicateGenreIdsReturnsBadRequest()
            throws Exception {
        GenreService genres = new GenreService(new GenreRepository(database));
        var genre = genres.createGenre("Thriller");

        HttpResponse<String> response = request(
            "POST", "/films",
            """
            {"title":"Duplicate Genre","year":2024,"genreIds":[%d,%d]}
            """.formatted(genre.genreId(), genre.genreId())
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void updateFilmCanReplaceAndClearGenres() throws Exception {
        GenreService genres = new GenreService(new GenreRepository(database));
        var action = genres.createGenre("Action");
        var comedy = genres.createGenre("Comedy");
        var drama = genres.createGenre("Drama");

        JsonNode created = createFilm("Genre Update", 2024);
        int filmId = created.get("filmId").asInt();

        HttpResponse<String> assignedResponse = request(
            "PUT", "/films/" + filmId,
            """
            {"title":"Genre Update","year":2024,"genreIds":[%d,%d]}
            """.formatted(action.genreId(), comedy.genreId())
        );
        assertEquals(200, assignedResponse.statusCode());
        JsonNode assigned = json(assignedResponse);
        assertTrue(hasGenre(assigned, action.genreId(), "Action"));
        assertTrue(hasGenre(assigned, comedy.genreId(), "Comedy"));

        HttpResponse<String> replacedResponse = request(
            "PUT", "/films/" + filmId,
            """
            {"title":"Genre Update","year":2024,"genreIds":[%d]}
            """.formatted(drama.genreId())
        );
        assertEquals(200, replacedResponse.statusCode());
        JsonNode replaced = json(replacedResponse);
        assertEquals(1, replaced.get("genres").size());
        assertTrue(hasGenre(replaced, drama.genreId(), "Drama"));
        assertFalse(hasGenre(replaced, action.genreId(), "Action"));
        assertFalse(hasGenre(replaced, comedy.genreId(), "Comedy"));

        HttpResponse<String> clearedResponse = request(
            "PUT", "/films/" + filmId,
            """
            {"title":"Genre Update","year":2024,"genreIds":[]}
            """
        );
        assertEquals(200, clearedResponse.statusCode());
        assertEquals(0, json(clearedResponse).get("genres").size());
    }

    @Test
    void assignGenreReturns201() throws Exception {
        int filmId = createFilmId("Arrival Association", 2016);
        int genreId = createGenreId("Sci-Fi Association");

        HttpResponse<String> response = request(
            "POST",
            "/films/" + filmId + "/genres",
            "{\"genre_id\":" + genreId + "}"
        );

        assertEquals(201, response.statusCode());
        assertEquals(filmId, json(response).get("filmId").asInt());
        assertEquals(genreId, json(response).get("genreId").asInt());
    }

    @Test
    void assigningDuplicateGenreReturns409() throws Exception {
        int filmId = createFilmId("Dune Association", 2021);
        int genreId = createGenreId("Drama Association");

        HttpResponse<String> first = request(
            "POST", "/films/" + filmId + "/genres",
            "{\"genre_id\":" + genreId + "}"
        );
        assertEquals(201, first.statusCode());

        HttpResponse<String> second = request(
            "POST", "/films/" + filmId + "/genres",
            "{\"genre_id\":" + genreId + "}"
        );

        assertEquals(409, second.statusCode());
    }

    @Test
    void removingAssignedGenreReturns204() throws Exception {
        int filmId = createFilmId("Removal Association", 2020);
        int genreId = createGenreId("Mystery Association");

        HttpResponse<String> assigned = request(
            "POST", "/films/" + filmId + "/genres",
            "{\"genre_id\":" + genreId + "}"
        );
        assertEquals(201, assigned.statusCode());

        HttpResponse<String> removed = request(
            "DELETE",
            "/films/" + filmId + "/genres/" + genreId,
            null
        );

        assertEquals(204, removed.statusCode());
        assertTrue(removed.body().isEmpty());
    }

    @Test
    void assigningGenreToMissingFilmReturns404() throws Exception {
        int genreId = createGenreId("Missing Film Association");

        HttpResponse<String> response = request(
            "POST", "/films/999999/genres",
            "{\"genre_id\":" + genreId + "}"
        );

        assertEquals(404, response.statusCode());
    }

    @Test
    void assigningMissingGenreReturns404() throws Exception {
        int filmId = createFilmId("Missing Genre Association", 2019);

        HttpResponse<String> response = request(
            "POST", "/films/" + filmId + "/genres",
            "{\"genre_id\":999999}"
        );

        assertEquals(404, response.statusCode());
    }

    private int createFilmId(String title, int year) throws Exception {
        HttpResponse<String> response = request(
            "POST",
            "/films",
            "{\"title\":\"" + title + "\",\"year\":" + year + "}"
        );

        assertEquals(201, response.statusCode());
        return json(response).get("filmId").asInt();
    }

    private int createGenreId(String name) throws Exception {
        try (var connection = database.getConnection();
             var statement = connection.prepareStatement(
                 "INSERT INTO genres (name) VALUES (?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, name);
            statement.executeUpdate();

            try (var keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next(), "Expected a generated genre ID");
                return keys.getInt(1);
            }
        }
    }

    private boolean hasGenre(JsonNode film, int genreId, String name) {
        JsonNode genres = film.get("genres");
        if (genres == null || !genres.isArray()) {
            return false;
        }

        for (JsonNode genre : genres) {
            if (genre.path("genreId").asInt() == genreId
                    && name.equals(genre.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    private HttpResponse<String> request(
            String method, String path, String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json");

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        return client.send(
            builder.build(), HttpResponse.BodyHandlers.ofString()
        );
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }
}
