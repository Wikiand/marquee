# ADR-002: Structured request and server-error logging

- Status: Accepted
- Decision area: HTTP observability and error handling

## Context

HTTP request failures need to be diagnosable without exposing internal
implementation details, SQL errors, or stack traces to API clients.

Request metadata and unexpected application exceptions serve different
diagnostic purposes and should be recorded separately.

## Decision

Use a custom HTTP filter named `RequestLoggingFilter` to produce one
structured JSON request log entry per request.

The request log includes:

- Timestamp
- HTTP method
- Request path
- HTTP response status
- Request duration in milliseconds

Request bodies are not included.

Use `ServerErrorLogger` to record unexpected server-side exceptions,
including internal diagnostic details and stack traces.

Unexpected failures return a generic HTTP 500 response to the client.
Expected client errors retain their appropriate status codes, including
400, 404, 405, and 409.

## Consequences

- Request activity can be inspected using structured log entries.
- Unexpected failures retain diagnostic information for developers.
- API responses do not disclose internal exception details.
- Request bodies are excluded from request logging.
- Logs currently use the application's configured Java logging output;
  production deployments may require log aggregation, retention,
  rotation, and access controls.

## Verification

Handler tests verify generic 500 responses and server-side exception
logging. Request logging tests verify structured request metadata,
including status and duration.
