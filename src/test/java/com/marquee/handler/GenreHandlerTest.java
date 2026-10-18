package com.marquee.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.database.Database;
import com.marquee.repository.GenreRepository;
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
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class GenreHandlerTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private HttpClient client;
    private ObjectMapper mapper;
    private String baseUrl;
    private Database database;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(tempDir.resolve("genres.db"));
        database.initialize();

        GenreService service = new GenreService(
            new GenreRepository(database)
        );

        server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0), 0
        );
        server.createContext("/genres", new GenreHandler(service));
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
    void createGenreReturns201AndCreatedGenre() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/genres", "{\"name\":\"Comedy\"}"
        );

        assertEquals(201, response.statusCode());
        assertEquals("Comedy", json(response).get("name").asText());
        assertTrue(json(response).get("genreId").asInt() > 0);
    }

    @Test
    void listGenresReturnsCreatedGenres() throws Exception {
        request("POST", "/genres", "{\"name\":\"Comedy\"}");

        HttpResponse<String> response = request("GET", "/genres", null);

        assertEquals(200, response.statusCode());
        assertTrue(json(response).isArray());
        assertEquals(1, json(response).size());
        assertEquals(
            "Comedy", json(response).get(0).get("name").asText()
        );
    }

    @Test
    void getGenreByIdReturns200ForExistingGenre() throws Exception {
        JsonNode created = json(request(
            "POST", "/genres", "{\"name\":\"Drama\"}"
        ));

        int id = created.get("genreId").asInt();

        HttpResponse<String> response = request(
            "GET", "/genres/" + id, null
        );

        assertEquals(200, response.statusCode());
        assertEquals("Drama", json(response).get("name").asText());
    }

    @Test
    void getGenreByIdReturns404WhenMissing() throws Exception {
        HttpResponse<String> response = request(
            "GET", "/genres/9999", null
        );

        assertEquals(404, response.statusCode());
        assertEquals(
            404, json(response).get("error").get("code").asInt()
        );
        assertTrue(json(response).get("error").get("details").isArray());
    }

    @Test
    void getGenreByIdRejectsMalformedId() throws Exception {
        assertInvalidIdResponse(request("GET", "/genres/abc", null));
    }

    @Test
    void getGenreByIdRejectsZeroAndNegativeIds() throws Exception {
        for (String id : new String[]{"0", "-1"}) {
            HttpResponse<String> response = request(
                "GET", "/genres/" + id, null
            );

            assertEquals(400, response.statusCode(), "ID: " + id);
        }
    }

    @Test
    void createGenreRejectsMalformedJson() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/genres", "{\"name\":"
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void createGenreRejectsMissingName() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/genres", "{}"
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void createGenreRejectsDuplicateName() throws Exception {
        request("POST", "/genres", "{\"name\":\"Comedy\"}");

        HttpResponse<String> response = request(
            "POST", "/genres", "{\"name\":\"Comedy\"}"
        );

        assertEquals(409, response.statusCode());
        assertEquals(
            "Genre name already exists",
            json(response).get("error").get("message").asText()
        );
    }

    @Test
    void updateGenreChangesName() throws Exception {
        JsonNode created = json(request(
            "POST", "/genres", "{\"name\":\"Comedy\"}"
        ));

        int id = created.get("genreId").asInt();

        HttpResponse<String> response = request(
            "PUT", "/genres/" + id, "{\"name\":\"Dark Comedy\"}"
        );

        assertEquals(200, response.statusCode());
        assertEquals("Dark Comedy", json(response).get("name").asText());
    }

    @Test
    void updateMissingGenreReturns404() throws Exception {
        HttpResponse<String> response = request(
            "PUT", "/genres/9999", "{\"name\":\"Comedy\"}"
        );

        assertEquals(404, response.statusCode());
    }

    @Test
    void updateGenreRejectsMalformedId() throws Exception {
        assertInvalidIdResponse(request(
            "PUT", "/genres/abc", "{\"name\":\"Comedy\"}"
        ));
    }

    @Test
    void updateGenreRejectsZeroAndNegativeIds() throws Exception {
        for (String id : new String[]{"0", "-1"}) {
            HttpResponse<String> response = request(
                "PUT", "/genres/" + id, "{\"name\":\"Comedy\"}"
            );

            assertEquals(400, response.statusCode(), "ID: " + id);
        }
    }

    @Test
    void deleteGenreReturns204AndRemovesGenre() throws Exception {
        JsonNode created = json(request(
            "POST", "/genres", "{\"name\":\"Thriller\"}"
        ));

        int id = created.get("genreId").asInt();

        HttpResponse<String> deleted = request(
            "DELETE", "/genres/" + id, null
        );
        HttpResponse<String> fetched = request(
            "GET", "/genres/" + id, null
        );

        assertEquals(204, deleted.statusCode());
        assertTrue(deleted.body().isEmpty());
        assertEquals(404, fetched.statusCode());
    }

    @Test
    void deletingGenreRemovesFilmGenreAssociations() throws Exception {
        JsonNode createdGenre = json(request(
            "POST", "/genres", "{\"name\":\"Cascade Genre\"}"
        ));
        int genreId = createdGenre.get("genreId").asInt();

        int filmId = new com.marquee.repository.FilmRepository(database)
            .create("Cascade Film", 2024, null, java.util.List.of())
            .filmId();

        try (var connection = database.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate(
                "INSERT INTO film_genres (film_id, genre_id) VALUES ("
                    + filmId + ", " + genreId + ")"
            );
        }

        HttpResponse<String> deleted = request(
            "DELETE", "/genres/" + genreId, null
        );

        assertEquals(204, deleted.statusCode());

        try (var connection = database.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                 "SELECT COUNT(*) FROM film_genres WHERE genre_id = "
                     + genreId)) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1));
        }
    }

    @Test
    void deleteMissingGenreReturns404() throws Exception {
        HttpResponse<String> response = request(
            "DELETE", "/genres/9999", null
        );

        assertEquals(404, response.statusCode());
    }

    @Test
    void deleteGenreRejectsMalformedId() throws Exception {
        assertInvalidIdResponse(request(
            "DELETE", "/genres/abc", null
        ));
    }

    @Test
    void deleteGenreRejectsZeroAndNegativeIds() throws Exception {
        for (String id : new String[]{"0", "-1"}) {
            HttpResponse<String> response = request(
                "DELETE", "/genres/" + id, null
            );

            assertEquals(400, response.statusCode(), "ID: " + id);
        }
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        HttpResponse<String> response = request(
            "PATCH", "/genres", "{}"
        );

        assertEquals(405, response.statusCode());
    }

    private void assertInvalidIdResponse(
            HttpResponse<String> response
    ) throws Exception {
        assertEquals(400, response.statusCode());
        assertEquals(
            "Invalid genre ID",
            json(response).get("error").get("message").asText()
        );
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
            builder.method(
                method, HttpRequest.BodyPublishers.ofString(body)
            );
        }

        return client.send(
            builder.build(), HttpResponse.BodyHandlers.ofString()
        );
    }

    private JsonNode json(HttpResponse<String> response)
            throws Exception {
        return mapper.readTree(response.body());
    }
}
