package com.marquee.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.model.Screening;
import com.marquee.service.ScreeningService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public final class ScreeningHandler implements HttpHandler {

    private final ScreeningService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public ScreeningHandler(ScreeningService service) {
        this.service = service;
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

                    if (method.equals("GET")) {
                        handleGetById(exchange, suffix);
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

                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                if (method.equals("POST")) {
                    handleCreate(exchange);
                    return;
                }

                if (method.equals("GET")) {
                    send(exchange, 200, service.listScreenings());
                    return;
                }

                sendError(exchange, 405, "Method not allowed");

            } catch (IllegalArgumentException exception) {
                sendError(exchange, 400, exception.getMessage());
            } catch (SQLException exception) {
                if (isMissingFilm(exception)) {
                    sendError(exchange, 400, "Film does not exist");
                } else {
                    sendError(exchange, 500, "Database operation failed");
                }
            } catch (Exception exception) {
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    private void handleCreate(HttpExchange exchange)
            throws IOException, SQLException {
        JsonNode body = readBody(exchange);
        if (body == null) {
            return;
        }

        if (!body.isObject()
                || !body.has("filmId")
                || !body.get("filmId").canConvertToInt()
                || !body.has("venue")
                || !body.get("venue").isTextual()
                || !body.has("screeningTime")
                || !body.get("screeningTime").isTextual()) {
            sendError(exchange, 400,
                "filmId, venue, and screeningTime are required");
            return;
        }

        Screening created = service.createScreening(
            body.get("filmId").intValue(),
            body.get("venue").asText(),
            body.get("screeningTime").asText()
        );

        send(exchange, 201, created);
    }

    private void handleGetById(HttpExchange exchange, String suffix)
            throws IOException, SQLException {
        int screeningId = parseId(suffix);

        Optional<Screening> screening = service.getScreening(screeningId);
        if (screening.isEmpty()) {
            sendError(exchange, 404, "Screening not found");
            return;
        }

        send(exchange, 200, screening.get());
    }

    private void handleUpdate(HttpExchange exchange, String suffix)
            throws IOException, SQLException {
        int screeningId = parseId(suffix);
        JsonNode body = readBody(exchange);
        if (body == null) {
            return;
        }

        if (!body.isObject()
                || !body.has("filmId")
                || !body.get("filmId").canConvertToInt()
                || !body.has("venue")
                || !body.get("venue").isTextual()
                || !body.has("screeningTime")
                || !body.get("screeningTime").isTextual()) {
            sendError(exchange, 400,
                "filmId, venue, and screeningTime are required");
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

    private void handleDelete(HttpExchange exchange, String suffix)
            throws IOException, SQLException {
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
            return Integer.parseInt(suffix);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid screening ID");
        }
    }

    private JsonNode readBody(HttpExchange exchange) throws IOException {
        try {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            if (body == null) {
                sendError(exchange, 400, "Invalid JSON request body");
            }
            return body;
        } catch (JsonProcessingException exception) {
            sendError(exchange, 400, "Invalid JSON request body");
            return null;
        }
    }

    private boolean isMissingFilm(SQLException exception) {
        String message = exception.getMessage();
        return message != null
            && message.contains("FOREIGN KEY constraint failed");
    }

    private void sendError(
            HttpExchange exchange, int status, String message
    ) throws IOException {
        send(exchange, status, Map.of(
            "error", Map.of(
                "code", status,
                "message", message
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
        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
