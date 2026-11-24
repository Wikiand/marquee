package com.marquee.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public final class StaticResourceHandler implements HttpHandler {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
        ".css", "text/css; charset=utf-8",
        ".js", "application/javascript; charset=utf-8",
        ".png", "image/png",
        ".jpg", "image/jpeg",
        ".jpeg", "image/jpeg",
        ".svg", "image/svg+xml",
        ".ico", "image/x-icon"
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();

            if (!path.startsWith("/static/")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String resourcePath = path.substring(1);

            if (resourcePath.contains("..")
                    || resourcePath.contains("\\")
                    || resourcePath.contains("%")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream(resourcePath)) {

                if (input == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                byte[] bytes = input.readAllBytes();

                exchange.getResponseHeaders().set(
                    "Content-Type",
                    contentType(resourcePath)
                );
                exchange.getResponseHeaders().set(
                    "Cache-Control",
                    "no-store"
                );

                exchange.sendResponseHeaders(200, bytes.length);

                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            }
        }
    }

    private String contentType(String path) {
        int dot = path.lastIndexOf('.');

        if (dot < 0) {
            return "application/octet-stream";
        }

        return CONTENT_TYPES.getOrDefault(
            path.substring(dot).toLowerCase(),
            "application/octet-stream"
        );
    }
}
