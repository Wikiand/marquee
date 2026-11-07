package com.marquee.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RequestLoggingFilter extends Filter {

    private final ObjectMapper objectMapper;
    private final PrintStream output;

    public RequestLoggingFilter() {
        this(System.out);
    }

    public RequestLoggingFilter(PrintStream output) {
        this.objectMapper = new ObjectMapper();
        this.output = output;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain)
            throws IOException {

        long startTime = System.nanoTime();
        int status = 500;

        try {
            chain.doFilter(exchange);

            int responseCode = exchange.getResponseCode();

            if (responseCode != -1) {
                status = responseCode;
            }
        } finally {
            int responseCode = exchange.getResponseCode();

            if (responseCode != -1) {
                status = responseCode;
            }

            long elapsedNanos = System.nanoTime() - startTime;
            double durationMillis =
                Math.round(elapsedNanos / 10_000.0) / 100.0;

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("method", exchange.getRequestMethod());
            logEntry.put("path", exchange.getRequestURI().getPath());
            logEntry.put("status", status);
            logEntry.put("duration_ms", durationMillis);

            try {
                output.println(
                    objectMapper.writeValueAsString(logEntry)
                );
            } catch (JsonProcessingException exception) {
                output.println(
                    "{\"level\":\"ERROR\",\"message\":\"Request log serialization failed\"}"
                );
            }
        }
    }

    @Override
    public String description() {
        return "Logs HTTP requests as structured JSON lines";
    }
}
