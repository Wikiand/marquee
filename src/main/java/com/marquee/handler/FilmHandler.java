package com.marquee.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.model.Film;
import com.marquee.service.FilmService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public final class FilmHandler implements HttpHandler {

    private final FilmService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public FilmHandler(FilmService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET")) {
                send(exchange, 405, Map.of(
                    "error", Map.of(
                        "code", 405,
                        "message", "Method not allowed"
                    )
                ));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String base = "/films";

            if (path.equals(base) || path.equals(base + "/")) {
                send(exchange, 200, Map.of(
                    "page", 1,
                    "per_page", 20,
                    "total", service.listFilms().size(),
                    "films", service.listFilms()
                ));
                return;
            }

            String suffix = path.substring((base + "/").length());

            if (suffix.isBlank() || suffix.contains("/")) {
                sendError(exchange, 404, "Film not found");
                return;
            }

            int filmId;
            try {
                filmId = Integer.parseInt(suffix);
            } catch (NumberFormatException exception) {
                sendError(exchange, 400, "Invalid film ID");
                return;
            }

            Optional<Film> film = service.getFilm(filmId);

            if (film.isEmpty()) {
                sendError(exchange, 404, "Film not found");
                return;
            }

            send(exchange, 200, film.get());

        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            sendError(exchange, 500, "Database operation failed");
        } catch (Exception exception) {
            sendError(exchange, 500, "Internal server error");
        }
    }

    private void sendError(
            HttpExchange exchange, int status, String message
    ) throws IOException {
        send(exchange, status, Map.of(
            "error", Map.of("code", status, "message", message)
        ));
    }

    private void send(
            HttpExchange exchange, int status, Object body
    ) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);

        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=utf-8"
        );

        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
