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
        seedIfEmpty(database);

        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", 8080), 0
        );

        server.createContext("/health", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("GET")) {
                    sendJson(exchange, 405,
                        "{\"error\":{\"code\":405,\"message\":\"Method not allowed\"}}");
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

        server.createContext("/", exchange -> {
            try {
                sendJson(exchange, 404,
                    "{\"error\":{\"code\":404,\"message\":\"Route not found\"}}");
            } finally {
                exchange.close();
            }
        });

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Marquee listening on http://localhost:8080");
        System.out.println("Database: " + databasePath.toAbsolutePath());
    }

    private static void seedIfEmpty(Database database) throws Exception {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT COUNT(*) FROM films"
             )) {

            if (result.next() && result.getInt(1) == 0) {
                try (Statement seed = connection.createStatement()) {
                    seed.executeUpdate("""
                        INSERT INTO genres (genre_id, name) VALUES
                        (1, 'Action'),
                        (2, 'Comedy'),
                        (3, 'Drama'),
                        (4, 'Science Fiction'),
                        (5, 'Horror'),
                        (6, 'Documentary'),
                        (7, 'Animation'),
                        (8, 'Thriller')
                        """);

                    seed.executeUpdate("""
                        INSERT INTO films (film_id, title, year, description) VALUES
                        (1, 'The Invisible Fight', 2023,
                         'A Soviet Estonian security guard discovers an unexpected calling.'),
                        (2, '2001: A Space Odyssey', 1968,
                         'An enigmatic monolith accompanies humanity on a journey through space.'),
                        (3, 'Inception', 2010,
                         'A specialist enters dreams to carry out an unusual mission.'),
                        (4, 'Parasite', 2019,
                         'Two families become entangled across a sharp class divide.'),
                        (5, 'Get Out', 2017,
                         'A weekend visit reveals increasingly disturbing secrets.')
                        """);

                    seed.executeUpdate("""
                        INSERT INTO film_genres (film_id, genre_id) VALUES
                        (1, 1), (1, 2),
                        (2, 4), (2, 3),
                        (3, 1), (3, 4), (3, 8),
                        (4, 3), (4, 8),
                        (5, 5), (5, 8)
                        """);

                    seed.executeUpdate("""
                        INSERT INTO screenings
                            (film_id, venue, screening_time) VALUES
                        (1, 'Main Hall', '2026-10-20T19:30:00Z'),
                        (2, 'Rooftop Cinema', '2026-10-21T18:00:00Z'),
                        (3, 'Main Hall', '2026-10-22T19:00:00Z'),
                        (4, 'Studio A', '2026-10-23T20:00:00Z'),
                        (5, 'Studio B', '2026-10-24T21:00:00Z')
                        """);
                }

                System.out.println("Initial catalogue data loaded.");
            }
        }
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
