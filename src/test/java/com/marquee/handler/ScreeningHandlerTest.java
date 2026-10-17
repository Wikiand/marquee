package com.marquee.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.database.Database;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.ScreeningRepository;
import com.marquee.service.ScreeningService;
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

class ScreeningHandlerTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private HttpClient client;
    private ObjectMapper mapper;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        Database database = new Database(tempDir.resolve("screenings.db"));
        database.initialize();

        new FilmRepository(database).create(
            "Test Film", 2025, "Film for screening tests", null
        );

        ScreeningService service =
            new ScreeningService(new ScreeningRepository(database));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/screenings", new ScreeningHandler(service));
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
    void createScreeningReturns201AndCreatedScreening() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/screenings",
            "{\"filmId\":1,\"venue\":\"Main Hall\","
                + "\"screeningTime\":\"2026-11-01T19:30:00Z\"}"
        );

        assertEquals(201, response.statusCode());
        JsonNode body = json(response);
        assertTrue(body.get("screeningId").asInt() > 0);
        assertEquals(1, body.get("filmId").asInt());
        assertEquals("Main Hall", body.get("venue").asText());
    }

    @Test
    void listScreeningsReturnsCreatedScreenings() throws Exception {
        createScreening("Main Hall", "2026-11-01T19:30:00Z");
        createScreening("Studio A", "2026-11-02T18:00:00Z");

        HttpResponse<String> response = request("GET", "/screenings", null);

        assertEquals(200, response.statusCode());
        assertEquals(2, json(response).get("total").asInt());
        assertEquals(2, json(response).get("screenings").size());
        assertEquals("Test Film", json(response).get("screenings")
            .get(0).get("filmTitle").asText());
    }

    @Test
    void listScreeningsCanFilterByVenue() throws Exception {
        createScreening("Main Hall", "2026-11-01T19:30:00Z");
        createScreening("Studio A", "2026-11-02T18:00:00Z");

        HttpResponse<String> response =
            request("GET", "/screenings?venue=main", null);

        assertEquals(200, response.statusCode());
        assertEquals(1, json(response).get("total").asInt());
        assertEquals("Main Hall", json(response).get("screenings")
            .get(0).get("venue").asText());
    }

    @Test
    void listScreeningsCanFilterByFilmAndDate() throws Exception {
        createScreening("Main Hall", "2026-11-01T19:30:00Z");
        createScreening("Studio A", "2026-11-02T18:00:00Z");

        HttpResponse<String> response = request(
            "GET", "/screenings?film_id=1&date=2026-11-02", null
        );

        assertEquals(200, response.statusCode());
        assertEquals(1, json(response).get("total").asInt());
        assertEquals("Studio A", json(response).get("screenings")
            .get(0).get("venue").asText());
    }

    @Test
    void listScreeningsSupportsPagination() throws Exception {
        createScreening("Hall 1", "2026-11-01T19:30:00Z");
        createScreening("Hall 2", "2026-11-02T19:30:00Z");

        HttpResponse<String> response =
            request("GET", "/screenings?page=2&per_page=1", null);

        assertEquals(200, response.statusCode());
        JsonNode body = json(response);
        assertEquals(2, body.get("total").asInt());
        assertEquals(2, body.get("page").asInt());
        assertEquals(1, body.get("per_page").asInt());
        assertEquals(1, body.get("screenings").size());
        assertEquals("Hall 2", body.get("screenings")
            .get(0).get("venue").asText());
    }

    @Test
    void listScreeningsRejectsInvalidDate() throws Exception {
        HttpResponse<String> response =
            request("GET", "/screenings?date=tomorrow", null);

        assertEquals(400, response.statusCode());
    }

    @Test
    void listScreeningsRejectsUnknownQueryParameter() throws Exception {
        HttpResponse<String> response =
            request("GET", "/screenings?unknown=value", null);

        assertEquals(400, response.statusCode());
    }

    @Test
    void getScreeningReturnsExistingScreening() throws Exception {
        JsonNode created =
            createScreening("Main Hall", "2026-11-01T19:30:00Z");

        HttpResponse<String> response = request(
            "GET", "/screenings/" + created.get("screeningId").asInt(), null
        );

        assertEquals(200, response.statusCode());
        assertEquals("Main Hall", json(response).get("venue").asText());
    }

    @Test
    void getScreeningReturns404WhenMissing() throws Exception {
        HttpResponse<String> response =
            request("GET", "/screenings/9999", null);

        assertEquals(404, response.statusCode());
        assertTrue(json(response).get("error").get("details").isArray());
    }

    @Test
    void getScreeningRejectsInvalidId() throws Exception {
        HttpResponse<String> response =
            request("GET", "/screenings/abc", null);

        assertEquals(400, response.statusCode());
    }

    @Test
    void updateScreeningChangesDetails() throws Exception {
        JsonNode created =
            createScreening("Main Hall", "2026-11-01T19:30:00Z");
        int id = created.get("screeningId").asInt();

        HttpResponse<String> response = request(
            "PUT", "/screenings/" + id,
            "{\"filmId\":1,\"venue\":\"Studio B\","
                + "\"screeningTime\":\"2026-11-03T20:00:00Z\"}"
        );

        assertEquals(200, response.statusCode());
        assertEquals("Studio B", json(response).get("venue").asText());
        assertEquals(
            "2026-11-03T20:00:00Z",
            json(response).get("screeningTime").asText()
        );
    }

    @Test
    void updateMissingScreeningReturns404() throws Exception {
        HttpResponse<String> response = request(
            "PUT", "/screenings/9999",
            "{\"filmId\":1,\"venue\":\"Main Hall\","
                + "\"screeningTime\":\"2026-11-01T19:30:00Z\"}"
        );

        assertEquals(404, response.statusCode());
    }

    @Test
    void deleteScreeningReturns204() throws Exception {
        JsonNode created =
            createScreening("Main Hall", "2026-11-01T19:30:00Z");

        HttpResponse<String> response = request(
            "DELETE", "/screenings/" + created.get("screeningId").asInt(), null
        );

        assertEquals(204, response.statusCode());
    }

    @Test
    void deleteMissingScreeningReturns404() throws Exception {
        HttpResponse<String> response =
            request("DELETE", "/screenings/9999", null);

        assertEquals(404, response.statusCode());
    }

    @Test
    void createScreeningRejectsEmptyBody() throws Exception {
        HttpResponse<String> response =
            request("POST", "/screenings", "");

        assertEquals(400, response.statusCode());
    }

    @Test
    void createScreeningRejectsMalformedJson() throws Exception {
        HttpResponse<String> response =
            request("POST", "/screenings", "{\"filmId\":");

        assertEquals(400, response.statusCode());
    }

    @Test
    void createScreeningRejectsMissingVenue() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/screenings",
            "{\"filmId\":1,\"screeningTime\":\"2026-11-01T19:30:00Z\"}"
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void createScreeningRejectsNonexistentFilm() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/screenings",
            "{\"filmId\":9999,\"venue\":\"Main Hall\","
                + "\"screeningTime\":\"2026-11-01T19:30:00Z\"}"
        );

        assertEquals(404, response.statusCode());
    }

    @Test
    void createScreeningRejectsBlankVenue() throws Exception {
        HttpResponse<String> response = request(
            "POST", "/screenings",
            "{\"filmId\":1,\"venue\":\"  \","
                + "\"screeningTime\":\"2026-11-01T19:30:00Z\"}"
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        HttpResponse<String> response =
            request("PATCH", "/screenings", null);

        assertEquals(405, response.statusCode());
    }

    private JsonNode createScreening(String venue, String time)
        throws Exception {
        HttpResponse<String> response = request(
            "POST", "/screenings",
            mapper.writeValueAsString(java.util.Map.of(
                "filmId", 1,
                "venue", venue,
                "screeningTime", time
            ))
        );

        assertEquals(201, response.statusCode(), response.body());
        return json(response);
    }

    private HttpResponse<String> request(
        String method, String path, String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path));

        if (body != null) {
            builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else if (method.equals("GET") || method.equals("DELETE")) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return client.send(
            builder.build(), HttpResponse.BodyHandlers.ofString()
        );
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }
}
