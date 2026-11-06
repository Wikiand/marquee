
package com.marquee.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RequestBodyReaderTest {

    private RequestBodyReader reader;

    @BeforeEach
    void setUp() {
        reader = new RequestBodyReader(new ObjectMapper());
    }

    @Test
    void shouldReadValidJsonObject() throws Exception {
        String body = "{\"title\":\"Inception\"}";

        JsonNode result = reader.readJsonObject(input(body));

        assertEquals("Inception", result.get("title").asText());
    }

    @Test
    void shouldRejectEmptyBody() {
        RequestBodyException exception = assertThrows(
                RequestBodyException.class,
                () -> reader.readJsonObject(input(""))
        );

        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void shouldRejectMalformedJson() {
        RequestBodyException exception = assertThrows(
                RequestBodyException.class,
                () -> reader.readJsonObject(input("{"))
        );

        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void shouldRejectNonObjectJson() {
        RequestBodyException exception = assertThrows(
                RequestBodyException.class,
                () -> reader.readJsonObject(input("[1, 2, 3]"))
        );

        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void shouldRejectTrailingJsonTokens() {
        RequestBodyException exception = assertThrows(
                RequestBodyException.class,
                () -> reader.readJsonObject(
                        input("{\"title\":\"First\"} {\"title\":\"Second\"}")
                )
        );

        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void shouldAcceptBodyAtMaximumSize() throws Exception {
        String prefix = "{\"value\":\"";
        String suffix = "\"}";
        int paddingLength = RequestBodyReader.MAX_BODY_SIZE
                - prefix.length()
                - suffix.length();

        String body = prefix + "a".repeat(paddingLength) + suffix;

        JsonNode result = reader.readJsonObject(input(body));

        assertTrue(result.has("value"));
        assertEquals(paddingLength, result.get("value").asText().length());
    }

    @Test
    void shouldRejectBodyExceedingMaximumSize() {
        String body = " ".repeat(RequestBodyReader.MAX_BODY_SIZE + 1);

        RequestBodyException exception = assertThrows(
                RequestBodyException.class,
                () -> reader.readJsonObject(input(body))
        );

        assertEquals(413, exception.getStatusCode());
    }

    private ByteArrayInputStream input(String body) {
        return new ByteArrayInputStream(
                body.getBytes(StandardCharsets.UTF_8)
        );
    }
}
