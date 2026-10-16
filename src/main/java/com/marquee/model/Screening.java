package com.marquee.model;

public record Screening(
    int screeningId,
    int filmId,
    String venue,
    String screeningTime
) {
}
