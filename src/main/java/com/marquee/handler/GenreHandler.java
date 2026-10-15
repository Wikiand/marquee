package com.marquee.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.model.Genre;
import com.marquee.service.GenreService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public final class GenreHandler implements HttpHandler {

    private final GenreService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public GenreHandler(GenreService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();

                if (!path.equals("/genres") && !path.equals("/genres/")) {
                    if (!path.startsWith("/genres/")) {
                        sendError(exchange, 404, "Route not found");
                        return;
                    }

                    String suffix = path.substring("/genres/".length());

                    if (method.equals("DELETE")) {
                        handleDelete(exchange, suffix);
                        return;
                    }

                    if (method.equals("PUT")) {
                        handleUpdate(exchange, suffix);
                        return;
                    }

                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                if (method.equals("POST")) {
                    handleCreate(exchange);
                    return;
                }

                if (!method.equals("GET")) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                send(exchange, 200, service.listGenres());

            } catch (IllegalArgumentException exception) {
                sendError(exchange, 400, exception.getMessage());
            } catch (SQLException exception) {
                if (isDuplicateGenre(exception)) {
                    sendError(exchange, 409, "Genre name already exists");
                } else {
                    sendError(exchange, 500, "Database operation failed");
                }
            } catch (Exception exception) {
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    private void handleCreate(HttpExchange exchange) throws IOException, SQLException {
        JsonNode body = readBody(exchange);

        if (body == null || !body.isObject()
                || !body.has("name")
                || !body.get("name").isTextual()) {
            sendError(exchange, 400, "Genre name must be a string");
            return;
        }

        Genre created = service.createGenre(body.get("name").asText());
        send(exchange, 201, created);
    }

    private void handleUpdate(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException {

        if (suffix.isBlank() || suffix.contains("/")) {
            sendError(exchange, 404, "Genre not found");
            return;
        }

        int genreId;
        try {
            genreId = Integer.parseInt(suffix);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid genre ID");
        }

        JsonNode body = readBody(exchange);

        if (body == null || !body.isObject()
                || !body.has("name")
                || !body.get("name").isTextual()) {
            sendError(exchange, 400, "Genre name must be a string");
            return;
        }

        Optional<Genre> updated = service.updateGenre(
            genreId, body.get("name").asText()
        );

        if (updated.isEmpty()) {
            sendError(exchange, 404, "Genre not found");
            return;
        }

        send(exchange, 200, updated.get());
    }


    private void handleDelete(
            HttpExchange exchange, String suffix
    ) throws IOException, SQLException {

        if (suffix.isBlank() || suffix.contains("/")) {
            sendError(exchange, 404, "Genre not found");
            return;
        }

        int genreId;
        try {
            genreId = Integer.parseInt(suffix);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid genre ID");
        }

        if (!service.deleteGenre(genreId)) {
            sendError(exchange, 404, "Genre not found");
            return;
        }

        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(204, -1);
    }

    private JsonNode readBody(HttpExchange exchange) throws IOException {
        try {
            return mapper.readTree(exchange.getRequestBody());
        } catch (JsonProcessingException exception) {
            sendError(exchange, 400, "Invalid JSON request body");
            return null;
        }
    }

    private boolean isDuplicateGenre(SQLException exception) {
        String message = exception.getMessage();
        return message != null
            && message.contains("UNIQUE constraint failed: genres.name");
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
