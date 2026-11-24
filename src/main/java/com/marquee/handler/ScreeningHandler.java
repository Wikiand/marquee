package com.marquee.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.http.RequestBodyException;
import com.marquee.http.RequestBodyReader;
import com.marquee.http.ServerErrorLogger;
import com.marquee.model.Screening;
import com.marquee.service.ScreeningService;
import com.marquee.service.FilmService;
import com.marquee.web.ContentNegotiator;
import com.marquee.web.TemplateRenderer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ScreeningHandler implements HttpHandler {

    private final ScreeningService service;
    private final FilmService filmService;
    private final TemplateRenderer renderer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RequestBodyReader bodyReader =
            new RequestBodyReader(mapper);

    public ScreeningHandler(ScreeningService service) {
        this(service, null, null);
    }

    public ScreeningHandler(
            ScreeningService service,
            FilmService filmService,
            TemplateRenderer renderer
    ) {
        this.service = service;
        this.filmService = filmService;
        this.renderer = renderer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();

                if (!path.equals("/screenings")
                        && !path.equals("/screenings/")) {
                    if (!path.startsWith("/screenings/")) {
                        sendError(exchange, 404, "Route not found");
                        return;
                    }

                    String suffix = path.substring("/screenings/".length());

                    switch (method) {
                        case "GET" -> handleGetById(exchange, suffix);
                        case "PUT" -> handleUpdate(exchange, suffix);
                        case "DELETE" -> handleDelete(exchange, suffix);
                        default -> sendError(
                            exchange, 405, "Method not allowed"
                        );
                    }
                    return;
                }

                switch (method) {
                    case "GET" -> handleList(exchange);
                    case "POST" -> handleCreate(exchange);
                    default -> sendError(
                        exchange, 405, "Method not allowed"
                    );
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
                if (isMissingFilm(exception)) {
                    sendError(exchange, 404, "Film not found");
                } else {
                    ServerErrorLogger.log(exchange, exception);
                    sendError(exchange, 500, "Internal server error");
                }
            } catch (Exception exception) {
                ServerErrorLogger.log(exchange, exception);
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    private void handleList(HttpExchange exchange)
            throws IOException, SQLException {

        Map<String, String> query = parseQuery(
            exchange.getRequestURI().getRawQuery()
        );

        for (String key : query.keySet()) {
            if (!Set.of(
                    "film_id", "venue", "date", "page", "per_page"
            ).contains(key)) {
                throw new IllegalArgumentException(
                    "Unknown query parameter: " + key
                );
            }
        }

        Integer filmId = null;

        if (query.containsKey("film_id")) {
            filmId = parsePositiveInt(query.get("film_id"), "film_id");
        }

        int page = query.containsKey("page")
            ? parsePositiveInt(query.get("page"), "page")
            : ScreeningService.DEFAULT_PAGE;

        int perPage = query.containsKey("per_page")
            ? parsePositiveInt(query.get("per_page"), "per_page")
            : ScreeningService.DEFAULT_PER_PAGE;

        var result = service.listScreenings(
            filmId,
            query.get("venue"),
            query.get("date"),
            page,
            perPage
        );

        if (wantsHtml(exchange)) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("screenings", result.screenings());
            variables.put("page", result.page());
            variables.put("total", result.total());
            variables.put("filmFilter", filmId == null ? "" : filmId);
            variables.put(
                "venueFilter",
                query.get("venue") == null ? "" : query.get("venue")
            );
            variables.put(
                "dateFilter",
                query.get("date") == null ? "" : query.get("date")
            );

            variables.put(
                "films",
                filmService == null
                    ? List.of()
                    : filmService.listFilms(null, null, null, 1, 100).films()
            );

            sendHtml(
                exchange,
                200,
                renderer.render("screenings", variables)
            );
        } else {
            send(exchange, 200, result);
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();

        if (rawQuery == null || rawQuery.isBlank()) {
            return parameters;
        }

        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);

            String key = URLDecoder.decode(
                pair[0], StandardCharsets.UTF_8
            );

            String value = pair.length > 1
                ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                : "";

            if (key.isBlank() || value.isBlank()) {
                throw new IllegalArgumentException(
                    "Query parameter names and values must not be blank"
                );
            }

            if (parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                    "Duplicate query parameter: " + key
                );
            }
        }

        return parameters;
    }

    private int parsePositiveInt(String value, String parameter) {
        try {
            int parsed = Integer.parseInt(value);

            if (parsed <= 0) {
                throw new IllegalArgumentException(
                    parameter + " must be a positive integer"
                );
            }

            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                parameter + " must be a positive integer"
            );
        }
    }

    private void handleCreate(HttpExchange exchange)
            throws IOException, SQLException, RequestBodyException {

        JsonNode body = bodyReader.readJsonObject(exchange);

        if (!hasValidScreeningFields(body)) {
            sendError(
                exchange,
                400,
                "filmId, venue, and screeningTime are required"
            );
            return;
        }

        Screening created = service.createScreening(
            body.get("filmId").intValue(),
            body.get("venue").asText(),
            body.get("screeningTime").asText()
        );

        send(exchange, 201, created);
    }

    private void handleGetById(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException {

        int screeningId = parseId(suffix);

        Optional<Screening> screening =
            service.getScreening(screeningId);

        if (screening.isEmpty()) {
            sendError(exchange, 404, "Screening not found");
            return;
        }

        send(exchange, 200, screening.get());
    }

    private void handleUpdate(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException, RequestBodyException {

        int screeningId = parseId(suffix);

        JsonNode body = bodyReader.readJsonObject(exchange);

        if (!hasValidScreeningFields(body)) {
            sendError(
                exchange,
                400,
                "filmId, venue, and screeningTime are required"
            );
            return;
        }

        Optional<Screening> updated = service.updateScreening(
            screeningId,
            body.get("filmId").intValue(),
            body.get("venue").asText(),
            body.get("screeningTime").asText()
        );

        if (updated.isEmpty()) {
            sendError(exchange, 404, "Screening not found");
            return;
        }

        send(exchange, 200, updated.get());
    }

    private boolean hasValidScreeningFields(JsonNode body) {
        return body.has("filmId")
            && body.get("filmId").isIntegralNumber()
            && body.get("filmId").canConvertToInt()
            && body.has("venue")
            && body.get("venue").isTextual()
            && body.has("screeningTime")
            && body.get("screeningTime").isTextual();
    }

    private void handleDelete(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException {

        int screeningId = parseId(suffix);

        if (!service.deleteScreening(screeningId)) {
            sendError(exchange, 404, "Screening not found");
            return;
        }

        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(204, -1);
    }

    private int parseId(String suffix) {
        if (suffix.isBlank() || suffix.contains("/")) {
            throw new IllegalArgumentException("Invalid screening ID");
        }

        try {
            int id = Integer.parseInt(suffix);

            if (id <= 0) {
                throw new IllegalArgumentException(
                    "Invalid screening ID"
                );
            }

            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid screening ID");
        }
    }

    private boolean isMissingFilm(SQLException exception) {
        String message = exception.getMessage();

        return message != null
            && message.contains("FOREIGN KEY constraint failed");
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

    private void sendError(
            HttpExchange exchange, int status, String message
    ) throws IOException {

        send(exchange, status, Map.of(
            "error", Map.of(
                "code", status,
                "message", message,
                "details", java.util.List.of()
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
