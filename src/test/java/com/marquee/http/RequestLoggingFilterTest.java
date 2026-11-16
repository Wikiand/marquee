package com.marquee.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                    .header("Content-Type", "text/plain")
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

    @Test
    void usesDefaultStatusWhenResponseCodeIsUnavailable()
            throws Exception {

        ByteArrayOutputStream capturedOutput =
            new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(
                capturedOutput,
                true,
                StandardCharsets.UTF_8
        )) {
            RequestLoggingFilter filter =
                new RequestLoggingFilter(output);

            TestExchange exchange = new TestExchange();

            Filter.Chain chain = new Filter.Chain(
                List.of(),
                ignored -> {
                    // No response headers are sent.
                }
            );

            filter.doFilter(exchange, chain);

            JsonNode log = objectMapper.readTree(
                capturedOutput.toString(StandardCharsets.UTF_8).trim()
            );

            assertEquals(500, log.get("status").asInt());
            assertEquals("GET", log.get("method").asText());
            assertEquals("/test", log.get("path").asText());
            assertTrue(log.get("duration_ms").isNumber());
        }
    }

    @Test
    void logsRequestWhenHandlerThrowsException()
            throws Exception {

        ByteArrayOutputStream capturedOutput =
            new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(
                capturedOutput,
                true,
                StandardCharsets.UTF_8
        )) {
            RequestLoggingFilter filter =
                new RequestLoggingFilter(output);

            TestExchange exchange = new TestExchange();

            Filter.Chain chain = new Filter.Chain(
                List.of(),
                ignored -> {
                    throw new IOException("Simulated handler failure");
                }
            );

            IOException exception = assertThrows(
                IOException.class,
                () -> filter.doFilter(exchange, chain)
            );

            assertEquals(
                "Simulated handler failure",
                exception.getMessage()
            );

            JsonNode log = objectMapper.readTree(
                capturedOutput.toString(StandardCharsets.UTF_8).trim()
            );

            assertEquals(500, log.get("status").asInt());
            assertEquals("/test", log.get("path").asText());
        }
    }

    @Test
    void defaultConstructorCreatesUsableFilter() {
        RequestLoggingFilter filter = new RequestLoggingFilter();

        assertEquals(
            "Logs HTTP requests as structured JSON lines",
            filter.description()
        );
    }

    @Test
    void descriptionReturnsExpectedText() {
        RequestLoggingFilter filter =
            new RequestLoggingFilter(
                new PrintStream(new ByteArrayOutputStream())
            );

        assertEquals(
            "Logs HTTP requests as structured JSON lines",
            filter.description()
        );
    }

    private static class TestExchange extends HttpExchange {

        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();

        private final ByteArrayOutputStream responseBody =
            new ByteArrayOutputStream();

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return URI.create("/test");
        }

        @Override
        public String getRequestMethod() {
            return "GET";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int responseCode, long length) {
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public int getResponseCode() {
            return -1;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(
                InputStream input,
                OutputStream output
        ) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
