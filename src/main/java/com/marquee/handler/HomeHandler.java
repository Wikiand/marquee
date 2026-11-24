package com.marquee.handler;

import com.marquee.http.ServerErrorLogger;
import com.marquee.service.FilmService;
import com.marquee.service.GenreService;
import com.marquee.web.TemplateRenderer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HomeHandler implements HttpHandler {

    private final FilmService filmService;
    private final GenreService genreService;
    private final TemplateRenderer renderer;

    public HomeHandler(
            FilmService filmService,
            GenreService genreService,
            TemplateRenderer renderer
    ) {
        this.filmService = filmService;
        this.genreService = genreService;
        this.renderer = renderer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendJsonError(exchange, 405, "Method not allowed");
                return;
            }

            if (!"/".equals(exchange.getRequestURI().getPath())) {
                sendJsonError(exchange, 404, "Route not found");
                return;
            }

            try {
                FilmService.FilmPage result = filmService.listFilms(
                    null,
                    null,
                    null,
                    1,
                    FilmService.MAX_PER_PAGE
                );

                List<com.marquee.model.Film> films = result.films();

                Map<String, Object> variables = new HashMap<>();
                variables.put("films", films);
                variables.put("featuredFilm",
                    films.isEmpty() ? null : films.get(0));
                variables.put("genres", genreService.listGenres());
                variables.put("total", result.total());

                String html = renderer.render("home", variables);
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                    "Content-Type", "text/html; charset=utf-8"
                );
                exchange.getResponseHeaders().set(
                    "Cache-Control", "no-store"
                );
                exchange.getResponseHeaders().set(
                    "X-Content-Type-Options", "nosniff"
                );
                exchange.sendResponseHeaders(200, bytes.length);

                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            } catch (SQLException exception) {
                ServerErrorLogger.log(exchange, exception);
                sendJsonError(exchange, 500, "Internal server error");
            } catch (Exception exception) {
                ServerErrorLogger.log(exchange, exception);
                sendJsonError(exchange, 500, "Internal server error");
            }
        }
    }

    private void sendJsonError(
            HttpExchange exchange,
            int status,
            String message
    ) throws IOException {
        String body = "{\"error\":{\"code\":" + status
            + ",\"message\":\"" + message + "\",\"details\":[]}}";

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

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
