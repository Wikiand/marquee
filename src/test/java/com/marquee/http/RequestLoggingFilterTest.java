package com.marquee.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLoggingFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void logsRequestAsStructuredJsonWithoutSensitiveData()
            throws Exception {

        ByteArrayOutputStream capturedOutput =
            new ByteArrayOutputStream();

        CountDownLatch logWritten = new CountDownLatch(1);

        PrintStream logOutput = new PrintStream(
            capturedOutput,
            true,
            StandardCharsets.UTF_8
        ) {
            @Override
            public void println(String value) {
                super.println(value);
                logWritten.countDown();
            }
        };

        try (logOutput) {
            HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
            );

            try {
                var context = server.createContext(
                    "/test",
                    exchange -> {
                        byte[] response =
                            "{\"result\":\"created\"}"
                                .getBytes(StandardCharsets.UTF_8);

                        exchange.getResponseHeaders().set(
                            "Content-Type",
                            "application/json"
                        );

                        exchange.sendResponseHeaders(
                            201, response.length
                        );

                        try (var responseBody =
                                exchange.getResponseBody()) {
                            responseBody.write(response);
                        }
                    }
                );

                context.getFilters().add(
                    new RequestLoggingFilter(logOutput)
                );

                server.start();

                int port = server.getAddress().getPort();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                        "http://127.0.0.1:" + port
                            + "/test?token=hidden-query"
                    ))
                    .header(
                        "Content-Type",
                        "text/plain"
                    )
                    .POST(HttpRequest.BodyPublishers.ofString(
                        "sensitive-request-body"
                    ))
                    .build();

                HttpResponse<String> response =
                    HttpClient.newHttpClient().send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                    );

                assertEquals(201, response.statusCode());

                assertTrue(
                    logWritten.await(2, TimeUnit.SECONDS),
                    "Request logging filter did not write a log entry"
                );

                String logLine = capturedOutput
                    .toString(StandardCharsets.UTF_8)
                    .trim();

                assertFalse(logLine.isBlank());

                JsonNode log = objectMapper.readTree(logLine);

                assertTrue(log.hasNonNull("timestamp"));
                assertEquals("POST", log.get("method").asText());
                assertEquals("/test", log.get("path").asText());
                assertEquals(201, log.get("status").asInt());
                assertTrue(log.get("duration_ms").isNumber());

                assertFalse(logLine.contains("hidden-query"));
                assertFalse(
                    logLine.contains("sensitive-request-body")
                );

            } finally {
                server.stop(0);
            }
        }
    }
}
