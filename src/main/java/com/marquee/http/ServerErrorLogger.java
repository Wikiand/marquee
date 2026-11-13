package com.marquee.http;

import com.sun.net.httpserver.HttpExchange;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs unexpected server-side exceptions without exposing
 * internal details in HTTP responses.
 */
public final class ServerErrorLogger {

    private static final Logger LOGGER =
            Logger.getLogger(ServerErrorLogger.class.getName());

    private ServerErrorLogger() {
    }

    public static void log(
            HttpExchange exchange,
            Exception exception
    ) {
        String message = String.format(
            "API request failed: method=%s path=%s",
            exchange.getRequestMethod(),
            exchange.getRequestURI().getPath()
        );

        LOGGER.log(Level.SEVERE, message, exception);
    }
}
