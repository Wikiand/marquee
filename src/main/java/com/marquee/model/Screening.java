package com.marquee.model;

public record Screening(
    int screeningId,
    int filmId,
    String filmTitle,
    String venue,
    String screeningTime
) {
}
