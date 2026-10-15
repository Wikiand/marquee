package com.marquee.service;

import com.marquee.model.Film;
import com.marquee.model.FilmInput;
import com.marquee.repository.FilmRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class FilmService {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PER_PAGE = 20;
    public static final int MAX_PER_PAGE = 100;

    private final FilmRepository repository;

    public FilmService(FilmRepository repository) {
        this.repository = repository;
    }

    public FilmPage listFilms(
            String genre,
            Integer year,
            String title,
            int page,
            int perPage
    ) throws SQLException {

        if (year != null && year <= 0) {
            throw new IllegalArgumentException(
                "Year must be a positive integer"
            );
        }

        if (page < 1) {
            throw new IllegalArgumentException(
                "Page must be at least 1"
            );
        }

        if (perPage < 1 || perPage > MAX_PER_PAGE) {
            throw new IllegalArgumentException(
                "per_page must be between 1 and " + MAX_PER_PAGE
            );
        }

        List<Film> films = repository.findAll(
            genre, year, title, page, perPage
        );

        int total = repository.count(genre, year, title);

        return new FilmPage(page, perPage, total, films);
    }

    public Film createFilm(FilmInput input) throws SQLException {
        if (input == null) {
            throw new IllegalArgumentException(
                "Request body is required"
            );
        }

        if (input.title() == null || input.title().isBlank()) {
            throw new IllegalArgumentException(
                "Title is required and cannot be blank"
            );
        }

        if (input.year() == null || input.year() <= 0) {
            throw new IllegalArgumentException(
                "Year must be a positive integer"
            );
        }

        String title = input.title().trim();

        return repository.create(
            title,
            input.year(),
            input.description()
        );
    }

    public Optional<Film> updateFilm(
            int filmId,
            FilmInput input
    ) throws SQLException {

        if (filmId <= 0) {
            throw new IllegalArgumentException(
                "Film ID must be a positive integer"
            );
        }

        if (input == null) {
            throw new IllegalArgumentException(
                "Request body is required"
            );
        }

        if (input.title() == null || input.title().isBlank()) {
            throw new IllegalArgumentException(
                "Title is required and cannot be blank"
            );
        }

        if (input.year() == null || input.year() <= 0) {
            throw new IllegalArgumentException(
                "Year must be a positive integer"
            );
        }

        return repository.update(
            filmId,
            input.title().trim(),
            input.year(),
            input.description()
        );
    }

    public Optional<Film> getFilm(int filmId) throws SQLException {
        if (filmId <= 0) {
            throw new IllegalArgumentException(
                "Film ID must be a positive integer"
            );
        }

        return repository.findById(filmId);
    }

    public record FilmPage(
        int page,
        int per_page,
        int total,
        List<Film> films
    ) {
        public FilmPage {
            films = List.copyOf(films);
        }
    }
}
