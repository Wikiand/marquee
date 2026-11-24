package com.marquee.web;

import com.sun.net.httpserver.Headers;

import java.util.HashMap;
import java.util.Map;

public final class ContentNegotiator {

    private ContentNegotiator() {
    }

    public static boolean prefersHtml(Headers headers) {
        String accept = headers.getFirst("Accept");

        if (accept == null || accept.isBlank()) {
            return false;
        }

        Map<String, Double> qualities = new HashMap<>();

        for (String entry : accept.split(",")) {
            String[] parts = entry.trim().split(";");

            String mediaType = parts[0].trim().toLowerCase();
            double quality = 1.0;

            for (int i = 1; i < parts.length; i++) {
                String parameter = parts[i].trim();

                if (parameter.startsWith("q=")) {
                    try {
                        quality = Double.parseDouble(
                            parameter.substring(2)
                        );
                    } catch (NumberFormatException exception) {
                        quality = 0.0;
                    }
                }
            }

            if (quality < 0.0 || quality > 1.0) {
                quality = 0.0;
            }

            qualities.merge(
                mediaType,
                quality,
                Math::max
            );
        }

        double htmlQuality = qualityFor(
            qualities, "text/html", "text/*"
        );

        double jsonQuality = qualityFor(
            qualities, "application/json", "application/*"
        );

        double wildcardQuality = qualities.getOrDefault("*/*", 0.0);

        if (!qualities.containsKey("text/html")
                && !qualities.containsKey("text/*")) {
            htmlQuality = wildcardQuality;
        }

        if (!qualities.containsKey("application/json")
                && !qualities.containsKey("application/*")) {
            jsonQuality = wildcardQuality;
        }

        // JSON remains the default when preferences are equal.
        return htmlQuality > jsonQuality;
    }

    private static double qualityFor(
            Map<String, Double> qualities,
            String exact,
            String categoryWildcard
    ) {
        if (qualities.containsKey(exact)) {
            return qualities.get(exact);
        }

        if (qualities.containsKey(categoryWildcard)) {
            return qualities.get(categoryWildcard);
        }

        return 0.0;
    }
}
