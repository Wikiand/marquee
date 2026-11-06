
package com.marquee.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class RequestBodyReader {

    public static final int MAX_BODY_SIZE = 1024 * 1024;

    private final ObjectMapper mapper;

    public RequestBodyReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode readJsonObject(HttpExchange exchange)
            throws IOException, RequestBodyException {
        return readJsonObject(exchange.getRequestBody());
    }

    JsonNode readJsonObject(InputStream input)
            throws IOException, RequestBodyException {

        byte[] body = readLimited(input, MAX_BODY_SIZE);

        if (body.length == 0) {
            throw new RequestBodyException(400, "Request body is required");
        }

        JsonNode json;

        try {
            json = mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(body);
        } catch (JsonProcessingException exception) {
            throw new RequestBodyException(400, "Invalid JSON request body");
        }

        if (json == null || !json.isObject()) {
            throw new RequestBodyException(
                    400,
                    "Request body must be a JSON object"
            );
        }

        return json;
    }

    private byte[] readLimited(InputStream input, int limit)
            throws IOException, RequestBodyException {

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int bytesRead;

        while (true) {
            int remaining = limit - total;
            int readSize = Math.min(buffer.length, remaining + 1);

            bytesRead = input.read(buffer, 0, readSize);

            if (bytesRead == -1) {
                break;
            }

            if (bytesRead > remaining) {
                throw new RequestBodyException(
                        413,
                        "Request body too large"
                );
            }

            output.write(buffer, 0, bytesRead);
            total += bytesRead;
        }

        return output.toByteArray();
    }
}
