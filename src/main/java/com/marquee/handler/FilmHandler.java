package com.marquee.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.http.RequestBodyException;
import com.marquee.http.RequestBodyReader;
import com.marquee.http.ServerErrorLogger;
import com.marquee.model.Film;
import com.marquee.model.FilmInput;
import com.marquee.service.FilmService;
import com.marquee.service.ScreeningService;
import com.marquee.service.GenreService;
import com.marquee.web.ContentNegotiator;
import com.marquee.web.TemplateRenderer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FilmHandler implements HttpHandler {

    private final FilmService service;
    private final ScreeningService screeningService;
    private final GenreService genreService;
    private final TemplateRenderer renderer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RequestBodyReader bodyReader =
            new RequestBodyReader(mapper);

    public FilmHandler(FilmService service) {
        this(service, null, null, null);
    }

    public FilmHandler(
            FilmService service,
            ScreeningService screeningService,
            GenreService genreService,
            TemplateRenderer renderer
    ) {
        this.service = service;
        this.screeningService = screeningService;
        this.genreService = genreService;
        this.renderer = renderer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                String path = exchange.getRequestURI().getPath();

                if (!path.equals("/films") && !path.equals("/films/")) {
                    if (!path.startsWith("/films/")) {
                        sendError(exchange, 404, "Route not found");
                        return;
                    }

                    String method = exchange.getRequestMethod();
                    String suffix = path.substring("/films/".length());

                    if (suffix.endsWith("/genres")
                            && method.equals("POST")) {
                        handleAssignGenre(exchange, suffix);
                        return;
                    }

                    if (suffix.contains("/genres/")
                            && method.equals("DELETE")) {
                        handleRemoveGenre(exchange, suffix);
                        return;
                    }

                    if (method.equals("PUT")) {
                        handleUpdate(exchange, suffix);
                        return;
                    }

                    if (method.equals("DELETE")) {
                        handleDelete(exchange, suffix);
                        return;
                    }

                    if (!method.equals("GET")) {
                        sendError(exchange, 405, "Method not allowed");
                        return;
                    }

                    handleFilmById(exchange, suffix);
                    return;
                }

                String method = exchange.getRequestMethod();

                if (method.equals("POST")) {
                    handleCreate(exchange);
                    return;
                }

                if (!method.equals("GET")) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                Map<String, String> query = parseQuery(
                    exchange.getRequestURI().getRawQuery()
                );

                String genre = query.get("genre");
                String title = query.get("title");
                Integer year = parseOptionalInteger(query, "year");

                int page = parseInteger(
                    query.getOrDefault("page", "1"), "page"
                );

                int perPage = parseInteger(
                    query.getOrDefault("per_page", "20"), "per_page"
                );

                FilmService.FilmPage result = service.listFilms(
                    genre, year, title, page, perPage
                );

                if (wantsHtml(exchange)) {
                    Map<String, Object> variables = new HashMap<>();
                    variables.put("films", result.films());
                    variables.put("page", result.page());
                    variables.put("perPage", result.per_page());
                    variables.put("total", result.total());
                    variables.put("titleFilter", title == null ? "" : title);
                    variables.put("genreFilter", genre == null ? "" : genre);
                    variables.put("yearFilter", year == null ? "" : year);
                    variables.put(
                        "genres",
                        genreService == null
                            ? List.of()
                            : genreService.listGenres()
                    );

                    sendHtml(
                        exchange,
                        200,
                        renderer.render("films", variables)
                    );
                } else {
                    send(exchange, 200, result);
                }

            } catch (RequestBodyException exception) {
                sendError(
                    exchange,
                    exception.getStatusCode(),
                    exception.getMessage()
                );
            } catch (IllegalArgumentException exception) {
                sendError(exchange, 400, exception.getMessage());
            } catch (SQLException exception) {
                ServerErrorLogger.log(exchange, exception);
                sendError(exchange, 500, "Internal server error");
            } catch (Exception exception) {
                ServerErrorLogger.log(exchange, exception);
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    private void handleCreate(HttpExchange exchange)
            throws IOException, SQLException, RequestBodyException {

        JsonNode body = bodyReader.readJsonObject(exchange);

        List<String> details = new ArrayList<>();

        JsonNode titleNode = body.get("title");

        if (titleNode == null || !titleNode.isTextual()
                || titleNode.asText().isBlank()) {
            details.add("title is required and cannot be blank");
        }

        JsonNode yearNode = body.get("year");

        if (yearNode == null || !yearNode.isIntegralNumber()
                || !yearNode.canConvertToInt()
                || yearNode.asInt() <= 0) {
            details.add("year must be a positive integer");
        }

        if (!details.isEmpty()) {
            sendValidationError(exchange, details);
            return;
        }

        FilmInput input = mapper.treeToValue(body, FilmInput.class);
        Film created = service.createFilm(input);

        send(exchange, 201, created);
    }

    private void handleUpdate(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException, RequestBodyException {

        if (suffix.isBlank() || suffix.contains("/")) {
            sendError(exchange, 404, "Film not found");
            return;
        }

        int filmId = parseInteger(suffix, "film ID");

        JsonNode body = bodyReader.readJsonObject(exchange);

        FilmInput input;

        try {
            input = mapper.treeToValue(body, FilmInput.class);
        } catch (JsonProcessingException exception) {
            sendError(exchange, 400, "Invalid JSON request body");
            return;
        }

        Optional<Film> updated = service.updateFilm(filmId, input);

        if (updated.isEmpty()) {
            sendError(exchange, 404, "Film not found");
            return;
        }

        send(exchange, 200, updated.get());
    }

    private void handleDelete(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException {

        if (suffix.isBlank() || suffix.contains("/")) {
            sendError(exchange, 404, "Film not found");
            return;
        }

        int filmId = parseInteger(suffix, "film ID");

        if (!service.deleteFilm(filmId)) {
            sendError(exchange, 404, "Film not found");
            return;
        }

        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(204, -1);
    }

    private void handleAssignGenre(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException, RequestBodyException {

        String filmIdPart = suffix.substring(
            0, suffix.length() - "/genres".length()
        );

        int filmId = parsePositiveId(filmIdPart, "film");

        JsonNode body = bodyReader.readJsonObject(exchange);

        if (!body.has("genre_id")
                || !body.get("genre_id").isIntegralNumber()
                || !body.get("genre_id").canConvertToInt()) {
            sendError(exchange, 400, "genre_id must be a positive integer");
            return;
        }

        int genreId = body.get("genre_id").asInt();

        if (genreId <= 0) {
            sendError(exchange, 400, "genre_id must be a positive integer");
            return;
        }

        var result = service.assignGenre(filmId, genreId);

        switch (result) {
            case SUCCESS -> send(exchange, 201, Map.of(
                "filmId", filmId,
                "genreId", genreId,
                "message", "Genre assigned to film"
            ));
            case FILM_NOT_FOUND ->
                sendError(exchange, 404, "Film not found");
            case GENRE_NOT_FOUND ->
                sendError(exchange, 404, "Genre not found");
            case ALREADY_ASSIGNED ->
                sendError(
                    exchange,
                    409,
                    "Genre is already assigned to this film"
                );
            case ASSOCIATION_NOT_FOUND ->
                sendError(exchange, 404, "Film-genre association not found");
        }
    }

    private void handleRemoveGenre(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException {

        String[] parts = suffix.split("/", -1);

        if (parts.length != 3 || !parts[1].equals("genres")) {
            sendError(exchange, 404, "Route not found");
            return;
        }

        int filmId = parsePositiveId(parts[0], "film");
        int genreId = parsePositiveId(parts[2], "genre");

        var result = service.removeGenre(filmId, genreId);

        switch (result) {
            case SUCCESS -> {
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(204, -1);
            }
            case FILM_NOT_FOUND ->
                sendError(exchange, 404, "Film not found");
            case GENRE_NOT_FOUND ->
                sendError(exchange, 404, "Genre not found");
            case ASSOCIATION_NOT_FOUND ->
                sendError(exchange, 404, "Film-genre association not found");
            case ALREADY_ASSIGNED ->
                sendError(exchange, 409, "Unexpected association state");
        }
    }

    private int parsePositiveId(String value, String resource) {
        try {
            int id = Integer.parseInt(value);

            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // Return the same client-facing error for non-numeric IDs.
        }

        throw new IllegalArgumentException(
            "Invalid " + resource + " ID"
        );
    }

    private void handleFilmById(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException {

        if (suffix.isBlank() || suffix.contains("/")) {
            sendError(exchange, 404, "Film not found");
            return;
        }

        int filmId = parseInteger(suffix, "film ID");
        Optional<Film> film = service.getFilm(filmId);

        if (film.isEmpty()) {
            sendError(exchange, 404, "Film not found");
            return;
        }

        if (wantsHtml(exchange)) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("film", film.get());

            List<com.marquee.model.Screening> screenings = List.of();

            if (screeningService != null) {
                screenings = screeningService.listScreenings(
                    filmId, null, null, 1, 100
                ).screenings();
            }

            variables.put("screenings", screenings);

            sendHtml(
                exchange,
                200,
                renderer.render("film-detail", variables)
            );
        } else {
            send(exchange, 200, film.get());
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();

        if (rawQuery == null || rawQuery.isBlank()) {
            return parameters;
        }

        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);

            String key = URLDecoder.decode(
                parts[0], StandardCharsets.UTF_8
            );

            String value = parts.length > 1
                ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                : "";

            if (!key.matches("[a-zA-Z_]+")) {
                throw new IllegalArgumentException(
                    "Invalid query parameter name"
                );
            }

            if (parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                    "Duplicate query parameter: " + key
                );
            }
        }

        for (String key : parameters.keySet()) {
            if (!key.equals("genre")
                    && !key.equals("year")
                    && !key.equals("title")
                    && !key.equals("page")
                    && !key.equals("per_page")) {
                throw new IllegalArgumentException(
                    "Unknown query parameter: " + key
                );
            }
        }

        return parameters;
    }

    private Integer parseOptionalInteger(
            Map<String, String> query, String key
    ) {
        if (!query.containsKey(key)) {
            return null;
        }

        return parseInteger(query.get(key), key);
    }

    private int parseInteger(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                field + " must be an integer"
            );
        }
    }

    private boolean wantsHtml(HttpExchange exchange) {
        return renderer != null
            && ContentNegotiator.prefersHtml(
                exchange.getRequestHeaders()
            );
    }

    private void sendHtml(
            HttpExchange exchange, int status, String html
    ) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type", "text/html; charset=utf-8"
        );
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Vary", "Accept");

        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void sendValidationError(
            HttpExchange exchange, List<String> details
    ) throws IOException {

        send(exchange, 400, Map.of(
            "error", Map.of(
                "code", 400,
                "message", "Validation failed",
                "details", details
            )
        ));
    }

    private void sendError(
            HttpExchange exchange, int status, String message
    ) throws IOException {

        send(exchange, status, Map.of(
            "error", Map.of(
                "code", status,
                "message", message,
                "details", List.of()
            )
        ));
    }

    private void send(
            HttpExchange exchange, int status, Object body
    ) throws IOException {

        byte[] bytes = mapper.writeValueAsBytes(body);

        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=utf-8"
        );

        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Vary", "Accept");
        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
