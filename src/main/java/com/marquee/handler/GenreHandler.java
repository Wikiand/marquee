package com.marquee.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
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
            if (!exchange.getRequestMethod().equals("GET")) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                var genres = service.listGenres();
                send(exchange, 200, genres);
            } catch (SQLException exception) {
                sendError(exchange, 500, "Database operation failed");
            } catch (Exception exception) {
                sendError(exchange, 500, "Internal server error");
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
