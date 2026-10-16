package com.marquee.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.database.Database;
import com.marquee.repository.FilmRepository;
import com.marquee.service.FilmService;
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

class FilmHandlerTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private HttpClient client;
    private ObjectMapper mapper;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        Database database = new Database(tempDir.resolve("films.db"));
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
    }

    @Test
    void createFilmRejectsInvalidYear() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/films", "{\"title\":\"Arrival\",\"year\":0}"
        );

        assertEquals(400, response.statusCode());
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
