package com.marquee.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
