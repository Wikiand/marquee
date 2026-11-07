package com.marquee;

import com.marquee.database.Database;
import com.marquee.handler.FilmHandler;
import com.marquee.handler.GenreHandler;
import com.marquee.handler.ScreeningHandler;
import com.marquee.http.RequestLoggingFilter;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.GenreRepository;
import com.marquee.repository.ScreeningRepository;
import com.marquee.service.FilmService;
import com.marquee.service.GenreService;
import com.marquee.service.ScreeningService;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.Executors;

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
            Files.createDirectories(parent);
        }

        Database database = new Database(databasePath);
        database.initialize();

        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", 8080), 0
        );

        createLoggedContext(server, "/health", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("GET")) {
                    sendJson(
                        exchange,
                        405,
                        "{\"error\":{\"code\":405,\"message\":\"Method not allowed\",\"details\":[]}}"
                    );
                    return;
                }

                sendJson(
                    exchange,
                    200,
                    "{\"status\":\"ok\",\"service\":\"Marquee\"}"
                );
            } finally {
                exchange.close();
            }
        });

        FilmRepository filmRepository =
            new FilmRepository(database);
        FilmService filmService =
            new FilmService(filmRepository);

        createLoggedContext(
            server,
            "/films",
            new FilmHandler(filmService)
        );

        GenreRepository genreRepository =
            new GenreRepository(database);
        GenreService genreService =
            new GenreService(genreRepository);

        createLoggedContext(
            server,
            "/genres",
            new GenreHandler(genreService)
        );

        ScreeningRepository screeningRepository =
            new ScreeningRepository(database);
        ScreeningService screeningService =
            new ScreeningService(screeningRepository);

        createLoggedContext(
            server,
            "/screenings",
            new ScreeningHandler(screeningService)
        );

        createLoggedContext(server, "/", exchange -> {
            try {
                sendJson(
                    exchange,
                    404,
                    "{\"error\":{\"code\":404,\"message\":\"Route not found\",\"details\":[]}}"
                );
            } finally {
                exchange.close();
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Marquee listening on http://localhost:8080");
        System.out.println(
            "Database: " + databasePath.toAbsolutePath()
        );
    }

    private static HttpContext createLoggedContext(
            HttpServer server,
            String path,
            HttpHandler handler
    ) {
        HttpContext context = server.createContext(path, handler);
        context.getFilters().add(new RequestLoggingFilter());
        return context;
    }

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=utf-8"
        );

        exchange.getResponseHeaders().set(
            "Cache-Control",
            "no-store"
        );

        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
