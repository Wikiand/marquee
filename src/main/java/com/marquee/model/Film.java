package com.marquee.model;

import java.util.List;

public record Film(
    int filmId,
    String title,
    int year,
    String description,
    String createdAt,
    List<Genre> genres
) {
    public Film {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }
}
