package com.marquee;

import com.marquee.database.Database;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Path databasePath = Path.of(
            System.getenv().getOrDefault(
                "MARQUEE_DB", "data/marquee.db"
            )
        );

        Path parent = databasePath.toAbsolutePath().getParent();
        if (parent != null) {
            java.nio.file.Files.createDirectories(parent);
        }

        Database database = new Database(databasePath);
        database.initialize();

        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", 8080), 0
        );

        server.createContext("/health", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("GET")) {
                    sendJson(exchange, 405,
                        "{\"error\":{\"code\":405,\"message\":\"Method not allowed\",\"details\":[]}}");
                    return;
                }

                sendJson(exchange, 200,
                    "{\"status\":\"ok\",\"service\":\"Marquee\"}");
            } finally {
                exchange.close();
            }
        });

        var filmRepository =
            new com.marquee.repository.FilmRepository(database);
        var filmService =
            new com.marquee.service.FilmService(filmRepository);

        server.createContext(
            "/films",
            new com.marquee.handler.FilmHandler(filmService)
        );

        var genreRepository =
            new com.marquee.repository.GenreRepository(database);
        var genreService =
            new com.marquee.service.GenreService(genreRepository);

        server.createContext(
            "/genres",
            new com.marquee.handler.GenreHandler(genreService)
        );

        var screeningRepository =
            new com.marquee.repository.ScreeningRepository(database);
        var screeningService =
            new com.marquee.service.ScreeningService(screeningRepository);

        server.createContext(
            "/screenings",
            new com.marquee.handler.ScreeningHandler(screeningService)
        );

        server.createContext("/", exchange -> {
            try {
                sendJson(exchange, 404,
                    "{\"error\":{\"code\":404,\"message\":\"Route not found\",\"details\":[]}}");
            } finally {
                exchange.close();
            }
        });

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Marquee listening on http://localhost:8080");
        System.out.println("Database: " + databasePath.toAbsolutePath());
    }

    private static void sendJson(
            HttpExchange exchange, int status, String body
    ) throws IOException {
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
