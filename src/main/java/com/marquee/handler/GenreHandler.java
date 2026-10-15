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

public final class GenreHandler implements HttpHandler {

    private final GenreService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public GenreHandler(GenreService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();

            if (method.equals("POST")) {
                handleCreate(exchange);
                return;
            }

            if (!method.equals("GET")) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                send(exchange, 200, service.listGenres());
            } catch (SQLException exception) {
                sendError(exchange, 500, "Database operation failed");
            }
        } catch (Exception exception) {
            if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        JsonNode body;

        try {
            body = mapper.readTree(exchange.getRequestBody());
        } catch (JsonProcessingException exception) {
            sendError(exchange, 400, "Invalid JSON request body");
            return;
        }

        if (body == null || !body.isObject()
                || !body.has("name")
                || !body.get("name").isTextual()) {
            sendError(exchange, 400, "Genre name must be a string");
            return;
        }

        try {
            Genre created = service.createGenre(body.get("name").asText());
            send(exchange, 201, created);
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            String message = exception.getMessage();

            if (message != null
                    && message.contains("UNIQUE constraint failed: genres.name")) {
                sendError(exchange, 409, "Genre name already exists");
            } else {
                sendError(exchange, 500, "Database operation failed");
            }
        }
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
