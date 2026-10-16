package com.marquee.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FilmInput(
    String title,
    Integer year,
    String description,
    List<Integer> genreIds
) {}
