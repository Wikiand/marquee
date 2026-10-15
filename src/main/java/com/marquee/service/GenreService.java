package com.marquee.service;

import com.marquee.model.Genre;
import com.marquee.repository.GenreRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class GenreService {

    private final GenreRepository repository;

    public GenreService(GenreRepository repository) {
        this.repository = repository;
    }

    public List<Genre> listGenres() throws SQLException {
        return repository.findAll();
    }

    public Genre createGenre(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Genre name is required and cannot be blank"
            );
        }

        return repository.create(name.trim());
    }

    public Optional<Genre> updateGenre(
            int genreId, String name
    ) throws SQLException {
        if (genreId <= 0) {
            throw new IllegalArgumentException(
                "Genre ID must be a positive integer"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Genre name is required and cannot be blank"
            );
        }

        return repository.update(genreId, name.trim());
    }
}
